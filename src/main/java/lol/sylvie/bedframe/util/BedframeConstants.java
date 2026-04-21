package lol.sylvie.bedframe.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.util.Identifier;
import org.geysermc.pack.converter.util.DefaultLogListener;
import org.geysermc.pack.converter.util.LogListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class BedframeConstants {
    // To save file space it's technically better to disable pretty printing
    public static final Gson GSON = FabricLoader.getInstance().isDevelopmentEnvironment() ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
    public static final String MOD_ID = "bedframe";
    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

    static {
        CONFIG_DIR.toFile().mkdir();
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final ModMetadata METADATA = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

    public static final Identifier GENERATED_IDENTIFIER = Identifier.ofVanilla("item/generated");
    public static final Identifier HANDHELD_IDENTIFIER = Identifier.ofVanilla("item/handheld");
}
