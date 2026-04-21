package lol.sylvie.bedframe.screen.waystones;

import net.blay09.mods.waystones.menu.WaystoneEditMenu;
import net.blay09.mods.waystones.menu.WaystoneSelectionMenu;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaystonesRuntimeContext {
    private static final Map<UUID, WaystoneSelectionMenu.Data> SELECTION = new ConcurrentHashMap<>();
    private static final Map<UUID, WaystoneEditMenu.Data> EDIT = new ConcurrentHashMap<>();

    private WaystonesRuntimeContext() {
    }

    public static void putSelectionData(ServerPlayerEntity player, WaystoneSelectionMenu.Data data) {
        SELECTION.put(player.getUuid(), data);
    }

    public static WaystoneSelectionMenu.Data getSelectionData(ServerPlayerEntity player) {
        return SELECTION.get(player.getUuid());
    }

    public static void putEditData(ServerPlayerEntity player, WaystoneEditMenu.Data data) {
        EDIT.put(player.getUuid(), data);
    }

    public static WaystoneEditMenu.Data getEditData(ServerPlayerEntity player) {
        return EDIT.get(player.getUuid());
    }

    public static void clear(ServerPlayerEntity player) {
        SELECTION.remove(player.getUuid());
        EDIT.remove(player.getUuid());
    }
}
