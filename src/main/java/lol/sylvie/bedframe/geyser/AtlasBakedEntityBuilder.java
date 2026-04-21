package lol.sylvie.bedframe.geyser;

import com.google.gson.*;
import lol.sylvie.bedframe.display.AutoRenderMetadataResolver;
import lol.sylvie.bedframe.display.OversizedDisplaySpec;
import lol.sylvie.bedframe.util.ResourceHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class AtlasBakedEntityBuilder {
    private AtlasBakedEntityBuilder() {
    }

    public record BakedResult(
            JsonObject bakedGeometry,
            BufferedImage atlasImage,
            String atlasTextureReference,
            AtlasAnimInfo atlasAnimInfo
    ) {
    }

    public record AtlasAnimInfo(
            boolean hasAnimation,
            int totalFrames,
            float fps
    ) {
        public static AtlasAnimInfo none() {
            return new AtlasAnimInfo(false, 1, 0f);
        }
    }

    private record TextureSource(
            String key,
            String rawRef,
            String namespace,
            String relativePath,
            BufferedImage image,
            boolean animated,
            int frames,
            float fps
    ) {
        int frameWidth() {
            return image.getWidth();
        }

        int frameHeight() {
            if (!animated || frames <= 1) {
                return image.getHeight();
            }
            return image.getHeight() / frames;
        }

        BufferedImage getFrame(int index) {
            if (!animated || frames <= 1) {
                return image;
            }
            int fh = frameHeight();
            int safe = Math.max(0, Math.min(index, frames - 1));
            return image.getSubimage(0, safe * fh, image.getWidth(), fh);
        }
    }

    private record AtlasSlot(
            String key,
            int x,
            int y,
            int width,
            int height
    ) {
    }

    private record ParsedRef(String namespace, String relativePath) {
    }

    public static BakedResult bake(
            OversizedDisplaySpec spec,
            JsonObject geometryRoot,
            AutoRenderMetadataResolver.RenderMetadata renderMeta
    ) throws Exception {
        if (geometryRoot == null) {
            return null;
        }

        List<TextureSource> sources = loadSources(renderMeta);
        if (sources.isEmpty()) {
            LOGGER.warn("Atlas bake skipped for {} because no texture sources were resolved", spec.anchorBlockId());
            return null;
        }

        int originalTexWidth = readGeometryTextureWidth(geometryRoot);
        int originalTexHeight = readGeometryTextureHeight(geometryRoot);

        int totalFrames = 1;
        float fps = 0f;
        for (TextureSource src : sources) {
            if (src.animated && src.frames > totalFrames) {
                totalFrames = src.frames;
            }
            if (src.animated && src.fps > fps) {
                fps = src.fps;
            }
        }

        if (totalFrames > 1 && fps <= 0f) {
            fps = 7f;
        }

        Map<String, AtlasSlot> singleFrameSlots = new LinkedHashMap<>();
        Dimension singleFrameSize = buildSingleFrameLayout(sources, singleFrameSlots);

        BufferedImage fullAtlas = buildFramedAtlas(
                sources,
                singleFrameSlots,
                singleFrameSize.width,
                singleFrameSize.height,
                totalFrames
        );

        JsonObject bakedGeometry = deepCopy(geometryRoot);

        rewriteGeometryTextureSize(
                bakedGeometry,
                singleFrameSize.width,
                singleFrameSize.height
        );

        remapGeometryUvsToSingleFrameAtlas(
                bakedGeometry,
                singleFrameSlots,
                originalTexWidth,
                originalTexHeight
        );

        String atlasRef = "textures/entity/" + spec.bedrockEntityId().getNamespace() + "/" + spec.bedrockEntityId().getPath();
        AtlasAnimInfo animInfo = totalFrames > 1 ? new AtlasAnimInfo(true, totalFrames, fps) : AtlasAnimInfo.none();

        LOGGER.warn("Atlas baked for {} -> atlasRef={}, original={}x{}, singleFrame={}x{}, totalFrames={}, full={}x{}, fps={}",
                spec.anchorBlockId(),
                atlasRef,
                originalTexWidth,
                originalTexHeight,
                singleFrameSize.width,
                singleFrameSize.height,
                totalFrames,
                fullAtlas.getWidth(),
                fullAtlas.getHeight(),
                fps);

        return new BakedResult(bakedGeometry, fullAtlas, atlasRef, animInfo);
    }

    private static List<TextureSource> loadSources(AutoRenderMetadataResolver.RenderMetadata renderMeta) {
        List<TextureSource> out = new ArrayList<>();

        List<String> keys = new ArrayList<>(renderMeta.materialInstanceTextures().keySet());
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (Exception ignored) {
                return a.compareTo(b);
            }
        });

        for (String key : keys) {
            String raw = renderMeta.materialInstanceTextures().get(key);
            ParsedRef parsed = parse(raw);
            if (parsed == null) {
                continue;
            }

            String sourcePath = parsed.relativePath();
            if (!sourcePath.startsWith("textures/")) {
                sourcePath = "textures/" + sourcePath;
            }
            if (!sourcePath.endsWith(".png")) {
                sourcePath = sourcePath + ".png";
            }

            try (InputStream in = ResourceHelper.getResource(parsed.namespace(), sourcePath)) {
                if (in == null) {
                    LOGGER.warn("Atlas source missing for key={} raw={} at {}:{}", key, raw, parsed.namespace(), sourcePath);
                    continue;
                }

                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    LOGGER.warn("Atlas source unreadable for key={} raw={} at {}:{}", key, raw, parsed.namespace(), sourcePath);
                    continue;
                }

                OversizedDisplaySpec.AnimTextureOptions anim = renderMeta.animTextures().get(key);
                boolean animated = anim != null && anim.frames() > 1;
                int frames = animated ? anim.frames() : 1;
                float fps = animated ? anim.fps() : 0f;

                out.add(new TextureSource(
                        key,
                        raw,
                        parsed.namespace(),
                        parsed.relativePath(),
                        image,
                        animated,
                        frames,
                        fps
                ));
            } catch (Exception e) {
                LOGGER.warn("Failed to load atlas source for key={} raw={}", key, raw, e);
            }
        }

        return out;
    }

    private static Dimension buildSingleFrameLayout(List<TextureSource> sources, Map<String, AtlasSlot> slots) {
        int width = 0;
        int height = 0;

        for (TextureSource src : sources) {
            width = Math.max(width, src.frameWidth());
            height += src.frameHeight();
        }

        if (width <= 0) width = 16;
        if (height <= 0) height = 16;

        int y = 0;
        for (TextureSource src : sources) {
            slots.put(src.key(), new AtlasSlot(
                    src.key(),
                    0,
                    y,
                    src.frameWidth(),
                    src.frameHeight()
            ));
            y += src.frameHeight();
        }

        return new Dimension(width, height);
    }

    private static BufferedImage buildFramedAtlas(
            List<TextureSource> sources,
            Map<String, AtlasSlot> slots,
            int singleFrameWidth,
            int singleFrameHeight,
            int totalFrames
    ) {
        BufferedImage atlas = new BufferedImage(
                singleFrameWidth,
                singleFrameHeight * totalFrames,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g = atlas.createGraphics();

        for (int frame = 0; frame < totalFrames; frame++) {
            int frameBaseY = frame * singleFrameHeight;

            for (TextureSource src : sources) {
                AtlasSlot slot = slots.get(src.key());
                if (slot == null) continue;

                BufferedImage frameImage;
                if (src.animated) {
                    int srcFrame = Math.min(frame, src.frames - 1);
                    frameImage = src.getFrame(srcFrame);
                } else {
                    frameImage = src.image;
                }

                g.drawImage(frameImage, slot.x, frameBaseY + slot.y, null);
            }
        }

        g.dispose();
        return atlas;
    }

    private static void rewriteGeometryTextureSize(JsonObject root, int atlasWidth, int atlasHeight) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            return;
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        if (description == null) {
            return;
        }

        description.addProperty("texture_width", atlasWidth);
        description.addProperty("texture_height", atlasHeight);
    }

    private static void remapGeometryUvsToSingleFrameAtlas(
            JsonObject root,
            Map<String, AtlasSlot> atlasSlots,
            int originalTexWidth,
            int originalTexHeight
    ) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            return;
        }

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonArray bones = geometry.getAsJsonArray("bones");
        if (bones == null) {
            return;
        }

        for (JsonElement boneEl : bones) {
            if (!boneEl.isJsonObject()) continue;
            JsonObject bone = boneEl.getAsJsonObject();
            JsonArray cubes = bone.getAsJsonArray("cubes");
            if (cubes == null) continue;

            for (JsonElement cubeEl : cubes) {
                if (!cubeEl.isJsonObject()) continue;
                JsonObject cube = cubeEl.getAsJsonObject();

                JsonElement uvEl = cube.get("uv");
                if (uvEl == null || !uvEl.isJsonObject()) continue;

                JsonObject uvObj = uvEl.getAsJsonObject();

                for (Map.Entry<String, JsonElement> faceEntry : uvObj.entrySet()) {
                    if (!faceEntry.getValue().isJsonObject()) continue;
                    JsonObject face = faceEntry.getValue().getAsJsonObject();

                    String materialInstance = face.has("material_instance")
                            ? face.get("material_instance").getAsString()
                            : "0";

                    AtlasSlot slot = atlasSlots.get(materialInstance);
                    if (slot == null) {
                        continue;
                    }

                    if (!face.has("uv") || !face.get("uv").isJsonArray()) {
                        continue;
                    }

                    JsonArray uv = face.getAsJsonArray("uv");
                    if (uv.size() < 2) {
                        continue;
                    }

                    double oldU = uv.get(0).getAsDouble();
                    double oldV = uv.get(1).getAsDouble();

                    double newU = slot.x + (oldU / originalTexWidth) * slot.width;
                    double newV = slot.y + (oldV / originalTexHeight) * slot.height;

                    JsonArray newUv = new JsonArray();
                    newUv.add(newU);
                    newUv.add(newV);
                    face.add("uv", newUv);

                    if (face.has("uv_size") && face.get("uv_size").isJsonArray()) {
                        JsonArray uvSize = face.getAsJsonArray("uv_size");
                        if (uvSize.size() >= 2) {
                            double oldUS = uvSize.get(0).getAsDouble();
                            double oldVS = uvSize.get(1).getAsDouble();

                            double newUS = (oldUS / originalTexWidth) * slot.width;
                            double newVS = (oldVS / originalTexHeight) * slot.height;

                            JsonArray newUvSize = new JsonArray();
                            newUvSize.add(newUS);
                            newUvSize.add(newVS);
                            face.add("uv_size", newUvSize);
                        }
                    }

                    face.remove("material_instance");
                }
            }
        }
    }

    private static ParsedRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String namespace = "minecraft";
        String path = raw.trim();

        if (path.contains(":")) {
            String[] split = path.split(":", 2);
            namespace = split[0];
            path = split[1];
        }

        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }

        return new ParsedRef(namespace, path);
    }

    private static JsonObject deepCopy(JsonObject root) {
        return JsonParser.parseString(root.toString()).getAsJsonObject();
    }

    private static int readGeometryTextureWidth(JsonObject root) {
        try {
            JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.isEmpty()) return 16;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            if (description == null || !description.has("texture_width")) return 16;
            return Math.max(1, Math.round(description.get("texture_width").getAsFloat()));
        } catch (Exception ignored) {
            return 16;
        }
    }

    private static int readGeometryTextureHeight(JsonObject root) {
        try {
            JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.isEmpty()) return 16;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            if (description == null || !description.has("texture_height")) return 16;
            return Math.max(1, Math.round(description.get("texture_height").getAsFloat()));
        } catch (Exception ignored) {
            return 16;
        }
    }
}
