package lol.sylvie.bedframe.geyser.model;

import net.kyori.adventure.key.Key;
import net.minecraft.util.Pair;
import org.geysermc.pack.bedrock.resource.models.entity.ModelEntity;
import org.geysermc.pack.bedrock.resource.models.entity.modelentity.Geometry;
import org.geysermc.pack.bedrock.resource.models.entity.modelentity.geometry.Bones;
import org.geysermc.pack.bedrock.resource.models.entity.modelentity.geometry.Description;
import org.geysermc.pack.bedrock.resource.models.entity.modelentity.geometry.bones.Cubes;
import org.geysermc.pack.bedrock.resource.models.entity.modelentity.geometry.bones.cubes.Uv;
import org.geysermc.pack.bedrock.resource.models.entity.modelentity.geometry.bones.cubes.uv.*;
import team.unnamed.creative.base.Axis3D;
import team.unnamed.creative.base.CubeFace;
import team.unnamed.creative.model.Element;
import team.unnamed.creative.model.ElementFace;
import team.unnamed.creative.model.ElementRotation;
import team.unnamed.creative.model.Model;
import team.unnamed.creative.texture.TextureUV;

import java.util.*;

import static lol.sylvie.bedframe.util.BedframeConstants.LOGGER;

/*
 * Concepts here were inspired by:
 *  tomalbrc's fork: https://github.com/tomalbrc/bedframe/blob/main/src/main/java/lol/sylvie/bedframe/geyser/translator/JavaToBedrockGeometryTranslator.java
 *  Pack Converter's ModelConverter: https://github.com/GeyserMC/PackConverter/blob/master/converter/src/main/java/org/geysermc/pack/converter/converter/model/ModelConverter.java#L62
 * (I'm not using ModelConverter directly as it requires some resource pack boilerplate we don't need)
 */
public class JavaGeometryConverter {
    private static final String FORMAT_VERSION = "1.16.0";
    private static final String GEOMETRY_FORMAT = "geometry.%s";

    private static float[] javaPosToBedrock(float[] java) {
        return new float[] { java[0] - 8.0f, java[1], java[2] - 8.0f };
    }

    private static void applyFaceUv(Uv uv, CubeFace cubeFace, float[] uvValue, float[] uvSize, String texture) {
        switch (cubeFace) {
            case UP -> {
                Up up = new Up();
                up.uv(uvValue);
                up.uvSize(uvSize);
                up.materialInstance(texture);
                uv.up(up);
            }
            case DOWN -> {
                Down down = new Down();
                down.uv(uvValue);
                down.uvSize(uvSize);
                down.materialInstance(texture);
                uv.down(down);
            }
            case NORTH -> {
                North north = new North();
                north.uv(uvValue);
                north.uvSize(uvSize);
                north.materialInstance(texture);
                uv.north(north);
            }
            case SOUTH -> {
                South south = new South();
                south.uv(uvValue);
                south.uvSize(uvSize);
                south.materialInstance(texture);
                uv.south(south);
            }
            case EAST -> {
                East east = new East();
                east.uv(uvValue);
                east.uvSize(uvSize);
                east.materialInstance(texture);
                uv.east(east);
            }
            case WEST -> {
                West west = new West();
                west.uv(uvValue);
                west.uvSize(uvSize);
                west.materialInstance(texture);
                uv.west(west);
            }
        }
    }

    public static Pair<String, ModelEntity> convert(Model model) {
        List<Element> elements = model.elements();
        if (elements.isEmpty()) {
            LOGGER.error("Model {} is empty :(", model.key());
            return null;
        }

        ModelEntity modelEntity = new ModelEntity();
        modelEntity.formatVersion(FORMAT_VERSION);

        Geometry geometry = new Geometry();
        List<Bones> bones = new ArrayList<>();

        int nthElement = 0;
        for (Element element : elements) {
            float[] javaFrom = element.from().toArray();
            float[] javaTo = element.to().toArray();

            // TODO: I've seen a lot of discussion over whether it should be one bone per cube or one bone for all cubes
            Bones bone = new Bones();
            bone.name("element_" + nthElement);

            // Transform
            Cubes cube = new Cubes();
            cube.origin(javaPosToBedrock(javaFrom));
            cube.size(new float[] {
                    javaTo[0] - javaFrom[0],
                    javaTo[1] - javaFrom[1],
                    javaTo[2] - javaFrom[2] });
            cube.inflate(0f);

            // Rotation
            ElementRotation rotation = element.rotation();
            if (rotation != null) { // This can be null actually
                float[] rotOrigin = rotation.origin().toArray();
                bone.pivot(javaPosToBedrock(rotOrigin));

                // We are given an angle and an axis, and we need to provide a vector
                float rotValue = (360 - rotation.angle()) % 360;
                Axis3D axis = rotation.axis();
                float[] rotArray = new float[] {
                        axis == Axis3D.X ? rotValue : 0f,
                        axis == Axis3D.Y ? rotValue : 0f,
                        axis == Axis3D.Z ? rotValue : 0f
                };
                bone.rotation(rotArray);
            } else {
                // this might be default, not sure
                bone.pivot(new float[] { 0f, 0f, 0f });
            }

            // UV
            Uv uv = new Uv();
            Map<CubeFace, ElementFace> faceMap = element.faces();
            if (faceMap.isEmpty()) {
                faceMap = new HashMap<>();
                for (CubeFace face : CubeFace.values()) {
                    faceMap.put(face, ElementFace.face().texture(face.name()).build());
                }
            }

            for (Map.Entry<CubeFace, ElementFace> faceEntry : faceMap.entrySet()) {
                CubeFace direction = faceEntry.getKey();
                ElementFace face = faceEntry.getValue();

                TextureUV textureUV = face.uv0();
                if (textureUV == null)
                    textureUV = TextureUV.uv(0, 0, 16f, 16f);
                else textureUV = TextureUV.uv(textureUV.from().multiply(16f), textureUV.to().multiply(16f));

                float[] uvValue;
                float[] uvSize;
                if (direction.axis() == Axis3D.Y) {
                    uvValue = new float[] { textureUV.to().x(), textureUV.to().y() };
                    uvSize = new float[] { (textureUV.from().x() - uvValue[0]), (textureUV.from().y() - uvValue[1]) };
                } else {
                    uvValue = new float[] { textureUV.from().x(), textureUV.from().y() };
                    uvSize = new float[] { (textureUV.to().x() - uvValue[0]), (textureUV.to().y() - uvValue[1]) };
                }

                applyFaceUv(uv, direction, uvValue, uvSize, face.texture().replace("#", ""));
            }

            cube.uv(uv);
            bone.cubes(List.of(cube));
            bone.textureMeshes(null);
            bones.add(bone);

            nthElement++;
        }

        geometry.bones(bones);
        modelEntity.geometry(List.of(geometry));

        String namespace = model.key().namespace();
        String[] pathSplit = model.key().value().split("/");
        String path = pathSplit[pathSplit.length - 1];
        String geometryName = String.format(GEOMETRY_FORMAT, (namespace.equals(Key.MINECRAFT_NAMESPACE) ? "" : namespace + ".") +
                path.replace(":", "."));

        Description description = new Description();
        description.identifier(geometryName);
        description.textureWidth(16);
        description.textureHeight(16);
        geometry.description(description);

        return new Pair<>(geometryName, modelEntity);
    }
}
