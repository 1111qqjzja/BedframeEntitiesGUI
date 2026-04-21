package lol.sylvie.bedframe.geyser.translator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lol.sylvie.bedframe.display.AutoOversizedDisplayRegistry;
import lol.sylvie.bedframe.util.PolymerPackContentIndex;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Either;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.MultiPolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import lol.sylvie.bedframe.util.ConvertedModelRegistry;
import lol.sylvie.bedframe.geyser.TranslationManager;
import lol.sylvie.bedframe.geyser.Translator;
import lol.sylvie.bedframe.geyser.model.JavaGeometryConverter;
import lol.sylvie.bedframe.mixin.BlockResourceCreatorAccessor;
import lol.sylvie.bedframe.mixin.PolymerBlockResourceUtilsAccessor;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.kyori.adventure.key.Key;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.CustomBlockPermutation;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.*;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBoundingBox;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.geyser.util.SoundUtils;
import org.geysermc.pack.bedrock.resource.models.entity.ModelEntity;
import org.geysermc.pack.converter.type.model.ModelStitcher;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.model.ModelTexture;
import team.unnamed.creative.model.ModelTextures;
import team.unnamed.creative.serialize.minecraft.model.ModelSerializer;
import xyz.nucleoid.packettweaker.PacketContext;
import lol.sylvie.bedframe.display.OversizedDisplaySpec;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;
import static lol.sylvie.bedframe.util.PathHelper.createDirectoryOrThrow;

