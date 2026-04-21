package lol.sylvie.bedframe.screen.api;

import java.util.List;

public record DropdownField(String id, String label, List<String> options, int defaultIndex) implements ScreenField {
}
