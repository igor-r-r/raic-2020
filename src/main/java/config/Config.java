package config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import model.Entity;
import model.EntityType;
import model.PlayerView;
import model.Vec2Int;

public class Config {

    public static final Set<Vec2Int> defensePositions = new HashSet<>();
    public static final Set<EntityType> buildingTypes = new HashSet<>();
    public static final Map<EntityType, Integer> entitySizes = new HashMap<>();
    static {
        buildingTypes.add(EntityType.HOUSE);
        buildingTypes.add(EntityType.BUILDER_BASE);
        buildingTypes.add(EntityType.RANGED_BASE);
        buildingTypes.add(EntityType.MELEE_BASE);
        buildingTypes.add(EntityType.TURRET);
    }

    static {
        for (int x = 21; x < 30; x++) {
            for (int y = 0; y < 30; y++) {
                defensePositions.add(new Vec2Int(x, y));
            }
        }

        for (int x = 0; x < 30; x++) {
            for (int y = 21; y < 30; y++) {
                defensePositions.add(new Vec2Int(x, y));
            }
        }
    }

    static {
        entitySizes.put(EntityType.HOUSE, 3);
        entitySizes.put(EntityType.BUILDER_BASE, 5);
        entitySizes.put(EntityType.RANGED_BASE, 5);
        entitySizes.put(EntityType.MELEE_BASE, 5);
        entitySizes.put(EntityType.TURRET, 2);
        entitySizes.put(EntityType.MELEE_UNIT, 1);
        entitySizes.put(EntityType.RANGED_UNIT, 1);
        entitySizes.put(EntityType.BUILDER_UNIT, 1);
        entitySizes.put(EntityType.RESOURCE, 1);
        entitySizes.put(EntityType.WALL, 1);
    }


    public static PlayerView playerView = null;
    public static Map<EntityType, Integer> maxHealthByType;
    public static Map<EntityType, List<Entity>> myEntities = new HashMap<>();
    public static Map<Integer, Entity> myIdToEntity = new HashMap<>();
    public static Map<Integer, Map<EntityType, List<Entity>>> enemyEntities = new HashMap<>();
    public static int maxPopulation = 0;
    public static boolean initialBuilders = false;

    public static Vec2Int middle;
    public static Map<EntityType, List<Vec2Int>> myEntitiesPositions;
    public static int currentEnemyId = 0;
    public static Vec2Int currentEnemyBase = new Vec2Int();
    public static Set<Vec2Int> occupiedPositions = new HashSet<>();
    public static List<Vec2Int> allowedDefensePositions = new ArrayList<>();

    public static void initConfig(PlayerView playerView) {
        Config.playerView = playerView;
        middle = new Vec2Int(playerView.getMapSize() / 2, playerView.getMapSize() / 2);

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

        myIdToEntity = myEntities.values().stream().flatMap(List::stream).collect(Collectors.toMap(Entity::getId, e -> e));

        myEntitiesPositions = myEntities.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .map(Entity::getPosition)
                        .collect(Collectors.toList())));

        enemyEntities = Arrays.stream(playerView.getEntities())
                .filter(entity -> entity.getPlayerId() != null && entity.getPlayerId() != playerView.getMyId())
                .collect(Collectors.groupingBy(Entity::getPlayerId, Collectors.groupingBy(Entity::getEntityType)));

        currentEnemyId = enemyEntities.keySet().stream().max(Integer::compareTo).orElse(-1);
        currentEnemyId = currentEnemyId();
        currentEnemyBase = enemyBase();
        occupiedPositions = occupiedPositions();
        allowedDefensePositions = allowedDefensePositions();
    }

    public static int currentEnemyId() {
        int currentEnemyId = enemyEntities.entrySet().stream()
                .filter(e -> e.getValue().keySet().stream().anyMatch(buildingTypes::contains))
                .map(Map.Entry::getKey)
                .max(Integer::compareTo).orElse(-1);

        if (currentEnemyId == -1) {
            currentEnemyId = enemyEntities.entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().size() > 0)
                    .map(Map.Entry::getKey)
                    .max(Integer::compareTo).orElse(-1);
        }

        return currentEnemyId;
    }

    public static Vec2Int anyEnemyUnit() {
        return enemyEntities.get(currentEnemyId).values().stream()
                .map(entities -> entities.get(0).getPosition())
                .findFirst()
                .orElse(middle);
    }

    private static Set<Vec2Int> occupiedPositions() {
        return Arrays.stream(playerView.getEntities())
                .filter(e -> e.getEntityType() != EntityType.RANGED_UNIT
                        && e.getEntityType() != EntityType.MELEE_UNIT)
                .map(Config::occupiedPositionsForEntity)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private static Set<Vec2Int> occupiedPositionsForEntity(Entity entity) {
        Set<Vec2Int> positions = new HashSet<>();
        int size = entitySizes.get(entity.getEntityType());
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                positions.add(entity.getPosition());
                if (i != 0 && j != 0) {
                    positions.add(new Vec2Int(entity.getPosition().getX() + i,
                            entity.getPosition().getY() + j));
                }
            }
        }

        return positions;
    }

    private static List<Vec2Int> allowedDefensePositions() {
        List<Vec2Int> result = new ArrayList<>(defensePositions);
        result.removeAll(occupiedPositions);

        return result;
    }

    private static Vec2Int enemyBase() {
        if (enemyEntities == null || enemyEntities.get(currentEnemyId) == null) {
            return middle;
        }

        return enemyEntities.get(currentEnemyId).entrySet().stream()
                .filter(e -> e.getKey() == EntityType.BUILDER_BASE
                        || e.getKey() == EntityType.MELEE_BASE
                        || e.getKey() == EntityType.RANGED_BASE)
                .map(e -> e.getValue().get(0).getPosition())
                .findFirst()
                .orElse(anyEnemyUnit());
    }
}
