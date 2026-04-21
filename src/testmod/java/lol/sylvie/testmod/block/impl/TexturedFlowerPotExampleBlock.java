package lol.sylvie.testmod.block.impl;

import eu.pb4.polymer.blocks.api.BlockModelType;
import lol.sylvie.testmod.Testmod;
import net.minecraft.util.Identifier;

public class TexturedFlowerPotExampleBlock extends SimplePolymerTexturedBlock {
    public TexturedFlowerPotExampleBlock(Settings settings) {
        super(settings, BlockModelType.TRANSPARENT_BLOCK, Identifier.of(Testmod.MOD_ID, "block/example_flower_pot"));
    }
}
