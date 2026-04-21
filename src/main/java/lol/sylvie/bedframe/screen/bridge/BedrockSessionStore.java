package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BedrockSessionStore {
    public record Session(String sessionId, InterceptedScreenModel model) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private BedrockSessionStore() {
    }

    public static void put(UUID playerUuid, InterceptedScreenModel model) {
        SESSIONS.put(playerUuid, new Session(UUID.randomUUID().toString(), model));
    }

    public static Optional<Session> get(UUID playerUuid) {
        return Optional.ofNullable(SESSIONS.get(playerUuid));
    }

    public static void remove(UUID playerUuid) {
        SESSIONS.remove(playerUuid);
    }
}
