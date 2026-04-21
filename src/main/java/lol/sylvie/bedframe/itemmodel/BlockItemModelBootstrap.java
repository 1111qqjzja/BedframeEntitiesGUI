package lol.sylvie.bedframe.itemmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lol.sylvie.bedframe.util.ConvertedModelRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class BlockItemModelBootstrap {
    private BlockItemModelBootstrap() {
    }

    public static void load(Path geometryRoot) {
        BlockItemRenderRegistry.clear();

        GeometryIndex geometryIndex = buildGeometryIndex(geometryRoot);

        for (Identifier itemId : Registries.ITEM.getIds()) {
            Item item = Registries.ITEM.get(itemId);
            if (!(item instanceof BlockItem blockItem)) {
                continue;
            }

            Block block = blockItem.getBlock();
            Identifier blockId = Registries.BLOCK.getId(block);

            ModelResolution resolution = resolveBlockItemModel(itemId, blockId);
            if (resolution == null) {
                continue;
            }

            Identifier modelId = resolution.modelId();
            JsonObject modelObject = resolveModelObject(modelId);
            if (modelObject == null) {
                continue;
            }

            Identifier textureId = extractBestTextureFromModel(modelId, modelObject);
            if (textureId == null) {
                LOGGER.warn("No usable texture found for block item {} from model {}", itemId, modelId);
                continue;
            }

            List<GeoLookup> matchedGeometries = new ArrayList<>();

            var oversizedSpec = lol.sylvie.bedframe.display.AutoOversizedDisplayRegistry.get(blockId);
            if (oversizedSpec != null) {
                Path oversizedGeoPath = geometryIndex.byIdentifier().get(oversizedSpec.geometryIdentifier());
                if (oversizedGeoPath != null) {
                    matchedGeometries.add(new GeoLookup(oversizedSpec.geometryIdentifier(), oversizedGeoPath));
                    LOGGER.warn("Using oversized geometry for block item {} -> {}", itemId, oversizedSpec.geometryIdentifier());
                }
            }

            if (matchedGeometries.isEmpty()) {
                matchedGeometries = mapToGeometries(itemId, blockId, resolution, geometryIndex);
            }

            if (matchedGeometries.isEmpty()) {
                LOGGER.warn("No attachable geometry match found for block item {} (block {})", itemId, blockId);
                continue;
            }

            GeoLookup best = matchedGeometries.get(0);

            boolean handheldLike = false;
            Identifier parentId = tryGetParentIdentifier(modelObject);
            if (parentId != null && parentId.getPath().contains("handheld")) {
                handheldLike = true;
            }

            String textureOutputPath = "textures/entity/items/" + itemId.getNamespace() + "_" + itemId.getPath();

            BlockItemRenderSpec spec = new BlockItemRenderSpec(
                    itemId,
                    blockId,
                    itemId,
                    best.geometryIdentifier(),
                    best.geoPath(),
                    textureId,
                    textureOutputPath,
                    handheldLike, 0.7f, 0.7f, 0.7f,
                    0.0f, 0.1f, 0.0f
            );

            BlockItemRenderRegistry.register(spec);

            LOGGER.warn("Registered block-item attachable spec {} -> geometry {} texture {}",
                    itemId, best.geometryIdentifier(), textureId);
        }

        LOGGER.warn("Block item render registry loaded {} entries", BlockItemRenderRegistry.all().size());
    }

    private static GeometryIndex buildGeometryIndex(Path geometryRoot) {
        Map<String, Path> byIdentifier = new LinkedHashMap<>();
        Map<String, List<GeoLookup>> bySimpleName = new LinkedHashMap<>();

        if (!Files.exists(geometryRoot)) {
            LOGGER.warn("Geometry root does not exist: {}", geometryRoot);
            return new GeometryIndex(byIdentifier, bySimpleName);
        }

        try (Stream<Path> stream = Files.walk(geometryRoot)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".geo.json"))
                    .sorted()
                    .toList();

            for (Path file : files) {
                try {
                    JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                    if (root == null) continue;

                    JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
                    if (geometries == null || geometries.isEmpty()) continue;

                    JsonObject geometry = geometries.get(0).getAsJsonObject();
                    JsonObject description = geometry.getAsJsonObject("description");
                    if (description == null || !description.has("identifier")) continue;

                    String geometryIdentifier = description.get("identifier").getAsString();
                    if (geometryIdentifier == null || geometryIdentifier.isBlank()) continue;

                    byIdentifier.put(geometryIdentifier, file);

                    String simpleName = extractSimpleName(geometryIdentifier);
                    GeoLookup lookup = new GeoLookup(geometryIdentifier, file);

                    bySimpleName.computeIfAbsent(simpleName, ignored -> new ArrayList<>()).add(lookup);
                } catch (Exception e) {
                    LOGGER.warn("Failed to index geometry file {}", file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan geometry directory {}", geometryRoot, e);
        }

        LOGGER.warn("Indexed {} block-item geometry identifiers from {}", byIdentifier.size(), geometryRoot);
        return new GeometryIndex(byIdentifier, bySimpleName);
    }

    private static ModelResolution resolveBlockItemModel(Identifier itemId, Identifier blockId) {
        for (String raw : ConvertedModelRegistry.getModelCandidates(blockId)) {
            for (Identifier expanded : expandConvertedModelString(blockId, raw)) {
                JsonObject obj = resolveModelObject(expanded);
                if (obj != null) {
                    LOGGER.warn("Resolved block-item model for {} using converted candidate {} (raw={})", itemId, expanded, raw);
                    return new ModelResolution(expanded, raw);
                }
            }
        }

        for (Identifier candidate : buildBlockItemCandidates(blockId)) {
            JsonObject obj = resolveModelObject(candidate);
            if (obj != null) {
                LOGGER.warn("Resolved block-item model for {} using block fallback candidate {}", itemId, candidate);
                return new ModelResolution(candidate, candidate.toString());
            }
        }

        LOGGER.warn("Couldn't resolve block-item model for {} (block {})", itemId, blockId);
        return null;
    }

    private static JsonObject resolveModelObject(Identifier modelId) {
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
            JsonObject obj = lol.sylvie.bedframe.util.ResourceHelper.tryReadJsonResource(modelId.getNamespace(), candidate);
            if (obj != null) {
                return obj;
            }
        }

        return null;
    }

    private static List<Identifier> buildBlockItemCandidates(Identifier blockId) {
        LinkedHashSet<Identifier> out = new LinkedHashSet<>();

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

        return List.copyOf(out);
    }

    private static List<Identifier> expandConvertedModelString(Identifier blockId, String modelString) {
        LinkedHashSet<Identifier> out = new LinkedHashSet<>();

        String blockNamespace = blockId.getNamespace();
        String raw = modelString.trim();

        if (raw.matches("^[a-z0-9_.-]+:[a-z0-9/._-]+$")) {
            try {
                out.add(Identifier.of(raw));
            } catch (RuntimeException ignored) {
            }
            return List.copyOf(out);
        }

        if (raw.startsWith("furniture:item/")) {
            String rest = raw.substring("furniture:item/".length());
            out.add(Identifier.of(blockNamespace, "item/" + rest));
            out.add(Identifier.of(blockNamespace, "furniture/item/" + rest));
            out.add(Identifier.of(blockNamespace, rest));
            return List.copyOf(out);
        }

        if (raw.startsWith("furniture:block/")) {
            String rest = raw.substring("furniture:block/".length());
            out.add(Identifier.of(blockNamespace, "block/" + rest));
            out.add(Identifier.of(blockNamespace, "furniture/block/" + rest));
            out.add(Identifier.of(blockNamespace, rest));
            return List.copyOf(out);
        }

        if (raw.startsWith("item/")) {
            out.add(Identifier.of(blockNamespace, raw));
            out.add(Identifier.of(blockNamespace, raw.substring("item/".length())));
            return List.copyOf(out);
        }

        if (raw.startsWith("block/")) {
            out.add(Identifier.of(blockNamespace, raw));
            out.add(Identifier.of(blockNamespace, raw.substring("block/".length())));
            return List.copyOf(out);
        }

        out.add(Identifier.of(blockNamespace, raw));
        return List.copyOf(out);
    }

    private static List<GeoLookup> mapToGeometries(
            Identifier itemId,
            Identifier blockId,
            ModelResolution resolution,
            GeometryIndex geometryIndex
    ) {
        LinkedHashSet<GeoLookup> out = new LinkedHashSet<>();

        String itemName = itemId.getPath();
        String blockName = blockId.getPath();
        String modelPath = resolution.modelId().getPath();
        String rawModel = resolution.rawModelString();

        addNameVariants(out, geometryIndex, itemName);
        if (!Objects.equals(itemName, blockName)) {
            addNameVariants(out, geometryIndex, blockName);
        }

        for (String candidate : buildModelPathCandidates(modelPath)) {
            addNameVariants(out, geometryIndex, candidate);
        }

        for (String candidate : buildRawModelCandidates(rawModel)) {
            addNameVariants(out, geometryIndex, candidate);
        }

        LOGGER.warn("Geometry match candidates for item {} block {} model {} raw {} -> {}",
                itemId, blockId, resolution.modelId(), rawModel, out.stream().map(GeoLookup::geometryIdentifier).toList());

        return List.copyOf(out);
    }

    private static void addBySimpleName(Set<GeoLookup> out, GeometryIndex geometryIndex, String simpleName) {
        List<GeoLookup> matches = geometryIndex.bySimpleName().get(simpleName);
        if (matches != null) {
            out.addAll(matches);
        }
    }

    private static void addByIdentifier(Set<GeoLookup> out, GeometryIndex geometryIndex, String identifier) {
        Path path = geometryIndex.byIdentifier().get(identifier);
        if (path != null) {
            out.add(new GeoLookup(identifier, path));
        }
    }

    private static Identifier tryGetParentIdentifier(JsonObject modelObject) {
        if (modelObject == null || !modelObject.has("parent")) {
            return null;
        }

        try {
            return Identifier.of(modelObject.get("parent").getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Identifier extractBestTextureFromModel(Identifier modelId, JsonObject modelObject) {
        JsonObject textures = modelObject.getAsJsonObject("textures");
        if (textures == null) {
            return null;
        }

        String[] preferredKeys = new String[] {
                "layer0", "all", "side", "top", "front", "particle", "north", "south", "east", "west"
        };

        for (String key : preferredKeys) {
            if (!textures.has(key)) continue;
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

    private static Identifier parseTextureIdentifier(Identifier modelId, JsonElement element) {
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

    private static String extractSimpleName(String geometryIdentifier) {
        int dot = geometryIdentifier.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < geometryIdentifier.length()) {
            return geometryIdentifier.substring(dot + 1);
        }
        return geometryIdentifier;
    }

    private record GeometryIndex(
            Map<String, Path> byIdentifier,
            Map<String, List<GeoLookup>> bySimpleName
    ) {
    }

    private record GeoLookup(String geometryIdentifier, Path geoPath) {
    }

    private static void addNameVariants(Set<GeoLookup> out, GeometryIndex geometryIndex, String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        addBySimpleName(out, geometryIndex, extractLeaf(name));

        addByIdentifier(out, geometryIndex, "geometry.furniture." + name);
        addByIdentifier(out, geometryIndex, "geometry.custom." + name);
        addByIdentifier(out, geometryIndex, "geometry." + name);

        String leaf = extractLeaf(name);
        if (!leaf.equals(name)) {
            addByIdentifier(out, geometryIndex, "geometry.furniture." + leaf);
            addByIdentifier(out, geometryIndex, "geometry.custom." + leaf);
            addByIdentifier(out, geometryIndex, "geometry." + leaf);
        }
    }

    private static List<String> buildModelPathCandidates(String modelPath) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (modelPath == null || modelPath.isBlank()) {
            return List.of();
        }

        String normalized = modelPath;
        out.add(normalized);

        String[] parts = normalized.split("/");
        if (parts.length == 0) {
            return List.copyOf(out);
        }

        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = String.join("/", Arrays.copyOfRange(parts, i, parts.length));
            out.add(candidate);
        }

        return List.copyOf(out);
    }

    private static String extractLeaf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            return path.substring(slash + 1);
        }
        return path;
    }

    private record ModelResolution(Identifier modelId, String rawModelString) {
    }

    private static List<String> buildRawModelCandidates(String rawModel) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (rawModel == null || rawModel.isBlank()) {
            return List.of();
        }

        String raw = rawModel.trim();

        String withoutNamespace = raw;
        int colon = raw.indexOf(':');
        if (colon >= 0 && colon + 1 < raw.length()) {
            withoutNamespace = raw.substring(colon + 1);
        }

        out.add(raw);
        out.add(withoutNamespace);

        String[] parts = withoutNamespace.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = String.join("/", Arrays.copyOfRange(parts, i, parts.length));
            out.add(candidate);
        }

        String[] prefixes = new String[] {
                "custom/",
                "furniture/",
                "item/",
                "block/",
                "furniture/item/",
                "furniture/block/",
                "custom/furniture/"
        };

        for (String prefix : prefixes) {
            if (withoutNamespace.startsWith(prefix) && withoutNamespace.length() > prefix.length()) {
                String trimmed = withoutNamespace.substring(prefix.length());
                out.add(trimmed);

                String[] trimmedParts = trimmed.split("/");
                for (int i = trimmedParts.length - 1; i >= 0; i--) {
                    String candidate = String.join("/", Arrays.copyOfRange(trimmedParts, i, trimmedParts.length));
                    out.add(candidate);
                }
            }
        }

        return List.copyOf(out);
    }
}
