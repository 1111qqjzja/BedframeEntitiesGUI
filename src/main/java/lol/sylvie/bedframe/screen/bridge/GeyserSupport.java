package lol.sylvie.bedframe.screen.bridge;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.geysermc.geyser.api.GeyserApi;

public final class GeyserSupport {
    private GeyserSupport() {
    }

    public static boolean isPresent() {
        return FabricLoader.getInstance().isModLoaded("geyser-fabric");
    }

    public static boolean isBedrock(ServerPlayerEntity player) {
        if (!isPresent()) {
            return false;
        }

        try {
            return GeyserApi.api().isBedrockPlayer(player.getUuid());
        } catch (Throwable t) {
            return false;
        }
    }
}
