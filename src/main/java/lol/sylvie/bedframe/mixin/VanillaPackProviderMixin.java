package lol.sylvie.bedframe.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import lol.sylvie.bedframe.geyser.TranslationManager;
import org.geysermc.pack.converter.util.VanillaPackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/*
 * this code does nothing to better humanity
 * i don't want to deal with any stuff related to
 */
@Mixin(value = VanillaPackProvider.class, remap = false)
public class VanillaPackProviderMixin {
	@ModifyExpressionValue(method = "lambda$clean$0", at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"))
	private static boolean bedframe$forceAddTextures(boolean original, @Local(name = "pathName") String name) {
		if (!TranslationManager.INCLUDE_TEXTURE_HACK) return original;
		return name.startsWith("/assets/minecraft/textures");
	}
}
