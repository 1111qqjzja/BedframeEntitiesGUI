package lol.sylvie.bedframe.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.registry.populator.CustomItemRegistryPopulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Geyser forces the `minecraft:icon` component onto *all* items, even when it is a block
 * Custom Item API v2 will add options that make this mixin obsolete, but for now this should fix invisible block items
 */
@Mixin(CustomItemRegistryPopulator.class)
public class CustomItemRegistryPopulatorMixin {
    // require = 0, prevents nasty crashes when Geyser makes changes :(
    @Inject(method = "createComponentNbt(Lorg/geysermc/geyser/api/item/custom/NonVanillaCustomItemData;Ljava/lang/String;IZZI)Lorg/cloudburstmc/nbt/NbtMapBuilder;",
            at = @At(value = "INVOKE", target = "Lorg/geysermc/geyser/registry/populator/CustomItemRegistryPopulator;computeBlockItemProperties(Ljava/lang/String;Lorg/cloudburstmc/nbt/NbtMapBuilder;)V", shift = At.Shift.AFTER),
            remap = false, require = 0)
    private static void bedframe$applyBlockFix(NonVanillaCustomItemData customItemData, String customItemName, int customItemId, boolean isHat, boolean displayHandheld, int protocolVersion, CallbackInfoReturnable<NbtMapBuilder> cir, @Local(ordinal = 1) NbtMapBuilder itemProperties) {
        bedframe$applyBlockIconFix(customItemData, itemProperties);
    }

    @Inject(method = "createComponentNbt(Lorg/geysermc/geyser/api/item/custom/NonVanillaCustomItemData;Ljava/lang/String;IZZI)Lorg/cloudburstmc/nbt/NbtMapBuilder;",
            at = @At(value = "INVOKE", target = "Lorg/geysermc/geyser/registry/populator/CustomItemRegistryPopulator;computeBlockItemProperties(Lorg/geysermc/geyser/api/item/custom/v2/component/geyser/BlockPlacer;Lorg/cloudburstmc/nbt/NbtMapBuilder;)V", shift = At.Shift.AFTER),
            remap = false, require = 0)
    private static void bedframe$applyBlockFixV2(NonVanillaCustomItemData customItemData, String customItemName, int customItemId, boolean isHat, boolean displayHandheld, int protocolVersion, CallbackInfoReturnable<NbtMapBuilder> cir, @Local(ordinal = 1) NbtMapBuilder itemProperties) {
        bedframe$applyBlockIconFix(customItemData, itemProperties);
    }

    @Inject(method = "createComponentNbt(Lorg/geysermc/geyser/api/item/custom/NonVanillaCustomItemData;Ljava/lang/String;IZZ)Lorg/cloudburstmc/nbt/NbtMapBuilder;",
            at = @At(value = "INVOKE", target = "Lorg/geysermc/geyser/registry/populator/CustomItemRegistryPopulator;computeBlockItemProperties(Ljava/lang/String;Lorg/cloudburstmc/nbt/NbtMapBuilder;)V", shift = At.Shift.AFTER),
            remap = false, require = 0)
    private static void bedframe$applyBlockFixLegacy(NonVanillaCustomItemData customItemData, String customItemName, int customItemId, boolean isHat, boolean displayHandheld, CallbackInfoReturnable<NbtMapBuilder> cir, @Local(ordinal = 1) NbtMapBuilder itemProperties) {
        bedframe$applyBlockIconFix(customItemData, itemProperties);
    }

    @Inject(method = "createComponentNbt(Lorg/geysermc/geyser/api/item/custom/NonVanillaCustomItemData;Ljava/lang/String;IZZ)Lorg/cloudburstmc/nbt/NbtMapBuilder;",
            at = @At(value = "INVOKE", target = "Lorg/geysermc/geyser/registry/populator/CustomItemRegistryPopulator;computeBlockItemProperties(Lorg/geysermc/geyser/api/item/custom/v2/component/geyser/BlockPlacer;Lorg/cloudburstmc/nbt/NbtMapBuilder;)V", shift = At.Shift.AFTER),
            remap = false, require = 0)
    private static void bedframe$applyBlockFixLegacyV2(NonVanillaCustomItemData customItemData, String customItemName, int customItemId, boolean isHat, boolean displayHandheld, CallbackInfoReturnable<NbtMapBuilder> cir, @Local(ordinal = 1) NbtMapBuilder itemProperties) {
        bedframe$applyBlockIconFix(customItemData, itemProperties);
    }

    private static void bedframe$applyBlockIconFix(NonVanillaCustomItemData customItemData, NbtMapBuilder itemProperties) {
        String blockItem = customItemData.block();
        if (blockItem != null && !blockItem.isBlank()) {
            if (customItemData.icon().isEmpty()) {
                itemProperties.remove("minecraft:icon");
            }
            itemProperties.putCompound("minecraft:block_placer", NbtMap.builder()
                    .putString("block", blockItem)
                    .putBoolean("canUseBlockAsIcon", true)
                    .build());
        }
    }
}
