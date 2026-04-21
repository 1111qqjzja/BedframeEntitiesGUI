package lol.sylvie.bedframe.display;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lol.sylvie.bedframe.util.BedframeConstants;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class JavaModelTextureResolver {
    private JavaModelTextureResolver() {
    }

    public record JavaModelInfo(
            Identifier modelId,
            Map<String, JavaTextureRef> textureSlots,
            Map<String, OversizedDisplaySpec.AnimTextureOptions> animTextures,
            OversizedDisplaySpec.GuiDisplayTransform guiDisplayTransform
    ) {
    }

    public static JavaModelInfo resolve(Path javaModelFile, Identifier modelId) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(javaModelFile)).getAsJsonObject();

            Map<String, JavaTextureRef> slots = new LinkedHashMap<>();
            Map<String, OversizedDisplaySpec.AnimTextureOptions> animTextures = new LinkedHashMap<>();

            if (root.has("textures") && root.get("textures").isJsonObject()) {
                JsonObject textures = root.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                    String slot = entry.getKey();
                    String raw = entry.getValue().getAsString();

                    if ("particle".equals(slot)) {
                        continue;
                    }

                    JavaTextureRef ref = parseJavaTextureRef(raw, modelId);
                    if (ref != null) {
                        slots.put(slot, ref);
                    }

                    OversizedDisplaySpec.AnimTextureOptions anim = detectAnimTexture(ref);
                    if (anim != null) {
                        animTextures.put(slot, anim);
                    }
                }
            }

            OversizedDisplaySpec.GuiDisplayTransform gui = parseGuiDisplay(root);

            return new JavaModelInfo(modelId, slots, animTextures, gui);
        } catch (Exception e) {
            BedframeConstants.LOGGER.warn("Failed to resolve Java model textures from {}", javaModelFile, e);
            return new JavaModelInfo(
                    modelId,
                    Map.of(),
                    Map.of(),
                    OversizedDisplaySpec.GuiDisplayTransform.identity()
            );
        }
    }

    private static JavaTextureRef parseJavaTextureRef(String raw, Identifier modelId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        if (value.startsWith("#")) {
            return null;
        }

        String namespace = modelId.getNamespace();
        String logicalPath = value;

        if (value.contains(":")) {
            String[] split = value.split(":", 2);
            namespace = split[0];
            logicalPath = split[1];
        }

        if (logicalPath.endsWith(".png")) {
            logicalPath = logicalPath.substring(0, logicalPath.length() - 4);
        }
        if (logicalPath.startsWith("textures/")) {
            logicalPath = logicalPath.substring("textures/".length());
        }

        return new JavaTextureRef(namespace, logicalPath);
    }

    private static OversizedDisplaySpec.AnimTextureOptions detectAnimTexture(JavaTextureRef ref) {
        try {
            String resolved = resolveTexturePath(ref);
            if (resolved == null) {
                return null;
            }

            String namespace = ref.namespace();
            String path = resolved;

            if (resolved.contains(":")) {
                String[] split = resolved.split(":", 2);
                namespace = split[0];
                path = split[1];
            }

            BufferedImage image = readImage(namespace, path + ".png");
            if (image == null) {
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width > 0 && height > width && height % width == 0) {
                int frames = height / width;
                if (frames > 1) {
                    return new OversizedDisplaySpec.AnimTextureOptions(7.0f, frames);
                }
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage readImage(String namespace, String path) {
        try (InputStream in = ResourceHelper.getResource(namespace, path)) {
            if (in == null) {
                return null;
            }
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static OversizedDisplaySpec.GuiDisplayTransform parseGuiDisplay(JsonObject root) {
        try {
            if (!root.has("display") || !root.get("display").isJsonObject()) {
                return OversizedDisplaySpec.GuiDisplayTransform.identity();
            }

            JsonObject display = root.getAsJsonObject("display");
            if (!display.has("gui") || !display.get("gui").isJsonObject()) {
                return OversizedDisplaySpec.GuiDisplayTransform.identity();
            }

            JsonObject gui = display.getAsJsonObject("gui");

            float[] rot = readVec3(gui, "rotation", new float[]{0f, 0f, 0f});
            float[] trans = readVec3(gui, "translation", new float[]{0f, 0f, 0f});
            float[] scale = readVec3(gui, "scale", new float[]{1f, 1f, 1f});

            return new OversizedDisplaySpec.GuiDisplayTransform(
                    rot[0], rot[1], rot[2],
                    trans[0], trans[1], trans[2],
                    scale[0], scale[1], scale[2]
            );
        } catch (Exception e) {
            return OversizedDisplaySpec.GuiDisplayTransform.identity();
        }
    }

    private static float[] readVec3(JsonObject obj, String key, float[] fallback) {
        try {
            if (!obj.has(key) || !obj.get(key).isJsonArray()) {
                return fallback;
            }

            var arr = obj.getAsJsonArray(key);
            if (arr.size() < 3) {
                return fallback;
            }

            return new float[]{
                    arr.get(0).getAsFloat(),
                    arr.get(1).getAsFloat(),
                    arr.get(2).getAsFloat()
            };
        } catch (Exception e) {
            return fallback;
        }
    }

    public record JavaTextureRef(
            String namespace,
            String logicalPath
    ) {
    }

    public static String resolveTexturePath(JavaTextureRef ref) {
        if (ref == null) {
            return null;
        }

        String fromPolymer = resolveTexturePathFromPolymerPack(ref);
        if (fromPolymer != null) {
            BedframeConstants.LOGGER.warn("Resolved Java texture {} -> {}", ref, fromPolymer);
            return fromPolymer;
        }

        List<String> candidates = new ArrayList<>();
        String p = ref.logicalPath();

        candidates.add("textures/" + p + ".png");
        candidates.add("textures/entity/" + p + ".png");
        candidates.add("textures/block/" + p + ".png");
        candidates.add("textures/item/" + p + ".png");

        for (String candidate : candidates) {
            try (InputStream in = ResourceHelper.getResource(ref.namespace(), candidate)) {
                if (in != null) {
                    return ref.namespace() + ":" + candidate.substring(0, candidate.length() - 4);
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static String resolveTexturePathFromPolymerPack(JavaTextureRef ref) {
        if (ref == null) {
            BedframeConstants.LOGGER.warn("resolveTexturePathFromPolymerPack: ref is null");
            return null;
        }

        if (ResourceHelper.POLYMER_GENERATED_PACK == null) {
            BedframeConstants.LOGGER.warn("resolveTexturePathFromPolymerPack: POLYMER_GENERATED_PACK is null for {}", ref);
            return null;
        }

        try {
            String logicalPath = ref.logicalPath();

            String normalized = logicalPath;
            if (normalized.endsWith(".png")) {
                normalized = normalized.substring(0, normalized.length() - 4);
            }
            if (normalized.startsWith("textures/")) {
                normalized = normalized.substring("textures/".length());
            }

            String normalizedLower = normalized.replace('\\', '/').toLowerCase(Locale.ROOT);

            int slash = normalized.lastIndexOf('/');
            String leafName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
            String leafLower = leafName.toLowerCase(Locale.ROOT);

            List<String> matches = new ArrayList<>();

            Enumeration<? extends java.util.zip.ZipEntry> entries = ResourceHelper.POLYMER_GENERATED_PACK.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');

                if (entry.isDirectory()) continue;
                if (!name.startsWith("assets/")) continue;
                if (!name.contains("/textures/")) continue;
                if (!name.endsWith(".png")) continue;

                String lower = name.toLowerCase(Locale.ROOT);

                if (!lower.endsWith("/" + leafLower + ".png") && !lower.endsWith(leafLower + ".png")) {
                    continue;
                }

                matches.add(name);
            }

            if (matches.isEmpty()) {
                BedframeConstants.LOGGER.warn(
                        "resolveTexturePathFromPolymerPack: no matches for {} (normalized={}, leaf={})",
                        ref, normalized, leafName
                );
                return null;
            }

            matches.sort(Comparator.comparingInt(path -> scoreTextureCandidateGlobal(path, normalizedLower)));

            String best = matches.get(0);

            String withoutAssets = best.substring("assets/".length());
            int firstSlash = withoutAssets.indexOf('/');
            if (firstSlash < 0) {
                BedframeConstants.LOGGER.warn("resolveTexturePathFromPolymerPack: malformed best match {} for {}", best, ref);
                return null;
            }

            String realNamespace = withoutAssets.substring(0, firstSlash);
            String relative = withoutAssets.substring(firstSlash + 1, withoutAssets.length() - 4);

            BedframeConstants.LOGGER.warn("Resolved Java texture {} -> {}:{} via polymer pack",
                    ref, realNamespace, relative);

            return realNamespace + ":" + relative;
        } catch (Exception e) {
            BedframeConstants.LOGGER.warn("Failed to resolve texture from polymer pack for {}", ref, e);
            return null;
        }
    }

    private static int scoreTextureCandidateGlobal(String zipPath, String logicalPath) {
        String normalizedZip = zipPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        String normalizedLogical = logicalPath.replace('\\', '/').toLowerCase(Locale.ROOT);

        if (normalizedZip.endsWith("/textures/" + normalizedLogical + ".png")) {
            return 0;
        }

        if (normalizedZip.contains("/textures/" + normalizedLogical + ".png")) {
            return 5;
        }

        String leaf = normalizedLogical;
        int slash = leaf.lastIndexOf('/');
        if (slash >= 0) {
            leaf = leaf.substring(slash + 1);
        }

        if (normalizedZip.endsWith("/" + leaf + ".png")) {
            return 20;
        }

        if (normalizedZip.contains("/textures/entity/")) {
            return 30;
        }
        if (normalizedZip.contains("/textures/block/")) {
            return 40;
        }
        if (normalizedZip.contains("/textures/item/")) {
            return 50;
        }

        return 100;
    }
}
