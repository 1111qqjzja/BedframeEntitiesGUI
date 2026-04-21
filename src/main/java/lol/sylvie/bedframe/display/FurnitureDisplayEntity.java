package lol.sylvie.bedframe.display;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import lol.sylvie.bedframe.view.BedrockViewEntityMarker;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.geysermc.floodgate.api.FloodgateApi;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FurnitureDisplayEntity extends Entity implements BedrockViewEntityMarker, PolymerEntity  {
    private static final Map<UUID, Identifier> BLOCK_ID_BY_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Identifier> JAVA_ENTITY_ID_BY_UUID = new ConcurrentHashMap<>();

    private Identifier displayModel = Identifier.of("bedframe", "tcfurniture");
    private BlockPos anchorPos = BlockPos.ORIGIN;

    public FurnitureDisplayEntity(EntityType<? extends FurnitureDisplayEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void setDisplayModel(Identifier model) {
        this.displayModel = model;
    }

    public Identifier getDisplayModel() {
        return displayModel;
    }

    public void setAnchorPos(BlockPos pos) {
        this.anchorPos = pos.toImmutable();
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public Identifier getAnchorBlockId() {
        World world = this.getEntityWorld();
        if (world == null) {
            return null;
        }

        Block block = world.getBlockState(this.anchorPos).getBlock();
        Identifier blockId = Registries.BLOCK.getId(block);

        if (blockId == null || blockId.equals(Identifier.of("minecraft", "air"))) {
            return null;
        }

        return blockId;
    }

    public OversizedDisplaySpec getDisplaySpec() {
        Identifier blockId = this.getAnchorBlockId();
        if (blockId == null) {
            return null;
        }

        return AutoOversizedDisplayRegistry.get(blockId);
    }

    public void registerSpawnLookup() {
        UUID uuid = this.getUuid();
        if (uuid == null) {
            return;
        }

        Identifier blockId = this.getAnchorBlockId();
        if (blockId == null) {
            BLOCK_ID_BY_UUID.remove(uuid);
            JAVA_ENTITY_ID_BY_UUID.remove(uuid);
            return;
        }

        OversizedDisplaySpec spec = AutoOversizedDisplayRegistry.get(blockId);
        if (spec == null) {
            BLOCK_ID_BY_UUID.remove(uuid);
            JAVA_ENTITY_ID_BY_UUID.remove(uuid);
            return;
        }

        BLOCK_ID_BY_UUID.put(uuid, blockId);
        JAVA_ENTITY_ID_BY_UUID.put(uuid, spec.javaEntityId());
    }

    public void unregisterSpawnLookup() {
        UUID uuid = this.getUuid();
        if (uuid == null) {
            return;
        }

        BLOCK_ID_BY_UUID.remove(uuid);
        JAVA_ENTITY_ID_BY_UUID.remove(uuid);
    }

    public static Identifier getCachedBlockId(UUID uuid) {
        return BLOCK_ID_BY_UUID.get(uuid);
    }

    public static Identifier getCachedJavaEntityId(UUID uuid) {
        return JAVA_ENTITY_ID_BY_UUID.get(uuid);
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (serverWorld.getBlockState(anchorPos).isAir()) {
            unregisterSpawnLookup();
            this.discard();
            return;
        }

        this.setVelocity(Vec3d.ZERO);
    }

    @Override
    public void remove(RemovalReason reason) {
        unregisterSpawnLookup();
        super.remove(reason);
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
        String model = view.getString("DisplayModel", "bedframe:tcfurniture");
        int x = view.getInt("AnchorX", 0);
        int y = view.getInt("AnchorY", 0);
        int z = view.getInt("AnchorZ", 0);

        try {
            this.displayModel = Identifier.of(model);
        } catch (Exception ignored) {
            this.displayModel = Identifier.of("bedframe", "tcfurniture");
        }

        this.anchorPos = new BlockPos(x, y, z);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.putString("DisplayModel", this.displayModel.toString());
        view.putInt("AnchorX", this.anchorPos.getX());
        view.putInt("AnchorY", this.anchorPos.getY());
        view.putInt("AnchorZ", this.anchorPos.getZ());
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        if(FloodgateApi.getInstance().isFloodgatePlayer(getUuid())){
            return this.getType();
        }
        return EntityType.ITEM_DISPLAY;
    }
}
