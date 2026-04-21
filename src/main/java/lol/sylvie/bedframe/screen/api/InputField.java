package lol.sylvie.bedframe.screen.api;

import org.jetbrains.annotations.Nullable;

public record InputField(String id, String label, String placeholder, @Nullable String defaultValue) implements ScreenField {
}
