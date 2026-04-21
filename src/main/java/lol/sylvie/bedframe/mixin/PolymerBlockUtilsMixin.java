package lol.sylvie.bedframe.mixin;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import lol.sylvie.bedframe.geyser.translator.BlockTranslator;
import lol.sylvie.bedframe.util.GeyserHelper;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(PolymerBlockUtils.class)
public class PolymerBlockUtilsMixin {
    // This mixin tells Polymer to send Bedrock clients
    // the actual blocks rather than their Polymer representations
    @Inject(method = "getBlockStateSafely(Leu/pb4/polymer/core/api/block/PolymerBlock;Lnet/minecraft/block/BlockState;ILxyz/nucleoid/packettweaker/PacketContext;)Lnet/minecraft/block/BlockState;", at = @At("RETURN"), cancellable = true)
    private static void bedframe$tellPolymerToAbstain(PolymerBlock block, BlockState blockState, int maxDistance, PacketContext context, CallbackInfoReturnable<BlockState> cir) {
        if (GeyserHelper.isBedrockPlayer(context.getPlayer()) && BlockTranslator.isRegisteredBlock(block))
            cir.setReturnValue(blockState);
    }
}
