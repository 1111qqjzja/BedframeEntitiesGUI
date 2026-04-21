package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import net.minecraft.server.network.ServerPlayerEntity;

public final class UniversalScreenBridge {
    private UniversalScreenBridge() {
    }

    public static boolean tryIntercept(ServerPlayerEntity player, ScreenOpenContext context) {
        if (!GeyserSupport.isBedrock(player)) {
            return false;
        }
        InterceptedScreenModel model = InterceptorRegistry.tryBuild(context);
        if (SkipPolicy.shouldSkip(model)) {
            return false;
        }
        BedrockAutoFormService.open(player, model);
        return true;
    }
}
