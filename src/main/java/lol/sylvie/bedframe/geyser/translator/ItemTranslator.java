package lol.sylvie.bedframe.geyser.translator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.pb4.polymer.core.api.item.PolymerItem;
import lol.sylvie.bedframe.display.AutoOversizedDisplayRegistry;
import lol.sylvie.bedframe.geyser.TranslationManager;
import lol.sylvie.bedframe.geyser.Translator;
import lol.sylvie.bedframe.util.*;
import net.minecraft.block.Block;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.api.util.CreativeCategory;
import xyz.nucleoid.packettweaker.PacketContext;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;
import static lol.sylvie.bedframe.util.PathHelper.createDirectoryOrThrow;

public class ItemTranslator extends Translator {
    private final HashMap<Identifier, PolymerItem> items = new HashMap<>();
    private static final ArrayList<Item> registeredItems = new ArrayList<>();

    public ItemTranslator() {
        Stream<Identifier> itemIds = Registries.ITEM.getIds().stream();

        itemIds.forEach(identifier -> {
            Item item = Registries.ITEM.get(identifier);
            if (item instanceof PolymerItem polymerItem) {
                items.put(identifier, polymerItem);
            }
        });
    }

    private void forEachItem(BiConsumer<Identifier, PolymerItem> function) {
        for (Map.Entry<Identifier, PolymerItem> entry : items.entrySet()) {
            try {
                function.accept(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                LOGGER.error("Couldn't load item {}", entry.getKey(), e);
            }
        }
    }

    public static boolean isTexturedItem(Item item) {
        return registeredItems.contains(item);
    }

    private void handle(GeyserDefineCustomItemsEvent event, Path packRoot) {
        Path textureDir = createDirectoryOrThrow(packRoot.resolve("textures"));
        createDirectoryOrThrow(textureDir.resolve("items"));

        JsonObject itemTextureObject = new JsonObject();
        itemTextureObject.addProperty("resource_pack_name", BedframeConstants.MOD_ID);
        itemTextureObject.addProperty("texture_name", "atlas.items");

        JsonObject textureDataObject = new JsonObject();

        forEachItem((identifier, item) -> {
            Item realItem = Registries.ITEM.get(identifier);
            ItemStack realDefaultItemStack = realItem.getDefaultStack();

            Identifier model = null;
            boolean isBlockItem = realItem instanceof BlockItem;

            if (!isBlockItem) {
                TranslationManager.INCLUDE_OPTIONAL_TEXTURES_HACK = true;
                ItemStack itemStack = item.getPolymerItemStack(realItem.getDefaultStack(), TooltipType.BASIC, PacketContext.get());
                model = itemStack.get(DataComponentTypes.ITEM_MODEL);
                TranslationManager.INCLUDE_OPTIONAL_TEXTURES_HACK = false;

                if (model == null) {
                    return;
                }
            }

            CustomItemOptions.Builder itemOptions = CustomItemOptions.builder();

            String translated = Text.translatable(realItem.getTranslationKey()).getString();
            NonVanillaCustomItemData.Builder itemBuilder = NonVanillaCustomItemData.builder()
                    .name(identifier.toString())
                    .identifier(identifier.toString())
                    .displayName(translated)
                    .creativeGroup("itemGroup." + identifier.getNamespace() + ".items")
                    .creativeCategory(CreativeCategory.CONSTRUCTION.id())
                    .allowOffhand(true);

            ComponentMap components = realDefaultItemStack.getComponents();

            FoodComponent foodComponent = components.get(DataComponentTypes.FOOD);
            if (foodComponent != null) {
                itemBuilder.edible(true);
                itemBuilder.canAlwaysEat(foodComponent.canAlwaysEat());
            }

            itemBuilder.foil(components.getOrDefault(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false));
            itemOptions.unbreakable(realDefaultItemStack.isDamageable());
            itemBuilder.chargeable(realItem instanceof CrossbowItem || realItem instanceof BowItem);

            itemBuilder.customItemOptions(itemOptions.build());
            itemBuilder.javaId(Registries.ITEM.getRawIdOrThrow(realItem));

            BlockItem blockItem = null;
            if (realItem instanceof BlockItem bi) {
                blockItem = bi;
                itemBuilder.block(Registries.BLOCK.getEntry(bi.getBlock()).getIdAsString());
            }

            String bedrockKey = "item." + identifier + ".name";
            addTranslationKey(bedrockKey, realItem.getTranslationKey());

            boolean iconAssigned = false;

            try {
                JsonObject itemDescription = model != null ? resolveItemDescription(model) : null;
                Identifier modelId = null;

                if (itemDescription != null) {
                    if (itemDescription.has("model")
                            && itemDescription.get("model").isJsonObject()
                            && itemDescription.getAsJsonObject("model").has("model")) {
                        modelId = Identifier.of(itemDescription.getAsJsonObject("model").get("model").getAsString());
                    } else {
                        modelId = model;
                    }
                } else if (blockItem != null) {
                    modelId = resolveBlockItemModelId(identifier, blockItem, model == null ? identifier : model);
                }

                if (modelId != null) {
                    JsonObject modelObject = resolveItemModelObject(modelId);
                    if (modelObject != null) {
                        iconAssigned = tryApplyIconFromModelObject(identifier, itemBuilder, textureDataObject, packRoot, modelObject);

                        Identifier parentId = tryGetParentIdentifier(modelObject);
                        boolean handheld = parentId != null && parentId.equals(BedframeConstants.HANDHELD_IDENTIFIER);
                        if (handheld) {
                            itemBuilder.displayHandheld(true);
                        }
                    } else {
                        LOGGER.warn("Skipping direct model-object resolution for {} because no model object could be resolved from {}", identifier, modelId);
                    }
                }

                if (!iconAssigned && blockItem != null) {
                    iconAssigned = tryApplyGeometryBasedBlockIcon(
                            identifier,
                            blockItem,
                            itemBuilder,
                            textureDataObject,
                            packRoot
                    );
                }

                if (!iconAssigned && blockItem != null) {
                    Identifier blockFallbackModelId = resolveBlockItemModelId(identifier, blockItem, model == null ? identifier : model);
                    if (blockFallbackModelId != null) {
                        JsonObject blockModelObject = resolveItemModelObject(blockFallbackModelId);
                        if (blockModelObject != null) {
                            iconAssigned = tryApplyIconFromBlockModel(
                                    identifier,
                                    itemBuilder,
                                    textureDataObject,
                                    packRoot,
                                    blockFallbackModelId,
                                    blockModelObject
                            );
                        }
                    }
                }

                if (!iconAssigned) {
                    LOGGER.warn("Item {} still has no icon after item/block fallback", identifier);
                }
            } catch (NullPointerException e) {
                LOGGER.warn("Item {} has no model", identifier);
            }

            registeredItems.add(realItem);
            event.register(itemBuilder.build());
        });

        itemTextureObject.add("texture_data", textureDataObject);
        writeJsonToFile(itemTextureObject, textureDir.resolve("item_texture.json").toFile());
        markResourcesProvided();
    }

    @Override
    public void register(EventBus<EventRegistrar> eventBus, Path packRoot) {
        eventBus.subscribe(this, GeyserDefineCustomItemsEvent.class, event -> handle(event, packRoot));
    }

    private JsonObject resolveItemDescription(Identifier modelId) {
        JsonObject indexed = PolymerPackContentIndex.findModelJson(modelId);
        if (indexed != null) {
            return indexed;
        }

        String path = modelId.getPath();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        candidates.add("items/" + path + ".json");
        candidates.add("models/" + path + ".json");

        if (!path.startsWith("item/")) {
            candidates.add("models/item/" + path + ".json");
        } else {
            candidates.add("models/" + path.substring("item/".length()) + ".json");
        }

        if (!path.startsWith("block/")) {
            candidates.add("models/block/" + path + ".json");
        } else {
            candidates.add("models/" + path.substring("block/".length()) + ".json");
        }

        for (String candidate : candidates) {
            JsonObject obj = ResourceHelper.tryReadJsonResource(modelId.getNamespace(), candidate);
            if (obj != null) {
                return obj;
            }
        }

        LOGGER.warn("Couldn't resolve item description {} from zip index or candidates {}", modelId, candidates);
        return null;
    }

    private JsonObject resolveItemModelObject(Identifier modelId) {
        JsonObject indexed = PolymerPackContentIndex.findModelJson(modelId);
        if (indexed != null) {
            return indexed;
        }

        String path = modelId.getPath();
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        candidates.add("models/" + path + ".json");

        if (!path.startsWith("item/") && !path.startsWith("block/")) {
            candidates.add("models/item/" + path + ".json");
            candidates.add("models/block/" + path + ".json");
        }

        if (path.startsWith("item/")) {
            candidates.add("models/" + path.substring("item/".length()) + ".json");
        } else if (path.startsWith("block/")) {
            candidates.add("models/" + path.substring("block/".length()) + ".json");
        }

        for (String candidate : candidates) {
            JsonObject obj = ResourceHelper.tryReadJsonResource(modelId.getNamespace(), candidate);
            if (obj != null) {
                return obj;
            }
        }

        LOGGER.warn("Couldn't resolve item model {} from zip index or candidates {}", modelId, candidates);
        return null;
    }

    private Identifier resolveBlockItemModelId(Identifier itemId, BlockItem blockItem, Identifier originalModel) {
        Block block = blockItem.getBlock();
        Identifier blockId = Registries.BLOCK.getId(block);

        for (String raw : ConvertedModelRegistry.getModelCandidates(blockId)) {
            for (Identifier expanded : expandConvertedModelString(blockId, raw)) {
                JsonObject obj = resolveItemModelObject(expanded);
                if (obj != null) {
                    LOGGER.warn("Resolved block-item model for {} using converted candidate {}", itemId, expanded);
                    return expanded;
                }
            }
        }

        for (Identifier candidate : PolymerPackContentIndex.findBestItemModelCandidates(blockId)) {
            JsonObject obj = resolveItemModelObject(candidate);
            if (obj != null) {
                LOGGER.warn("Resolved block-item model for {} using polymer zip indexed block candidate {}", itemId, candidate);
                return candidate;
            }
        }

        for (Identifier candidate : PolymerPackContentIndex.findBestItemModelCandidates(itemId)) {
            JsonObject obj = resolveItemModelObject(candidate);
            if (obj != null) {
                LOGGER.warn("Resolved block-item model for {} using polymer zip indexed item candidate {}", itemId, candidate);
                return candidate;
            }
        }

        for (Identifier candidate : buildBlockItemCandidates(blockId)) {
            JsonObject obj = resolveItemModelObject(candidate);
            if (obj != null) {
                LOGGER.warn("Resolved block-item model for {} using block fallback candidate {}", itemId, candidate);
                return candidate;
            }
        }

        JsonObject original = resolveItemModelObject(originalModel);
        if (original != null) {
            LOGGER.warn("Resolved block-item model for {} using original model {}", itemId, originalModel);
            return originalModel;
        }

        LOGGER.warn("Couldn't resolve block-item model for {} (block {})", itemId, blockId);
        return null;
    }

    private java.util.List<Identifier> buildBlockItemCandidates(Identifier blockId) {
        java.util.LinkedHashSet<Identifier> out = new java.util.LinkedHashSet<>();

        out.addAll(PolymerPackContentIndex.findBestItemModelCandidates(blockId));

        String ns = blockId.getNamespace();
        String path = blockId.getPath();

        out.add(Identifier.of(ns, path));
        out.add(Identifier.of(ns, "block/" + path));
        out.add(Identifier.of(ns, "item/" + path));

        out.add(Identifier.of(ns, "furniture/" + path));
        out.add(Identifier.of(ns, "furniture/block/" + path));
        out.add(Identifier.of(ns, "furniture/item/" + path));

        out.add(Identifier.of(ns, "custom/" + path));
        out.add(Identifier.of(ns, "custom/block/" + path));
        out.add(Identifier.of(ns, "custom/item/" + path));

        return java.util.List.copyOf(out);
    }

    private java.util.List<Identifier> expandConvertedModelString(Identifier blockId, String modelString) {
        java.util.LinkedHashSet<Identifier> out = new java.util.LinkedHashSet<>();

        String blockNamespace = blockId.getNamespace();
        String raw = modelString.trim();

        if (raw.matches("^[a-z0-9_.-]+:[a-z0-9/._-]+$")) {
            try {
                out.add(Identifier.of(raw));
            } catch (RuntimeException ignored) {
            }
            return java.util.List.copyOf(out);
        }

        if (raw.startsWith("furniture:item/")) {
            String rest = raw.substring("furniture:item/".length());
            out.add(Identifier.of(blockNamespace, "item/" + rest));
            out.add(Identifier.of(blockNamespace, "furniture/item/" + rest));
            out.add(Identifier.of(blockNamespace, rest));
            return java.util.List.copyOf(out);
        }

        if (raw.startsWith("furniture:block/")) {
            String rest = raw.substring("furniture:block/".length());
            out.add(Identifier.of(blockNamespace, "block/" + rest));
            out.add(Identifier.of(blockNamespace, "furniture/block/" + rest));
            out.add(Identifier.of(blockNamespace, rest));
            return java.util.List.copyOf(out);
        }

        if (raw.startsWith("item/")) {
            out.add(Identifier.of(blockNamespace, raw));
            out.add(Identifier.of(blockNamespace, raw.substring("item/".length())));
            return java.util.List.copyOf(out);
        }

        if (raw.startsWith("block/")) {
            out.add(Identifier.of(blockNamespace, raw));
            out.add(Identifier.of(blockNamespace, raw.substring("block/".length())));
            return java.util.List.copyOf(out);
        }

        out.add(Identifier.of(blockNamespace, raw));
        return java.util.List.copyOf(out);
    }

    private boolean tryApplyIconFromModelObject(
            Identifier itemId,
            NonVanillaCustomItemData.Builder itemBuilder,
            JsonObject textureDataObject,
            Path packRoot,
            JsonObject modelObject
    ) {
        Identifier parentId = tryGetParentIdentifier(modelObject);
        boolean handheld = parentId != null && parentId.equals(BedframeConstants.HANDHELD_IDENTIFIER);

        if (parentId != null && (parentId.equals(BedframeConstants.GENERATED_IDENTIFIER) || handheld)) {
            JsonObject textures = modelObject.getAsJsonObject("textures");
            if (textures == null || !textures.has("layer0")) {
                return false;
            }

            Identifier textureId = Identifier.of(textures.get("layer0").getAsString());
            applyTextureAsIcon(itemId, itemBuilder, textureDataObject, packRoot, textureId);
            return true;
        }

        return false;
    }

    private boolean tryApplyIconFromBlockModel(
            Identifier itemId,
            NonVanillaCustomItemData.Builder itemBuilder,
            JsonObject textureDataObject,
            Path packRoot,
            Identifier modelId,
            JsonObject modelObject
    ) {
        BlockModelFaces faces = extractBlockModelFaces(modelId, modelObject);
        if (faces == null) {
            LOGGER.warn("Couldn't extract fallback block-item faces for {} from model {}", itemId, modelId);
            return false;
        }

        try {
            return applyPseudo3DBlockIcon(itemId, itemBuilder, textureDataObject, packRoot, faces);
        } catch (Exception e) {
            LOGGER.warn("Failed to generate pseudo 3D block icon for {} from {}", itemId, modelId, e);
            return false;
        }
    }

    private Identifier tryGetParentIdentifier(JsonObject modelObject) {
        if (modelObject == null || !modelObject.has("parent")) {
            return null;
        }

        try {
            return Identifier.of(modelObject.get("parent").getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Identifier extractBestTextureFromModel(Identifier modelId, JsonObject modelObject) {
        JsonObject textures = modelObject.getAsJsonObject("textures");
        if (textures == null) {
            return null;
        }

        String[] preferredKeys = new String[] {
                "layer0", "all", "side", "top", "front", "particle", "north", "south", "east", "west"
        };

        for (String key : preferredKeys) {
            if (!textures.has(key)) {
                continue;
            }

            Identifier texture = parseTextureIdentifier(modelId, textures.get(key));
            if (texture != null) {
                return texture;
            }
        }

        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            Identifier texture = parseTextureIdentifier(modelId, entry.getValue());
            if (texture != null) {
                return texture;
            }
        }

        return null;
    }

    private Identifier parseTextureIdentifier(Identifier modelId, JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        String raw = element.getAsString();
        if (raw == null || raw.isBlank() || raw.startsWith("#")) {
            return null;
        }

        try {
            if (raw.contains(":")) {
                return Identifier.of(raw);
            }
            return Identifier.of(modelId.getNamespace(), raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyTextureAsIcon(
            Identifier itemId,
            NonVanillaCustomItemData.Builder itemBuilder,
            JsonObject textureDataObject,
            Path packRoot,
            Identifier textureId
    ) {
        String texturePath = "textures/" + textureId.getPath();
        String bedrockPath = ResourceHelper.javaToBedrockTexture(texturePath);
        String textureName = itemId.toString();

        JsonObject textureObject = new JsonObject();
        textureObject.addProperty("textures", bedrockPath);

        textureDataObject.add(textureName, textureObject);
        ResourceHelper.copyResource(textureId.getNamespace(), texturePath + ".png", packRoot.resolve(bedrockPath + ".png"));

        itemBuilder.icon(textureName);
    }

    private record BlockModelFaces(
            Identifier top,
            Identifier side,
            Identifier front
    ) {
    }

    private BlockModelFaces extractBlockModelFaces(Identifier modelId, JsonObject modelObject) {
        JsonObject textures = modelObject.getAsJsonObject("textures");
        if (textures == null) {
            return null;
        }

        Identifier all = getTextureByKey(modelId, textures, "all");
        Identifier top = getTextureByKey(modelId, textures, "top");
        Identifier side = getTextureByKey(modelId, textures, "side");
        Identifier front = getTextureByKey(modelId, textures, "front");

        if (top == null) {
            top = all;
        }
        if (side == null) {
            side = all;
        }
        if (front == null) {
            front = side != null ? side : all;
        }

        if (top == null && side == null && front == null) {
            Identifier fallback = extractBestTextureFromModel(modelId, modelObject);
            if (fallback == null) {
                return null;
            }
            return new BlockModelFaces(fallback, fallback, fallback);
        }

        if (top == null) {
            top = side != null ? side : front;
        }
        if (side == null) {
            side = front != null ? front : top;
        }
        if (front == null) {
            front = side != null ? side : top;
        }

        if (top == null || side == null || front == null) {
            return null;
        }

        return new BlockModelFaces(top, side, front);
    }

    private Identifier getTextureByKey(Identifier modelId, JsonObject textures, String key) {
        if (!textures.has(key)) {
            return null;
        }
        return parseTextureIdentifier(modelId, textures.get(key));
    }

    private boolean applyPseudo3DBlockIcon(
            Identifier itemId,
            NonVanillaCustomItemData.Builder itemBuilder,
            JsonObject textureDataObject,
            Path packRoot,
            BlockModelFaces faces
    ) throws Exception {
        BufferedImage top = readTextureImage(faces.top());
        BufferedImage left = readTextureImage(faces.side());
        BufferedImage right = readTextureImage(faces.front());

        if (top == null || left == null || right == null) {
            LOGGER.warn("Missing block face textures for pseudo 3D icon of {} top={} side={} front={}",
                    itemId, faces.top(), faces.side(), faces.front());
            return false;
        }

        BufferedImage icon = BlockIconRenderer.renderPseudo3D(
                new BlockIconRenderer.BlockFaces(top, left, right)
        );

        String textureName = itemId.toString();
        String outputRelative = "textures/items/generated/" + itemId.getNamespace() + "_" + itemId.getPath();
        String bedrockPath = ResourceHelper.javaToBedrockTexture(outputRelative);

        Path output = packRoot.resolve(bedrockPath + ".png");
        BlockIconRenderer.writePng(icon, output);

        JsonObject textureObject = new JsonObject();
        textureObject.addProperty("textures", bedrockPath);
        textureDataObject.add(textureName, textureObject);

        itemBuilder.icon(textureName);
        LOGGER.warn("Using pseudo 3D block icon for {}", itemId);
        return true;
    }

    private BufferedImage readTextureImage(Identifier textureId) throws Exception {
        String texturePath = "textures/" + textureId.getPath() + ".png";
        try (InputStream in = ResourceHelper.getResource(textureId.getNamespace(), texturePath)) {
            if (in == null) {
                LOGGER.warn("Missing texture source for {} at {}", textureId, texturePath);
                return null;
            }
            return BlockIconRenderer.readPng(in);
        }
    }

    private boolean tryApplyGeometryBasedBlockIcon(
            Identifier itemId,
            BlockItem blockItem,
            NonVanillaCustomItemData.Builder itemBuilder,
            JsonObject textureDataObject,
            Path packRoot
    ) {
        try {
            Identifier blockId = Registries.BLOCK.getId(blockItem.getBlock());

            var oversized = AutoOversizedDisplayRegistry.get(blockId);
            if (oversized == null) {
                return false;
            }

            Path geometryFile = oversized.geoPath();
            if (geometryFile == null || !Files.exists(geometryFile)) {
                LOGGER.warn("No geometry file for block item {} block {}", itemId, blockId);
                return false;
            }

            JsonObject geometryRoot = GeometryIconRenderer.readGeometry(geometryFile);
            if (geometryRoot == null) {
                return false;
            }

            Identifier textureId = oversized.anchorBlockId();
            if (textureId == null) {
                LOGGER.warn("No oversized textureId for block item {} block {}", itemId, blockId);
                return false;
            }

            String texturePath = "textures/" + textureId.getPath() + ".png";
            try (InputStream in = ResourceHelper.getResource(textureId.getNamespace(), texturePath)) {
                if (in == null) {
                    LOGGER.warn("Missing texture source for geometry icon {} at {}", textureId, texturePath);
                    return false;
                }

                GeometryIconRenderer.RenderTextureSet textureSet = GeometryIconRenderer.readTextureSet(in);
                if (textureSet == null) {
                    return false;
                }

                BufferedImage icon = GeometryIconRenderer.renderFromGeometry(geometryRoot, textureSet, 32);
                if (icon == null) {
                    return false;
                }

                String textureName = itemId.toString();
                String outputRelative = "textures/items/generated/" + itemId.getNamespace() + "_" + itemId.getPath();
                String bedrockPath = ResourceHelper.javaToBedrockTexture(outputRelative);

                Path output = packRoot.resolve(bedrockPath + ".png");
                GeometryIconRenderer.writePng(icon, output);

                JsonObject textureObject = new JsonObject();
                textureObject.addProperty("textures", bedrockPath);
                textureDataObject.add(textureName, textureObject);

                itemBuilder.icon(textureName);
                LOGGER.warn("Using geometry-based pseudo 3D icon for {}", itemId);
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed geometry-based block icon render for {}", itemId, e);
            return false;
        }
    }
}
