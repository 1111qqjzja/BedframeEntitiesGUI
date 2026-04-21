package lol.sylvie.bedframe.view;

public interface JavaViewEntityMarker extends BedframeViewEntity {
    @Override
    default boolean bedframe$isJavaView() {
        return true;
    }

    @Override
    default boolean bedframe$isBedrockView() {
        return false;
    }
}
