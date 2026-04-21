package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.bridge.GeyserSupport;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WaystonesEntrySupport {
    private WaystonesEntrySupport() {
    }

    public static boolean openSelection(
            ServerPlayerEntity player,
            String triggerId,
            WaystonesFamilyType type,
            Object sourceObject,
            @Nullable Object fromWaystone,
            List<?> targets,
            @Nullable Hand hand
    ) {
        if (!GeyserSupport.isBedrock(player)) {
            return false;
        }

        WaystonesBedrockBridge.setLastHand(player, hand);

        WaystonesFamilyPayloads.SelectionPayload payload =
                new WaystonesFamilyPayloads.SelectionPayload(
                        type, sourceObject, fromWaystone, targets, hand
                );

        return WaystonesBedrockBridge.tryOpenSelection(player, triggerId, payload);
    }
}
