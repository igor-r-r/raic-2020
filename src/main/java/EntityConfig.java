import model.AttackAction;
import model.Entity;
import model.MoveAction;
import model.Vec2Int;

public class EntityConfig {

    public Entity entity;
    public boolean moving = false;
    public Vec2Int target = new Vec2Int();
    public MoveAction moveAction = null;
    public AttackAction attackAction = null;

    public EntityConfig() {
    }

    public EntityConfig(Entity entity, boolean moving, Vec2Int target, MoveAction moveAction, AttackAction attackAction) {
        this.entity = entity;
        this.moving = moving;
        this.target = target;
        this.moveAction = moveAction;
        this.attackAction = attackAction;
    }
}
