import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import model.Action;
import model.AttackAction;
import model.AutoAttack;
import model.BuildAction;
import model.DebugCommand;
import model.Entity;
import model.EntityAction;
import model.EntityProperties;
import model.EntityType;
import model.MoveAction;
import model.Player;
import model.PlayerView;
import model.RepairAction;
import model.Vec2Int;

public class MyStrategy {

    private Map<EntityType, Integer> maxHealthByType;
    private Map<EntityType, List<Entity>> myEntities = new HashMap<>();
    private Map<Integer, Map<EntityType, List<Entity>>> enemyEntities = new HashMap<>();
    private int maxPopulation = 0;

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {

        initConfig(playerView);

        Action result = new Action(new java.util.HashMap<>());
        int myId = playerView.getMyId();
        Player me = Arrays.stream(playerView.getPlayers()).filter(p -> p.getId() == playerView.getMyId()).findAny().orElse(null);
        if (me == null) {
            return result;
        }

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != myId) {
                continue;
            }

            RepairAction repairAction = repair(entity, playerView);

            result.getEntityActions().put(entity.getId(), new EntityAction(
                    move(entity, playerView, me),
                    repairAction != null ? null : build(entity, playerView),
                    attack(entity, playerView, validAutoAttackTargets(entity)),
                    repairAction
            ));
        }
        return result;
    }


    private void initConfig(PlayerView playerView) {
        if (maxHealthByType == null) {
            maxHealthByType = playerView.getEntityProperties().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getMaxHealth()));
        }

        maxPopulation = myEntities.values().stream()
                .flatMap(List::stream)
                .mapToInt(e -> playerView.getEntityProperties().get(e.getEntityType()).getPopulationProvide())
                .sum();

        myEntities = Arrays.stream(playerView.getEntities())
                .filter(entity -> entity.getPlayerId() != null && entity.getPlayerId() == playerView.getMyId())
                .collect(Collectors.groupingBy(Entity::getEntityType));

        enemyEntities = Arrays.stream(playerView.getEntities())
                .filter(entity -> entity.getPlayerId() != null && entity.getPlayerId() != playerView.getMyId())
                .collect(Collectors.groupingBy(Entity::getPlayerId, Collectors.groupingBy(Entity::getEntityType)));
    }

    private RepairAction repair(Entity entity, PlayerView playerView) {
        if (entity.getEntityType() != EntityType.BUILDER_UNIT) {
            return null;
        }

        Entity target = Arrays.stream(playerView.getEntities())
                .filter(e -> e.getPlayerId() != null && e.getPlayerId() == playerView.getMyId())
                .filter(e -> e.getEntityType() == EntityType.HOUSE)
                .filter(e -> e.getHealth() < maxHealthByType.get(e.getEntityType()))
                .findAny().orElse(null);

        return target != null ? new RepairAction(target.getId()) : null;
    }

    private MoveAction move(Entity entity, PlayerView playerView, Player me) {
        EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

        if (!properties.isCanMove()) {
            return null;
        }

        System.out.println("MOVE: " + entity.getEntityType());

        return new MoveAction(
                new Vec2Int(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                true,
                true);
    }

    private BuildAction builderUnitBuildAction(Entity builder, PlayerView playerView) {
        EntityProperties properties = playerView.getEntityProperties().get(builder.getEntityType());

        List<Entity> houses = myEntities.get(EntityType.HOUSE);
        List<Entity> turrets = myEntities.get(EntityType.TURRET);

        EntityType targetEntityType;
        if (houses.size() / 3 > turrets.size()) {
            targetEntityType = EntityType.TURRET;
        } else {
            targetEntityType = EntityType.HOUSE;
        }

        return new BuildAction(
                targetEntityType,
                new Vec2Int(
                        builder.getPosition().getX() + properties.getSize(),
                        builder.getPosition().getY() + properties.getSize() - 1
                )
        );
    }

    private BuildAction builderBaseBuildAction(Entity builderBase, PlayerView playerView) {
        EntityProperties properties = playerView.getEntityProperties().get(builderBase.getEntityType());

        List<Entity> builders = myEntities.get(EntityType.BUILDER_UNIT);
        List<Entity> rangers = myEntities.get(EntityType.RANGED_UNIT);

        EntityType entityType = EntityType.BUILDER_UNIT;

        if (rangers != null) {
            if (builders != null && builders.size() > rangers.size()) {
                return null;
            }
        }

        long currentUnits = Utils.currentUnits(playerView, entityType);

        if ((currentUnits + 1) * playerView.getEntityProperties().get(entityType).getPopulationUse() <= maxPopulation) {
            return new BuildAction(
                    entityType,
                    new Vec2Int(
                            builderBase.getPosition().getX() + properties.getSize(),
                            builderBase.getPosition().getY() + properties.getSize() - 1
                    )
            );
        }

        return null;
    }

    private BuildAction rangedBaseBuildAction(Entity rangedBase, PlayerView playerView) {
        EntityProperties properties = playerView.getEntityProperties().get(rangedBase.getEntityType());

        List<Entity> builders = myEntities.get(EntityType.BUILDER_UNIT);

        if (builders == null || builders.size() < 10) {
            return null;
        }

        return new BuildAction(
                EntityType.RANGED_UNIT,
                new Vec2Int(
                        rangedBase.getPosition().getX() + properties.getSize(),
                        rangedBase.getPosition().getY() + properties.getSize() - 1
                )
        );
    }

    private BuildAction meleeBaseBuildAction(Entity meleeBase, PlayerView playerView) {
        return null;
    }

    private BuildAction build(Entity entity, PlayerView playerView) {
        EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

        if (properties.getBuild() == null) {
            return null;
        }

        EntityType entityType = properties.getBuild().getOptions()[0];

        List<Entity> builders = myEntities.get(EntityType.BUILDER_UNIT);
        List<Entity> rangers = myEntities.get(EntityType.RANGED_UNIT);

        if (entityType != EntityType.HOUSE && entityType != EntityType.BUILDER_UNIT) {
            if (builders.size() < 10) {
                return null;
            }
        }

        if (entityType == EntityType.BUILDER_UNIT) {
            if (rangers != null && !rangers.isEmpty()) {
                if (myEntities.get(EntityType.BUILDER_UNIT) != null
                        && builders.size() > rangers.size()) {
                    return null;
                }
            }
        }

        long currentUnits = Utils.currentUnits(playerView, entityType);

        if ((currentUnits + 1) * playerView.getEntityProperties().get(entityType).getPopulationUse() <= maxPopulation) {
            if (entityType == EntityType.MELEE_UNIT) {
                return null;
            }

            System.out.println("BUILD: " + entityType);
            return new BuildAction(
                    entityType,
                    new Vec2Int(
                            entity.getPosition().getX() + properties.getSize(),
                            entity.getPosition().getY() + properties.getSize() - 1
                    )
            );
        }

        return null;
    }

    private AttackAction attack(Entity entity, PlayerView playerView, EntityType[] validAutoAttackTargets) {
        EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

        return new AttackAction(
                null, new AutoAttack(properties.getSightRange(), validAutoAttackTargets)
        );
    }

    //private Map<Integer, EntityAction> entityAction(Entity entity, PlayerView playerView) {
    //    EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());
    //
    //    return switch (entity.getEntityType()) {
    //        case BUILDER_UNIT -> builderAction(entity, playerView);
    //        case MELEE_UNIT -> meleeAction();
    //        case RANGED_UNIT -> rangedAction();
    //        case BUILDER_BASE -> builderBaseAction();
    //        case MELEE_BASE -> meleeBaseAction();
    //        case RANGED_BASE -> rangedBaseAction();
    //        default -> Collections.emptyMap();
    //    };
    //}

    private EntityType[] validAutoAttackTargets(Entity entity) {
        if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
            return new EntityType[]{EntityType.RESOURCE};
        } else {
            return new EntityType[0];
        }
    }

    //private Map<Integer, EntityAction> builderAction(Entity entity, PlayerView playerView) {
    //    new EntityAction(
    //            move(entity, playerView),
    //            build(entity, playerView),
    //            attack(entity, playerView, validAutoAttackTargets(entity)),
    //            null
    //    );
    //
    //    return null;
    //}

    private Map<Integer, EntityAction> builderBaseAction() {
        return null;
    }

    private Map<Integer, EntityAction> meleeAction() {
        return null;
    }

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }
}

class Utils {

    public static long currentUnits(PlayerView playerView, EntityType entityType) {
        return Arrays.stream(playerView.getEntities())
                .filter(e -> e.getPlayerId() != null && e.getPlayerId() == playerView.getMyId() && e.getEntityType() == entityType)
                .count();
    }
}
