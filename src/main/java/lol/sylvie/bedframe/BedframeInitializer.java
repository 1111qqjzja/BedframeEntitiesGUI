package lol.sylvie.bedframe;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import lol.sylvie.bedframe.display.AutoOversizedBootstrap;
import lol.sylvie.bedframe.display.FurnitureDisplayRuntime;
import lol.sylvie.bedframe.geyser.TranslationManager;
import lol.sylvie.bedframe.screen.bridge.GeyserSupport;
import lol.sylvie.bedframe.screen.waystones.WaystonesBridgeHooks;
import lol.sylvie.bedframe.util.ConvertedModelRegistry;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.Person;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;
import static lol.sylvie.bedframe.util.BedframeConstants.METADATA;

public class BedframeInitializer implements ModInitializer {
	private final TranslationManager translationManager = new TranslationManager();

	@Override
	public void onInitialize() {
		LOGGER.info("Bedframe - {}", METADATA.getVersion().getFriendlyString());
		LOGGER.info("Contributors: {}", String.join(", ", METADATA.getAuthors().stream().map(Person::getName).toList()));
		LOGGER.info("[{}] init, geyserPresent={}", "bedframe", GeyserSupport.isPresent());

		WaystonesBridgeHooks.bootstrap();

		Path convertedDir = FabricLoader.getInstance()
				.getConfigDir()
				.resolve("bedframe")
				.resolve("converted-models");

		Path javaResDir = FabricLoader.getInstance().getConfigDir().resolve("bedframe").resolve("polymer")
				.resolve("resource_pack.zip");

		FurnitureDisplayRuntime.init();

		ServerLifecycleEvents.SERVER_STARTING.register(ignored -> {
			ConvertedModelRegistry.loadDirectory(convertedDir);

			try {
                if (Files.exists(javaResDir)) {
					ResourceHelper.POLYMER_GENERATED_PACK = new ZipFile(javaResDir.toFile());
					LOGGER.warn("Loaded POLYMER_GENERATED_PACK from {}", javaResDir);
				} else {
					LOGGER.warn("POLYMER_GENERATED_PACK not found at {}", javaResDir);
				}
			} catch (IOException e) {
				LOGGER.error("Failed to open POLYMER_GENERATED_PACK", e);
			}

			Path generatedRoot = FabricLoader.getInstance()
					.getConfigDir()
					.resolve("bedframe")
					.resolve("generated");

			AutoOversizedBootstrap.load(
					generatedRoot,
					new java.util.ArrayList<>(ConvertedModelRegistry.getAllBlockIds())
			);

			translationManager.registerHooks();
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			translationManager.ensureLateGenerated();
		});

		PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(resourcePackBuilder -> {
			ResourceHelper.PACK_BUILDER = resourcePackBuilder;
		});
	}
}