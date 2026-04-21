package lol.sylvie.bedframe.mixin.waystones;

import lol.sylvie.bedframe.screen.waystones.WaystonesBedrockBridge;
import net.blay09.mods.balm.world.BalmMenuProvider;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaystoneBlockBase.class)
public abstract class WaystoneBlockBaseMixin {

    @Inject(method = "handleEditActions", at = @At("HEAD"), cancellable = true)
    private void bedframe$interceptSettingsMenu(
            World world,
            PlayerEntity player,
            WaystoneBlockEntityBase blockEntity,
            Waystone waystone,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (world.isClient()) {
            return;
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        if (!player.isSneaking()) {
            return;
        }

        var providerOpt = blockEntity.getSettingsMenuProvider();

        if (providerOpt.isEmpty()) {
            return;
        }

        Object settingsData = blockEntity;
        var provider = providerOpt.get();

        if (provider instanceof BalmMenuProvider balmMenuProvider) {
            try {
                Object openingData = balmMenuProvider.getScreenOpeningData(serverPlayer);
                if (openingData != null) {
                    settingsData = openingData;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        boolean intercepted = WaystonesBedrockBridge.tryOpenSettings(serverPlayer, settingsData);

        if (intercepted) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
