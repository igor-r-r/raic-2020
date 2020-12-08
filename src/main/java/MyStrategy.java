import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

import static config.Config.*;

public class MyStrategy {

    private final Random random = new Random();
    private Player me;
    private Map<Integer, EntityConfig> defenders = new HashMap<>();
    private Map<Integer, EntityConfig> attackers = new HashMap<>();

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        initConfig(playerView);

        defenders = defenders.entrySet().stream().filter(e -> myIdToEntity.containsKey(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
        attackers = attackers.entrySet().stream().filter(e -> myIdToEntity.containsKey(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));

        Action result = new Action(new java.util.HashMap<>());
        int myId = playerView.getMyId();
        me = Arrays.stream(playerView.getPlayers()).filter(p -> p.getId() == playerView.getMyId()).findAny().orElse(null);
        if (me == null) {
            return result;
        }

        for (Entity entity : playerView.getEntities()) {
            if (entity.getPlayerId() == null || entity.getPlayerId() != myId) {
                continue;
            }

            result.getEntityActions().put(entity.getId(), entityAction(entity));
        }
        return result;
    }

    private Tuple<RepairAction, Entity> repair(Entity entity) {
        if (entity.getEntityType() != EntityType.BUILDER_UNIT) {
            return null;
        }

        Entity target = Arrays.stream(playerView.getEntities())
                .filter(e -> e.getPlayerId() != null && e.getPlayerId() == playerView.getMyId())
                .filter(e -> buildingTypes.contains(e.getEntityType()))
                .filter(e -> e.getHealth() < maxHealthByType.get(e.getEntityType()))
                .findAny().orElse(null);

        return target != null ? new Tuple<>(new RepairAction(target.getId()), target) : null;
    }

    private MoveAction move(Entity entity) {
        return new MoveAction(
                new Vec2Int(playerView.getMapSize() - 1, playerView.getMapSize() - 1),
                true,
                true);
    }

    private MoveAction move(Entity entity, Vec2Int target) {
        return new MoveAction(
                target,
                true,
                true);
    }

    private EntityAction entityAction(Entity entity) {
        return switch (entity.getEntityType()) {
            case BUILDER_UNIT -> builderAction(entity);
            case MELEE_UNIT -> meleeAction(entity);
            case RANGED_UNIT -> rangedAction(entity);
            case BUILDER_BASE -> builderBaseAction(entity);
            case MELEE_BASE -> meleeBaseAction(entity);
            case RANGED_BASE -> rangedBaseAction(entity);
            case TURRET -> turretAction(entity);
            default -> new EntityAction();
        };
    }

    private BuildAction builderBuildAction(Entity builder) {
        EntityProperties properties = playerView.getEntityProperties().get(builder.getEntityType());

        List<Entity> houses = myEntities.get(EntityType.HOUSE);
        List<Entity> turrets = myEntities.get(EntityType.TURRET);

        EntityType targetEntityType;
        if (houses == null) {
            targetEntityType = EntityType.HOUSE;
        } else if (turrets == null || houses.size() / 3 > turrets.size()) {
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

    //private Vec2Int buildPosition(EntityType entityType) {
    //    return switch (entityType) {
    //        case HOUSE ->
    //    };
    //}
    //
    //private Vec2Int housePosition() {
    //    for (int )
    //}

    //private boolean canBuild(EntityType entityType, Vec2Int position) {
    //
    //}


    private BuildAction builderBaseBuildAction(Entity builderBase) {
        EntityProperties properties = playerView.getEntityProperties().get(builderBase.getEntityType());

        List<Entity> builders = myEntities.get(EntityType.BUILDER_UNIT);
        List<Entity> rangers = myEntities.get(EntityType.RANGED_UNIT);

        EntityType entityType = EntityType.BUILDER_UNIT;

        if (initialBuilders) {
            if (rangers != null) {
                if (builders != null && builders.size() > rangers.size()) {
                    return null;
                }
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

    private BuildAction rangedBaseBuildAction(Entity rangedBase) {
        EntityProperties properties = playerView.getEntityProperties().get(rangedBase.getEntityType());
        EntityType entityType = EntityType.RANGED_UNIT;

        List<Entity> builders = myEntities.get(EntityType.BUILDER_UNIT);

        if (builders == null) {
            return null;
        }

        if (!initialBuilders) {
            if (builders.size() >= 5) {
                initialBuilders = true;
            } else {
                return null;
            }
        }

        long currentUnits = Utils.currentUnits(playerView, entityType);

        if ((currentUnits + 1) * playerView.getEntityProperties().get(entityType).getPopulationUse() <= maxPopulation) {
            return new BuildAction(
                    entityType,
                    new Vec2Int(
                            rangedBase.getPosition().getX() + properties.getSize(),
                            rangedBase.getPosition().getY() + properties.getSize() - 1
                    )
            );
        }

        return null;
    }

    private BuildAction meleeBaseBuildAction(Entity meleeBase) {
        if (me.getResource() > 100) {
            EntityProperties properties = playerView.getEntityProperties().get(meleeBase.getEntityType());

            EntityType entityType = EntityType.MELEE_UNIT;
            long currentUnits = Utils.currentUnits(playerView, entityType);

            if ((currentUnits + 1) * playerView.getEntityProperties().get(entityType).getPopulationUse() <= maxPopulation) {
                return new BuildAction(
                        entityType,
                        new Vec2Int(
                                meleeBase.getPosition().getX() + properties.getSize(),
                                meleeBase.getPosition().getY() + properties.getSize() - 1
                        )
                );
            }
        }
        return null;
    }

    private AttackAction attack(Entity entity, EntityType[] validAutoAttackTargets) {
        EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

        return new AttackAction(
                null, new AutoAttack(properties.getSightRange(), validAutoAttackTargets)
        );
    }


    private EntityAction builderAction(Entity entity) {
        Tuple<RepairAction, Entity> repairAction = repair(entity);

        MoveAction moveAction;
        if (repairAction != null) {
            moveAction = move(entity, repairAction.right.getPosition());
        } else {
            moveAction = move(entity);
        }

        AttackAction attackAction = null;
        if (repairAction == null
                || repairAction.right.getEntityType() == EntityType.HOUSE
                || repairAction.right.getEntityType() == EntityType.TURRET) {
            attackAction = attack(entity, validAutoAttackTargets(entity));
        }

        return new EntityAction(
                moveAction,
                repairAction != null ? null : builderBuildAction(entity),
                attackAction,
                repairAction == null ? null : repairAction.left
        );
    }

    private EntityAction meleeAction(Entity entity) {
        MoveAction moveAction = move(entity, randomDefensePosition());
        AttackAction attackAction = attack(entity, validAutoAttackTargets(entity));

        if (defenders.containsKey(entity.getId())) {
            EntityConfig entityConfig = defenders.get(entity.getId());
            if (entityConfig.moveAction != null
                    && !occupiedPositions.contains(entityConfig.moveAction.getTarget())
                    && !entity.getPosition().equals(entityConfig.moveAction.getTarget())) {
                moveAction = entityConfig.moveAction;
            }
        } else if (attackers.containsKey(entity.getId())) {
            moveAction = move(entity, currentEnemyBase);
        } else if (defenders.size() < 10 || defenders.size() * 2 < attackers.size()) {
            attackAction = new AttackAction(null, new AutoAttack(20, validAutoAttackTargets(entity)));
            defenders.put(entity.getId(), new EntityConfig(entity, true, moveAction.getTarget(), moveAction, attackAction));
        } else {
            moveAction = move(entity, currentEnemyBase);
            attackers.put(entity.getId(), new EntityConfig(entity, true, moveAction.getTarget(), moveAction, attackAction));
        }

        return new EntityAction(
                moveAction,
                null,
                attackAction,
                null
        );
    }

    private Vec2Int randomDefensePosition() {
        return allowedDefensePositions != null && allowedDefensePositions.size() > 0
                ? allowedDefensePositions.get(random.nextInt(allowedDefensePositions.size()))
                : middle;
    }

    private EntityAction rangedAction(Entity entity) {
        MoveAction moveAction = move(entity, randomDefensePosition());
        AttackAction attackAction = attack(entity, validAutoAttackTargets(entity));

        if (defenders.containsKey(entity.getId())) {
            EntityConfig entityConfig = defenders.get(entity.getId());
            if (entityConfig.moveAction != null
                    && !occupiedPositions.contains(entityConfig.moveAction.getTarget())
                    && !entity.getPosition().equals(entityConfig.moveAction.getTarget())) {
                moveAction = entityConfig.moveAction;
            }
        } else if (attackers.containsKey(entity.getId())) {
            moveAction = move(entity, currentEnemyBase);
        } else if (defenders.size() < 10 || defenders.size() * 2 < attackers.size()) {
            attackAction = new AttackAction(null, new AutoAttack(20, validAutoAttackTargets(entity)));
            defenders.put(entity.getId(), new EntityConfig(entity, true, moveAction.getTarget(), moveAction, attackAction));
        } else {
            moveAction = move(entity, currentEnemyBase);
            attackers.put(entity.getId(), new EntityConfig(entity, true, moveAction.getTarget(), moveAction, attackAction));
        }

        return new EntityAction(
                moveAction,
                null,
                attackAction,
                null
        );
    }

    private EntityAction builderBaseAction(Entity entity) {
        return new EntityAction(
                null,
                builderBaseBuildAction(entity),
                null,
                null
        );
    }

    private EntityAction meleeBaseAction(Entity entity) {
        return new EntityAction(
                null,
                meleeBaseBuildAction(entity),
                null,
                null
        );
    }

    private EntityAction rangedBaseAction(Entity entity) {
        return new EntityAction(
                null,
                rangedBaseBuildAction(entity),
                null,
                null
        );
    }

    private EntityAction turretAction(Entity entity) {
        return new EntityAction(
                null,
                null,
                attack(entity, validAutoAttackTargets(entity)),
                null
        );
    }

    private EntityType[] validAutoAttackTargets(Entity entity) {
        if (entity.getEntityType() == EntityType.BUILDER_UNIT) {
            return new EntityType[]{EntityType.RESOURCE};
        } else {
            return new EntityType[0];
        }
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

class Tuple<X, Y> {

    public final X left;
    public final Y right;

    public Tuple(X left, Y right) {
        this.left = left;
        this.right = right;
    }
}
