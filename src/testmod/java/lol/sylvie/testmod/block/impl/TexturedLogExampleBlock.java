package lol.sylvie.testmod.block.impl;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import lol.sylvie.testmod.Testmod;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;

public class TexturedLogExampleBlock extends PillarBlock implements PolymerTexturedBlock {
    private final HashMap<BlockState, BlockState> polymerBlockStates = new HashMap<>();

    public TexturedLogExampleBlock(Settings settings) {
        super(settings);
        for (BlockState state : this.getStateManager().getStates()) {
            Direction.Axis axis = state.get(AXIS);
            boolean horizontal = axis.isHorizontal();
            Identifier model = Identifier.of(Testmod.MOD_ID, "block/example_log" + (horizontal ? "_horizontal" : ""));
            polymerBlockStates.put(state, PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model, horizontal ? 90 : 0, axis == Direction.Axis.Z ? 0 : 90)));
        }
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
        return polymerBlockStates.get(state);
    }
}
