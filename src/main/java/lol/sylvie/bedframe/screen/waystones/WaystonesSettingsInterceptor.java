package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WaystonesSettingsInterceptor implements ScreenSourceInterceptor {

    @Override
    public boolean supports(ScreenOpenContext context) {
        return WaystonesFamilyRouter.WAYSTONE_SETTINGS.equals(context.triggerId())
                && context.payload() != null;
    }

    @Override
    public InterceptedScreenModel intercept(ScreenOpenContext context) {
        Object payload = context.payload();
        Object waystone = extractWaystone(payload);

        if (waystone == null) {
            return InterceptedScreenModel.skipped(
                    id(),
                    SkipReason.NO_STANDARD_COMPONENTS,
                    "Waystones settings payload does not contain a waystone"
            );
        }

        String waystoneName = WaystonesSelectionSupport.extractWaystoneName(waystone);
        if (waystoneName.isBlank()) {
            waystoneName = "Waystone";
        }

        List<String> rawVisibilityOptions = extractRawVisibilityOptions(payload, waystone);
        List<String> displayVisibilityOptions = toDisplayLabels(rawVisibilityOptions);
        int defaultVisibilityIndex = extractDefaultVisibilityIndex(waystone, rawVisibilityOptions);

        return new InterceptedScreenModel(
                id(),
                "Waystone 设置",
                "编辑 §e" + waystoneName + "§r\n关闭表单将返回传送菜单",
                AutoFormKind.CUSTOM,
                List.of(),
                List.of(
                        new InputField("name", "名称", "请输入 Waystone 名称", waystoneName),
                        new DropdownField("visibility", "可见性", displayVisibilityOptions, defaultVisibilityIndex),
                        new ToggleField("return_to_selection", "提交后返回传送菜单", false)
                ),
                true,
                SkipReason.NONE,
                ""
        );
    }

    @Override
    public String id() {
        return "waystones:settings";
    }

    public static Object extractWaystone(Object payload) {
        if (payload == null) {
            return null;
        }

        Object value = WaystonesSelectionSupport.tryGetter(payload, "getWaystone");
        if (value != null) {
            return value;
        }

        value = WaystonesSelectionSupport.tryGetter(payload, "waystone");
        if (value != null) {
            return value;
        }

        value = WaystonesSelectionSupport.tryField(payload, "waystone");
        if (value != null) {
            return value;
        }

        if (payload instanceof WaystonesFamilyPayloads.SettingsPayload settingsPayload) {
            return settingsPayload.waystone();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractRawVisibilityOptions(Object payload, Object waystone) {
        if (payload != null) {
            Object value = WaystonesSelectionSupport.tryGetter(payload, "getVisibilityOptions");
            if (value instanceof List<?> list) {
                return stringifyList(list);
            }

            value = WaystonesSelectionSupport.tryGetter(payload, "visibilityOptions");
            if (value instanceof List<?> list) {
                return stringifyList(list);
            }

            value = WaystonesSelectionSupport.tryField(payload, "visibilityOptions");
            if (value instanceof List<?> list) {
                return stringifyList(list);
            }
        }

        List<String> fallback = new ArrayList<>();
        String current = extractCurrentVisibilityName(waystone);
        if (!current.isBlank()) {
            fallback.add(current);
        }

        for (String common : List.of("ACTIVATION", "GLOBAL", "SHARD_ONLY")) {
            if (!fallback.contains(common)) {
                fallback.add(common);
            }
        }

        return fallback.isEmpty() ? Collections.singletonList("ACTIVATION") : fallback;
    }

    public static List<String> toDisplayLabels(List<String> rawVisibilityOptions) {
        List<String> result = new ArrayList<>();
        for (String raw : rawVisibilityOptions) {
            result.add(toDisplayLabel(raw));
        }
        return result;
    }

    public static String toDisplayLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "未知";
        }

        return switch (raw) {
            case "ACTIVATION" -> "激活后可见";
            case "GLOBAL" -> "全局可见";
            case "SHARD_ONLY" -> "仅碎片可见";
            case "ORANGE_SHARESTONE" -> "橙色同族传送石";
            case "MAGENTA_SHARESTONE" -> "品红同族传送石";
            case "LIGHT_BLUE_SHARESTONE" -> "淡蓝同族传送石";
            case "YELLOW_SHARESTONE" -> "黄色同族传送石";
            case "LIME_SHARESTONE" -> "黄绿同族传送石";
            case "PINK_SHARESTONE" -> "粉色同族传送石";
            case "GRAY_SHARESTONE" -> "灰色同族传送石";
            case "LIGHT_GRAY_SHARESTONE" -> "淡灰同族传送石";
            case "CYAN_SHARESTONE" -> "青色同族传送石";
            case "PURPLE_SHARESTONE" -> "紫色同族传送石";
            case "BLUE_SHARESTONE" -> "蓝色同族传送石";
            case "BROWN_SHARESTONE" -> "棕色同族传送石";
            case "GREEN_SHARESTONE" -> "绿色同族传送石";
            case "RED_SHARESTONE" -> "红色同族传送石";
            case "BLACK_SHARESTONE" -> "黑色同族传送石";
            default -> raw;
        };
    }

    public static String displayLabelToRaw(String label, List<String> rawVisibilityOptions) {
        if (label == null || label.isBlank()) {
            return "";
        }

        for (String raw : rawVisibilityOptions) {
            if (toDisplayLabel(raw).equals(label)) {
                return raw;
            }
        }

        if (rawVisibilityOptions.contains(label)) {
            return label;
        }

        return "";
    }

    public static int extractDefaultVisibilityIndex(Object waystone, List<String> rawVisibilityOptions) {
        String current = extractCurrentVisibilityName(waystone);
        if (current.isBlank()) {
            return 0;
        }

        for (int i = 0; i < rawVisibilityOptions.size(); i++) {
            if (rawVisibilityOptions.get(i).equalsIgnoreCase(current)) {
                return i;
            }
        }
        return 0;
    }

    public static String extractCurrentVisibilityName(Object waystone) {
        if (waystone == null) {
            return "";
        }

        Object visibility = WaystonesSelectionSupport.tryGetter(waystone, "getVisibility");
        if (visibility == null) {
            visibility = WaystonesSelectionSupport.tryField(waystone, "visibility");
        }
        if (visibility == null) {
            return "";
        }

        return String.valueOf(visibility);
    }

    private static List<String> stringifyList(List<?> list) {
        List<String> result = new ArrayList<>();
        for (Object obj : list) {
            if (obj == null) continue;
            result.add(String.valueOf(obj));
        }
        return result;
    }
}
