package lol.sylvie.bedframe.view;

import net.minecraft.server.network.ServerPlayerEntity;

public final class BedframePlayerViewUtil {
    private BedframePlayerViewUtil() {
    }

    public static boolean isBedrockPlayer(ServerPlayerEntity player) {
        try {
            return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.getUuid());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
