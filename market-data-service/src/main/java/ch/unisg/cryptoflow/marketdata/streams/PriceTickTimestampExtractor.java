package ch.unisg.cryptoflow.marketdata.streams;

import ch.unisg.cryptoflow.events.CryptoPriceUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * Pattern 32: custom event-time extractor for scope-05 OHLC aggregation.
 * Reads {@code CryptoPriceUpdatedEvent.timestamp()} from the payload so that
 * tumbling windows assign each tick by its Binance event time rather than by
 * Kafka record metadata. Reprocessing the same input therefore yields the
 * same OHLC bars regardless of when the run happens.
 */
public class PriceTickTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        Object value = record.value();
        if (value instanceof CryptoPriceUpdatedEvent event && event.timestamp() != null) {
            return event.timestamp().toEpochMilli();
        }
        // Fall back to broker-assigned time when the payload is missing or malformed
        return record.timestamp() > 0 ? record.timestamp() : partitionTime;
    }
}
