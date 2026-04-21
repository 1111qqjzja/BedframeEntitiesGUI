package lol.sylvie.bedframe.util;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PathRegistry {
    private static final Object LOCK = new Object();
    private static final LinkedHashSet<String> KNOWN_PACK_PATHS = new LinkedHashSet<>();
    private static int currentBuilderIdentity = 0;

    private PathRegistry() {
    }

    public static void onBuilderWrapped(Object builder) {
        int newIdentity = System.identityHashCode(builder);
        synchronized (LOCK) {
            if (currentBuilderIdentity != newIdentity) {
                currentBuilderIdentity = newIdentity;
                KNOWN_PACK_PATHS.clear();
            }
        }
    }

    public static void recordPath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }

        String normalized = normalize(path);
        synchronized (LOCK) {
            KNOWN_PACK_PATHS.add(normalized);
        }
    }

    public static Set<String> snapshot() {
        synchronized (LOCK) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(KNOWN_PACK_PATHS));
        }
    }

    public static boolean isEmpty() {
        synchronized (LOCK) {
            return KNOWN_PACK_PATHS.isEmpty();
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return KNOWN_PACK_PATHS.size();
        }
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
