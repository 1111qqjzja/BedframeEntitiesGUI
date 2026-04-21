package lol.sylvie.testmod.item;

import eu.pb4.polymer.core.api.item.PolymerItemGroupUtils;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import lol.sylvie.testmod.Testmod;
import lol.sylvie.testmod.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ModItems {
    public static final RegistryKey<ItemGroup> ITEM_GROUP_KEY = RegistryKey.of(Registries.ITEM_GROUP.getKey(), Identifier.of(Testmod.MOD_ID, "item_group"));
    public static final ItemGroup ITEM_GROUP = PolymerItemGroupUtils.builder()
            .icon(() -> new ItemStack(Items.CLAY_BALL))
            .displayName(Text.translatable("itemGroup.bedframe-testmod"))
            .build();

    public static final Item EXAMPLE_ITEM = register(
            "example_item",
            SimplePolymerItem::new,
            new Item.Settings()
    );

    public static final Item VANILLA_TEXTURED_ITEM = register(
            "vanilla_textured",
            settings -> new SimplePolymerItem(settings, Items.CLAY_BALL),
            new Item.Settings()
    );

    public static Item register(String name, Function<Item.Settings, Item> itemFactory, Item.Settings settings) {
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Testmod.MOD_ID, name));
        Item item = itemFactory.apply(settings.registryKey(itemKey));
        Registry.register(Registries.ITEM, itemKey, item);

        return item;
    }

    public static void initialize() {
        PolymerItemGroupUtils.registerPolymerItemGroup(ITEM_GROUP_KEY, ITEM_GROUP);

        ItemGroupEvents.modifyEntriesEvent(ITEM_GROUP_KEY).register(itemGroup -> {
            // Add items here:
            itemGroup.add(EXAMPLE_ITEM);
            itemGroup.add(VANILLA_TEXTURED_ITEM);

            // Or blocks:
            itemGroup.add(ModBlocks.EXAMPLE_BLOCK.asItem());
            itemGroup.add(ModBlocks.EXAMPLE_LOG.asItem());
            itemGroup.add(ModBlocks.EXAMPLE_FLOWER.asItem());
            itemGroup.add(ModBlocks.EXAMPLE_FLOWER_POT.asItem());
        });
    }
}