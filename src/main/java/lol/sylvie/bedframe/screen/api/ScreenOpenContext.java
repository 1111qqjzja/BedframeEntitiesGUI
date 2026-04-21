package lol.sylvie.bedframe.screen.api;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public record ScreenOpenContext(
        String triggerId,
        ServerPlayerEntity player,
        @Nullable BlockPos blockPos,
        @Nullable Entity entity,
        @Nullable ItemStack heldItem,
        @Nullable Object payload
) {
}
