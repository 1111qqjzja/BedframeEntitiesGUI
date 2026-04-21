package lol.sylvie.bedframe.screen.api;

import org.jetbrains.annotations.Nullable;

public record ScreenOption(
        String id,
        String text,
        @Nullable String imageType,
        @Nullable String imageData
) {
    public static ScreenOption of(String id, String text) {
        return new ScreenOption(id, text, null, null);
    }
}
