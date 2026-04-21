package lol.sylvie.bedframe.screen.api;

import java.util.List;

public record InterceptedScreenModel(
        String sourceId,
        String title,
        String content,
        AutoFormKind kind,
        List<ScreenOption> options,
        List<ScreenField> fields,
        boolean interceptable,
        SkipReason skipReason,
        String skipMessage
) {
    public static InterceptedScreenModel skipped(String sourceId, SkipReason reason, String message) {
        return new InterceptedScreenModel(sourceId, "", "", AutoFormKind.UNSUPPORTED, List.of(), List.of(), false, reason, message);
    }
}
