package lol.sylvie.bedframe.mixin.waystones;

import lol.sylvie.bedframe.screen.bridge.GeyserSupport;
import lol.sylvie.bedframe.screen.waystones.WaystonesBedrockBridge;
import lol.sylvie.bedframe.screen.waystones.WaystonesFamilyPayloads;
import lol.sylvie.bedframe.screen.waystones.WaystonesFamilyType;
import lol.sylvie.bedframe.screen.waystones.WaystonesMenuDataExtractor;
import net.blay09.mods.waystones.block.WarpPlateBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WarpPlateBlock.class)
public abstract class WarpPlateBlockMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void bedframe$interceptWarpPlateSettings(
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient()) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (!GeyserSupport.isBedrock(serverPlayer)) return;

        if (player.isSneaking()) {
            return;
        }

        var blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }

        Object provider = WaystonesMenuDataExtractor.tryGetter(blockEntity, "getSettingsMenuProvider");
        if (!(provider instanceof java.util.Optional<?> optional) || optional.isEmpty()) {
            return;
        }

        Object openingData = WaystonesMenuDataExtractor.getOpeningData(optional.get(), serverPlayer);
        Object waystone = openingData != null
                ? WaystonesMenuDataExtractor.tryGetter(openingData, "getWaystone")
                : null;

        if (waystone == null) {
            waystone = WaystonesMenuDataExtractor.tryGetter(blockEntity, "getWaystone");
        }
        if (waystone == null) {
            return;
        }

        WaystonesFamilyPayloads.SettingsPayload payload =
                new WaystonesFamilyPayloads.SettingsPayload(
                        WaystonesFamilyType.WARP_PLATE_BLOCK,
                        blockEntity,
                        waystone
                );

        if (WaystonesBedrockBridge.tryOpenSettings(serverPlayer, payload)) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