public class BlockTranslator extends Translator {
    private static final Map<String, List<Pair<String, String>>> parentFaceMap = Map.of(
            "block/cube_all", List.of(
                    new Pair<>("all", "*")
            ),
            "block/cross", List.of(
                    new Pair<>("particle", "*"),
                    new Pair<>("cross", "*")
            ),
            "block/cube_bottom_top", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("top", "up"),
                    new Pair<>("bottom", "down"),
                    new Pair<>("side", "north"),
                    new Pair<>("side", "south"),
                    new Pair<>("side", "east"),
                    new Pair<>("side", "west")
            ),
            "block/cube_column", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("end", "up"),
                    new Pair<>("end", "down"),
                    new Pair<>("side", "north"),
                    new Pair<>("side", "south"),
                    new Pair<>("side", "east"),
                    new Pair<>("side", "west")
            ),
            "block/cube_column_horizontal", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("end", "up"),
                    new Pair<>("end", "down"),
                    new Pair<>("side", "north"),
                    new Pair<>("side", "south"),
                    new Pair<>("side", "east"),
                    new Pair<>("side", "west")
            ),
            "block/orientable", List.of(
                    new Pair<>("side", "*"),
                    new Pair<>("front", "north"),
                    new Pair<>("top", "up"),
                    new Pair<>("bottom", "down")
            )
    );

    private static final ArrayList<PolymerBlock> registeredBlocks = new ArrayList<>();
    private final HashMap<Identifier, PolymerTexturedBlock> blocks = new HashMap<>();

    public BlockTranslator() {
        Stream<Identifier> blockIds = Registries.BLOCK.getIds().stream();

        blockIds.forEach(identifier -> {
            Block block = Registries.BLOCK.get(identifier);
            if (block instanceof PolymerTexturedBlock texturedBlock) {
                blocks.put(identifier, texturedBlock);
            }
        });
    }

    private void forEachBlock(BiConsumer<Identifier, PolymerTexturedBlock> function) {
        for (Map.Entry<Identifier, PolymerTexturedBlock> entry : blocks.entrySet()) {
            try {
                function.accept(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                LOGGER.error("Couldn't load block {}", entry.getKey(), e);
            }
        }
    }

    private void populateProperties(CustomBlockData.Builder builder, Collection<Property<?>> properties) {
        for (Property<?> property : properties) {
            switch (property) {
                case IntProperty intProperty ->
                        builder.intProperty(property.getName(), List.copyOf(intProperty.getValues()));
                case BooleanProperty ignored ->
                        builder.booleanProperty(property.getName());
                case EnumProperty<?> enumProperty ->
                        builder.stringProperty(enumProperty.getName(), enumProperty.getValues().stream().map(Enum::name).map(String::toLowerCase).toList());
                default ->
                        LOGGER.error("Unknown property type: {}", property.getClass().getName());
            }
        }
    }

    private BoxComponent voxelShapeToBoxComponent(VoxelShape shape) {
        if (shape.isEmpty())
            return BoxComponent.emptyBox();

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        for (Box boundingBox : shape.getBoundingBoxes()) {
            double offsetX = boundingBox.getLengthX() * 0.5;
            double offsetY = boundingBox.getLengthY() * 0.5;
            double offsetZ = boundingBox.getLengthZ() * 0.5;

            Vec3d center = boundingBox.getCenter();

            minX = Math.min(minX, (float) (center.getX() - offsetX));
            minY = Math.min(minY, (float) (center.getY() - offsetY));
            minZ = Math.min(minZ, (float) (center.getZ() - offsetZ));

            maxX = Math.max(maxX, (float) (center.getX() + offsetX));
            maxY = Math.max(maxY, (float) (center.getY() + offsetY));
            maxZ = Math.max(maxZ, (float) (center.getZ() + offsetZ));
        }

        minX = MathUtils.clamp(minX, 0, 1);
        minY = MathUtils.clamp(minY, 0, 1);
        minZ = MathUtils.clamp(minZ, 0, 1);

        maxX = MathUtils.clamp(maxX, 0, 1);
        maxY = MathUtils.clamp(maxY, 0, 1);
        maxZ = MathUtils.clamp(maxZ, 0, 1);

        return new BoxComponent(
                16 * (1 - maxX) - 8,
                16 * minY,
                16 * minZ - 8,
                16 * (maxX - minX),
                16 * (maxY - minY),
                16 * (maxZ - minZ)
        );
    }

    private List<Identifier> expandConvertedModelString(Identifier blockId, String modelString) {
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

    private ResolvedBlockModel resolveModelFromConvertedYaml(Identifier blockId, BlockState state) {
        List<String> rawCandidates = ConvertedModelRegistry.getModelCandidates(blockId);
        if (rawCandidates.isEmpty()) {
            return null;
        }

        List<Identifier> expanded = new ArrayList<>();
        for (String raw : rawCandidates) {
            expanded.addAll(expandConvertedModelString(blockId, raw));
        }

        for (Identifier candidate : expanded) {
            ResolvedBlockModel resolved = resolveModelWithCandidates(candidate, 0, 0);
            if (resolved != null) {
                LOGGER.warn("Resolved converted-yml model for {} using {} (from raw Pack.model)", state, candidate);
                return resolved;
            }
        }

        LOGGER.warn("Converted-yml model fallback failed for {} with raw candidates {} and expanded candidates {}",
                state, rawCandidates, expanded);
        return null;
    }

    private ResolvedBlockModel resolveFallbackModel(Identifier blockId, BlockState state) {
        ResolvedBlockModel resolved;

        resolved = resolveModelFromConvertedYaml(blockId, state);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveModelFromPolymerPackFallback(blockId, state);
        if (resolved != null) {
            return resolved;
        }

        resolved = resolveModelFromEnumeratedPolymerModels(blockId, state);
        if (resolved != null) {
            return resolved;
        }

        return null;
    }

    private record ResolvedBlockModel(Identifier modelId, Model model, int x, int y) {}

    private record CandidateModelId(Identifier modelId, int x, int y, String source) {}

    private Model resolveModel(Identifier identifier) {
        ResolvedBlockModel resolved = resolveModelWithCandidates(identifier, 0, 0);
        return resolved == null ? null : resolved.model();
    }

    private ResolvedBlockModel resolveModelWithCandidates(Identifier identifier, int x, int y) {
        try {
            String modelPath = identifier.getPath();
            LinkedHashSet<String> candidates = new LinkedHashSet<>();

            candidates.add("models/" + modelPath + ".json");

            if (!modelPath.startsWith("block/") && !modelPath.startsWith("item/")) {
                candidates.add("models/block/" + modelPath + ".json");
                candidates.add("models/item/" + modelPath + ".json");
            }

            if (modelPath.startsWith("block/")) {
                candidates.add("models/" + modelPath.substring("block/".length()) + ".json");
            } else if (modelPath.startsWith("item/")) {
                candidates.add("models/" + modelPath.substring("item/".length()) + ".json");
            }

            for (String candidate : candidates) {
                JsonObject modelJson = ResourceHelper.tryReadJsonResource(identifier.getNamespace(), candidate);
                if (modelJson == null) {
                    continue;
                }

                Model model = ModelSerializer.INSTANCE.deserializeFromJson(modelJson, Key.key(identifier.toString()));
                return new ResolvedBlockModel(identifier, model, x, y);
            }

            LOGGER.warn("Couldn't resolve model {} from candidates {}", identifier, candidates);
            return null;
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't resolve model {}", identifier, e);
            return null;
        }
    }

    private ResolvedBlockModel resolveModelFromPolymerPackFallback(Identifier blockId, BlockState state) {
        List<CandidateModelId> modelIdCandidates = buildPolymerPackModelCandidates(blockId, state);

        int attempts = 0;
        for (CandidateModelId candidate : modelIdCandidates) {
            attempts++;
            ResolvedBlockModel resolved = resolveModelWithCandidates(candidate.modelId(), candidate.x(), candidate.y());
            if (resolved != null) {
                LOGGER.warn("Resolved polymer-zip indexed model for {} using {} ({}) after {} attempts",
                        state, candidate.modelId(), candidate.source(), attempts);
                return resolved;
            }

            if (attempts >= 16) {
                break;
            }
        }

        LOGGER.warn("Polymer zip indexed model fallback failed for {} after {} attempts", state, attempts);
        return null;
    }

    private List<CandidateModelId> buildPolymerPackModelCandidates(Identifier blockId, BlockState state) {
        List<Identifier> indexed = PolymerPackContentIndex.findBestBlockModelCandidates(blockId, state);
        if (indexed.isEmpty()) {
            return List.of();
        }

        List<CandidateModelId> out = new ArrayList<>();
        for (Identifier id : indexed) {
            out.add(new CandidateModelId(id, 0, 0, "polymer-zip-index-direct"));
        }
        return out;
    }

    private record ScoredModelCandidate(CandidateModelId candidate, int score) {}

    private final Map<String, List<CandidateModelId>> polymerModelIndexCache = new HashMap<>();

    private ResolvedBlockModel resolveModelFromEnumeratedPolymerModels(Identifier blockId, BlockState state) {
        List<CandidateModelId> rankedCandidates = enumerateAndRankPolymerPackModels(blockId, state);

        if (rankedCandidates.isEmpty()) {
            LOGGER.warn("No polymer-zip indexed model candidates found for {}", state);
            debugClosestRawResourcePaths(blockId, state);
            return null;
        }

        int attempts = 0;
        for (CandidateModelId candidate : rankedCandidates) {
            attempts++;
            ResolvedBlockModel resolved = resolveModelWithCandidates(candidate.modelId(), candidate.x(), candidate.y());
            if (resolved != null) {
                LOGGER.warn("Resolved enumerated polymer-zip indexed fallback model for {} using {} ({}) after {} attempts",
                        state, candidate.modelId(), candidate.source(), attempts);
                return resolved;
            }

            if (attempts >= 16) {
                break;
            }
        }

        LOGGER.warn("Enumerated polymer-zip indexed model fallback failed for {} after {} attempts", state, attempts);
        debugClosestRawResourcePaths(blockId, state);
        return null;
    }

    private List<CandidateModelId> enumerateAndRankPolymerPackModels(Identifier blockId, BlockState state) {
        String cacheKey = "zip-enum|" + blockId;
        List<CandidateModelId> cached = polymerModelIndexCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Identifier> indexed = PolymerPackContentIndex.findBestBlockModelCandidates(blockId, state);
        if (indexed.isEmpty()) {
            polymerModelIndexCache.put(cacheKey, List.of());
            return List.of();
        }

        List<CandidateModelId> result = new ArrayList<>();
        LinkedHashSet<String> dedupe = new LinkedHashSet<>();

        for (Identifier id : indexed) {
            if (dedupe.add(id.toString())) {
                result.add(new CandidateModelId(id, 0, 0, "polymer-zip-index-enumerated"));
            }
        }

        List<String> preview = result.stream()
                .limit(12)
                .map(c -> c.modelId().toString())
                .toList();

        LOGGER.warn("Enumerated {} ranked polymer-zip indexed model candidates for {}. Top matches: {}",
                result.size(), state, preview);

        polymerModelIndexCache.put(cacheKey, result);
        return result;
    }

    private int scoreModelPathForBlock(String blockNamespace, String blockPath, String modelNamespace, String modelPath, BlockState state) {
        int score = 0;

        String normalizedBlockNamespace = normalizeName(blockNamespace);
        String normalizedModelNamespace = normalizeName(modelNamespace);
        String normalizedBlock = normalizeName(blockPath);
        String normalizedModel = normalizeName(modelPath);

        if (normalizedModelNamespace.equals(normalizedBlockNamespace)) {
            score += 180;
        }

        if (normalizedModel.equals(normalizedBlock)) {
            score += 1000;
        }

        if (normalizedModel.endsWith("/" + normalizedBlock)) {
            score += 900;
        }

        if (normalizedModel.contains("/" + normalizedBlock + "/")) {
            score += 850;
        }

        if (normalizedModel.contains(normalizedBlock)) {
            score += 500;
        }

        Set<String> blockTokens = tokenize(normalizedBlock);
        Set<String> modelTokens = tokenize(normalizedModel);

        int overlap = 0;
        for (String token : blockTokens) {
            if (modelTokens.contains(token)) {
                overlap++;
            }
        }
        score += overlap * 55;

        String inferredFolder = inferFolderFromPath(blockPath);
        if (inferredFolder != null) {
            String normalizedFolder = normalizeName(inferredFolder);

            if (normalizedModel.contains("/" + normalizedFolder + "/")) {
                score += 220;
            }

            if (normalizedModel.startsWith("furniture/item/" + normalizedFolder + "/")) {
                score += 320;
            }

            if (normalizedModel.startsWith("furniture/block/" + normalizedFolder + "/")) {
                score += 300;
            }

            if (normalizedModel.startsWith("item/" + normalizedFolder + "/")) {
                score += 240;
            }

            if (normalizedModel.startsWith("block/" + normalizedFolder + "/")) {
                score += 220;
            }

            if (normalizedModel.startsWith("custom/block/" + normalizedFolder + "/")) {
                score += 240;
            }

            if (normalizedModel.startsWith("custom/item/" + normalizedFolder + "/")) {
                score += 220;
            }
        }

        String stateString = state.toString().toLowerCase(Locale.ROOT);

        if (stateString.contains("lit=true")) {
            if (normalizedModel.contains("lit") || normalizedModel.contains("on")) {
                score += 120;
            }
        } else if (stateString.contains("lit=false")) {
            if (normalizedModel.contains("unlit") || normalizedModel.contains("off")) {
                score += 120;
            }
        }

        if (stateString.contains("rotation=")) {
            score += 25;
        }

        if (normalizedModel.startsWith("generated") || normalizedModel.startsWith("template")) {
            score -= 100;
        }

        return score;

    }

    private void debugClosestRawResourcePaths(Identifier blockId, BlockState state) {
        Set<String> allPaths = ResourceHelper.listPackResourcePaths();
        LOGGER.warn("debugClosestRawResourcePaths: resource path source returned {} paths for {}", allPaths.size(), state);

        if (allPaths.isEmpty()) {
            LOGGER.warn("debugClosestRawResourcePaths: pack resource path list is empty for {}", state);
            return;
        }

        String blockPath = normalizeName(blockId.getPath());
        Set<String> tokens = tokenize(blockPath);

        List<String> matched = new ArrayList<>();
        for (String resourcePath : allPaths) {
            String normalized = normalizeName(resourcePath);
            int overlap = 0;
            for (String token : tokens) {
                if (normalized.contains(token)) {
                    overlap++;
                }
            }

            if (overlap >= Math.max(1, tokens.size() / 2)) {
                matched.add(resourcePath);
            }
        }

        if (matched.isEmpty()) {
            LOGGER.warn("debugClosestRawResourcePaths: no raw zip paths loosely matched {}", state);
        } else {
            matched.sort(String::compareTo);
            LOGGER.warn("debugClosestRawResourcePaths: loosely matched raw zip paths for {} => {}",
                    state, matched.stream().limit(30).toList());
        }
    }

    private String normalizeName(String in) {
        return in.toLowerCase(Locale.ROOT).replace('\\', '/');
    }

    private Set<String> tokenize(String in) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String slashPart : in.split("/")) {
            for (String underscorePart : slashPart.split("_")) {
                String token = underscorePart.trim();
                if (!token.isEmpty()) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    private String inferFolderFromPath(String path) {
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

    private ResolvedBlockModel resolveModelFromBlockStateFallback(Identifier blockId, BlockState state) {
        JsonObject blockstateJson = ResourceHelper.tryReadJsonResource(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json");
        if (blockstateJson == null) {
            return null;
        }

        if (blockstateJson.has("variants") && blockstateJson.get("variants").isJsonObject()) {
            JsonObject variants = blockstateJson.getAsJsonObject("variants");

            for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
                String variantKey = entry.getKey();
                if (!matchesVariantKey(state, variantKey)) {
                    continue;
                }

                VariantSelection selected = extractVariantSelection(entry.getValue());
                if (selected == null || selected.modelId() == null) {
                    continue;
                }

                ResolvedBlockModel resolved = resolveModelWithCandidates(selected.modelId(), selected.x(), selected.y());
                if (resolved != null) {
                    return resolved;
                }
            }

            if (variants.has("")) {
                VariantSelection selected = extractVariantSelection(variants.get(""));
                if (selected != null && selected.modelId() != null) {
                    ResolvedBlockModel resolved = resolveModelWithCandidates(selected.modelId(), selected.x(), selected.y());
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }

            if (variants.has("normal")) {
                VariantSelection selected = extractVariantSelection(variants.get("normal"));
                if (selected != null && selected.modelId() != null) {
                    ResolvedBlockModel resolved = resolveModelWithCandidates(selected.modelId(), selected.x(), selected.y());
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
        }

        if (blockstateJson.has("multipart") && blockstateJson.get("multipart").isJsonArray()) {
            JsonArray multipart = blockstateJson.getAsJsonArray("multipart");

            for (JsonElement partElement : multipart) {
                if (!partElement.isJsonObject()) {
                    continue;
                }

                JsonObject part = partElement.getAsJsonObject();
                JsonElement when = part.get("when");
                if (!matchesMultipartWhen(state, when)) {
                    continue;
                }

                VariantSelection selected = extractVariantSelection(part.get("apply"));
                if (selected == null || selected.modelId() == null) {
                    continue;
                }

                ResolvedBlockModel resolved = resolveModelWithCandidates(selected.modelId(), selected.x(), selected.y());
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        return null;
    }

    private record VariantSelection(Identifier modelId, int x, int y) {}

    private VariantSelection extractVariantSelection(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                VariantSelection selected = extractVariantSelection(child);
                if (selected != null) {
                    return selected;
                }
            }
            return null;
        }

        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        if (!object.has("model")) {
            return null;
        }

        String modelString = object.get("model").getAsString();
        Identifier modelId = Identifier.of(modelString);

        int x = object.has("x") ? object.get("x").getAsInt() : 0;
        int y = object.has("y") ? object.get("y").getAsInt() : 0;

        return new VariantSelection(modelId, x, y);
    }

    private boolean matchesVariantKey(BlockState state, String variantKey) {
        if (variantKey == null || variantKey.isBlank() || variantKey.equals("normal")) {
            return true;
        }

        String[] clauses = variantKey.split(",");
        for (String clause : clauses) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int split = trimmed.indexOf('=');
            if (split < 0) {
                return false;
            }

            String propName = trimmed.substring(0, split);
            String expectedValue = trimmed.substring(split + 1);

            Property<?> property = state.getProperties()
                    .stream()
                    .filter(p -> p.getName().equals(propName))
                    .findFirst()
                    .orElse(null);

            if (property == null) {
                return false;
            }

            String actualValue = stringifyPropertyValue(state, property);
            if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesMultipartWhen(BlockState state, JsonElement whenElement) {
        if (whenElement == null || whenElement.isJsonNull()) {
            return true;
        }

        if (whenElement.isJsonArray()) {
            for (JsonElement child : whenElement.getAsJsonArray()) {
                if (matchesMultipartWhen(state, child)) {
                    return true;
                }
            }
            return false;
        }

        if (!whenElement.isJsonObject()) {
            return false;
        }

        JsonObject object = whenElement.getAsJsonObject();

        if (object.has("OR")) {
            JsonArray orArray = object.getAsJsonArray("OR");
            for (JsonElement child : orArray) {
                if (matchesMultipartWhen(state, child)) {
                    return true;
                }
            }
            return false;
        }

        if (object.has("AND")) {
            JsonArray andArray = object.getAsJsonArray("AND");
            for (JsonElement child : andArray) {
                if (!matchesMultipartWhen(state, child)) {
                    return false;
                }
            }
            return true;
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String propName = entry.getKey();
            JsonElement valueElement = entry.getValue();

            Property<?> property = state.getProperties()
                    .stream()
                    .filter(p -> p.getName().equals(propName))
                    .findFirst()
                    .orElse(null);

            if (property == null) {
                return false;
            }

            String actualValue = stringifyPropertyValue(state, property);

            if (valueElement.isJsonPrimitive()) {
                JsonPrimitive primitive = valueElement.getAsJsonPrimitive();
                String raw = primitive.getAsString();

                String[] options = raw.split("\\|");
                boolean matched = false;
                for (String option : options) {
                    if (actualValue.equals(option)) {
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String stringifyPropertyValue(BlockState state, Property property) {
        Comparable value = state.get(property);
        return property.name(value);
    }

    public static boolean isRegisteredBlock(PolymerBlock block) {
        return registeredBlocks.contains(block);
    }

    public void handle(GeyserDefineCustomBlocksEvent event, Path packRoot) {
        Path textureDir = createDirectoryOrThrow(packRoot.resolve("textures"));
        createDirectoryOrThrow(textureDir.resolve("blocks"));

        Path modelsDir = createDirectoryOrThrow(packRoot.resolve("models"));
        Path blockModelsDir = createDirectoryOrThrow(modelsDir.resolve("blocks"));

        JsonObject terrainTextureObject = new JsonObject();
        terrainTextureObject.addProperty("resource_pack_name", "Bedframe");
        terrainTextureObject.addProperty("texture_name", "atlas.terrain");

        JsonObject blocksJson = new JsonObject();
        blocksJson.addProperty("format_version", "1.21.40");

        JsonObject soundsJson = new JsonObject();
        JsonObject blockSoundsObject = new JsonObject();
        JsonObject interactiveSoundsObject = new JsonObject();

        JsonObject interactiveSoundsWrapper = new JsonObject();
        JsonObject textureDataObject = new JsonObject();

        forEachBlock((identifier, block) -> {
            Block realBlock = Registries.BLOCK.get(identifier);

            OversizedDisplaySpec oversizedSpec = AutoOversizedDisplayRegistry.get(identifier);
            if (oversizedSpec != null) {
                LOGGER.warn("Block {} is oversized; registering anchor-only block", identifier);
                registerAnchorOnlyBlock(event, identifier, realBlock);
                return;
            }
            addTranslationKey("block." + identifier.getNamespace() + "." + identifier.getPath(), realBlock.getTranslationKey());

            NonVanillaCustomBlockData.Builder builder = NonVanillaCustomBlockData.builder()
                    .name(identifier.getPath())
                    .namespace(identifier.getNamespace())
                    .creativeGroup("itemGroup." + identifier.getNamespace() + ".blocks")
                    .creativeCategory(CreativeCategory.CONSTRUCTION)
                    .includedInCreativeInventory(true);

            populateProperties(builder, realBlock.getStateManager().getProperties());

            List<CustomBlockPermutation> permutations = new ArrayList<>();
            for (BlockState state : realBlock.getStateManager().getStates()) {
                CustomBlockComponents.Builder stateComponentBuilder = CustomBlockComponents.builder();

                float hardness = state.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                stateComponentBuilder.destructibleByMining(hardness);

                TranslationManager.INCLUDE_OPTIONAL_TEXTURES_HACK = true;
                BlockState polymerBlockState = block.getPolymerBlockState(state, PacketContext.get());
                BlockResourceCreator creator = PolymerBlockResourceUtilsAccessor.getCREATOR();
                Either<PolymerBlockModel[], MultiPolymerBlockModel> polymerBlockModels =
                        ((BlockResourceCreatorAccessor) (Object) creator).getModels().get(polymerBlockState);
                TranslationManager.INCLUDE_OPTIONAL_TEXTURES_HACK = false;

                ResolvedBlockModel resolvedBlockModel = null;

                if (polymerBlockModels == null) {
                    LOGGER.warn("Models are null for blockstate {}, trying converted-yml / Polymer fallback chain", state);
                    resolvedBlockModel = resolveFallbackModel(identifier, state);

                    if (resolvedBlockModel == null) {
                        LOGGER.warn("Fallback also failed for blockstate {}", state);
                        continue;
                    }
                } else {
                    PolymerBlockModel[] listModels;
                    if (polymerBlockModels.left().isPresent()) {
                        listModels = polymerBlockModels.left().orElseThrow();
                    } else {
                        listModels = polymerBlockModels.right().orElseThrow().models().getFirst();
                    }

                    if (listModels.length == 0) {
                        LOGGER.warn("Models are empty for blockstate {}, trying converted-yml / Polymer fallback chain", state);
                        resolvedBlockModel = resolveFallbackModel(identifier, state);

                        if (resolvedBlockModel == null) {
                            LOGGER.warn("Fallback also failed for blockstate {}", state);
                            continue;
                        }
                    } else {
                        PolymerBlockModel modelEntry = listModels[0];
                        resolvedBlockModel = resolveModelWithCandidates(modelEntry.model(), modelEntry.x(), modelEntry.y());

                        if (resolvedBlockModel == null) {
                            LOGGER.warn("Couldn't load polymer model for blockstate {}, trying converted-yml / Polymer fallback chain", state);
                            resolvedBlockModel = resolveFallbackModel(identifier, state);

                            if (resolvedBlockModel == null) {
                                LOGGER.warn("Fallback also failed for blockstate {}", state);
                                continue;
                            }
                        }
                    }
                }

                TransformationComponent rotationComponent = new TransformationComponent(
                        (360 - resolvedBlockModel.x()) % 360,
                        (360 - resolvedBlockModel.y()) % 360,
                        0
                );
                stateComponentBuilder.transformation(rotationComponent);

                String renderMethod = state.isOpaque() ? "opaque" : "blend";
                Identifier blockModelId = resolvedBlockModel.modelId();
                Model blockModel = resolvedBlockModel.model();

                HashMap<String, ModelTexture> materials = new HashMap<>();
                Key modelParentKey = blockModel.parent();
                if (modelParentKey != null && parentFaceMap.containsKey(modelParentKey.value())) {
                    boolean cross = modelParentKey.toString().equals("minecraft:block/cross");
                    String geometryIdentifier = cross ?  "minecraft:geometry.cross" : "minecraft:geometry.full_block";
                    if (cross) renderMethod = "alpha_test_single_sided";

                    GeometryComponent geometryComponent = GeometryComponent.builder().identifier(geometryIdentifier).build();
                    stateComponentBuilder.geometry(geometryComponent);

                    ModelTextures textures = blockModel.textures();
                    Map<String, ModelTexture> textureMap = textures.variables();
                    List<Pair<String, String>> faceMap = parentFaceMap.get(modelParentKey.value());

                    for (Pair<String, String> face : faceMap) {
                        String javaFaceName = face.getLeft();
                        String bedrockFaceName = face.getRight();
                        if (!textureMap.containsKey(javaFaceName)) continue;
                        materials.put(bedrockFaceName, textureMap.get(javaFaceName));
                    }
                } else {
                    ModelStitcher.Provider provider = key -> {
                        try {
                            Identifier parentId = Identifier.of(key.asString());
                            return resolveModel(parentId);
                        } catch (RuntimeException e) {
                            LOGGER.warn("Couldn't resolve stitched parent model {}", key.asString(), e);
                            return null;
                        }
                    };
                    blockModel = new ModelStitcher(provider, blockModel).stitch();

                    Pair<String, ModelEntity> nameAndModel = JavaGeometryConverter.convert(blockModel);
                    if (nameAndModel == null) {
                        LOGGER.error("Couldn't convert model for blockstate {}", state);
                        continue;
                    }
                    String geometryId = nameAndModel.getLeft();
                    writeJsonToFile(nameAndModel.getRight(), blockModelsDir.resolve(geometryId + ".geo.json").toFile());

                    for (Map.Entry<String, ModelTexture> entry : blockModel.textures().variables().entrySet()) {
                        String key = entry.getKey();
                        ModelTexture texture = entry.getValue();
                        materials.put(key, texture);
                    }

                    GeometryComponent geometryComponent = GeometryComponent.builder().identifier(geometryId).build();
                    stateComponentBuilder.geometry(geometryComponent);
                }

                if (materials.isEmpty()) {
                    LOGGER.error("Couldn't generate materials for blockstate {}", state);
                    continue;
                }

                ModelTextures textures = blockModel.textures();
                if (!materials.containsKey("*")) {
                    ModelTexture texture = textures.particle() == null ? materials.values().iterator().next() : textures.particle();
                    materials.put("*", texture);
                }

                for (Map.Entry<String, ModelTexture> entry : materials.entrySet()) {
                    ModelTexture texture = entry.getValue();

                    while (texture.key() == null) {
                        String reference = texture.reference();
                        if (reference == null || !materials.containsKey(reference)) {
                            break;
                        }

                        texture = materials.get(reference);
                    }

                    if (texture.key() == null) {
                        LOGGER.warn("Texture for block {} on side {} is missing", identifier, entry.getKey());
                        continue;
                    }

                    String textureName = texture.key().asString();
                    if (!textureDataObject.has(textureName)) {
                        Identifier textureIdentifier = Identifier.of(textureName);

                        String texturePath = "textures/" + textureIdentifier.getPath();
                        String bedrockPath = ResourceHelper.javaToBedrockTexture(texturePath);

                        JsonObject thisTexture = new JsonObject();
                        thisTexture.addProperty("textures", bedrockPath);
                        textureDataObject.add(textureName, thisTexture);

                        ResourceHelper.copyResource(textureIdentifier.getNamespace(), texturePath + ".png", packRoot.resolve(bedrockPath + ".png"));
                    }

                    stateComponentBuilder.materialInstance(entry.getKey(), MaterialInstance.builder()
                            .renderMethod(renderMethod)
                            .texture(textureName)
                            .faceDimming(true)
                            .ambientOcclusion(blockModel.ambientOcclusion())
                            .build());
                }

                VoxelShape collisionBox = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                stateComponentBuilder.collisionBox(voxelShapeToBoxComponent(collisionBox));

                VoxelShape outlineBox = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                stateComponentBuilder.selectionBox(voxelShapeToBoxComponent(outlineBox));

                stateComponentBuilder.lightEmission(state.getLuminance());

                CustomBlockComponents stateComponents = stateComponentBuilder.build();
                if (state.getProperties().isEmpty()) {
                    builder.components(stateComponents);
                    continue;
                }

                List<String> conditions = new ArrayList<>();
                for (Property<?> property : state.getProperties()) {
                    String propertyValue = state.get(property).toString();
                    if (property instanceof EnumProperty<?>) {
                        propertyValue = "'" + propertyValue.toLowerCase() + "'";
                    }

                    conditions.add("q.block_property('%name%') == %value%"
                            .replace("%name%", property.getName())
                            .replace("%value%", propertyValue));
                }

                String stateCondition = String.join(" && ", conditions);
                permutations.add(new CustomBlockPermutation(stateComponents, stateCondition));
            }
            builder.permutations(permutations);

            String blockAsString = identifier.toString();
            JsonObject thisBlockObject = new JsonObject();
            thisBlockObject.addProperty("sound", blockAsString);
            blocksJson.add(blockAsString, thisBlockObject);

            BlockSoundGroup soundGroup = realBlock.getDefaultState().getSoundGroup();
            JsonObject baseSoundObject = new JsonObject();
            baseSoundObject.addProperty("pitch", soundGroup.getPitch());
            baseSoundObject.addProperty("volume", soundGroup.getVolume());

            JsonObject soundEventsObject = new JsonObject();
            soundEventsObject.addProperty("break", SoundUtils.translatePlaySound(soundGroup.getBreakSound().id().toString()));
            soundEventsObject.addProperty("hit", SoundUtils.translatePlaySound(soundGroup.getHitSound().id().toString()));
            soundEventsObject.addProperty("place", SoundUtils.translatePlaySound(soundGroup.getPlaceSound().id().toString()));
            baseSoundObject.add("events", soundEventsObject);

            blockSoundsObject.add(blockAsString, baseSoundObject);
            JsonObject interactiveSoundObject = new JsonObject();
            interactiveSoundObject.addProperty("pitch", soundGroup.getPitch());
            interactiveSoundObject.addProperty("volume", soundGroup.getVolume() * .4);

            JsonObject interactiveEventsObject = new JsonObject();
            interactiveEventsObject.addProperty("fall", SoundUtils.translatePlaySound(soundGroup.getFallSound().id().toString()));
            interactiveEventsObject.addProperty("jump", SoundUtils.translatePlaySound(soundGroup.getStepSound().id().toString()));
            interactiveEventsObject.addProperty("step", SoundUtils.translatePlaySound(soundGroup.getStepSound().id().toString()));
            interactiveEventsObject.addProperty("land", SoundUtils.translatePlaySound(soundGroup.getFallSound().id().toString()));
            interactiveSoundObject.add("events", interactiveEventsObject);
            interactiveSoundsObject.add(blockAsString, interactiveSoundObject);

            NonVanillaCustomBlockData data = builder.build();
            event.register(data);
            registeredBlocks.add(block);

            for (BlockState state : realBlock.getStateManager().getStates()) {
                CustomBlockState.Builder stateBuilder = data.blockStateBuilder();

                for (Property<?> property : state.getProperties()) {
                    switch (property) {
                        case IntProperty intProperty ->
                                stateBuilder.intProperty(property.getName(), state.get(intProperty));
                        case BooleanProperty booleanProperty ->
                                stateBuilder.booleanProperty(property.getName(), state.get(booleanProperty));
                        case EnumProperty<?> enumProperty ->
                                stateBuilder.stringProperty(enumProperty.getName(), state.get(enumProperty).toString().toLowerCase());
                        default ->
                                throw new IllegalArgumentException("Unknown property type: " + property.getClass().getName());
                    }
                }

                CustomBlockState customBlockState = stateBuilder.build();
                JavaBlockState.Builder javaBlockState = JavaBlockState.builder();
                javaBlockState.blockHardness(state.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));

                VoxelShape shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                if (shape.isEmpty()) {
                    javaBlockState.collision(new JavaBoundingBox[0]);
                } else {
                    Box box = shape.getBoundingBox();
                    javaBlockState.collision(new JavaBoundingBox[]{
                        new JavaBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                    });
                }

                javaBlockState.javaId(Block.getRawIdFromState(state));
                javaBlockState.identifier(BlockArgumentParser.stringifyBlockState(state));
                javaBlockState.waterlogged(state.get(Properties.WATERLOGGED, false));
                if (realBlock.asItem() != null) javaBlockState.pickItem(Registries.ITEM.getId(realBlock.asItem()).toString());
                javaBlockState.canBreakWithHand(state.isToolRequired());

                PistonBehavior pistonBehavior = state.getPistonBehavior();
                javaBlockState.pistonBehavior(pistonBehavior == PistonBehavior.IGNORE ? "NORMAL" : pistonBehavior.name());

                event.registerOverride(javaBlockState.build(), customBlockState);
            }
        });

        terrainTextureObject.add("texture_data", textureDataObject);
        soundsJson.add("block_sounds", blockSoundsObject);
        interactiveSoundsWrapper.add("block_sounds", interactiveSoundsObject);
        soundsJson.add("interactive_sounds", interactiveSoundsWrapper);
        writeJsonToFile(terrainTextureObject, textureDir.resolve("terrain_texture.json").toFile());
        writeJsonToFile(blocksJson, packRoot.resolve("blocks.json").toFile());
        writeJsonToFile(soundsJson, packRoot.resolve("sounds.json").toFile());
        markResourcesProvided();
    }

    private void registerAnchorOnlyBlock(GeyserDefineCustomBlocksEvent event, Identifier identifier, Block realBlock) {
        NonVanillaCustomBlockData.Builder builder = NonVanillaCustomBlockData.builder()
                .name(identifier.getPath())
                .namespace(identifier.getNamespace())
                .creativeGroup("itemGroup." + identifier.getNamespace() + ".blocks")
                .creativeCategory(CreativeCategory.CONSTRUCTION)
                .includedInCreativeInventory(true);

        populateProperties(builder, realBlock.getStateManager().getProperties());

        NonVanillaCustomBlockData data = builder.build();
        event.register(data);

        for (BlockState state : realBlock.getStateManager().getStates()) {
            CustomBlockState customBlockState = data.blockStateBuilder().build();

            JavaBlockState.Builder javaBlockState = JavaBlockState.builder();
            javaBlockState.blockHardness(state.getHardness(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));

            VoxelShape shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            if (shape.isEmpty()) {
                javaBlockState.collision(new JavaBoundingBox[0]);
            } else {
                Box box = shape.getBoundingBox();
                javaBlockState.collision(new JavaBoundingBox[] {
                        new JavaBoundingBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
                });
            }

            javaBlockState.javaId(Block.getRawIdFromState(state));
            javaBlockState.identifier(BlockArgumentParser.stringifyBlockState(state));
            javaBlockState.waterlogged(state.get(Properties.WATERLOGGED, false));
            if (realBlock.asItem() != null) {
                javaBlockState.pickItem(Registries.ITEM.getId(realBlock.asItem()).toString());
            }
            javaBlockState.canBreakWithHand(state.isToolRequired());

            PistonBehavior pistonBehavior = state.getPistonBehavior();
            javaBlockState.pistonBehavior(pistonBehavior == PistonBehavior.IGNORE ? "NORMAL" : pistonBehavior.name());

            event.registerOverride(javaBlockState.build(), customBlockState);
        }
    }

    @Override
    public void register(EventBus<EventRegistrar> eventBus, Path packRoot) {
        eventBus.subscribe(this, GeyserDefineCustomBlocksEvent.class, event -> handle(event, packRoot));
    }
}
