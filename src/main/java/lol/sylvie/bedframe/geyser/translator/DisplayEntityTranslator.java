package lol.sylvie.bedframe.geyser.translator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lol.sylvie.bedframe.display.*;
import lol.sylvie.bedframe.geyser.AtlasBakedEntityBuilder;
import lol.sylvie.bedframe.geyser.Translator;
import lol.sylvie.bedframe.util.ResourceHelper;
import net.minecraft.util.Identifier;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static lol.sylvie.bedframe.util.BedframeConstants.GSON;
import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

public class DisplayEntityTranslator extends Translator {
    private final Map<Identifier, CustomEntityDefinition> definitionsByBedrockId = new HashMap<>();
    private Path packRoot;

    @Override
    public void register(EventBus<EventRegistrar> eventBus, Path packRoot) {
        this.packRoot = packRoot;

        eventBus.subscribe(this, GeyserDefineEntitiesEvent.class, this::onDefineEntities);
        eventBus.subscribe(this, ServerSpawnEntityEvent.class, this::onSpawnEntities);
    }

    private record ZipModelCandidate(
            String zipPath,
            String namespace,
            String relativeModelPath,
            int score
    ) {
    }

    private record GeneratedGeometry(
            String geometryIdentifier,
            JsonObject geometryRoot,
            Path geometryPath,
            Path javaModelPath
    ) {
    }

    private void generateEntityResources(Path packRoot) throws IOException {
        Path entityDir = packRoot.resolve("entity");
        Path controllerDir = packRoot.resolve("render_controllers");
        Path modelEntityDir = packRoot.resolve("models").resolve("entity");
        Path textureEntityDir = packRoot.resolve("textures").resolve("entity");
        Path generatedRoot = packRoot.resolve(".display-generated");

        Files.createDirectories(entityDir);
        Files.createDirectories(controllerDir);
        Files.createDirectories(modelEntityDir);
        Files.createDirectories(textureEntityDir);
        Files.createDirectories(generatedRoot);

        writeMaterialDefinitions(packRoot);

        for (OversizedDisplaySpec spec : AutoOversizedDisplayRegistry.all()) {
            try {
                GeneratedGeometry generated = createGeneratedGeometry(spec, generatedRoot);
                if (generated == null) {
                    LOGGER.warn("Skipping {} because generated geometry could not be created", spec.anchorBlockId());
                    continue;
                }

                AutoRenderMetadataResolver.RenderMetadata renderMeta = AutoRenderMetadataResolver.resolve(
                        spec.anchorBlockId(),
                        generated.geometryIdentifier(),
                        generated.geometryPath(),
                        generated.javaModelPath(),
                        spec.defaultTexturePath()
                );

                AtlasBakedEntityBuilder.BakedResult baked = AtlasBakedEntityBuilder.bake(
                        spec,
                        generated.geometryRoot(),
                        renderMeta
                );

                if (baked == null) {
                    LOGGER.warn("Atlas bake failed for {}, skipping entity output", spec.anchorBlockId());
                    continue;
                }

                writeSingleAtlasClientEntityJson(entityDir, spec, baked);
                writeSingleAtlasRenderControllerJson(controllerDir, spec, baked);
                writeBakedGeometry(modelEntityDir, spec, baked);
                writeBakedAtlas(textureEntityDir, spec, baked);
            } catch (Exception e) {
                LOGGER.warn("Failed to generate display entity resources for {}", spec.anchorBlockId(), e);
            }
        }
    }

