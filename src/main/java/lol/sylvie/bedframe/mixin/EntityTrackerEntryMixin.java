package lol.sylvie.bedframe.mixin;

import lol.sylvie.bedframe.view.BedframePlayerViewUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin {
    @Shadow @Final private Entity entity;

    @Inject(method = "startTracking", at = @At("HEAD"), cancellable = true)
    private void bedframe$hideFurnitureArmorStandFromJava(ServerPlayerEntity player, CallbackInfo ci) {
        if (!(this.entity instanceof ArmorStandEntity armorStand)) {
            return;
        }

        if (!armorStand.getCommandTags().contains("bedframe_furniture_carrier")) {
            return;
        }

        boolean bedrock = BedframePlayerViewUtil.isBedrockPlayer(player);

        if (!bedrock) {
            ci.cancel();
        }
    }
}
