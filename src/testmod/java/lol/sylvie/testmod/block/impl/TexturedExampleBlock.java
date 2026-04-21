package lol.sylvie.testmod.block.impl;

import eu.pb4.polymer.blocks.api.BlockModelType;
import lol.sylvie.testmod.Testmod;
import net.minecraft.util.Identifier;

public class TexturedExampleBlock extends SimplePolymerTexturedBlock {
    public TexturedExampleBlock(Settings settings) {
        super(settings, BlockModelType.FULL_BLOCK, Identifier.of(Testmod.MOD_ID, "block/example_block"));
    }
}