    private GeneratedGeometry createGeneratedGeometry(OversizedDisplaySpec spec, Path generatedRoot) {
        List<String> rawModels = lol.sylvie.bedframe.util.ConvertedModelRegistry.getModelCandidates(spec.anchorBlockId());
        if (rawModels.isEmpty()) {
            LOGGER.warn("No converted-model candidates found for {}", spec.anchorBlockId());
            return null;
        }

        lol.sylvie.bedframe.util.PolymerJavaModelResolver.ResolvedJavaModel resolved =
                lol.sylvie.bedframe.util.PolymerJavaModelResolver.resolveBestJavaModel(spec.anchorBlockId(), rawModels);

        if (resolved == null || resolved.model() == null) {
            LOGGER.warn("Could not resolve Java model from polymer zip for {}", spec.anchorBlockId());
            return null;
        }

        net.minecraft.util.Pair<String, org.geysermc.pack.bedrock.resource.models.entity.ModelEntity> converted =
                lol.sylvie.bedframe.geyser.model.JavaGeometryConverter.convert(resolved.model());

        if (converted == null || converted.getRight() == null) {
            LOGGER.warn("JavaGeometryConverter returned null for {}", spec.anchorBlockId());
            return null;
        }

        JsonObject geometryRoot = GSON.toJsonTree(converted.getRight()).getAsJsonObject();
        Path geometryPath = writeGeneratedGeometryTemp(generatedRoot, spec, geometryRoot);
        Path javaModelPath = lol.sylvie.bedframe.util.PolymerJavaModelResolver.writeResolvedModelToTemp(generatedRoot, resolved);

        return new GeneratedGeometry(
                converted.getLeft(),
                geometryRoot,
                geometryPath,
                javaModelPath
        );
    }

    private Path writeGeneratedGeometryTemp(Path generatedRoot, OversizedDisplaySpec spec, JsonObject geometryRoot) {
        try {
            Path dir = generatedRoot.resolve("geometry");
            Files.createDirectories(dir);

            Path out = dir.resolve(spec.anchorBlockId().getNamespace() + "__"
                    + spec.anchorBlockId().getPath().replace('/', '_') + ".geo.json");

            Files.writeString(out, GSON.toJson(geometryRoot));
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write generated geometry temp file for " + spec.anchorBlockId(), e);
        }
    }

    @Subscribe
    public void onDefineEntities(GeyserDefineEntitiesEvent event) {
        for (OversizedDisplaySpec spec : AutoOversizedDisplayRegistry.all()) {
            CustomEntityDefinition definition = CustomEntityDefinition.of(spec.bedrockEntityId().toString());
            definitionsByBedrockId.put(spec.bedrockEntityId(), definition);

            event.register(definition);

            LOGGER.warn("Registered Bedrock furniture definition {}", spec.bedrockEntityId());
        }
    }

    @Subscribe
    public void onSpawnEntities(ServerSpawnEntityEvent event) {

        FurnitureRuntimeRegistry.FurnitureInstance instance = FurnitureRuntimeRegistry.getByCarrierUuid(event.uuid());
        if (instance == null) {
            return;
        }

        OversizedDisplaySpec spec = instance.spec();
        if (spec == null) {
            return;
        }

        CustomEntityDefinition def = definitionsByBedrockId.get(spec.bedrockEntityId());
        if (def == null) {
            return;
        }

        event.definition(def);
    }

    private void writeClientEntityJson(
            Path entityDir,
            OversizedDisplaySpec spec,
            AutoRenderMetadataResolver.RenderMetadata renderMeta
    ) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");

        JsonObject clientEntity = new JsonObject();
        JsonObject description = new JsonObject();

        description.addProperty("identifier", spec.bedrockEntityId().toString());
        description.addProperty("min_engine_version", "1.21.0");

        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        materials.addProperty("anim", "entity_alphatest_anim_change_color_one_sided");
        description.add("materials", materials);

        JsonObject textures = new JsonObject();

        List<String> keys = new ArrayList<>(renderMeta.materialInstanceTextures().keySet());
        keys.sort(DisplayEntityTranslator::safeIntCompare);

        for (String key : keys) {
            String raw = renderMeta.materialInstanceTextures().get(key);
            String bedrockRef = toBedrockTextureReference(raw);

            if (bedrockRef == null || bedrockRef.isBlank()) {
                LOGGER.warn("Skipping empty bedrock texture ref for {} key={} raw={}",
                        spec.anchorBlockId(), key, raw);
                continue;
            }

            textures.addProperty("tex_" + sanitizeKey(key), bedrockRef);
        }

