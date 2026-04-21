package lol.sylvie.bedframe.display;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;
import static lol.sylvie.bedframe.util.BedframeConstants.GSON;

public final class AutoOversizedDisplayRegistry {
    private static final Map<Identifier, OversizedDisplaySpec> BY_BLOCK = new HashMap<>();

    private AutoOversizedDisplayRegistry() {
    }

    public static java.util.Collection<OversizedDisplaySpec> all() {
        return java.util.List.copyOf(BY_BLOCK.values());
    }

    public static void clear() {
        BY_BLOCK.clear();
    }

    public static OversizedDisplaySpec get(Identifier blockId) {
        return BY_BLOCK.get(blockId);
    }

    public static boolean isOversizedDisplay(Identifier blockId) {
        return BY_BLOCK.containsKey(blockId);
    }

    public static void registerFromGeometry(
            Identifier blockId,
            String geometryIdentifier,
            Path geoFile,
            String texturePath
    ) {
        try {
            if (!Files.exists(geoFile)) {
                return;
            }

            JsonObject root = GSON.fromJson(Files.readString(geoFile), JsonObject.class);
            GeometryBoundsAnalyzer.Bounds bounds = GeometryBoundsAnalyzer.analyze(root);

            if (!bounds.exceedsOversizedLimits()) {
                return;
            }

            Identifier javaEntityId = Identifier.of(
                    "bedframe",
                    blockId.getNamespace() + "_" + blockId.getPath() + "_display"
            );

            Identifier bedrockEntityId = Identifier.of(
                    "bedframe",
                    blockId.getNamespace() + "_" + blockId.getPath() + "_display"
            );

            double autoYOffset = 0.0;
            if (bounds.minY() < 0.0) {
                autoYOffset = (-bounds.minY()) / 16.0;
            }

            Identifier textureId = parseTextureIdentifier(blockId, texturePath);
            if (textureId == null) {
                textureId = Identifier.of(blockId.getNamespace(), "block/" + blockId.getPath());
            }

            double targetWidth = bounds.sizeX();
            double targetHeight = bounds.sizeY();
            double targetDepth = bounds.sizeZ();

            double geoWidth = bounds.maxX() - bounds.minX();
            double geoHeight = bounds.maxY() - bounds.minY();
            double geoDepth = bounds.maxZ() - bounds.minZ();

            double scale = geoHeight == 0 ? 1.0 : (targetHeight / geoHeight);

            double offsetY = (-bounds.minY()) * scale / 16.0;

            BY_BLOCK.put(blockId, new OversizedDisplaySpec(
                    blockId,
                    javaEntityId,
                    bedrockEntityId,
                    geometryIdentifier,
                    geoFile,
                    texturePath,
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new OversizedDisplaySpec.GuiDisplayTransform(
                            0f, 0f, 0f,
                            0f, 0f, 0f,
                            1f, 1f, 1f
                    ),
                    new Vec3d(0.5, offsetY, 0.5),
                    (float) scale, (float) scale, (float) scale,
                    true
            ));

            LOGGER.warn("Auto oversized detected: {} bounds=({}, {}, {}) -> ({}, {}, {}) size=({}, {}, {})",
                    blockId,
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                    bounds.sizeX(), bounds.sizeY(), bounds.sizeZ());
        } catch (Exception e) {
            LOGGER.error("Failed to analyze geometry for {}", blockId, e);
        }
    }

    private static Identifier parseTextureIdentifier(Identifier blockId, String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }

        String raw = texturePath.trim();

        if (raw.startsWith("textures/")) {
            raw = raw.substring("textures/".length());
        }
        if (raw.endsWith(".png")) {
            raw = raw.substring(0, raw.length() - 4);
        }

        try {
            if (raw.contains(":")) {
                return Identifier.of(raw);
            }
            return Identifier.of(blockId.getNamespace(), raw);
        } catch (Exception e) {
            return null;
        }
    }
}
