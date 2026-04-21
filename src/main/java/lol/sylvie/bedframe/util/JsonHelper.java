package lol.sylvie.bedframe.util;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;

public class JsonHelper {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Identifier.class, new SimpleCodecDeserializer<>(Identifier.CODEC))
            .create();

    public static class SimpleCodecDeserializer<T> implements JsonDeserializer<T> {
        private final Codec<T> codec;

        public SimpleCodecDeserializer(Codec<T> codec) {
            this.codec = codec;
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return codec.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new JsonParseException("Failed to deserialize using Codec: " + error));
        }
    }
}