        description.add("textures", textures);

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", spec.geometryIdentifier());
        description.add("geometry", geometry);

        JsonArray renderControllers = new JsonArray();
        for (String key : keys) {
            renderControllers.add(controllerName(spec, key));
        }
        if (renderControllers.isEmpty()) {
            renderControllers.add("controller.render.default");
        }
        description.add("render_controllers", renderControllers);

        clientEntity.add("description", description);
        root.add("minecraft:client_entity", clientEntity);

        Files.writeString(
                entityDir.resolve(spec.bedrockEntityId().getPath() + ".entity.json"),
                GSON.toJson(root)
        );
    }

    private void writeRenderControllerJson(Path controllerDir, OversizedDisplaySpec spec, AutoRenderMetadataResolver.RenderMetadata renderMeta) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");

        JsonObject renderControllers = new JsonObject();
        root.add("render_controllers", renderControllers);

        List<String> keys = new ArrayList<>(renderMeta.materialInstanceTextures().keySet());
        keys.sort(DisplayEntityTranslator::safeIntCompare);

        for (String key : keys) {
            JsonObject controller = new JsonObject();
            renderControllers.add(controllerName(spec, key), controller);

            controller.addProperty("geometry", "Geometry.default");

            JsonArray materials = new JsonArray();
            JsonObject materialObj = new JsonObject();
            OversizedDisplaySpec.AnimTextureOptions anim = renderMeta.animTextures().get(key);
            if (anim != null && anim.frames() > 1) {
                materialObj.addProperty("*", "Material.anim");
            } else {
                materialObj.addProperty("*", "Material.default");
            }
            materials.add(materialObj);
            controller.add("materials", materials);

            JsonArray textures = new JsonArray();
            textures.add("Texture.tex_" + sanitizeKey(key));
            controller.add("textures", textures);
            if (anim != null && anim.frames() > 1) {
                JsonObject uvAnim = new JsonObject();

                JsonArray offset = new JsonArray();
                offset.add(0.0);
                double fps = anim.fps() > 60 ? 7.0 : anim.fps();
                offset.add("math.mod(math.floor(q.life_time * " + fps + ")," + anim.frames() + ") / " + anim.frames());
                uvAnim.add("offset", offset);

                JsonArray scale = new JsonArray();
                scale.add(1.0);
                scale.add("1 / " + anim.frames());
                uvAnim.add("scale", scale);

                controller.add("uv_anim", uvAnim);
            }
        }

        Files.writeString(
                controllerDir.resolve(spec.bedrockEntityId().getPath() + ".render_controller.json"),
                GSON.toJson(root)
        );
    }

    private Path findJavaModelPath(OversizedDisplaySpec spec) {
        String blockPath = spec.anchorBlockId().getPath();
        String geometryToken = lastGeometryToken(spec.geometryIdentifier());

        ZipFile zip = ResourceHelper.POLYMER_GENERATED_PACK;
        if (zip == null) {
            LOGGER.warn("POLYMER_GENERATED_PACK is null, can't resolve Java model from polymer zip for {}",
                    spec.anchorBlockId());

            Path fallback = spec.geoPath().getParent().getParent().resolve("java-models").resolve(blockPath + ".json");
            return fallback;
        }

        List<ZipModelCandidate> matches = new ArrayList<>();

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry == null || entry.isDirectory()) {
                continue;
            }

            String rawName = entry.getName();
            if (rawName == null || rawName.isBlank()) {
                continue;
            }

            String zipPath = rawName.replace('\\', '/');

            if (!zipPath.startsWith("assets/") || !zipPath.endsWith(".json")) {
                continue;
            }

            String[] split = zipPath.split("/", 4);
            if (split.length < 4) {
                continue;
            }

            String namespace = split[1];
            String section = split[2];
            String relative = split[3];

            if (!"models".equals(section)) {
                continue;
            }

            String relativeNoExt = relative.substring(0, relative.length() - ".json".length());

            int score = scoreZipModelCandidate(
                    spec.anchorBlockId(),
                    blockPath,
                    geometryToken,
                    namespace,
                    relativeNoExt,
                    zipPath
            );

            if (score >= 0) {
                matches.add(new ZipModelCandidate(zipPath, namespace, relativeNoExt, score));
            }
        }

        if (matches.isEmpty()) {
            LOGGER.warn("No polymer zip model json found for {} (geometry={})",
                    spec.anchorBlockId(), spec.geometryIdentifier());

            return extractZipModelToTemp(spec, null);
        }

        matches.sort(Comparator.comparingInt(ZipModelCandidate::score));

        ZipModelCandidate chosen = matches.get(0);

        LOGGER.warn("Resolved Java model from polymer zip for {} -> {} (score={})",
                spec.anchorBlockId(), chosen.zipPath(), chosen.score());

        return extractZipModelToTemp(spec, chosen);
    }

    private int scoreZipModelCandidate(
            Identifier blockId,
            String blockPath,
            String geometryToken,
            String namespace,
            String relativeModelPath,
            String zipPath
    ) {
        String blockNs = normalizeToken(blockId.getNamespace());
        String block = normalizeToken(blockPath);
        String geo = normalizeToken(geometryToken);
        String modelNs = normalizeToken(namespace);
        String modelPath = normalizeToken(relativeModelPath);
        String full = normalizeToken(zipPath);

        int score = 100000;

        if (blockNs.equals(modelNs)) {
            score -= 30000;
        }

        if (modelPath.equals(block)) {
            score -= 50000;
        }
        if (modelPath.equals("block/" + block)) {
            score -= 49000;
        }
        if (modelPath.equals("item/" + block)) {
            score -= 42000;
        }

        if (modelPath.equals(geo)) {
            score -= 47000;
        }
        if (modelPath.equals("block/" + geo)) {
            score -= 46000;
        }
        if (modelPath.equals("item/" + geo)) {
            score -= 39000;
        }

        String fileNameOnly = modelPath;
        int slash = fileNameOnly.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < fileNameOnly.length()) {
            fileNameOnly = fileNameOnly.substring(slash + 1);
        }

        if (fileNameOnly.equals(block)) {
            score -= 28000;
        }
        if (fileNameOnly.equals(geo)) {
            score -= 26000;
        }

        if (fileNameOnly.equals(block.replace('/', '_'))) {
            score -= 22000;
        }
        if (fileNameOnly.equals(geo.replace('/', '_'))) {
            score -= 21000;
        }

        if (modelPath.contains(block)) {
            score -= 12000;
        }
        if (modelPath.contains(geo)) {
            score -= 10000;
        }

        if (full.contains("/block/")) {
            score -= 3000;
        }
        if (full.contains("/item/")) {
            score -= 1800;
        }
        if (full.contains("/furniture/")) {
            score -= 2500;
        }
        if (full.contains("/custom/")) {
            score -= 2200;
        }

        Set<String> blockTokens = tokenizeForScore(block);
        Set<String> geoTokens = tokenizeForScore(geo);
        Set<String> modelTokens = tokenizeForScore(modelPath);

        int overlap = 0;
        for (String token : blockTokens) {
            if (modelTokens.contains(token)) {
                overlap++;
            }
        }
        for (String token : geoTokens) {
            if (modelTokens.contains(token)) {
                overlap++;
            }
        }
        score -= overlap * 1800;

        boolean related =
                modelPath.contains(block)
                        || modelPath.contains(geo)
                        || overlap > 0
                        || fileNameOnly.equals(block)
                        || fileNameOnly.equals(geo);

        if (!related) {
            return -1;
        }

        return score;
    }

    private Path extractZipModelToTemp(OversizedDisplaySpec spec, ZipModelCandidate candidate) {
        Path tempRoot = this.packRoot.resolve(".polymer-java-model-cache");
        try {
            Files.createDirectories(tempRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create polymer java model cache dir: " + tempRoot, e);
        }

        String safeName = spec.anchorBlockId().getNamespace()
                + "__"
                + spec.anchorBlockId().getPath().replace('/', '_')
                + ".json";

        Path output = tempRoot.resolve(safeName);

        if (candidate == null) {
            LOGGER.warn("No zip model candidate available for {}, returning unresolved temp path {}",
                    spec.anchorBlockId(), output);
            return output;
        }

        ZipFile zip = ResourceHelper.POLYMER_GENERATED_PACK;
        if (zip == null) {
            LOGGER.warn("POLYMER_GENERATED_PACK became null while extracting {}", candidate.zipPath());
            return output;
        }

        try {
            ZipEntry entry = zip.getEntry(candidate.zipPath());
            if (entry == null) {
                LOGGER.warn("Zip entry not found for {}", candidate.zipPath());
                return output;
            }

            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.warn("Extracted polymer zip model for {} -> {}",
                    spec.anchorBlockId(), output);

            return output;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract polymer model " + candidate.zipPath() + " for " + spec.anchorBlockId(), e);
        }
    }

    private String normalizeToken(String input) {
        if (input == null) {
            return "";
        }

        return input.toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replace(".json", "");
    }

    private Set<String> tokenizeForScore(String input) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (input == null || input.isBlank()) {
            return out;
        }

        String normalized = normalizeToken(input);

        for (String slashPart : normalized.split("/")) {
            for (String underscorePart : slashPart.split("_")) {
                String token = underscorePart.trim();
                if (!token.isEmpty()) {
                    out.add(token);
                }
            }
        }

        return out;
    }

    private String lastGeometryToken(String geometryIdentifier) {
        int dot = geometryIdentifier.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < geometryIdentifier.length()) {
            return geometryIdentifier.substring(dot + 1);
        }
        return geometryIdentifier;
    }

    private String controllerName(OversizedDisplaySpec spec, String key) {
        return "controller.render." + spec.bedrockEntityId().getNamespace()
                + "." + spec.bedrockEntityId().getPath()
                + "." + sanitizeKey(key);
    }

    private String normalizeTexturePath(String path) {
        String out = path;
        if (out.endsWith(".png")) {
            out = out.substring(0, out.length() - 4);
        }
        return out;
    }

    private String sanitizeKey(String key) {
        return key.replace(':', '_').replace('/', '_').replace('.', '_');
    }

    private static int safeIntCompare(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (Exception ignored) {
            return a.compareTo(b);
        }
    }

    private void copyEntityGeometry(OversizedDisplaySpec spec, Path modelEntityDir) throws IOException {
        if (spec.geoPath() == null || !Files.exists(spec.geoPath())) {
            LOGGER.warn("Missing geometry source for {} at {}", spec.anchorBlockId(), spec.geoPath());
            return;
        }

        String outputName = spec.bedrockEntityId().getPath() + ".geo.json";
        Files.copy(spec.geoPath(), modelEntityDir.resolve(outputName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyEntityTextures(
            OversizedDisplaySpec spec,
            AutoRenderMetadataResolver.RenderMetadata renderMeta,
            Path textureEntityDir
    ) throws IOException {
        LinkedHashMap<String, String> toCopy = new LinkedHashMap<>(renderMeta.materialInstanceTextures());

        for (Map.Entry<String, String> entry : toCopy.entrySet()) {
            String logicalKey = entry.getKey();
            String sourceRaw = entry.getValue();

            if (sourceRaw == null || sourceRaw.isBlank()) {
                continue;
            }

            TexturePathParts parts = parseSourceTexture(sourceRaw);
            if (parts == null) {
                LOGGER.warn("Skipping empty parsed texture path for {} key={} raw={}",
                        spec.anchorBlockId(), logicalKey, sourceRaw);
                continue;
            }

            String sourceNamespace = parts.namespace();
            String sourcePath = "textures/" + parts.relativePath() + ".png";

            Path outputFile = toBedrockTextureOutputFile(textureEntityDir, sourceRaw);
            Files.createDirectories(outputFile.getParent());

            LOGGER.warn("Copy entity texture {} key={} raw={} -> source={}:{} output={}",
                    spec.anchorBlockId(), logicalKey, sourceRaw, sourceNamespace, sourcePath, outputFile);

            try (InputStream in = ResourceHelper.getResource(sourceNamespace, sourcePath)) {
                if (in == null) {
                    LOGGER.warn("Missing texture source for {} key={} at {}:{}",
                            spec.anchorBlockId(), logicalKey, sourceNamespace, sourcePath);
                    continue;
                }

                Files.copy(in, outputFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private int scoreJavaModelPath(Path path, String blockPath, String geometryToken) {
        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - ".json".length());

        if (baseName.equalsIgnoreCase(blockPath)) {
            return 0;
        }
        if (baseName.equalsIgnoreCase(geometryToken)) {
            return 1;
        }
        if (baseName.equalsIgnoreCase(blockPath.replace('/', '_'))) {
            return 2;
        }
        if (baseName.equalsIgnoreCase(geometryToken.replace('/', '_'))) {
            return 3;
        }
        if (baseName.contains(blockPath)) {
            return 4;
        }
        if (baseName.contains(geometryToken)) {
            return 5;
        }
        return 100;
    }

    public void ensureGenerated() {
        if (providedResources) {
            return;
        }

        if (ResourceHelper.POLYMER_GENERATED_PACK == null) {
            LOGGER.warn("DisplayEntityTranslator.ensureGenerated skipped: POLYMER_GENERATED_PACK is still null");
            return;
        }

        try {
            generateEntityResources(this.packRoot);
            providedResources = true;
            LOGGER.warn("DisplayEntityTranslator generated resources after polymer pack became available");
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate display entity resources", e);
        }
    }

    private record TexturePathParts(
            String namespace,
            String relativePath
    ) {
    }

    private TexturePathParts parseSourceTexture(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String namespace = "minecraft";
        String path = raw;

        if (raw.contains(":")) {
            String[] split = raw.split(":", 2);
            namespace = split[0];
            path = split[1];
        }

        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }

        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }

        return new TexturePathParts(namespace, path);
    }

    private String toBedrockTextureReference(String raw) {
        TexturePathParts parts = parseSourceTexture(raw);
        if (parts == null) {
            return null;
        }

        String rel = parts.relativePath();

        if (rel.startsWith("textures/")) {
            rel = rel.substring("textures/".length());
        }

        if (rel.startsWith("entity/")) {
            return "textures/" + parts.namespace() + "/" + rel;
        }

        return "textures/entity/" + parts.namespace() + "/" + rel;
    }

    private Path toBedrockTextureOutputFile(Path textureRoot, String raw) {
        TexturePathParts parts = parseSourceTexture(raw);
        if (parts == null) {
            return textureRoot.resolve("minecraft").resolve("missing.png");
        }

        return textureRoot
                .resolve(parts.namespace())
                .resolve(parts.relativePath() + ".png");
    }

    private void writeMaterialDefinitions(Path packRoot) throws IOException {
        Path materialsDir = packRoot.resolve("materials");
        Files.createDirectories(materialsDir);

        String content = """
            {
              "materials": {
                "version": "1.0.0",
                "entity_alphatest_anim_change_color:entity_alphatest_change_color": {
                  "+defines": ["USE_UV_ANIM"]
                },
                "entity_change_color_one_sided:entity": {
                  "+defines": ["USE_OVERLAY", "USE_COLOR_MASK"]
                },
                "entity_alphatest_change_color_one_sided:entity_change_color_one_sided": {
                  "+defines": ["ALPHA_TEST"],
                  "+samplerStates": [
                    {
                      "samplerIndex": 1,
                      "textureWrap": "Repeat"
                    }
                  ],
                  "msaaSupport": "Both"
                },
                "entity_alphatest_anim_change_color_one_sided:entity_alphatest_change_color_one_sided": {
                  "+defines": ["USE_UV_ANIM"]
                }
              }
            }
            """;

        Files.writeString(materialsDir.resolve("entity.material"), content);
    }

    private void writeSingleAtlasClientEntityJson(
            Path entityDir,
            OversizedDisplaySpec spec,
            AtlasBakedEntityBuilder.BakedResult baked
    ) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");

        JsonObject clientEntity = new JsonObject();
        JsonObject description = new JsonObject();

        description.addProperty("identifier", spec.bedrockEntityId().toString());
        description.addProperty("min_engine_version", "1.21.0");

        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest_change_color_one_sided");

        if (baked.atlasAnimInfo() != null && baked.atlasAnimInfo().hasAnimation()) {
            materials.addProperty("anim", "entity_alphatest_anim_change_color_one_sided");
        }

        description.add("materials", materials);

        JsonObject textures = new JsonObject();
        textures.addProperty("default", baked.atlasTextureReference());
        description.add("textures", textures);

        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", spec.geometryIdentifier());
        description.add("geometry", geometry);

        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render." + spec.bedrockEntityId().getNamespace() + "." + spec.bedrockEntityId().getPath());
        description.add("render_controllers", renderControllers);

        clientEntity.add("description", description);
        root.add("minecraft:client_entity", clientEntity);

        Files.writeString(
                entityDir.resolve(spec.bedrockEntityId().getPath() + ".entity.json"),
                GSON.toJson(root)
        );
    }

    private void writeSingleAtlasRenderControllerJson(
            Path controllerDir,
            OversizedDisplaySpec spec,
            AtlasBakedEntityBuilder.BakedResult baked
    ) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");

        JsonObject renderControllers = new JsonObject();
        root.add("render_controllers", renderControllers);

        String controllerName = "controller.render." + spec.bedrockEntityId().getNamespace() + "." + spec.bedrockEntityId().getPath();

        JsonObject controller = new JsonObject();
        renderControllers.add(controllerName, controller);

        controller.addProperty("geometry", "Geometry.default");

        JsonArray materials = new JsonArray();
        JsonObject materialObj = new JsonObject();

        AtlasBakedEntityBuilder.AtlasAnimInfo animInfo = baked.atlasAnimInfo();
        boolean animated = animInfo != null && animInfo.hasAnimation() && animInfo.totalFrames() > 1;

        if (animated) {
            materialObj.addProperty("*", "Material.anim");
        } else {
            materialObj.addProperty("*", "Material.default");
        }

        materials.add(materialObj);
        controller.add("materials", materials);

        JsonArray textures = new JsonArray();
        textures.add("Texture.default");
        controller.add("textures", textures);

        if (animated) {
            JsonObject uvAnim = new JsonObject();

            double fps = animInfo.fps();
            if (fps <= 0) {
                fps = 7.0;
            } else if (fps > 60) {
                fps = 7.0;
            }

            JsonArray offset = new JsonArray();
            offset.add(0.0);
            offset.add("math.mod(math.floor(q.life_time * " + fps + ")," + animInfo.totalFrames() + ") / " + animInfo.totalFrames());
            uvAnim.add("offset", offset);

            JsonArray scale = new JsonArray();
            scale.add(1.0);
            scale.add("1 / " + animInfo.totalFrames());
            uvAnim.add("scale", scale);

            controller.add("uv_anim", uvAnim);
        }

        Files.writeString(
                controllerDir.resolve(spec.bedrockEntityId().getPath() + ".render_controller.json"),
                GSON.toJson(root)
        );
    }

    private void writeBakedGeometry(
            Path modelEntityDir,
            OversizedDisplaySpec spec,
            AtlasBakedEntityBuilder.BakedResult baked
    ) throws IOException {
        Files.writeString(
                modelEntityDir.resolve(spec.bedrockEntityId().getPath() + ".geo.json"),
                GSON.toJson(baked.bakedGeometry())
        );
    }

    private void writeBakedAtlas(
            Path textureEntityDir,
            OversizedDisplaySpec spec,
            AtlasBakedEntityBuilder.BakedResult baked
    ) throws IOException {
        Path output = textureEntityDir
                .resolve(spec.bedrockEntityId().getNamespace())
                .resolve(spec.bedrockEntityId().getPath() + ".png");

        Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(baked.atlasImage(), "PNG", output.toFile());
    }
}
