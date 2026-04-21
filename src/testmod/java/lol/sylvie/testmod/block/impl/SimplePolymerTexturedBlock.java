package lol.sylvie.testmod.block.impl;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;

public class SimplePolymerTexturedBlock extends Block implements PolymerTexturedBlock {
    private final HashMap<BlockState, BlockState> polymerBlockStates = new HashMap<>();

    public SimplePolymerTexturedBlock(Settings settings, BlockModelType modelType, Identifier model) {
        super(settings);
        for (BlockState state : this.getStateManager().getStates()) {
            polymerBlockStates.put(state, PolymerBlockResourceUtils.requestBlock(modelType, PolymerBlockModel.of(model)));
        }
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        return polymerBlockStates.get(state);
    }
}
