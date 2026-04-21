package lol.sylvie.bedframe.geyser.translator;

import lol.sylvie.bedframe.geyser.Translator;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.util.Identifier;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public class ModEntityTranslator extends Translator {
    private static final Map<Identifier, AutoEntityEntry> AUTO_ENTITIES = new LinkedHashMap<>();

    private static boolean initialized = false;

    @Override
    public void register(EventBus<EventRegistrar> eventBus, Path packRoot) {
        this.providedResources = true;

        eventBus.subscribe(this, GeyserDefineEntitiesEvent.class, this::onDefineEntities);
        eventBus.subscribe(this, ServerSpawnEntityEvent.class, this::onSpawnEntities);

        LOGGER.warn("Registered ModEntityTranslator as event-only translator (providedResources=true)");
    }

    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        ensureAutoEntitiesLoaded();

        int success = 0;
        int skipped = 0;

        for (AutoEntityEntry entry : AUTO_ENTITIES.values()) {
            try {
                event.register(entry.definition());

                event.registerEntityType(builder -> builder
                        .type(entry.identifier())
                        .javaId(entry.javaId())
                        .width(entry.width())
                        .height(entry.height())
                        .definition(entry.definition())
                );

                success++;
            } catch (Throwable t) {
                skipped++;
                LOGGER.warn("Skipped auto entity registration for {} (javaId={})",
                        entry.identifier(), entry.javaId(), t);
            }
        }

        LOGGER.warn("ModEntityTranslator registered {} auto entities, skipped {}", success, skipped);
    }

    @Subscribe
    public void onSpawnEntities(ServerSpawnEntityEvent event) {
        Identifier id;
        try {
            id = event.entityType().identifier();
        } catch (Throwable t) {
            return;
        }

        AutoEntityEntry entry = AUTO_ENTITIES.get(id);
        if (entry != null) {
            event.definition(entry.definition());
        }
    }

    private void ensureAutoEntitiesLoaded() {
        if (initialized) {
            return;
        }
        initialized = true;

        AUTO_ENTITIES.clear();

        int count = 0;

        for (EntityType<?> entityType : Registries.ENTITY_TYPE) {
            try {
                net.minecraft.util.Identifier key = Registries.ENTITY_TYPE.getId(entityType);
                if (key == null) {
                    continue;
                }

                String namespace = key.getNamespace();
                String path = key.getPath();

                if ("minecraft".equals(namespace)) {
                    continue;
                }

                int javaId = Registries.ENTITY_TYPE.getRawId(entityType);
                if (javaId < 0) {
                    continue;
                }

                Identifier geyserId = Identifier.of(namespace, path);
                CustomEntityDefinition definition = CustomEntityDefinition.of(namespace + ":" + path);

                float width;
                float height;

                if (path.endsWith("_boat") || path.endsWith("_chest_boat")) {
                    width = 1.375f;
                    height = 0.5625f;
                } else {
                    width = safeWidth(entityType);
                    height = safeHeight(entityType);
                }

                AutoEntityEntry entry = new AutoEntityEntry(
                        geyserId,
                        definition,
                        javaId,
                        width,
                        height
                );

                AUTO_ENTITIES.put(geyserId, entry);
                count++;
            } catch (Throwable t) {
                LOGGER.warn("Failed to auto-scan mod entity type {}", entityType, t);
            }
        }

        LOGGER.warn("ModEntityTranslator loaded {} non-minecraft entity mappings", count);
    }

    private float safeWidth(EntityType<?> entityType) {
        try {
            return entityType.getWidth();
        } catch (Throwable ignored) {
        }

        try {
            return entityType.getDimensions().width();
        } catch (Throwable ignored) {
        }

        return 1.0f;
    }

    private float safeHeight(EntityType<?> entityType) {
        try {
            return entityType.getHeight();
        } catch (Throwable ignored) {
        }

        try {
            return entityType.getDimensions().height();
        } catch (Throwable ignored) {
        }

        return 1.0f;
    }

    private record AutoEntityEntry(
            Identifier identifier,
            CustomEntityDefinition definition,
            int javaId,
            float width,
            float height
    ) {
    }
}
