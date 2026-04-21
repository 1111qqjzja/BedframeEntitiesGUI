package lol.sylvie.bedframe.mixin.waystones;

import lol.sylvie.bedframe.screen.waystones.WaystonesFamilyEntrypoints;
import lol.sylvie.bedframe.screen.waystones.WaystonesMenuDataExtractor;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.SharestoneBlock;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SharestoneBlock.class)
public abstract class SharestoneBlockMixin {

    @Inject(method = "handleActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void bedframe$interceptSharestoneSelection(
            World world,
            BlockPos pos,
            PlayerEntity player,
            WaystoneBlockEntityBase blockEntity,
            Waystone waystone,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient()) return;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        var providerOpt = blockEntity.getSelectionMenuProvider();
        if (providerOpt.isEmpty()) {
            return;
        }

        Object openingData = WaystonesMenuDataExtractor.getOpeningData(providerOpt.get(), serverPlayer);
        Object fromWaystone = WaystonesMenuDataExtractor.extractFromWaystone(openingData);
        List<?> targets = WaystonesMenuDataExtractor.extractTargets(openingData);

        boolean opened = WaystonesFamilyEntrypoints.tryOpenSharestoneSelection(
                serverPlayer,
                blockEntity,
                fromWaystone,
                targets,
                Hand.MAIN_HAND
        );

        if (opened) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
