package lol.sylvie.bedframe.screen.api;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;

public interface FormActionExecutor {
    boolean supports(String sourceId);
    boolean handleOption(ServerPlayerEntity player, String sourceId, String optionId);
    boolean handleCustom(ServerPlayerEntity player, String sourceId, Map<String, Object> values);
}
