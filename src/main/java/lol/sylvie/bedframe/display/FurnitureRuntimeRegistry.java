package lol.sylvie.bedframe.display;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FurnitureRuntimeRegistry {
    public record AnchorKey(RegistryKey<World> worldKey, BlockPos pos) {
        public AnchorKey {
            pos = pos.toImmutable();
        }
    }

    public static final class FurnitureInstance {
        private final RegistryKey<World> worldKey;
        private final BlockPos anchorPos;
        private final Identifier blockId;
        private final OversizedDisplaySpec spec;
        private final UUID carrierUuid;

        public FurnitureInstance(
                RegistryKey<World> worldKey,
                BlockPos anchorPos,
                Identifier blockId,
                OversizedDisplaySpec spec,
                UUID carrierUuid
        ) {
            this.worldKey = worldKey;
            this.anchorPos = anchorPos.toImmutable();
            this.blockId = blockId;
            this.spec = spec;
            this.carrierUuid = carrierUuid;
        }

        public RegistryKey<World> worldKey() {
            return worldKey;
        }

        public BlockPos anchorPos() {
            return anchorPos;
        }

        public Identifier blockId() {
            return blockId;
        }

        public OversizedDisplaySpec spec() {
            return spec;
        }

        public UUID carrierUuid() {
            return carrierUuid;
        }
    }

    private static final Map<AnchorKey, FurnitureInstance> BY_ANCHOR = new ConcurrentHashMap<>();
    private static final Map<UUID, FurnitureInstance> BY_ENTITY_UUID = new ConcurrentHashMap<>();

    private FurnitureRuntimeRegistry() {
    }

    public static Map<AnchorKey, FurnitureInstance> snapshotByAnchor() {
        return new java.util.LinkedHashMap<>(BY_ANCHOR);
    }

    public static FurnitureInstance getByAnchor(ServerWorld world, BlockPos pos) {
        return BY_ANCHOR.get(new AnchorKey(world.getRegistryKey(), pos));
    }

    public static FurnitureInstance getByCarrierUuid(UUID uuid) {
        return BY_ENTITY_UUID.get(uuid);
    }

    public static void register(ServerWorld world, FurnitureInstance instance) {
        AnchorKey key = new AnchorKey(world.getRegistryKey(), instance.anchorPos());
        BY_ANCHOR.put(key, instance);
        BY_ENTITY_UUID.put(instance.carrierUuid(), instance);
    }

    public static void unregister(ServerWorld world, BlockPos pos) {
        FurnitureInstance instance = BY_ANCHOR.remove(new AnchorKey(world.getRegistryKey(), pos));
        if (instance != null) {
            BY_ENTITY_UUID.remove(instance.carrierUuid());
        }
    }

    public static boolean isAlive(ServerWorld world, BlockPos pos) {
        FurnitureInstance instance = getByAnchor(world, pos);
        if (instance == null) {
            return false;
        }

        Entity entity = world.getEntity(instance.carrierUuid());
        return entity != null && entity.isAlive();
    }

    public static void clearDead(ServerWorld world, BlockPos pos) {
        FurnitureInstance instance = getByAnchor(world, pos);
        if (instance == null) {
            return;
        }

        Entity entity = world.getEntity(instance.carrierUuid());
        if (entity == null || !entity.isAlive()) {
            unregister(world, pos);
        }
    }
}
