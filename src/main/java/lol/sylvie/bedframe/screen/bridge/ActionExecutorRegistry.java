package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.FormActionExecutor;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ActionExecutorRegistry {
    private static final List<FormActionExecutor> EXECUTORS = new ArrayList<>();

    private ActionExecutorRegistry() {
    }

    public static void register(FormActionExecutor executor) {
        EXECUTORS.add(executor);
    }

    public static boolean handleOption(ServerPlayerEntity player, String sourceId, String optionId) {
        for (FormActionExecutor executor : EXECUTORS) {
            if (executor.supports(sourceId) && executor.handleOption(player, sourceId, optionId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean handleCustom(ServerPlayerEntity player, String sourceId, Map<String, Object> values) {
        for (FormActionExecutor executor : EXECUTORS) {
            if (executor.supports(sourceId) && executor.handleCustom(player, sourceId, values)) {
                return true;
            }
        }
        return false;
    }
}
