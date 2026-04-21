package lol.sylvie.bedframe.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lol.sylvie.bedframe.util.BedframeConstants;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class AutoRenderMetadataResolver {
    private AutoRenderMetadataResolver() {
    }

    public record RenderMetadata(
            String defaultTexturePath,
            Map<String, String> materialInstanceTextures,
            Map<String, OversizedDisplaySpec.AnimTextureOptions> animTextures,
            OversizedDisplaySpec.GuiDisplayTransform guiDisplayTransform
    ) {
    }

    public static RenderMetadata resolve(
            Identifier blockId,
            String geometryIdentifier,
            Path geoPath,
            Path javaModelPath,
            String fallbackTexturePath
    ) {
        try {
            Set<String> materialInstances = collectMaterialInstances(geoPath);

            JavaModelTextureResolver.JavaModelInfo javaInfo =
                    JavaModelTextureResolver.resolve(javaModelPath, Identifier.of(blockId.getNamespace(), blockId.getPath()));

            Map<String, String> materialTextures = new LinkedHashMap<>();
            Map<String, OversizedDisplaySpec.AnimTextureOptions> animTextures = new LinkedHashMap<>();

            List<String> sorted = new ArrayList<>(materialInstances);
            sorted.sort(AutoRenderMetadataResolver::safeIntCompare);

            for (String materialKey : sorted) {
                JavaModelTextureResolver.JavaTextureRef ref = javaInfo.textureSlots().get(materialKey);
                String texture = JavaModelTextureResolver.resolveTexturePath(ref);

                if (texture == null || texture.isBlank()) {
                    BedframeConstants.LOGGER.warn("Skipping unresolved material texture for {} material={} ref={}",
                            blockId, materialKey, ref);
                    continue;
                }

                String lower = texture.toLowerCase(Locale.ROOT);
                if (lower.startsWith("minecraft:textures/entity/")
                        || lower.startsWith("minecraft:textures/minecraft/")
                        || lower.contains(":textures/entity/minecraft/")
                        || lower.contains(":textures/minecraft/entity/")) {
                    BedframeConstants.LOGGER.warn("Skipping suspicious fallback material texture for {} material={} -> {}",
                            blockId, materialKey, texture);
                    continue;
                }

                materialTextures.put(materialKey, texture);

                OversizedDisplaySpec.AnimTextureOptions anim = javaInfo.animTextures().get(materialKey);
                if (anim != null) {
                    animTextures.put(materialKey, anim);
                }

                BedframeConstants.LOGGER.warn("Accepted material texture for {} material={} -> {}",
                        blockId, materialKey, texture);
            }

            if (materialTextures.isEmpty()) {
                String fallback = fallbackTexturePath;
                if (fallback != null && !fallback.isBlank()) {
                    materialTextures.put("0", fallback);
                }
            }

            String defaultTexture = fallbackTexturePath;
            if (!materialTextures.isEmpty()) {
                defaultTexture = materialTextures.values().iterator().next();
            }

            return new RenderMetadata(
                    defaultTexture,
                    materialTextures,
                    animTextures,
                    javaInfo.guiDisplayTransform()
            );
        } catch (Exception e) {
            BedframeConstants.LOGGER.warn("Failed to auto-resolve render metadata for {}", blockId, e);
            return new RenderMetadata(
                    fallbackTexturePath,
                    fallbackTexturePath == null || fallbackTexturePath.isBlank()
                            ? Map.of()
                            : Map.of("0", fallbackTexturePath),
                    Map.of(),
                    OversizedDisplaySpec.GuiDisplayTransform.identity()
            );
        }
    }

    private static Set<String> collectMaterialInstances(Path geoPath) {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        try {
            JsonObject root = BedframeConstants.GSON.fromJson(Files.readString(geoPath), JsonObject.class);
            JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
            if (geometries == null || geometries.isEmpty()) {
                return out;
            }

            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonArray bones = geometry.getAsJsonArray("bones");
            if (bones == null) {
                return out;
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
                        if (face.has("material_instance")) {
                            out.add(face.get("material_instance").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            BedframeConstants.LOGGER.warn("Failed to collect material instances from {}", geoPath, e);
        }

        return out;
    }

    private static int safeIntCompare(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (Exception ignored) {
            return a.compareTo(b);
        }
    }
}
