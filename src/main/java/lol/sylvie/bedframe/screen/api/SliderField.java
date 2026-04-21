package lol.sylvie.bedframe.screen.api;

public record SliderField(String id, String label, float min, float max, float step, float defaultValue) implements ScreenField {
}
