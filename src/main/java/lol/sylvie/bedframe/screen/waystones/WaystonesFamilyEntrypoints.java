package lol.sylvie.bedframe.screen.waystones;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WaystonesFamilyEntrypoints {
    private WaystonesFamilyEntrypoints() {
    }

    public static boolean tryOpenWaystoneBlockSelection(
            ServerPlayerEntity player, Object blockEntity, @Nullable Object fromWaystone, List<?> targets, @Nullable Hand hand
    ) {
        return WaystonesEntrySupport.openSelection(
                player, WaystonesFamilyRouter.WAYSTONE_SELECTION,
                WaystonesFamilyType.WAYSTONE_BLOCK, blockEntity, fromWaystone, targets, hand
        );
    }

    public static boolean tryOpenSharestoneSelection(
            ServerPlayerEntity player, Object blockEntity, @Nullable Object fromWaystone, List<?> targets, @Nullable Hand hand
    ) {
        return WaystonesEntrySupport.openSelection(
                player, WaystonesFamilyRouter.SHARESTONE_SELECTION,
                WaystonesFamilyType.SHARESTONE_BLOCK, blockEntity, fromWaystone, targets, hand
        );
    }

    public static boolean tryOpenWarpStoneSelection(
            ServerPlayerEntity player, ItemStack stack, List<?> targets, @Nullable Hand hand
    ) {
        return WaystonesEntrySupport.openSelection(
                player, WaystonesFamilyRouter.WARP_STONE_SELECTION,
                WaystonesFamilyType.WARP_STONE_ITEM, stack, null, targets, hand
        );
    }

    public static boolean tryOpenWarpScrollSelection(
            ServerPlayerEntity player, ItemStack stack, List<?> targets, @Nullable Hand hand
    ) {
        return WaystonesEntrySupport.openSelection(
                player, WaystonesFamilyRouter.WARP_SCROLL_SELECTION,
                WaystonesFamilyType.WARP_SCROLL_ITEM, stack, null, targets, hand
        );
    }

    public static boolean tryOpenWarpPlateSelection(
            ServerPlayerEntity player, Object blockEntity, @Nullable Object fromWaystone, List<?> targets, @Nullable Hand hand
    ) {
        return WaystonesEntrySupport.openSelection(
                player, WaystonesFamilyRouter.WARP_PLATE_SELECTION,
                WaystonesFamilyType.WARP_PLATE_BLOCK, blockEntity, fromWaystone, targets, hand
        );
    }
}
