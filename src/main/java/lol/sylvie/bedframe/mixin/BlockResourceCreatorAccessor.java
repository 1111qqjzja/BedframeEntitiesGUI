package lol.sylvie.bedframe.mixin;

import com.mojang.datafixers.util.Either;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.MultiPolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = BlockResourceCreator.class, remap = false)
public interface BlockResourceCreatorAccessor {
    @Accessor
    Map<BlockState, Either<PolymerBlockModel[], MultiPolymerBlockModel>> getModels();
}
    