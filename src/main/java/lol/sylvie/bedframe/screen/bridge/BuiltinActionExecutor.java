package lol.sylvie.bedframe.screen.bridge;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class BuiltinActionExecutor {
    private BuiltinActionExecutor() {
    }

    public static boolean handle(ServerPlayerEntity player, String optionId) {
        if (optionId == null) {
            return false;
        }
        if (optionId.equals("builtin:close")) {
            BedrockSessionStore.remove(player.getUuid());
            return true;
        }
        if (optionId.equals("builtin:back")) {
            player.sendMessage(Text.literal("§7返回动作需要由具体 interceptor 实现"), false);
            return true;
        }
        return false;
    }
}
