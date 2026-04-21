package lol.sylvie.bedframe.screen.api;

public interface ScreenSourceInterceptor {
    boolean supports(ScreenOpenContext context);
    InterceptedScreenModel intercept(ScreenOpenContext context);
    String id();
}
