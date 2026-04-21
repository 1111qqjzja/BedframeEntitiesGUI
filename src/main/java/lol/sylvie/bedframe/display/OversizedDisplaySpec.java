package lol.sylvie.bedframe.display;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.Map;

public record OversizedDisplaySpec(
        Identifier anchorBlockId,
        Identifier javaEntityId,
        Identifier bedrockEntityId,

        String geometryIdentifier,
        Path geoPath,

        String defaultTexturePath,
        Map<String, String> materialInstanceTextures,
        Map<String, AnimTextureOptions> animTextures,

        GuiDisplayTransform guiDisplayTransform,

        Vec3d offset,
        float scaleX,
        float scaleY,
        float scaleZ,
        boolean renderItemModel
) {
    public OversizedDisplaySpec {
        if (anchorBlockId == null) {
            throw new IllegalArgumentException("anchorBlockId cannot be null");
        }

        if (javaEntityId == null) {
            javaEntityId = Identifier.of(
                    "bedframe",
                    anchorBlockId.getNamespace() + "_" + anchorBlockId.getPath() + "_display"
            );
        }

        if (bedrockEntityId == null) {
            bedrockEntityId = javaEntityId;
        }

        if (geometryIdentifier == null || geometryIdentifier.isBlank()) {
            geometryIdentifier = "geometry." + anchorBlockId.getNamespace() + "." + anchorBlockId.getPath();
        }

        if (defaultTexturePath == null || defaultTexturePath.isBlank()) {
            defaultTexturePath = "textures/entity/" + anchorBlockId.getNamespace() + "/" + anchorBlockId.getPath();
        }

        if (materialInstanceTextures == null) {
            materialInstanceTextures = Map.of();
        } else {
            materialInstanceTextures = Map.copyOf(materialInstanceTextures);
        }

        if (animTextures == null) {
            animTextures = Map.of();
        } else {
            animTextures = Map.copyOf(animTextures);
        }

        if (guiDisplayTransform == null) {
            guiDisplayTransform = GuiDisplayTransform.identity();
        }

        if (offset == null) {
            offset = Vec3d.ZERO;
        }
    }

    public record AnimTextureOptions(
            float fps,
            int frames
    ) {
    }

    public record GuiDisplayTransform(
            float rotX, float rotY, float rotZ,
            float transX, float transY, float transZ,
            float scaleX, float scaleY, float scaleZ
    ) {
        public static GuiDisplayTransform identity() {
            return new GuiDisplayTransform(
                    0f, 0f, 0f,
                    0f, 0f, 0f,
                    1f, 1f, 1f
            );
        }
    }
}
