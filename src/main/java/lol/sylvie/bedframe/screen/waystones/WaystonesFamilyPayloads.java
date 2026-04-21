package lol.sylvie.bedframe.screen.waystones;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WaystonesFamilyPayloads {
    private WaystonesFamilyPayloads() {
    }

    public record SelectionPayload(
            WaystonesFamilyType type,
            Object sourceObject,
            @Nullable Object fromWaystone,
            List<?> targets,
            @Nullable Hand hand
    ) {
    }

    public record SettingsPayload(
            WaystonesFamilyType type,
            Object sourceObject,
            Object waystone
    ) {
    }

    public record ConfirmPayload(
            WaystonesFamilyType type,
            Object sourceObject,
            String title,
            String content,
            String confirmAction,
            String cancelAction
    ) {
    }
}
