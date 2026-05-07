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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingAuditServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-07T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final StubRepository<MatchingAuditBuyBidEntity, String> buyBidStore =
            new StubRepository<>(MatchingAuditBuyBidRepository.class);
    private final StubRepository<MatchingAuditMatchableAskEntity, String> matchableAskStore =
            new StubRepository<>(MatchingAuditMatchableAskRepository.class);
    private final StubRepository<MatchingAuditOrderMatchEntity, String> orderMatchStore =
            new StubRepository<>(MatchingAuditOrderMatchRepository.class);
    private final MatchingAuditService service = new MatchingAuditService(
            buyBidStore.repository(), matchableAskStore.repository(), orderMatchStore.repository(), CLOCK);

    @Test
    void duplicateBuyBidUpdatesExistingAuditRow() {
        BuyBid first = buyBid("tx-1", "99.00");
        BuyBid replay = buyBid("tx-1", "101.00");
        MatchingAuditBuyBidEntity existing = new MatchingAuditBuyBidEntity(first, "transaction.buy-bids", 0, 11L, NOW);

        buyBidStore.thenFindById(Optional.empty());
        buyBidStore.thenFindById(Optional.of(existing));

        service.recordBuyBid(first, "transaction.buy-bids", 0, 11L);
        service.recordBuyBid(replay, "transaction.buy-bids", 1, 42L);

        assertThat(buyBidStore.saved()).hasSize(2);
        MatchingAuditBuyBidEntity savedReplay = buyBidStore.saved().get(1);
        assertThat(savedReplay.getTransactionId()).isEqualTo("tx-1");
        assertThat(savedReplay.getBidPrice()).isEqualByComparingTo("101.00");
        assertThat(savedReplay.getPartition()).isEqualTo(1);
        assertThat(savedReplay.getOffset()).isEqualTo(42L);
    }

    @Test
    void duplicateMatchableAskUpdatesExistingAuditRow() {
        MatchableAsk first = ask("ask-1", "99.00");
        MatchableAsk replay = ask("ask-1", "98.50");
        MatchingAuditMatchableAskEntity existing =
                new MatchingAuditMatchableAskEntity(first, "crypto.scout.matchable-asks", 0, 7L, NOW);

        matchableAskStore.thenFindById(Optional.empty());
        matchableAskStore.thenFindById(Optional.of(existing));

        service.recordMatchableAsk(first, "crypto.scout.matchable-asks", 0, 7L);
        service.recordMatchableAsk(replay, "crypto.scout.matchable-asks", 0, 8L);

        assertThat(matchableAskStore.saved()).hasSize(2);
        MatchingAuditMatchableAskEntity savedReplay = matchableAskStore.saved().get(1);
        assertThat(savedReplay.getAskQuoteId()).isEqualTo("ask-1");
        assertThat(savedReplay.getAskPrice()).isEqualByComparingTo("98.50");
        assertThat(savedReplay.getOffset()).isEqualTo(8L);
    }

    @Test
    void duplicateOrderMatchUpdatesExistingAuditRow() {
        OrderMatched first = match("tx-1", "ask-1", "99.00");
        OrderMatched replay = match("tx-1", "ask-1", "98.50");
        MatchingAuditOrderMatchEntity existing =
                new MatchingAuditOrderMatchEntity(first, "transaction.order-matched", 0, 3L, NOW);

        orderMatchStore.thenFindById(Optional.empty());
        orderMatchStore.thenFindById(Optional.of(existing));

        service.recordOrderMatched(first, "transaction.order-matched", 0, 3L);
        service.recordOrderMatched(replay, "transaction.order-matched", 0, 4L);

        assertThat(orderMatchStore.saved()).hasSize(2);
        MatchingAuditOrderMatchEntity savedReplay = orderMatchStore.saved().get(1);
        assertThat(savedReplay.getTransactionId()).isEqualTo("tx-1");
        assertThat(savedReplay.getMatchedPrice()).isEqualByComparingTo("98.50");
        assertThat(savedReplay.getOffset()).isEqualTo(4L);
    }

    private static BuyBid buyBid(String transactionId, String bidPrice) {
        return BuyBid.newBuilder()
                .setTransactionId(transactionId)
                .setUserId("user-1")
                .setSymbol("BTC")
                .setBidPrice(new BigDecimal(bidPrice))
                .setBidQuantity(new BigDecimal("0.50"))
                .setCreatedAt(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
    }

    private static MatchableAsk ask(String askQuoteId, String askPrice) {
        return MatchableAsk.newBuilder()
                .setAskQuoteId(askQuoteId)
                .setSymbol("BTC")
                .setAskPrice(new BigDecimal(askPrice))
                .setAskQuantity(new BigDecimal("0.50"))
                .setEventTime(Instant.parse("2026-05-07T10:00:01Z"))
                .setSourceVenue("binance")
                .build();
    }

    private static OrderMatched match(String transactionId, String askQuoteId, String matchedPrice) {
        return OrderMatched.newBuilder()
                .setTransactionId(transactionId)
                .setAskQuoteId(askQuoteId)
                .setSymbol("BTC")
                .setMatchedPrice(new BigDecimal(matchedPrice))
                .setMatchedQuantity(new BigDecimal("0.50"))
                .setBidCreatedAt(Instant.parse("2026-05-07T10:00:00Z"))
                .setAskEventTime(Instant.parse("2026-05-07T10:00:01Z"))
                .setSourceVenue("binance")
                .build();
    }

    private static final class StubRepository<T, ID> {

        private final List<Optional<T>> findByIdResults = new ArrayList<>();
        private final List<T> saved = new ArrayList<>();
        private final Object repository;

        private StubRepository(Class<?> repositoryType) {
            this.repository = Proxy.newProxyInstance(
                    repositoryType.getClassLoader(),
                    new Class<?>[]{repositoryType},
                    (proxy, method, args) -> {
                        if ("findById".equals(method.getName())) {
                            return findByIdResults.isEmpty() ? Optional.empty() : findByIdResults.removeFirst();
                        }
                        if ("save".equals(method.getName())) {
                            @SuppressWarnings("unchecked")
                            T entity = (T) args[0];
                            saved.add(entity);
                            return entity;
                        }
                        if ("toString".equals(method.getName())) {
                            return "StubRepository(" + repositoryType.getSimpleName() + ")";
                        }
                        throw new UnsupportedOperationException("Unsupported repository method: " + method.getName());
                    });
        }

        @SuppressWarnings("unchecked")
        private <R> R repository() {
            return (R) repository;
        }

        private void thenFindById(Optional<T> result) {
            findByIdResults.add(result);
        }

        private List<T> saved() {
            return saved;
        }
    }
}
