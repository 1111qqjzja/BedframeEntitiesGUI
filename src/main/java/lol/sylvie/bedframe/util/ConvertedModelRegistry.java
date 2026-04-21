package lol.sylvie.bedframe.util;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class ConvertedModelRegistry {
    private static final Map<String, LinkedHashSet<String>> MODEL_STRINGS_BY_BLOCK_PATH = new HashMap<>();
    private static Path loadedFrom = null;

    private ConvertedModelRegistry() {
    }

    public static void clear() {
        MODEL_STRINGS_BY_BLOCK_PATH.clear();
        loadedFrom = null;
    }

    public static void loadDirectory(Path directory) {
        clear();
        loadedFrom = directory;

        if (directory == null || Files.notExists(directory)) {
            LOGGER.warn("ConvertedModelRegistry: directory does not exist: {}", directory);
            return;
        }

        int filesRead = 0;
        int mappingsLoaded = 0;

        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> yamlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();

            for (Path file : yamlFiles) {
                int before = countMappings();
                loadSingleFile(file);
                int after = countMappings();

                filesRead++;
                mappingsLoaded += Math.max(0, after - before);
            }
        } catch (IOException e) {
            LOGGER.error("ConvertedModelRegistry: failed to scan directory {}", directory, e);
            return;
        }

        LOGGER.warn("ConvertedModelRegistry: loaded {} model mappings from {} yaml files in {}",
                countMappings(), filesRead, directory);
    }

    public static List<String> getModelCandidates(Identifier blockId) {
        if (blockId == null) {
            return List.of();
        }

        LinkedHashSet<String> out = new LinkedHashSet<>();

        LinkedHashSet<String> exact = MODEL_STRINGS_BY_BLOCK_PATH.get(blockId.toString());
        if (exact != null && !exact.isEmpty()) {
            out.addAll(exact);
        }

        LinkedHashSet<String> byPath = MODEL_STRINGS_BY_BLOCK_PATH.get(blockId.getPath());
        if (byPath != null && !byPath.isEmpty()) {
            out.addAll(byPath);
        }

        return List.copyOf(out);
    }

    public static int countMappings() {
        return MODEL_STRINGS_BY_BLOCK_PATH.values().stream().mapToInt(Set::size).sum();
    }

    public static Path loadedFrom() {
        return loadedFrom;
    }

    @SuppressWarnings("unchecked")
    private static void loadSingleFile(Path file) {
        Yaml yaml = new Yaml();

        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> root)) {
                LOGGER.warn("ConvertedModelRegistry: skipping {}, root is not a map", file);
                return;
            }

            for (Map.Entry<?, ?> entry : root.entrySet()) {
                if (!(entry.getKey() instanceof String blockPath)) {
                    continue;
                }

                if (!(entry.getValue() instanceof Map<?, ?> blockSection)) {
                    continue;
                }

                Object packObj = blockSection.get("Pack");
                if (!(packObj instanceof Map<?, ?> packSection)) {
                    continue;
                }

                Object modelObj = packSection.get("model");
                if (!(modelObj instanceof String modelString) || modelString.isBlank()) {
                    continue;
                }

                try {
                    MODEL_STRINGS_BY_BLOCK_PATH
                            .computeIfAbsent(blockPath, ignored -> new LinkedHashSet<>())
                            .add(modelString.trim());
                } catch (RuntimeException e) {
                    LOGGER.warn("ConvertedModelRegistry: invalid model '{}' for block '{}' in {}",
                            modelString, blockPath, file);
                }
            }
        } catch (Exception e) {
            LOGGER.error("ConvertedModelRegistry: failed to load {}", file, e);
        }
    }

    public static Set<Identifier> getAllBlockIds() {
        LinkedHashSet<Identifier> out = new LinkedHashSet<>();

        for (String rawKey : MODEL_STRINGS_BY_BLOCK_PATH.keySet()) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }

            String normalized = rawKey.trim().replace('\\', '/');

            try {
                if (normalized.contains(":")) {
                    out.add(Identifier.of(normalized));
                    continue;
                }

                List<Identifier> inferredFromRegistry = inferBlockIdsFromRegistry(normalized);
                if (!inferredFromRegistry.isEmpty()) {
                    out.addAll(inferredFromRegistry);
                    continue;
                }

                LinkedHashSet<String> values = MODEL_STRINGS_BY_BLOCK_PATH.get(rawKey);
                String inferredNs = inferNamespaceFromValues(values);

                if (inferredNs != null && !inferredNs.isBlank()) {
                    out.add(Identifier.of(inferredNs, normalized));
                } else {
                    out.add(Identifier.of("minecraft", normalized));
                }
            } catch (Exception e) {
                LOGGER.warn("ConvertedModelRegistry: failed to parse block id from key '{}'", rawKey, e);
            }
        }

        return out;
    }

    private static String inferNamespaceFromValues(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }

            String normalized = value.trim().replace('\\', '/');
            int colon = normalized.indexOf(':');
            if (colon > 0) {
                return normalized.substring(0, colon);
            }
        }

        return null;
    }

    private static String inferNamespaceFromModelStrings(Set<String> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }

        for (String model : models) {
            if (model == null || model.isBlank()) {
                continue;
            }

            String normalized = model.trim();
            int colon = normalized.indexOf(':');
            if (colon > 0) {
                return normalized.substring(0, colon);
            }
        }

        return null;
    }

    private static List<Identifier> inferBlockIdsFromRegistry(String rawKey) {
        LinkedHashSet<Identifier> out = new LinkedHashSet<>();

        if (rawKey == null || rawKey.isBlank()) {
            return List.of();
        }

        String normalized = rawKey.trim().replace('\\', '/');

        if (normalized.contains(":")) {
            try {
                out.add(Identifier.of(normalized));
            } catch (Exception ignored) {
            }
            return List.copyOf(out);
        }

        for (Identifier id : Registries.BLOCK.getIds()) {
            if (id != null && normalized.equals(id.getPath())) {
                out.add(id);
            }
        }

        return List.copyOf(out);
    }
}
