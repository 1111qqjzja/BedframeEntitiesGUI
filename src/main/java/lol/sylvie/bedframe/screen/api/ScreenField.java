package lol.sylvie.bedframe.screen.api;

public sealed interface ScreenField permits LabelField, InputField, ToggleField, DropdownField, SliderField {
    String id();
    String label();
}
