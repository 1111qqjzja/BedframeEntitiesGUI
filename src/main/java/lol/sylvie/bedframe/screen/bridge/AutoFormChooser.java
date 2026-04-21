package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.AutoFormKind;
import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;

public final class AutoFormChooser {
    private AutoFormChooser() {
    }

    public static AutoFormKind choose(InterceptedScreenModel model) {
        if (model == null || !model.interceptable()) {
            return AutoFormKind.UNSUPPORTED;
        }
        if (!model.fields().isEmpty()) {
            return AutoFormKind.CUSTOM;
        }
        if (looksLikeModal(model)) {
            return AutoFormKind.MODAL;
        }
        if (!model.options().isEmpty()) {
            return AutoFormKind.SIMPLE;
        }
        return AutoFormKind.UNSUPPORTED;
    }

    private static boolean looksLikeModal(InterceptedScreenModel model) {
        if (model.options().size() != 2) {
            return false;
        }
        String a = model.options().get(0).text().toLowerCase();
        String b = model.options().get(1).text().toLowerCase();
        return containsAny(a, "确认", "接受", "yes", "confirm", "accept")
                && containsAny(b, "取消", "拒绝", "no", "cancel", "decline");
    }

    private static boolean containsAny(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
