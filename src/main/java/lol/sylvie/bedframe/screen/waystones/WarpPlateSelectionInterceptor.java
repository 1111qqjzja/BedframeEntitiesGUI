package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import lol.sylvie.bedframe.screen.api.ScreenSourceInterceptor;

public final class WarpPlateSelectionInterceptor implements ScreenSourceInterceptor {

    @Override
    public boolean supports(ScreenOpenContext context) {
        return WaystonesFamilyRouter.WARP_PLATE_SELECTION.equals(context.triggerId())
                && context.payload() instanceof WaystonesFamilyPayloads.SelectionPayload;
    }

    @Override
    public InterceptedScreenModel intercept(ScreenOpenContext context) {
        WaystonesFamilyPayloads.SelectionPayload payload =
                (WaystonesFamilyPayloads.SelectionPayload) context.payload();

        return WaystonesSelectionSupport.buildSelectionModel(
                id(),
                "传送石盘",
                "请选择石盘目标：",
                payload.targets(),
                false
        );
    }

    @Override
    public String id() {
        return "waystones:warp_plate_selection";
    }
}
