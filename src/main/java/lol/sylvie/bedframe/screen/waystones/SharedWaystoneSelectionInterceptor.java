package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import lol.sylvie.bedframe.screen.api.ScreenSourceInterceptor;

public final class SharedWaystoneSelectionInterceptor implements ScreenSourceInterceptor {

    @Override
    public boolean supports(ScreenOpenContext context) {
        return WaystonesFamilyRouter.SHARESTONE_SELECTION.equals(context.triggerId())
                && context.payload() instanceof WaystonesFamilyPayloads.SelectionPayload;
    }

    @Override
    public InterceptedScreenModel intercept(ScreenOpenContext context) {
        WaystonesFamilyPayloads.SelectionPayload payload =
                (WaystonesFamilyPayloads.SelectionPayload) context.payload();

        return WaystonesSelectionSupport.buildSelectionModel(
                id(),
                "同族传送石",
                "请选择共享目标：",
                payload.targets(),
                false
        );
    }

    @Override
    public String id() {
        return "waystones:sharestone_selection";
    }
}
