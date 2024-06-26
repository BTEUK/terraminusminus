package net.buildtheearth.terraminusminus.config;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.daporkchop.lib.common.util.PorkUtil;

/**
 * @author DaPorkchop_
 */
public abstract class TypedDeserializer<T> extends JsonDeserializer<T> {
    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String name = p.nextFieldName();
        if (name == null) {
            throw JsonMappingException.from(p, "expected type name, found: " + p.currentToken());
        }

        Class<? extends T> clazz = this.registry().get(name);
        if (clazz == null) {
            throw JsonMappingException.from(p, "invalid type type name: " + name);
        }

        if (!clazz.isAnnotationPresent(SingleProperty.class) & p.nextToken() != JsonToken.START_OBJECT) {
            throw JsonMappingException.from(p, "expected json object, but found: " + p.currentToken());
        }

        T value = ctxt.readValue(p, PorkUtil.<Class<? extends T>>uncheckedCast(clazz));

        if (p.nextToken() != JsonToken.END_OBJECT) {
            throw JsonMappingException.from(p, "expected json object end, but found: " + p.currentToken());
        }

        return value;
    }

    protected abstract Map<String, Class<? extends T>> registry();
}
