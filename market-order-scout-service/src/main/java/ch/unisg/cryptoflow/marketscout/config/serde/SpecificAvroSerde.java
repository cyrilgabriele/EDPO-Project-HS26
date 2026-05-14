package ch.unisg.cryptoflow.marketscout.config.serde;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Registryless Avro serde for internal MVP topics. The schema is fixed by the
 * generated SpecificRecord class used at each topology boundary.
 */
public class SpecificAvroSerde<T extends SpecificRecordBase> implements Serde<T> {

    private final Serializer<T> serializer;
    private final Deserializer<T> deserializer;

    public SpecificAvroSerde(Class<T> recordType, Schema schema) {
        this.serializer = new SpecificAvroSerializer<>(schema);
        this.deserializer = new SpecificAvroDeserializer<>(schema);
    }

    @Override
    public Serializer<T> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<T> deserializer() {
        return deserializer;
    }

    private static final class SpecificAvroSerializer<T extends SpecificRecordBase> implements Serializer<T> {

        private final SpecificDatumWriter<T> writer;

        private SpecificAvroSerializer(Schema schema) {
            this.writer = new SpecificDatumWriter<>(schema);
        }

        @Override
        public byte[] serialize(String topic, T data) {
            if (data == null) {
                return null;
            }

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
                writer.write(data, encoder);
                encoder.flush();
                return output.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to serialize Avro record for topic " + topic, e);
            }
        }
    }

    private static final class SpecificAvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

        private final SpecificDatumReader<T> reader;

        private SpecificAvroDeserializer(Schema schema) {
            this.reader = new SpecificDatumReader<>(schema);
        }

        @Override
        public T deserialize(String topic, byte[] data) {
            if (data == null || data.length == 0) {
                return null;
            }

            try {
                BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
                return reader.read(null, decoder);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize Avro record from topic " + topic, e);
            }
        }
    }
}
