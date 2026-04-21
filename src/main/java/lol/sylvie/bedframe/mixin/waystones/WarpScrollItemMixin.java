package lol.sylvie.bedframe.mixin.waystones;

import lol.sylvie.bedframe.screen.waystones.WaystonesFamilyEntrypoints;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.item.WarpScrollItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(WarpScrollItem.class)
public abstract class WarpScrollItemMixin {

    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true, require = 0)
    private void bedframe$interceptWarpScrollFinishUsing(
            ItemStack itemStack,
            World world,
            LivingEntity entity,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (world.isClient()) {
            return;
        }

        if (!(entity instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        List<?> targets = new ArrayList<>(PlayerWaystoneManager.getTargetsForItem(serverPlayer, itemStack));
        PlayerWaystoneManager.ensureSortingIndex(serverPlayer, new ArrayList<>(PlayerWaystoneManager.getTargetsForItem(serverPlayer, itemStack)));

        boolean opened = WaystonesFamilyEntrypoints.tryOpenWarpScrollSelection(
                serverPlayer,
                itemStack,
                targets,
                Hand.MAIN_HAND
        );

        if (opened) {
            cir.setReturnValue(itemStack);
        }
    }
}
