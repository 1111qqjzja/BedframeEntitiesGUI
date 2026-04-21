package lol.sylvie.bedframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.unnamed.creative.model.ElementRotation;

@Mixin(ElementRotation.class)
public class ElementRotationMixin {
    @Inject(method = "validate", at = @At("HEAD"), cancellable = true, remap = false)
    public void bedframe$removeAngleValidation(CallbackInfo ci) {
        ci.cancel();
    }
}
