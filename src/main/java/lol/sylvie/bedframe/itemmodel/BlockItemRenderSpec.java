package lol.sylvie.bedframe.itemmodel;

import net.minecraft.util.Identifier;

import java.nio.file.Path;

public record BlockItemRenderSpec(
        Identifier itemId,
        Identifier blockId,
        Identifier attachableId,
        String geometryIdentifier,
        Path geometryFile,
        Identifier textureId,
        String bedrockTexturePath,
        boolean handheldLike,
        float scaleX,
        float scaleY,
        float scaleZ,
        float offsetX,
        float offsetY,
        float offsetZ
) {
}
