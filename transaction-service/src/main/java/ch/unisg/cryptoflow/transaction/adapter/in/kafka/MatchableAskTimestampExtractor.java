package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

public class MatchableAskTimestampExtractor implements TimestampExtractor {

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        Object value = record.value();
        if (value instanceof MatchableAsk ask && ask.getEventTime() != null) {
            return ask.getEventTime().toEpochMilli();
        }
        return partitionTime >= 0 ? partitionTime : record.timestamp();
    }
}
