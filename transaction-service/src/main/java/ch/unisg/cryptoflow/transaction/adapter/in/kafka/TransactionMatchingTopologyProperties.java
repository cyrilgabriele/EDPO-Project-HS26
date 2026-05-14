package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import java.time.Duration;

public record TransactionMatchingTopologyProperties(
        String buyBidTopic,
        String matchableAskTopic,
        String orderMatchedTopic,
        Duration validityWindow,
        Duration gracePeriod
) {
}
