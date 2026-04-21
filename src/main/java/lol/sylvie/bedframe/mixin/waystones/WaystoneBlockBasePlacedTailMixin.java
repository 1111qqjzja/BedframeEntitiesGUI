package lol.sylvie.bedframe.mixin.waystones;

import lol.sylvie.bedframe.screen.waystones.WaystonesBedrockBridge;
import net.blay09.mods.balm.world.BalmMenuProvider;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WaystoneBlockBase.class)
public abstract class WaystoneBlockBasePlacedTailMixin {

    @Shadow
    protected abstract boolean shouldOpenMenuWhenPlaced();

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void bedframe$openSettingsAfterPlaced(
            World world,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack itemStack,
            CallbackInfo ci
    ) {
        if (world.isClient()) {
            return;
        }

        if (!(placer instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        if (!shouldOpenMenuWhenPlaced()) {
            return;
        }

        if (!(world.getBlockEntity(pos) instanceof WaystoneBlockEntityBase blockEntity)) {
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
            } catch (Throwable ignored) {
            }
        }

        WaystonesBedrockBridge.tryOpenSettings(serverPlayer, settingsData);
    }
}
