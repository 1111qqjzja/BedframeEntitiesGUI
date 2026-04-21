package lol.sylvie.bedframe.screen.api;

public enum SkipReason {
    NONE,
    CLIENT_ONLY_SCREEN,
    HANDLED_SCREEN,
    NO_SERVER_ENTRY,
    NO_STANDARD_COMPONENTS,
    UNSAFE_TO_REPLAY,
    UNSUPPORTED_COMPLEX_LAYOUT,
    UNKNOWN_WIDGET_TYPE
}
