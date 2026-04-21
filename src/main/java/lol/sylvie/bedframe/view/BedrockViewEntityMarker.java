package lol.sylvie.bedframe.view;

public interface BedrockViewEntityMarker extends BedframeViewEntity {
    @Override
    default boolean bedframe$isJavaView() {
        return false;
    }

    @Override
    default boolean bedframe$isBedrockView() {
        return true;
    }
}
