package lol.sylvie.bedframe.screen.waystones;

import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public final class NoopWarpRequirement implements WarpRequirement {
    public static final NoopWarpRequirement INSTANCE = new NoopWarpRequirement();

    private NoopWarpRequirement() {
    }

    @Override
    public boolean canAfford(PlayerEntity player) {
        return true;
    }

    @Override
    public void consume(WaystoneTeleportContext context, PlayerEntity player) {
    }

    @Override
    public void rollback(WaystoneTeleportContext context, PlayerEntity player) {
    }

    @Override
    public void appendHoverText(PlayerEntity player, List<Text> tooltip) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
