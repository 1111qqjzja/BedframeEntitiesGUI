package lol.sylvie.testmod.block.impl;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import lol.sylvie.testmod.Testmod;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.util.Identifier;
import xyz.nucleoid.packettweaker.PacketContext;

public class TexturedFlowerExampleBlock extends PlantBlock implements PolymerTexturedBlock {
    private final BlockState polymerBlockState;

    public TexturedFlowerExampleBlock(Settings settings) {
        super(settings);
        polymerBlockState = PolymerBlockResourceUtils.requestBlock(BlockModelType.PLANT_BLOCK, PolymerBlockModel.of(Identifier.of(Testmod.MOD_ID, "block/example_flower")));
    }

    @Override
    protected MapCodec<? extends PlantBlock> getCodec() {
        return createCodec(TexturedFlowerExampleBlock::new);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        return polymerBlockState;
    }
}
