package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.AutoFormKind;
import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.ScreenOption;
import lol.sylvie.bedframe.screen.api.SkipReason;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class WaystonesSelectionSupport {
    private WaystonesSelectionSupport() {
    }

    public static InterceptedScreenModel buildSelectionModel(
            String sourceId,
            String title,
            String content,
            List<?> targets,
            boolean includeSettings
    ) {
        if (targets == null || targets.isEmpty()) {
            return InterceptedScreenModel.skipped(
                    sourceId,
                    SkipReason.NO_STANDARD_COMPONENTS,
                    "No targets available"
            );
        }

        List<ScreenOption> options = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Object target = targets.get(i);
            String targetName = extractWaystoneName(target);
            if (targetName.isBlank()) {
                targetName = "目标 #" + (i + 1);
            }
            options.add(ScreenOption.of("select:" + i, targetName));
        }

        if (includeSettings) {
            options.add(ScreenOption.of("open_settings", "设置"));
        }
        options.add(ScreenOption.of("builtin:close", "关闭"));

        return new InterceptedScreenModel(
                sourceId,
                title,
                content,
                AutoFormKind.SIMPLE,
                List.copyOf(options),
                List.of(),
                true,
                SkipReason.NONE,
                ""
        );
    }

    public static String extractWaystoneName(Object waystone) {
        if (waystone == null) {
            return "";
        }

        Object nameObj = tryGetter(waystone, "getName");
        if (nameObj instanceof Text text) {
            return text.getString();
        }
        if (nameObj != null) {
            return String.valueOf(nameObj);
        }

        Object nameField = tryField(waystone, "name");
        if (nameField instanceof Text text) {
            return text.getString();
        }
        if (nameField != null) {
            return String.valueOf(nameField);
        }

        return "";
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
