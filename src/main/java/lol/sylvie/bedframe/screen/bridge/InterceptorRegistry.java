package lol.sylvie.bedframe.screen.bridge;

import lol.sylvie.bedframe.screen.api.InterceptedScreenModel;
import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import lol.sylvie.bedframe.screen.api.ScreenSourceInterceptor;
import lol.sylvie.bedframe.screen.api.SkipReason;

import java.util.ArrayList;
import java.util.List;

public final class InterceptorRegistry {
    private static final List<ScreenSourceInterceptor> INTERCEPTORS = new ArrayList<>();

    private InterceptorRegistry() {
    }

    public static void register(ScreenSourceInterceptor interceptor) {
        INTERCEPTORS.add(interceptor);
    }

    public static InterceptedScreenModel tryBuild(ScreenOpenContext context) {
        for (ScreenSourceInterceptor interceptor : INTERCEPTORS) {
            if (interceptor.supports(context)) {
                return interceptor.intercept(context);
            }
        }
        return InterceptedScreenModel.skipped("none", SkipReason.NO_SERVER_ENTRY, "No server-side interceptor matched this screen intent");
    }
}
