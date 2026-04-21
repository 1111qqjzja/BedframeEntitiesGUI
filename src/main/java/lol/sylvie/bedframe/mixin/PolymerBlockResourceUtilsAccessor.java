package lol.sylvie.bedframe.mixin;

import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PolymerBlockResourceUtils.class, remap = false)
public interface PolymerBlockResourceUtilsAccessor {
    @Accessor
    static BlockResourceCreator getCREATOR() {
        throw new AssertionError();
    }
}