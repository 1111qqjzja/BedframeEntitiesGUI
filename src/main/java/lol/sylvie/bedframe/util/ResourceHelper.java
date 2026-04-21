package lol.sylvie.bedframe.util;

import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Set;

public class ResourceHelper {
    public static ResourcePackBuilder PACK_BUILDER = null;
    public static ZipFile VANILLA_PACK = null;
    public static ZipFile POLYMER_GENERATED_PACK = null;

    public static InputStream getResource(String path) {
        if (PACK_BUILDER != null) {
            byte[] data = PACK_BUILDER.getData(path);
            if (data != null) {
                return new ByteArrayInputStream(data);
            }
        }

        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (stream != null) {
            return stream;
        }

        if (POLYMER_GENERATED_PACK != null) {
            try {
                ZipEntry entry = POLYMER_GENERATED_PACK.getEntry(path);
                if (entry != null) {
                    return POLYMER_GENERATED_PACK.getInputStream(entry);
                }
            } catch (IOException e) {
                BedframeConstants.LOGGER.warn("Couldn't open resource {} from POLYMER_GENERATED_PACK", path, e);
            }
        }

        if (VANILLA_PACK == null) {
            return null;
        }

        try {
            ZipEntry entry = VANILLA_PACK.getEntry(path);
            if (entry == null) {
                return null;
            }
            return VANILLA_PACK.getInputStream(entry);
        } catch (IOException e) {
            BedframeConstants.LOGGER.warn("Couldn't open resource {}", path, e);
            return null;
        }
    }

    public static String getResourcePath(String namespace, String path) {
        return "assets/" + namespace + "/" + path;
    }

    public static InputStream getResource(String namespace, String path) {
        return getResource(getResourcePath(namespace, path));
    }

    public static boolean hasResource(String namespace, String path) {
        try (InputStream ignored = getResource(namespace, path)) {
            return ignored != null;
        } catch (IOException e) {
            return false;
        }
    }

    public static void copyResource(String namespace, String path, Path destination) {
        try (InputStream stream = getResource(namespace, path)) {
            if (stream == null) {
                throw new RuntimeException("Couldn't copy resource " + Identifier.of(namespace, path) + " because it does not exist");
            }

            if (Files.notExists(destination)) {
                destination.toFile().getParentFile().mkdirs();
                Files.copy(stream, destination);
            }
        } catch (IOException e) {
            throw new RuntimeException("Couldn't copy resource " + Identifier.of(namespace, path), e);
        }
    }

    public static JsonObject tryReadJsonResource(String namespace, String path) {
        try (InputStream stream = getResource(namespace, path)) {
            if (stream == null) {
                return null;
            }
            return BedframeConstants.GSON.fromJson(new InputStreamReader(stream), JsonObject.class);
        } catch (IOException e) {
            BedframeConstants.LOGGER.warn("Couldn't load resource {}", Identifier.of(namespace, path), e);
            return null;
        } catch (RuntimeException e) {
            BedframeConstants.LOGGER.warn("Couldn't parse JSON resource {}", Identifier.of(namespace, path), e);
            return null;
        }
    }

    public static JsonObject readJsonResource(String namespace, String path) {
        JsonObject object = tryReadJsonResource(namespace, path);
        if (object == null) {
            throw new RuntimeException("Couldn't load resource " + Identifier.of(namespace, path));
        }
        return object;
    }

    public static String javaToBedrockTexture(String javaPath) {
        return javaPath.replaceFirst("block", "blocks").replaceFirst("item", "items");
    }

    public static Set<String> listPackResourcePaths() {
        if (POLYMER_GENERATED_PACK != null) {
            Set<String> paths = PolymerPackContentIndex.allPaths();
            if (!paths.isEmpty()) {
                BedframeConstants.LOGGER.warn("PolymerPackContentIndex returned {} zip paths", paths.size());
                return paths;
            }
        }

        Set<String> paths = PathRegistry.snapshot();
        if (paths.isEmpty()) {
            BedframeConstants.LOGGER.warn("PathRegistry is empty and PolymerPackContentIndex had no paths");
        } else {
            BedframeConstants.LOGGER.warn("Falling back to PathRegistry with {} recorded pack paths", paths.size());
        }
        return paths;
    }

    private static void collectPathsFromObject(Object current, Set<String> out, ArrayDeque<Object> queue) {
        Class<?> type = current.getClass();

        if (current instanceof CharSequence seq) {
            maybeAddPath(seq.toString(), out);
            return;
        }

        if (current instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();

                if (key instanceof CharSequence seq) {
                    maybeAddPath(seq.toString(), out);
                } else if (key != null) {
                    maybeAddPath(key.toString(), out);
                }

                if (value != null && !isLeafValue(value)) {
                    queue.add(value);
                }
            }
            return;
        }

        if (current instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof CharSequence seq) {
                    maybeAddPath(seq.toString(), out);
                } else if (value != null && !isLeafValue(value)) {
                    queue.add(value);
                }
            }
            return;
        }

        if (type.isArray()) {
            int length = Array.getLength(current);
            for (int i = 0; i < length; i++) {
                Object value = Array.get(current, i);
                if (value instanceof CharSequence seq) {
                    maybeAddPath(seq.toString(), out);
                } else if (value != null && !isLeafValue(value)) {
                    queue.add(value);
                }
            }
            return;
        }

        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(current);
                    if (value == null || isLeafValue(value)) {
                        continue;
                    }

                    if (value instanceof CharSequence seq) {
                        maybeAddPath(seq.toString(), out);
                    } else if (value instanceof Collection<?> col) {
                        queue.add(col);
                    } else if (value instanceof Map<?, ?> map) {
                        queue.add(map);
                    } else if (value.getClass().isArray()) {
                        queue.add(value);
                    } else {
                        queue.add(value);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean isLeafValue(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof byte[]
                || value.getClass().isEnum();
    }

    private static void maybeAddPath(String raw, Set<String> out) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        String path = raw.replace('\\', '/');

        if (path.startsWith("assets/") && path.endsWith(".json")) {
            out.add(path);
            return;
        }

        if ((path.startsWith("models/") || path.startsWith("blockstates/") || path.startsWith("items/"))
                && path.endsWith(".json")) {
            out.add(path);
        }
    }

    public static void setPackBuilder(ResourcePackBuilder builder) {
        if (builder == null) {
            PACK_BUILDER = null;
            return;
        }

        if (builder instanceof TrackingResourcePackBuilder tracking) {
            PACK_BUILDER = tracking;
        } else {
            PACK_BUILDER = new TrackingResourcePackBuilder(builder);
        }
    }

    public static ResourcePackBuilder getPackBuilder() {
        return PACK_BUILDER;
    }
}