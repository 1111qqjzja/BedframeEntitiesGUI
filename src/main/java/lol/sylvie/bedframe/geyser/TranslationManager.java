package lol.sylvie.bedframe.geyser;

import lol.sylvie.bedframe.geyser.translator.BlockTranslator;
import lol.sylvie.bedframe.geyser.translator.DisplayEntityTranslator;
import lol.sylvie.bedframe.geyser.translator.ItemTranslator;
import lol.sylvie.bedframe.geyser.translator.ModEntityTranslator;
import lol.sylvie.bedframe.itemmodel.BlockItemModelBootstrap;
import lol.sylvie.bedframe.util.BedframeConstants;
import lol.sylvie.bedframe.util.ResourceHelper;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.pack.converter.util.DefaultLogListener;
import org.geysermc.pack.converter.util.VanillaPackProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public class TranslationManager implements EventRegistrar {
    private static final PackGenerator packGenerator = new PackGenerator();
    private boolean generatedResources = false;

    DisplayEntityTranslator displayEntityTranslator = new DisplayEntityTranslator();
    ModEntityTranslator modEntityTranslator = new ModEntityTranslator();

    public static boolean INCLUDE_OPTIONAL_TEXTURES_HACK = false;
    public static boolean INCLUDE_TEXTURE_HACK = false;

    public TranslationManager() {}

    public void ensureLateGenerated() {
        displayEntityTranslator.ensureGenerated();
    }

    public void registerHooks() {
        List<Translator> translators = List.of(
                new BlockTranslator(),
                new ItemTranslator(),
                displayEntityTranslator,
                modEntityTranslator
        );

        // Generate the fold
        Path packSourceDir;
        try {
            packSourceDir = Files.createTempDirectory("bedframe");
        } catch (IOException e) {
            BedframeConstants.LOGGER.error("Couldn't create resource pack temporary directory", e);
            return;
        }

        Path resourcePack = BedframeConstants.CONFIG_DIR.resolve("bedframe.zip");
        Path vanillaPath = BedframeConstants.CONFIG_DIR.resolve("vanilla.zip");

        // i'm sorry
        // TODO: don't do this. and also see if we can avoid double-downloading
        INCLUDE_TEXTURE_HACK = true;
        VanillaPackProvider.create(vanillaPath, new DefaultLogListener());
        INCLUDE_TEXTURE_HACK = false;

        try {
            ResourceHelper.VANILLA_PACK = new ZipFile(vanillaPath.toFile());
        } catch (IOException e) {
			BedframeConstants.LOGGER.error("Couldn't read vanilla resources", e);
		}

        Path geometryDir = BedframeConstants.CONFIG_DIR.resolve("geometry");
        BlockItemModelBootstrap.load(geometryDir);

		EventBus<EventRegistrar> eventBus = GeyserApi.api().eventBus();
        for (Translator translator : translators) {
            translator.register(eventBus, packSourceDir);
        }

        eventBus.subscribe(this, GeyserDefineResourcePacksEvent.class, event -> {
            try {
                // For some reason, GeyserDefineResourcePacksEvent is called once *before* blocks
                // FIXME: It's probably better to generate resources before the event is ever called
                if (!translators.stream().allMatch(Translator::hasProvidedResources)) return;

                // This event is called multiple times, but regenerating the resource pack each time is redundant
                if (!generatedResources) {
                    Files.deleteIfExists(resourcePack);
                    packGenerator.generatePack(packSourceDir, resourcePack.toFile(), translators);
                    generatedResources = true;
                }

                event.register(ResourcePack.create(PackCodec.path(resourcePack)));
                BedframeConstants.LOGGER.info("Registered resource pack");
            } catch (IOException e) {
                BedframeConstants.LOGGER.error("Couldn't generate resource pack", e);
            }
        });
    }
}
