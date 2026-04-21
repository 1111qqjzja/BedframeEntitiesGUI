package lol.sylvie.bedframe.display;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import lol.sylvie.bedframe.view.JavaViewEntityMarker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import xyz.nucleoid.packettweaker.PacketContext;

public class JavaLoginCompatEntity extends Entity implements PolymerEntity, JavaViewEntityMarker {
    private BlockPos anchorPos = BlockPos.ORIGIN;

    public JavaLoginCompatEntity(EntityType<? extends JavaLoginCompatEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setAnchorPos(BlockPos pos) {
        this.anchorPos = pos.toImmutable();
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (serverWorld.getBlockState(anchorPos).isAir()) {
            this.discard();
            return;
        }

        this.setVelocity(Vec3d.ZERO);
        this.setInvisible(true);
        this.setNoGravity(true);
        this.setSilent(true);
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public void move(MovementType movementType, Vec3d movement) {
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readCustomData(ReadView view) {
        int x = view.getInt("AnchorX", 0);
        int y = view.getInt("AnchorY", 0);
        int z = view.getInt("AnchorZ", 0);
        this.anchorPos = new BlockPos(x, y, z);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.putInt("AnchorX", this.anchorPos.getX());
        view.putInt("AnchorY", this.anchorPos.getY());
        view.putInt("AnchorZ", this.anchorPos.getZ());
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        return EntityType.ITEM_DISPLAY;
    }
}
