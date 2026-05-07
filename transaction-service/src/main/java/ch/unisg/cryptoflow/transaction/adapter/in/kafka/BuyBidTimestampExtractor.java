package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class BuyBidTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        Object value = record.value();
        if (value instanceof BuyBid bid && bid.getCreatedAt() != null) {
            return bid.getCreatedAt().toEpochMilli();
        }
        return partitionTime >= 0 ? partitionTime : record.timestamp();
    }
}
