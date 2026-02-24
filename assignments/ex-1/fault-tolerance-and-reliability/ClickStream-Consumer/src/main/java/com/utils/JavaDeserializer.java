package com.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

// Note: deserialized objects are stored by default as LinkedHashMap by ObjectMapper jackson

public class JavaDeserializer implements Deserializer<Object> {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        try {
            if (data == null) {
                System.out.println("Null received at deserializing");
                return null;
            }
            //System.out.println("Deserializing...");
            return objectMapper.readValue(new String(data, "UTF-8"), Object.class);
        } catch (Exception e) {
            throw new SerializationException("Error when deserializing byte[] to object");
        }
    }

    @Override
    public void close() {
    }

}
