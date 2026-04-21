package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.AutoFormKind;
import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.SkipReason;

public final class SkipPolicy {
    private SkipPolicy() {
    }

    public static boolean shouldSkip(InterceptedScreenModel model) {
        if (model == null) return true;
        if (!model.interceptable()) return true;
        if (model.skipReason() != SkipReason.NONE) return true;
        return model.kind() == AutoFormKind.UNSUPPORTED;
    }
}
