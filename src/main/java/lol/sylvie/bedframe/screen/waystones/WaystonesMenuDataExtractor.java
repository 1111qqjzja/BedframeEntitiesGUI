package lol.sylvie.bedframe.screen.waystones;

import net.blay09.mods.balm.world.BalmMenuProvider;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public final class WaystonesMenuDataExtractor {
    private WaystonesMenuDataExtractor() {
    }

    public static @Nullable Object getOpeningData(@Nullable Object provider, ServerPlayerEntity player) {
        if (provider == null) {
            return null;
        }

        if (provider instanceof BalmMenuProvider balmMenuProvider) {
            try {
                return balmMenuProvider.getScreenOpeningData(player);
            } catch (Throwable ignored) {
            }
        }

        try {
            Method method = provider.getClass().getMethod("getScreenOpeningData", player.getClass());
            method.setAccessible(true);
            return method.invoke(provider, player);
        } catch (Throwable ignored) {
        }

        try {
            for (Method method : provider.getClass().getMethods()) {
                if (!method.getName().equals("getScreenOpeningData")) continue;
                if (method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(player.getClass())) continue;
                method.setAccessible(true);
                return method.invoke(provider, player);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static @Nullable Object extractFromWaystone(@Nullable Object openingData) {
        if (openingData == null) {
            return null;
        }

        Object value = tryGetter(openingData, "getFromWaystone");
        if (value != null) return value;

        return tryField(openingData, "fromWaystone");
    }

    @SuppressWarnings("unchecked")
    public static List<?> extractTargets(@Nullable Object openingData) {
        if (openingData == null) {
            return Collections.emptyList();
        }

        Object value = tryGetter(openingData, "getWaystones");
        if (value instanceof List<?> list) return list;

        value = tryField(openingData, "waystones");
        if (value instanceof List<?> list) return list;

        value = tryGetter(openingData, "waystones");
        if (value instanceof List<?> list) return list;

        return Collections.emptyList();
    }

    public static @Nullable Object extractWarpItem(@Nullable Object openingData) {
        if (openingData == null) {
            return null;
        }

        Object value = tryGetter(openingData, "getWarpItem");
        if (value != null) return value;

        return tryField(openingData, "warpItem");
    }

    public static Object tryGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object tryField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
