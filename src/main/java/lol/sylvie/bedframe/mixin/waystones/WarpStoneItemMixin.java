package lol.sylvie.bedframe.mixin.waystones;

import lol.sylvie.bedframe.screen.waystones.WaystonesFamilyEntrypoints;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.item.WarpStoneItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(WarpStoneItem.class)
public abstract class WarpStoneItemMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void bedframe$interceptWarpStoneUse(
            World world,
            PlayerEntity player,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient()) {
            return;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemStack stack = player.getStackInHand(hand);

        List<?> targets = new ArrayList<>(PlayerWaystoneManager.getTargetsForItem(serverPlayer, stack));
        PlayerWaystoneManager.ensureSortingIndex(serverPlayer, new ArrayList<>(PlayerWaystoneManager.getTargetsForItem(serverPlayer, stack)));

        boolean opened = WaystonesFamilyEntrypoints.tryOpenWarpStoneSelection(
                serverPlayer,
                stack,
                targets,
                hand
        );

        if (opened) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}