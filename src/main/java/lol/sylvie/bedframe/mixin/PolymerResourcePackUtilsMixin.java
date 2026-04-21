package lol.sylvie.bedframe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import lol.sylvie.bedframe.geyser.TranslationManager;
import lol.sylvie.bedframe.util.GeyserHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(value = PolymerResourcePackUtils.class, remap = false)
public class PolymerResourcePackUtilsMixin {
    @ModifyReturnValue(method = "hasMainPack(Lxyz/nucleoid/packettweaker/PacketContext;)Z", at = @At("RETURN"))
    private static boolean bedframe$fakePolymerResourcePack(boolean original, @Local(argsOnly = true) PacketContext context) {
        return GeyserHelper.isBedrockPlayer(context.getPlayer()) || TranslationManager.INCLUDE_OPTIONAL_TEXTURES_HACK || original;
    }
}
