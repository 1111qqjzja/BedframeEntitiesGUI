package lol.sylvie.bedframe.display;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static lol.sylvie.bedframe.util.BedframeConstants.MOD_ID;

public final class FurnitureDisplayRuntime {
    private record PendingChunk(ServerWorld world, WorldChunk chunk) {}
    private record PendingBlock(ServerWorld world, BlockPos pos) {}

    private static final Queue<PendingChunk> PENDING_CHUNKS = new ArrayDeque<>();
    private static final Set<String> PENDING_CHUNK_KEYS = new HashSet<>();

    private static final Queue<PendingBlock> PENDING_BLOCKS = new ArrayDeque<>();
    private static final Set<String> PENDING_BLOCK_KEYS = new HashSet<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private FurnitureDisplayRuntime() {
    }

    public static void init() {

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            enqueueChunk(world, chunk);
        });

        ServerTickEvents.END_SERVER_TICK.register(FurnitureDisplayRuntime::flushQueues);

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {

            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }

            removeDisplaysAt(serverWorld, pos);
            enqueueBlock(serverWorld, pos);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {

            if (!(world instanceof ServerWorld serverWorld)) {
                return ActionResult.PASS;
            }

            if (!(entity instanceof ArmorStandEntity armorStand)) {
                return ActionResult.PASS;
            }

            FurnitureRuntimeRegistry.FurnitureInstance hit = FurnitureRuntimeRegistry.getByCarrierUuid(armorStand.getUuid());
            if (hit == null) {
                return ActionResult.PASS;
            }

            enqueueBlock(serverWorld, hit.anchorPos());
            return ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {

            if (!(world instanceof ServerWorld serverWorld)) {
                return ActionResult.PASS;
            }

            BlockPos placedPos = hitResult.getBlockPos().offset(hitResult.getSide());
            enqueueBlock(serverWorld, placedPos);
            enqueueBlock(serverWorld, hitResult.getBlockPos());

            return ActionResult.PASS;
        });
    }

    private static void flushQueues(MinecraftServer server) {
        flushPendingBlocks();
        flushPendingChunks();

        for (ServerWorld world : server.getWorlds()) {
            validateRegistry(world);
        }
    }

    private static void enqueueChunk(ServerWorld world, WorldChunk chunk) {
        String key = keyOf(world, chunk);
        if (PENDING_CHUNK_KEYS.add(key)) {
            PENDING_CHUNKS.add(new PendingChunk(world, chunk));
        }
    }

    private static void enqueueBlock(ServerWorld world, BlockPos pos) {
        String key = keyOf(world, pos);
        if (PENDING_BLOCK_KEYS.add(key)) {
            PENDING_BLOCKS.add(new PendingBlock(world, pos.toImmutable()));
        }
    }

    private static void flushPendingBlocks() {
        int budget = 64;

        while (budget-- > 0 && !PENDING_BLOCKS.isEmpty()) {
            PendingBlock pending = PENDING_BLOCKS.poll();
            if (pending == null) {
                continue;
            }

            PENDING_BLOCK_KEYS.remove(keyOf(pending.world(), pending.pos()));

            ServerWorld world = pending.world();
            BlockPos pos = pending.pos();

            if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }

            try {
                BlockState state = world.getBlockState(pos);
                Identifier blockId = Registries.BLOCK.getId(state.getBlock());

                if (!AutoOversizedDisplayRegistry.isOversizedDisplay(blockId)) {
                    removeDisplaysAt(world, pos);
                    continue;
                }

                ensureDisplayExists(world, pos, state);
            } catch (Exception e) {
                LOGGER.error("Failed to process pending oversized display block at {}", pos, e);
            }
        }
    }

    private static void flushPendingChunks() {
        int budget = 2;

        while (budget-- > 0 && !PENDING_CHUNKS.isEmpty()) {
            PendingChunk pending = PENDING_CHUNKS.poll();
            if (pending == null) {
                continue;
            }

            PENDING_CHUNK_KEYS.remove(keyOf(pending.world(), pending.chunk()));

            if (!pending.world().isChunkLoaded(pending.chunk().getPos().x, pending.chunk().getPos().z)) {
                continue;
            }

            try {
                resyncChunk(pending.world(), pending.chunk());
            } catch (Exception e) {
                LOGGER.error("Failed to resync chunk {}", pending.chunk().getPos(), e);
            }
        }
    }

    private static String keyOf(ServerWorld world, WorldChunk chunk) {
        return world.getRegistryKey().getValue() + "|" + chunk.getPos().x + "|" + chunk.getPos().z;
    }

    private static String keyOf(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static float resolveYaw(BlockState state) {
        for (var property : state.getProperties()) {
            String name = property.getName();
            if ("rotation".equals(name) && property instanceof net.minecraft.state.property.IntProperty intProperty) {
                int value = state.get(intProperty);
                int max = java.util.Collections.max(intProperty.getValues());
                int steps = max + 1;

                if (steps <= 0) {
                    return 0.0f;
                }

                return -(360.0f / steps) * value;
            }
        }

        for (var property : state.getProperties()) {
            String name = property.getName();
            if ((name.equals("facing") || name.equals("horizontal_facing"))
                    && property instanceof EnumProperty<?> enumProperty) {

                Object value = state.get(enumProperty);
                if (value instanceof Direction dir) {
                    return directionToYaw(dir);
                }
            }
        }

        return 0.0f;
    }

    private static float directionToYaw(net.minecraft.util.math.Direction dir) {
        return switch (dir) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    private static void resyncChunk(ServerWorld world, WorldChunk chunk) {
        BlockPos start = chunk.getPos().getStartPos();

        int minY = world.getBottomY();
        int maxY = minY + world.getDimension().height();

        for (int x = start.getX(); x < start.getX() + 16; x++) {
            for (int z = start.getZ(); z < start.getZ() + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    BlockState state = chunk.getBlockState(pos);
                    Identifier blockId = Registries.BLOCK.getId(state.getBlock());

                    if (!AutoOversizedDisplayRegistry.isOversizedDisplay(blockId)) {
                        removeDisplaysAt(world, pos);
                        continue;
                    }

                    ensureDisplayExists(world, pos, state);
                }
            }
        }
    }

    public static void ensureDisplayExists(ServerWorld world, BlockPos pos, BlockState state) {
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());

        OversizedDisplaySpec spec = AutoOversizedDisplayRegistry.get(blockId);

        if (spec == null) {
            removeDisplaysAt(world, pos);
            return;
        }

        FurnitureRuntimeRegistry.clearDead(world, pos);

        if (FurnitureRuntimeRegistry.isAlive(world, pos)) {
            return;
        }

        float yaw = resolveYaw(state);

        ArmorStandEntity carrier = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        carrier.refreshPositionAndAngles(
                pos.getX() + spec.offset().x,
                pos.getY() + spec.offset().y,
                pos.getZ() + spec.offset().z,
                yaw,
                0.0f
        );

        carrier.setNoGravity(true);
        carrier.setSilent(true);
        carrier.setInvulnerable(true);
        carrier.addCommandTag("bedframe_furniture_carrier");
        carrier.isSmall();
        carrier.isMarker();

        boolean spawned = world.spawnEntity(carrier);

        if (!spawned) {
            return;
        }

        FurnitureRuntimeRegistry.FurnitureInstance instance =
                new FurnitureRuntimeRegistry.FurnitureInstance(
                        world.getRegistryKey(),
                        pos,
                        blockId,
                        spec,
                        carrier.getUuid()
                );

        FurnitureRuntimeRegistry.register(world, instance);

    }

    public static void removeDisplaysAt(ServerWorld world, BlockPos pos) {
        FurnitureRuntimeRegistry.FurnitureInstance instance = FurnitureRuntimeRegistry.getByAnchor(world, pos);
        if (instance == null) {
            return;
        }


        Entity entity = world.getEntity(instance.carrierUuid());
        if (entity != null) {
            entity.discard();
        }

        FurnitureRuntimeRegistry.unregister(world, pos);
    }

    private static void validateRegistry(ServerWorld world) {
        var toRemove = new java.util.ArrayList<BlockPos>();

        for (var entry : FurnitureRuntimeRegistry.snapshotByAnchor().entrySet()) {
            FurnitureRuntimeRegistry.AnchorKey anchorKey = entry.getKey();
            FurnitureRuntimeRegistry.FurnitureInstance instance = entry.getValue();

            if (!anchorKey.worldKey().equals(world.getRegistryKey())) {
                continue;
            }

            BlockPos pos = anchorKey.pos();

            BlockState state = world.getBlockState(pos);
            Identifier currentBlockId = Registries.BLOCK.getId(state.getBlock());

            if (!AutoOversizedDisplayRegistry.isOversizedDisplay(currentBlockId)) {
                Entity carrier = world.getEntity(instance.carrierUuid());
                if (carrier != null) {
                    carrier.discard();
                }
                toRemove.add(pos);
                continue;
            }

            Entity carrier = world.getEntity(instance.carrierUuid());
            if (carrier == null || !carrier.isAlive()) {
                toRemove.add(pos);
                try {
                    ensureDisplayExists(world, pos, state);
                } catch (Exception e) {
                    LOGGER.error("Failed to respawn carrier at {}", pos, e);
                }
                continue;
            }

            OversizedDisplaySpec spec = AutoOversizedDisplayRegistry.get(currentBlockId);
            if (spec != null) {
                float yaw = resolveYaw(state);
                carrier.refreshPositionAndAngles(
                        pos.getX() + spec.offset().x,
                        pos.getY() + spec.offset().y,
                        pos.getZ() + spec.offset().z,
                        yaw,
                        0.0f
                );
            }
        }

        for (BlockPos pos : toRemove) {
            FurnitureRuntimeRegistry.unregister(world, pos);
        }
    }
}
   