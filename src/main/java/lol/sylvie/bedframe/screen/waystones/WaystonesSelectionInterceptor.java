package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import lol.sylvie.bedframe.screen.api.ScreenSourceInterceptor;

public final class WaystonesSelectionInterceptor implements ScreenSourceInterceptor {

    @Override
    public boolean supports(ScreenOpenContext context) {
        return WaystonesFamilyRouter.WAYSTONE_SELECTION.equals(context.triggerId())
                && context.payload() instanceof WaystonesFamilyPayloads.SelectionPayload;
    }

    @Override
    public InterceptedScreenModel intercept(ScreenOpenContext context) {
        WaystonesFamilyPayloads.SelectionPayload payload =
                (WaystonesFamilyPayloads.SelectionPayload) context.payload();

        String fromName = WaystonesSelectionSupport.extractWaystoneName(payload.fromWaystone());
        String content = fromName.isBlank() ? "请选择目标 Waystone" : "从 §e" + fromName + "§r 传送到：";

        return WaystonesSelectionSupport.buildSelectionModel(
                id(), "Waystones", content, payload.targets(), true
        );
    }

    @Override
    public String id() {
        return "waystones:selection";
    }
}
