package ch.unisg.cryptoflow.marketscout.adapter.in.kafka;

import ch.unisg.cryptoflow.events.RawOrderBookDepthEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

import java.time.Instant;

/**
 * Uses Binance transaction time as event time for downstream windows.
 */
public class RawOrderBookDepthTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof RawOrderBookDepthEvent event) {
            Instant transactionTime = event.transactionTime();
            if (transactionTime != null && !Instant.EPOCH.equals(transactionTime)) {
                return transactionTime.toEpochMilli();
            }

            Instant eventTime = event.eventTime();
            if (eventTime != null && !Instant.EPOCH.equals(eventTime)) {
                return eventTime.toEpochMilli();
            }
        }

        return partitionTime >= 0 ? partitionTime : record.timestamp();
    }
}
