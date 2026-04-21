package lol.sylvie.bedframe.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class PolymerPackContentIndex {
    public enum ModelKind {
        BLOCK,
        ITEM,
        UNKNOWN
    }

    public record IndexedModel(
            String zipPath,
            Identifier modelId,
            String namespace,
            String modelPath,
            ModelKind kind,
            JsonObject json
    ) {
    }

    private static final Object LOCK = new Object();

    private static ZipFile indexedZip;
    private static String indexedZipIdentity;

    private static final LinkedHashSet<String> ALL_PATHS = new LinkedHashSet<>();
    private static final LinkedHashMap<String, IndexedModel> MODELS_BY_ZIP_PATH = new LinkedHashMap<>();
    private static final LinkedHashMap<String, IndexedModel> MODELS_BY_MODEL_ID = new LinkedHashMap<>();

    private PolymerPackContentIndex() {
    }

    public static void ensureIndexed() {
        ZipFile zip = ResourceHelper.POLYMER_GENERATED_PACK;
        if (zip == null) {
            return;
        }

        String identity = zipIdentity(zip);

        synchronized (LOCK) {
            if (indexedZip == zip && Objects.equals(indexedZipIdentity, identity) && !ALL_PATHS.isEmpty()) {
                return;
            }

            rebuild(zip, identity);
        }
    }

    public static Set<String> allPaths() {
        ensureIndexed();
        synchronized (LOCK) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(ALL_PATHS));
        }
    }

    public static JsonObject findModelJson(Identifier modelId) {
        if (modelId == null) {
            return null;
        }

        ensureIndexed();
        synchronized (LOCK) {
            IndexedModel model = MODELS_BY_MODEL_ID.get(modelId.toString());
            return model == null ? null : model.json();
        }
    }

    public static List<Identifier> findBestBlockModelCandidates(Identifier blockId, BlockState state) {
        if (blockId == null) {
            return List.of();
        }

        ensureIndexed();

        List<ScoredIdentifier> scored = new ArrayList<>();

        synchronized (LOCK) {
            for (IndexedModel model : MODELS_BY_ZIP_PATH.values()) {
                int score = scoreForBlock(blockId, state, model);
                if (score > 0) {
                    scored.add(new ScoredIdentifier(model.modelId(), score));
                }
            }
        }

        scored.sort(Comparator.comparingInt(ScoredIdentifier::score).reversed());

        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        List<Identifier> out = new ArrayList<>();
        for (ScoredIdentifier entry : scored) {
            if (dedupe.add(entry.id().toString())) {
                out.add(entry.id());
            }
        }

        if (!out.isEmpty()) {
            LOGGER.warn("PolymerPackContentIndex block candidates for {} => {}",
                    blockId, out.stream().limit(12).map(Identifier::toString).toList());
        } else {
            LOGGER.warn("PolymerPackContentIndex found no block candidates for {}", blockId);
        }

        return out;
    }

    public static List<Identifier> findBestItemModelCandidates(Identifier itemOrBlockId) {
        if (itemOrBlockId == null) {
            return List.of();
        }

        ensureIndexed();

        List<ScoredIdentifier> scored = new ArrayList<>();

        synchronized (LOCK) {
            for (IndexedModel model : MODELS_BY_ZIP_PATH.values()) {
                int score = scoreForItem(itemOrBlockId, model);
                if (score > 0) {
                    scored.add(new ScoredIdentifier(model.modelId(), score));
                }
            }
        }

        scored.sort(Comparator.comparingInt(ScoredIdentifier::score).reversed());

        LinkedHashSet<String> dedupe = new LinkedHashSet<>();
        List<Identifier> out = new ArrayList<>();
        for (ScoredIdentifier entry : scored) {
            if (dedupe.add(entry.id().toString())) {
                out.add(entry.id());
            }
        }

        if (!out.isEmpty()) {
            LOGGER.warn("PolymerPackContentIndex item candidates for {} => {}",
                    itemOrBlockId, out.stream().limit(12).map(Identifier::toString).toList());
        } else {
            LOGGER.warn("PolymerPackContentIndex found no item candidates for {}", itemOrBlockId);
        }

        return out;
    }

    private static void rebuild(ZipFile zip, String identity) {
        ALL_PATHS.clear();
        MODELS_BY_ZIP_PATH.clear();
        MODELS_BY_MODEL_ID.clear();

        int totalEntries = 0;
        int modelCount = 0;

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry == null || entry.isDirectory()) {
                continue;
            }

            String rawName = entry.getName();
            if (rawName == null || rawName.isBlank()) {
                continue;
            }

            totalEntries++;
            String path = normalize(rawName);
            ALL_PATHS.add(path);

            IndexedModel indexedModel = tryIndexModel(zip, path);
            if (indexedModel != null) {
                MODELS_BY_ZIP_PATH.put(path, indexedModel);
                MODELS_BY_MODEL_ID.putIfAbsent(indexedModel.modelId().toString(), indexedModel);
                modelCount++;
            }
        }

        indexedZip = zip;
        indexedZipIdentity = identity;

        LOGGER.warn("PolymerPackContentIndex indexed zip: entries={}, models={}, zip={}",
                totalEntries, modelCount, identity);
    }

    private static IndexedModel tryIndexModel(ZipFile zip, String zipPath) {
        if (!zipPath.startsWith("assets/") || !zipPath.endsWith(".json")) {
            return null;
        }

        String[] parts = zipPath.split("/", 4);
        if (parts.length < 4) {
            return null;
        }

        if (!"assets".equals(parts[0])) {
            return null;
        }

        String namespace = parts[1];
        String section = parts[2];
        String rest = parts[3];

        if (!"models".equals(section)) {
            return null;
        }

        if (!rest.endsWith(".json")) {
            return null;
        }

        String modelPath = rest.substring(0, rest.length() - ".json".length());

        Identifier modelId;
        try {
            modelId = Identifier.of(namespace, modelPath);
        } catch (RuntimeException e) {
            return null;
        }

        JsonObject json = readJson(zip, zipPath);
        if (json == null) {
            return null;
        }

        ModelKind kind = classifyModel(modelPath, json);
        return new IndexedModel(zipPath, modelId, namespace, modelPath, kind, json);
    }

    private static JsonObject readJson(ZipFile zip, String zipPath) {
        try {
            ZipEntry entry = zip.getEntry(zipPath);
            if (entry == null) {
                return null;
            }

            try (InputStream in = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(in)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read json from polymer zip path {}", zipPath, e);
            return null;
        }
    }

    private static ModelKind classifyModel(String modelPath, JsonObject json) {
        String normalizedPath = normalize(modelPath);

        if (normalizedPath.startsWith("block/")) {
            return ModelKind.BLOCK;
        }
        if (normalizedPath.startsWith("item/")) {
            return ModelKind.ITEM;
        }

        JsonObject textures = json.getAsJsonObject("textures");
        if (textures != null) {
            if (textures.has("layer0")) {
                return ModelKind.ITEM;
            }

            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                String key = normalize(entry.getKey());
                if (key.equals("all")
                        || key.equals("side")
                        || key.equals("top")
                        || key.equals("bottom")
                        || key.equals("front")
                        || key.equals("north")
                        || key.equals("south")
                        || key.equals("east")
                        || key.equals("west")
                        || key.equals("particle")) {
                    return ModelKind.BLOCK;
                }
            }
        }

        if (json.has("elements") && json.get("elements").isJsonArray()) {
            return ModelKind.BLOCK;
        }

        JsonElement parentEl = json.get("parent");
        if (parentEl != null && parentEl.isJsonPrimitive()) {
            String parent = normalize(parentEl.getAsString());
            if (parent.contains("generated") || parent.contains("handheld")) {
                return ModelKind.ITEM;
            }
            if (parent.contains("cube")
                    || parent.contains("orientable")
                    || parent.contains("cross")
                    || parent.contains("block/")) {
                return ModelKind.BLOCK;
            }
        }

        return ModelKind.UNKNOWN;
    }

    private static int scoreForBlock(Identifier blockId, BlockState state, IndexedModel model) {
        int score = 0;

        String blockNs = normalize(blockId.getNamespace());
        String blockPath = normalize(blockId.getPath());

        String modelNs = normalize(model.namespace());
        String modelPath = normalize(model.modelPath());

        if (model.kind() == ModelKind.BLOCK) {
            score += 280;
        } else if (model.kind() == ModelKind.ITEM) {
            score -= 120;
        }

        if (blockNs.equals(modelNs)) {
            score += 200;
        }

        if (modelPath.equals(blockPath)) {
            score += 1200;
        }

        if (modelPath.equals("block/" + blockPath)) {
            score += 1180;
        }

        if (modelPath.equals("item/" + blockPath)) {
            score += 850;
        }

        if (modelPath.endsWith("/" + blockPath)) {
            score += 1000;
        }

        if (modelPath.contains("/" + blockPath + "/")) {
            score += 900;
        }

        if (modelPath.contains(blockPath)) {
            score += 600;
        }

        Set<String> blockTokens = tokenize(blockPath);
        Set<String> modelTokens = tokenize(modelPath);

        int overlap = 0;
        for (String token : blockTokens) {
            if (modelTokens.contains(token)) {
                overlap++;
            }
        }
        score += overlap * 70;

        String inferredFolder = inferFolderFromPath(blockId.getPath());
        if (inferredFolder != null) {
            String folder = normalize(inferredFolder);

            if (modelPath.contains("/" + folder + "/")) {
                score += 240;
            }

            if (modelPath.startsWith("furniture/block/" + folder + "/")) {
                score += 340;
            }

            if (modelPath.startsWith("furniture/item/" + folder + "/")) {
                score += 260;
            }

            if (modelPath.startsWith("block/" + folder + "/")) {
                score += 260;
            }

            if (modelPath.startsWith("item/" + folder + "/")) {
                score += 180;
            }

            if (modelPath.startsWith("custom/block/" + folder + "/")) {
                score += 280;
            }

            if (modelPath.startsWith("custom/item/" + folder + "/")) {
                score += 180;
            }
        }

        String stateString = state == null ? "" : normalize(state.toString());
        if (stateString.contains("lit=true")) {
            if (modelPath.contains("lit") || modelPath.contains("on")) {
                score += 120;
            }
        } else if (stateString.contains("lit=false")) {
            if (modelPath.contains("unlit") || modelPath.contains("off")) {
                score += 120;
            }
        }

        if (stateString.contains("rotation=")) {
            score += 25;
        }

        if (modelPath.startsWith("generated") || modelPath.startsWith("template")) {
            score -= 100;
        }

        return score;
    }

    private static int scoreForItem(Identifier itemId, IndexedModel model) {
        int score = 0;

        String itemNs = normalize(itemId.getNamespace());
        String itemPath = normalize(itemId.getPath());

        String modelNs = normalize(model.namespace());
        String modelPath = normalize(model.modelPath());

        if (model.kind() == ModelKind.ITEM) {
            score += 300;
        } else if (model.kind() == ModelKind.BLOCK) {
            score += 80;
        }

        if (itemNs.equals(modelNs)) {
            score += 180;
        }

        if (modelPath.equals(itemPath)) {
            score += 1100;
        }

        if (modelPath.equals("item/" + itemPath)) {
            score += 1200;
        }

        if (modelPath.equals("block/" + itemPath)) {
            score += 850;
        }

        if (modelPath.endsWith("/" + itemPath)) {
            score += 1000;
        }

        if (modelPath.contains("/" + itemPath + "/")) {
            score += 900;
        }

        if (modelPath.contains(itemPath)) {
            score += 520;
        }

        Set<String> itemTokens = tokenize(itemPath);
        Set<String> modelTokens = tokenize(modelPath);

        int overlap = 0;
        for (String token : itemTokens) {
            if (modelTokens.contains(token)) {
                overlap++;
            }
        }
        score += overlap * 65;

        String inferredFolder = inferFolderFromPath(itemId.getPath());
        if (inferredFolder != null) {
            String folder = normalize(inferredFolder);

            if (modelPath.contains("/" + folder + "/")) {
                score += 220;
            }

            if (modelPath.startsWith("furniture/item/" + folder + "/")) {
                score += 340;
            }

            if (modelPath.startsWith("item/" + folder + "/")) {
                score += 280;
            }

            if (modelPath.startsWith("custom/item/" + folder + "/")) {
                score += 260;
            }

            if (modelPath.startsWith("furniture/block/" + folder + "/")) {
                score += 150;
            }

            if (modelPath.startsWith("block/" + folder + "/")) {
                score += 120;
            }
        }

        return score;
    }

    private static String inferFolderFromPath(String path) {
        int underscore = path.indexOf('_');
        if (underscore <= 0) {
            return null;
        }

        String first = path.substring(0, underscore).toLowerCase(Locale.ROOT);
        if (first.equals("block") || first.equals("item") || first.equals("custom")) {
            return null;
        }

        return first;
    }

    private static Set<String> tokenize(String in) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String slashPart : normalize(in).split("/")) {
            for (String underscorePart : slashPart.split("_")) {
                String token = underscorePart.trim();
                if (!token.isEmpty()) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    private static String normalize(String in) {
        return in == null ? "" : in.toLowerCase(Locale.ROOT).replace('\\', '/');
    }

    private static String zipIdentity(ZipFile zip) {
        try {
            return zip.getName() + "#" + zip.size();
        } catch (Throwable ignored) {
            return String.valueOf(System.identityHashCode(zip));
        }
    }

    private record ScoredIdentifier(Identifier id, int score) {
    }
}
