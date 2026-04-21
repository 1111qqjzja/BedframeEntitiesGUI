package lol.sylvie.bedframe.display;

import com.google.gson.JsonObject;
import lol.sylvie.bedframe.geyser.model.JavaGeometryConverter;
import lol.sylvie.bedframe.util.ConvertedModelRegistry;
import lol.sylvie.bedframe.util.PolymerJavaModelResolver;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.geysermc.pack.bedrock.resource.models.entity.ModelEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class AutoOversizedBootstrap {
    private AutoOversizedBootstrap() {
    }

    public static void load(Path generatedRoot, List<Identifier> blockIds) {
        AutoOversizedDisplayRegistry.clear();

        try {
            Files.createDirectories(generatedRoot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create generated geometry root " + generatedRoot, e);
        }

        int registered = 0;

        for (Identifier blockId : blockIds) {
            List<String> rawModels = ConvertedModelRegistry.getModelCandidates(blockId);
            if (rawModels.isEmpty()) {
                continue;
            }

            PolymerJavaModelResolver.ResolvedJavaModel resolved =
                    PolymerJavaModelResolver.resolveBestJavaModel(blockId, rawModels);

            if (resolved == null || resolved.model() == null) {
                LOGGER.warn("No Java model could be resolved for oversized bootstrap block {}", blockId);
                continue;
            }

            Pair<String, ModelEntity> converted;
            try {
                converted = JavaGeometryConverter.convert(resolved.model());
            } catch (Exception e) {
                LOGGER.warn("Failed to convert Java model to Bedrock geometry for {}", blockId, e);
                continue;
            }

            if (converted == null || converted.getRight() == null) {
                LOGGER.warn("JavaGeometryConverter returned null for {}", blockId);
                continue;
            }

            JsonObject geometryRoot;
            try {
                geometryRoot = GSON.toJsonTree(converted.getRight()).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.warn("Failed to serialize converted Bedrock geometry for {}", blockId, e);
                continue;
            }

            GeometryBoundsAnalyzer.Bounds bounds;
            try {
                bounds = GeometryBoundsAnalyzer.analyze(geometryRoot);
            } catch (Exception e) {
                LOGGER.warn("Failed to analyze converted geometry bounds for {}", blockId, e);
                continue;
            }

            if (!bounds.exceedsOversizedLimits()) {
                LOGGER.warn("Converted geometry for {} is not oversized: bounds={}", blockId, bounds);
                continue;
            }

            String geometryIdentifier = converted.getLeft();
            Path geoPath = writeGeneratedGeometry(generatedRoot, blockId, geometryRoot);

            String defaultTexturePath = "textures/entity/" + blockId.getNamespace() + "/" + blockId.getPath();

            AutoOversizedDisplayRegistry.registerFromGeometry(
                    blockId,
                    geometryIdentifier,
                    geoPath,
                    defaultTexturePath
            );

            if (AutoOversizedDisplayRegistry.isOversizedDisplay(blockId)) {
                registered++;
                LOGGER.warn("Registered oversized block {} from Java model {} -> geometry {} at {}",
                        blockId, resolved.modelId(), geometryIdentifier, geoPath);
            }
        }

        LOGGER.warn("Auto oversized registry loaded {} entries from Java-model conversion", registered);
    }

    private static Path writeGeneratedGeometry(Path root, Identifier blockId, JsonObject geometryRoot) {
        try {
            Path dir = root.resolve(".generated-geometry");
            Files.createDirectories(dir);

            Path out = dir.resolve(blockId.getNamespace() + "__" + blockId.getPath().replace('/', '_') + ".geo.json");
            Files.writeString(out, GSON.toJson(geometryRoot));
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write generated geometry for " + blockId, e);
        }
    }
}
