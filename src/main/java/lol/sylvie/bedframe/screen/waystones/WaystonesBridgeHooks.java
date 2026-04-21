package lol.sylvie.bedframe.screen.waystones;

import lol.sylvie.bedframe.screen.api.ScreenOpenContext;
import lol.sylvie.bedframe.screen.bridge.ActionExecutorRegistry;
import lol.sylvie.bedframe.screen.bridge.InterceptorRegistry;
import lol.sylvie.bedframe.screen.bridge.UniversalScreenBridge;
import net.blay09.mods.waystones.menu.WaystoneEditMenu;
import net.blay09.mods.waystones.menu.WaystoneSelectionMenu;
import net.minecraft.server.network.ServerPlayerEntity;

public final class WaystonesBridgeHooks {
    private static boolean bootstrapped;

    private WaystonesBridgeHooks() {
    }

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        InterceptorRegistry.register(new WaystonesSelectionInterceptor());
        InterceptorRegistry.register(new SharedWaystoneSelectionInterceptor());
        InterceptorRegistry.register(new WarpStoneSelectionInterceptor());
        InterceptorRegistry.register(new WarpScrollSelectionInterceptor());
        InterceptorRegistry.register(new WaystonesSettingsInterceptor());

        ActionExecutorRegistry.register(new WaystonesActionExecutor());
    }

    public static boolean tryOpenSelectionForm(ServerPlayerEntity player, WaystoneSelectionMenu.Data data) {
        bootstrap();
        WaystonesRuntimeContext.putSelectionData(player, data);
        return UniversalScreenBridge.tryIntercept(player, new ScreenOpenContext(
                WaystonesSourceIds.SELECTION,
                player,
                data.fromWaystone() != null ? data.fromWaystone().getPos() : null,
                null,
                player.getMainHandStack(),
                data
        ));
    }

    public static boolean tryOpenSettingsForm(ServerPlayerEntity player, WaystoneEditMenu.Data data) {
        bootstrap();
        WaystonesRuntimeContext.putEditData(player, data);
        return UniversalScreenBridge.tryIntercept(player, new ScreenOpenContext(
                WaystonesSourceIds.SETTINGS,
                player,
                data.pos(),
                null,
                player.getMainHandStack(),
                data
        ));
    }
}
