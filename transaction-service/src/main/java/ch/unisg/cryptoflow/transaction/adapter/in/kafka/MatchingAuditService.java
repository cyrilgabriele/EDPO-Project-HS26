package ch.unisg.cryptoflow.transaction.adapter.in.kafka;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditBuyBidEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditBuyBidRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditMatchableAskEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditMatchableAskRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditOrderMatchEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditOrderMatchRepository;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MatchingAuditService {

    private final MatchingAuditBuyBidRepository buyBidRepository;
    private final MatchingAuditMatchableAskRepository matchableAskRepository;
    private final MatchingAuditOrderMatchRepository orderMatchRepository;
    private final Clock clock;

    @Transactional
    public void recordBuyBid(BuyBid bid, String topic, int partition, long offset) {
        Instant consumedAt = Instant.now(clock);
        MatchingAuditBuyBidEntity entity = buyBidRepository.findById(bid.getTransactionId())
                .orElseGet(() -> new MatchingAuditBuyBidEntity(bid, topic, partition, offset, consumedAt));
        entity.updateFrom(bid, topic, partition, offset, consumedAt);
        buyBidRepository.save(entity);
    }

    @Transactional
    public void recordMatchableAsk(MatchableAsk ask, String topic, int partition, long offset) {
        Instant consumedAt = Instant.now(clock);
        MatchingAuditMatchableAskEntity entity = matchableAskRepository.findById(ask.getAskQuoteId())
                .orElseGet(() -> new MatchingAuditMatchableAskEntity(ask, topic, partition, offset, consumedAt));
        entity.updateFrom(ask, topic, partition, offset, consumedAt);
        matchableAskRepository.save(entity);
    }

    @Transactional
    public void recordOrderMatched(OrderMatched match, String topic, int partition, long offset) {
        Instant consumedAt = Instant.now(clock);
        MatchingAuditOrderMatchEntity entity = orderMatchRepository.findById(match.getTransactionId())
                .orElseGet(() -> new MatchingAuditOrderMatchEntity(match, topic, partition, offset, consumedAt));
        entity.updateFrom(match, topic, partition, offset, consumedAt);
        orderMatchRepository.save(entity);
    }
}
