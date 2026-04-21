package lol.sylvie.bedframe.mixin;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import lol.sylvie.bedframe.geyser.translator.ItemTranslator;
import lol.sylvie.bedframe.util.GeyserHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.packettweaker.PacketContext;

@Mixin(PolymerItemUtils.class)
public class PolymerItemUtilsMixin {
    // This mixin along with PolymerItemMixin tell Polymer to send Bedrock clients
    // the actual items rather than their Polymer representations
    @Inject(method = "getItemSafely(Leu/pb4/polymer/core/api/item/PolymerItem;Lnet/minecraft/item/ItemStack;Lxyz/nucleoid/packettweaker/PacketContext;I)Leu/pb4/polymer/core/api/item/PolymerItemUtils$ItemWithMetadata;", at = @At("RETURN"), cancellable = true)
    private static void bedframe$tellPolymerToAbstain(PolymerItem item, ItemStack stack, PacketContext context, int maxDistance, CallbackInfoReturnable<PolymerItemUtils.ItemWithMetadata> cir) {
        Item realItem = stack.getItem();
        if (GeyserHelper.isBedrockPlayer(context.getPlayer()) && ItemTranslator.isTexturedItem(realItem))
            cir.setReturnValue(new PolymerItemUtils.ItemWithMetadata(realItem, item.getPolymerItemModel(stack, context)));
    }
}
