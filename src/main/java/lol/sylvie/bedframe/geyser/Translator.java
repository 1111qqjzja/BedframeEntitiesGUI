package lol.sylvie.bedframe.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Pair;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import team.unnamed.creative.ResourcePack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiConsumer;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;

/**
 * Converts Java objects to Bedrock equivalents
 */
public abstract class Translator implements EventRegistrar {
    public boolean providedResources = false;
    private final ArrayList<Pair<String, String>> translations = new ArrayList<>();

    /**
     * Registers the Geyser events required of this translator
     * Translators are expected to write to the resource pack here as well.
     */
    public abstract void register(EventBus<EventRegistrar> eventBus, Path packRoot);

    public ArrayList<Pair<String, String>> getTranslations() {
        return translations;
    }

    protected void markResourcesProvided() {
        this.providedResources = true;
    }

    public boolean hasProvidedResources() {
        return providedResources;
    }

    // Helper methods
    protected void forEachKey(JsonObject root, BiConsumer<String, JsonElement> function) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            function.accept(entry.getKey(), entry.getValue());
        }
    }

    protected void addTranslationKey(String bedrockKey, String javaKey) {
        translations.add(new Pair<>(bedrockKey, javaKey));
    }

    protected void writeOrThrow(FileWriter writer, String content) {
        try {
            writer.write(content);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    protected static void writeJsonToFile(Object object, File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(object, writer);
        } catch (IOException e) { throw new RuntimeException(e); }
    }
}
