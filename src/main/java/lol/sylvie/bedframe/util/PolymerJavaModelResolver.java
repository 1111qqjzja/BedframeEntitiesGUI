package lol.sylvie.bedframe.util;

import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.minecraft.util.Identifier;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.serialize.minecraft.model.ModelSerializer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public final class PolymerJavaModelResolver {
    private PolymerJavaModelResolver() {
    }

    public record ResolvedJavaModel(
            Identifier blockId,
            Identifier modelId,
            String zipPath,
            JsonObject modelJson,
            Model model
    ) {
    }

    public static ResolvedJavaModel resolveBestJavaModel(Identifier blockId, List<String> rawModelHints) {
        ZipFile zip = ResourceHelper.POLYMER_GENERATED_PACK;
        if (zip == null) {
            LOGGER.warn("POLYMER_GENERATED_PACK is null; cannot resolve java model for {}", blockId);
            return null;
        }

        LinkedHashSet<Identifier> exactCandidates = new LinkedHashSet<>();
        if (rawModelHints != null) {
            for (String raw : rawModelHints) {
                exactCandidates.addAll(expandRawHintToModelIds(blockId, raw));
            }
        }

        ResolvedJavaModel exact = tryResolveFromCandidates(zip, blockId, rawModelHints, exactCandidates, "exact");
        if (exact != null) {
            return exact;
        }

        LOGGER.warn("No Java model candidate found in polymer zip for {} from raw hints {}", blockId, rawModelHints);
        return null;
    }

    private static ResolvedJavaModel tryResolveFromCandidates(
            ZipFile zip,
            Identifier blockId,
            List<String> rawModelHints,
            Collection<Identifier> candidates,
            String stage
    ) {
        for (Identifier candidateId : candidates) {
            JsonObject json = PolymerPackContentIndex.findModelJson(candidateId);
            if (json == null) {
                json = tryReadModelJsonFromZip(zip, candidateId);
            }

            if (json == null) {
                continue;
            }

            try {
                Model model = ModelSerializer.INSTANCE.deserializeFromJson(
                        json,
                        Key.key(candidateId.toString())
                );

                LOGGER.warn("Resolved Java model ({}) for {} -> {} from raw hints {}",
                        stage, blockId, candidateId, rawModelHints);

                return new ResolvedJavaModel(
                        blockId,
                        candidateId,
                        "indexed:" + candidateId,
                        json,
                        model
                );
            } catch (Exception e) {
                LOGGER.warn("Failed to deserialize Java model {} for block {} at stage {}",
                        candidateId, blockId, stage, e);
            }
        }

        return null;
    }

    private static List<Identifier> expandRawHintToModelIds(Identifier blockId, String raw) {
        LinkedHashSet<Identifier> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String s = raw.trim().replace('\\', '/');

        if (s.matches("^[a-z0-9_.-]+:[a-z0-9/._-]+$")) {
            try {
                out.add(Identifier.of(s));
            } catch (Exception ignored) {
            }

            int colon = s.indexOf(':');
            String ns = colon > 0 ? s.substring(0, colon) : blockId.getNamespace();
            String path = colon > 0 ? s.substring(colon + 1) : s;

            if (path.startsWith("item/")) {
                try {
                    out.add(Identifier.of(ns, path.substring("item/".length())));
                } catch (Exception ignored) {
                }
            }

            if (!path.startsWith("block/") && !path.startsWith("item/")) {
                try {
                    out.add(Identifier.of(ns, "block/" + path));
                } catch (Exception ignored) {
                }
                try {
                    out.add(Identifier.of(ns, "item/" + path));
                } catch (Exception ignored) {
                }
            }

            return List.copyOf(out);
        }

        try {
            out.add(Identifier.of(blockId.getNamespace(), s));
        } catch (Exception ignored) {
        }

        if (!s.startsWith("block/") && !s.startsWith("item/")) {
            try {
                out.add(Identifier.of(blockId.getNamespace(), "block/" + s));
            } catch (Exception ignored) {
            }
            try {
                out.add(Identifier.of(blockId.getNamespace(), "item/" + s));
            } catch (Exception ignored) {
            }
        }

        return List.copyOf(out);
    }

    private static JsonObject tryReadModelJsonFromZip(ZipFile zip, Identifier modelId) {
        List<String> candidates = new ArrayList<>();
        String ns = modelId.getNamespace();
        String path = modelId.getPath();

        candidates.add("assets/" + ns + "/models/" + path + ".json");

        if (!path.startsWith("block/") && !path.startsWith("item/")) {
            candidates.add("assets/" + ns + "/models/block/" + path + ".json");
            candidates.add("assets/" + ns + "/models/item/" + path + ".json");
        }

        if (path.startsWith("block/")) {
            candidates.add("assets/" + ns + "/models/" + path.substring("block/".length()) + ".json");
        } else if (path.startsWith("item/")) {
            candidates.add("assets/" + ns + "/models/" + path.substring("item/".length()) + ".json");
        }

        for (String zipPath : candidates) {
            try {
                ZipEntry entry = zip.getEntry(zipPath);
                if (entry == null) {
                    continue;
                }

                try (InputStream in = zip.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(in)) {
                    return GSON.fromJson(reader, JsonObject.class);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed reading model {} from zip path {}", modelId, zipPath, e);
            }
        }

        return null;
    }

    public static Path writeResolvedModelToTemp(Path root, ResolvedJavaModel resolved) {
        if (resolved == null || resolved.modelJson() == null) {
            return null;
        }

        try {
            Path dir = root.resolve(".generated-java-models");
            Files.createDirectories(dir);

            String safe = resolved.blockId().getNamespace()
                    + "__"
                    + resolved.blockId().getPath().replace('/', '_')
                    + ".json";

            Path out = dir.resolve(safe);
            Files.writeString(out, GSON.toJson(resolved.modelJson()));
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write generated java model temp file for " + resolved.blockId(), e);
        }
    }

    private static JsonObject readJsonFromZip(ZipFile zip, String zipPath) {
        try {
            ZipEntry entry = zip.getEntry(zipPath);
            if (entry == null) {
                return null;
            }

            try (InputStream in = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(in)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read JSON from polymer zip entry {}", zipPath, e);
            return null;
        }
    }

    private static int score(
            Identifier blockId,
            List<String> rawHints,
            String namespace,
            String relativeModelPath,
            String fullZipPath
    ) {
        String blockNs = normalize(blockId.getNamespace());
        String blockPath = normalize(blockId.getPath());
        String modelNs = normalize(namespace);
        String modelPath = normalize(relativeModelPath);
        String fileName = lastSegment(modelPath);

        int score = 100000;

        if (blockNs.equals(modelNs)) {
            score -= 30000;
        }

        if (modelPath.equals(blockPath)) {
            score -= 50000;
        }
        if (modelPath.equals("block/" + blockPath)) {
            score -= 48000;
        }
        if (modelPath.equals("item/" + blockPath)) {
            score -= 41000;
        }

        if (fileName.equals(blockPath)) {
            score -= 26000;
        }
        if (fileName.equals(blockPath.replace('/', '_'))) {
            score -= 20000;
        }

        if (modelPath.contains(blockPath)) {
            score -= 12000;
        }

        if (fullZipPath.contains("/furniture/")) {
            score -= 2500;
        }
        if (fullZipPath.contains("/block/")) {
            score -= 1500;
        }
        if (fullZipPath.contains("/item/")) {
            score -= 1000;
        }

        if (rawHints != null) {
            for (String raw : rawHints) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }

                String rawNorm = normalize(raw);
                String rawLast = lastSegment(rawNorm);

                if (modelPath.equals(rawNorm)) {
                    score -= 35000;
                }
                if (modelPath.endsWith(rawNorm)) {
                    score -= 22000;
                }
                if (fileName.equals(rawLast)) {
                    score -= 15000;
                }
                if (modelPath.contains(rawLast)) {
                    score -= 9000;
                }
            }
        }

        Set<String> blockTokens = tokenize(blockPath);
        Set<String> modelTokens = tokenize(modelPath);

        int overlap = 0;
        for (String token : blockTokens) {
            if (modelTokens.contains(token)) {
                overlap++;
            }
        }
        score -= overlap * 1800;

        boolean related = modelPath.contains(blockPath) || overlap > 0;
        if (!related && rawHints != null) {
            for (String raw : rawHints) {
                if (raw == null || raw.isBlank()) continue;
                String rawLast = lastSegment(normalize(raw));
                if (modelPath.contains(rawLast) || fileName.equals(rawLast)) {
                    related = true;
                    break;
                }
            }
        }

        return related ? score : -1;
    }

    private static String normalize(String in) {
        return in == null ? "" : in.toLowerCase(Locale.ROOT).replace('\\', '/').replace(".json", "");
    }

    private static String lastSegment(String in) {
        if (in == null || in.isBlank()) {
            return "";
        }
        int slash = in.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < in.length()) {
            return in.substring(slash + 1);
        }
        int colon = in.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < in.length()) {
            return in.substring(colon + 1);
        }
        return in;
    }

    private static Set<String> tokenize(String in) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String p1 : normalize(in).split("/")) {
            for (String p2 : p1.split("_")) {
                String token = p2.trim();
                if (!token.isEmpty()) {
                    out.add(token);
                }
            }
        }
        return out;
    }

    private record Candidate(
            Identifier modelId,
            String zipPath,
            int score
    ) {
    }
}
