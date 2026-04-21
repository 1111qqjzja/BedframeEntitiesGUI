package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import lol.sylvie.bedframe.screen.bridge.UniversalScreenBridge;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaystonesBedrockBridge {
    private static final Map<UUID, Object> SELECTION_CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> SETTINGS_CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Hand> LAST_HANDS = new ConcurrentHashMap<>();

    private WaystonesBedrockBridge() {
    }

    public static boolean tryOpenSelection(ServerPlayerEntity player, String triggerId, Object payload) {
        SELECTION_CONTEXTS.put(player.getUuid(), payload);

        ScreenOpenContext context = new ScreenOpenContext(
                triggerId, player, null, null, player.getMainHandStack(), payload
        );

        boolean intercepted = UniversalScreenBridge.tryIntercept(player, context);
        if (!intercepted) {
            SELECTION_CONTEXTS.remove(player.getUuid());
        }
        return intercepted;
    }

    public static boolean tryOpenSettings(ServerPlayerEntity player, Object payload) {
        SETTINGS_CONTEXTS.put(player.getUuid(), payload);

        ScreenOpenContext context = new ScreenOpenContext(
                WaystonesFamilyRouter.WAYSTONE_SETTINGS, player, null, null, player.getMainHandStack(), payload
        );

        boolean intercepted = UniversalScreenBridge.tryIntercept(player, context);
        if (!intercepted) {
            SETTINGS_CONTEXTS.remove(player.getUuid());
        }
        return intercepted;
    }

    public static boolean tryOpenWaystoneSettingsFromSelection(ServerPlayerEntity player) {
        Object selectionContext = getSelectionContext(player);
        if (!(selectionContext instanceof WaystonesFamilyPayloads.SelectionPayload payload)) {
            return false;
        }

        if (payload.fromWaystone() == null) {
            return false;
        }

        WaystonesFamilyPayloads.SettingsPayload settingsPayload =
                new WaystonesFamilyPayloads.SettingsPayload(
                        payload.type(), payload.sourceObject(), payload.fromWaystone()
                );

        return tryOpenSettings(player, settingsPayload);
    }

    public static boolean tryReopenWaystoneSelection(ServerPlayerEntity player) {
        Object selectionContext = getSelectionContext(player);
        if (!(selectionContext instanceof WaystonesFamilyPayloads.SelectionPayload payload)) {
            return false;
        }

        String trigger = switch (payload.type()) {
            case WAYSTONE_BLOCK -> WaystonesFamilyRouter.WAYSTONE_SELECTION;
            case SHARESTONE_BLOCK -> WaystonesFamilyRouter.SHARESTONE_SELECTION;
            case WARP_STONE_ITEM -> WaystonesFamilyRouter.WARP_STONE_SELECTION;
            case WARP_SCROLL_ITEM -> WaystonesFamilyRouter.WARP_SCROLL_SELECTION;
            case WARP_PLATE_BLOCK -> WaystonesFamilyRouter.WARP_PLATE_SELECTION;
        };

        return tryOpenSelection(player, trigger, payload);
    }

    public static @Nullable Object getSelectionContext(ServerPlayerEntity player) {
        return SELECTION_CONTEXTS.get(player.getUuid());
    }

    public static @Nullable Object getSettingsContext(ServerPlayerEntity player) {
        return SETTINGS_CONTEXTS.get(player.getUuid());
    }

    public static void clearSelectionContext(ServerPlayerEntity player) {
        SELECTION_CONTEXTS.remove(player.getUuid());
    }

    public static void clearSettingsContext(ServerPlayerEntity player) {
        SETTINGS_CONTEXTS.remove(player.getUuid());
    }

    public static void setLastHand(ServerPlayerEntity player, @Nullable Hand hand) {
        if (hand == null) LAST_HANDS.remove(player.getUuid());
        else LAST_HANDS.put(player.getUuid(), hand);
    }

    public static Hand getLastHand(ServerPlayerEntity player) {
        return LAST_HANDS.getOrDefault(player.getUuid(), Hand.MAIN_HAND);
    }

    public static void clearAll(ServerPlayerEntity player) {
        clearSelectionContext(player);
        clearSettingsContext(player);
        LAST_HANDS.remove(player.getUuid());
    }
}
