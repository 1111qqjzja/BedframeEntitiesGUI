package lol.sylvie.bedframe.util;

import net.minecraft.server.network.ServerPlayerEntity;
import org.geysermc.geyser.api.GeyserApi;
import org.jetbrains.annotations.Nullable;

public class GeyserHelper {
    public static boolean isBedrockPlayer(@Nullable ServerPlayerEntity player) {
        return player != null && GeyserApi.api().isBedrockPlayer(player.getUuid());
    }
}
