package ch.unisg.cryptoflow.transaction.adapter.in.web;

import ch.unisg.cryptoflow.events.avro.MatchableAsk;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.ConfirmedUserRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditBuyBidEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditBuyBidRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditMatchableAskEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditMatchableAskRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditOrderMatchEntity;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditOrderMatchRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.SpringDataOutboxEventRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.SpringDataTransactionRecordRepository;
import ch.unisg.cryptoflow.transaction.avro.BuyBid;
import ch.unisg.cryptoflow.transaction.avro.OrderMatched;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDashboardControllerTests {

    @Test
    void dashboardIncludesMatchingAuditArrays() {
        SpringDataTransactionRecordRepository transactionRepo =
                repository(SpringDataTransactionRecordRepository.class, List.of());
        SpringDataOutboxEventRepository outboxRepo =
                repository(SpringDataOutboxEventRepository.class, List.of());
        ConfirmedUserRepository confirmedUserRepo =
                repository(ConfirmedUserRepository.class, List.of());
        MatchingAuditBuyBidRepository buyBidRepo =
                repository(MatchingAuditBuyBidRepository.class, List.of(buyBidEntity()));
        MatchingAuditMatchableAskRepository askRepo =
                repository(MatchingAuditMatchableAskRepository.class, List.of(askEntity()));
        MatchingAuditOrderMatchRepository matchRepo =
                repository(MatchingAuditOrderMatchRepository.class, List.of(matchEntity()));

        TransactionDashboardController controller = new TransactionDashboardController(
                transactionRepo, outboxRepo, confirmedUserRepo, buyBidRepo, askRepo, matchRepo);

        Map<String, Object> dashboard = controller.dashboard();

        assertThat(dashboard).containsKeys("buyBids", "matchableAsks", "orderMatches");
        assertThat((List<?>) dashboard.get("buyBids")).hasSize(1);
        assertThat((List<?>) dashboard.get("matchableAsks")).hasSize(1);
        assertThat((List<?>) dashboard.get("orderMatches")).hasSize(1);
    }

    private static MatchingAuditBuyBidEntity buyBidEntity() {
        BuyBid bid = BuyBid.newBuilder()
                .setTransactionId("tx-1")
                .setUserId("user-1")
                .setSymbol("BTC")
                .setBidPrice(new BigDecimal("100.00"))
                .setBidQuantity(new BigDecimal("0.50"))
                .setCreatedAt(Instant.parse("2026-05-07T10:00:00Z"))
                .build();
        return new MatchingAuditBuyBidEntity(bid, "transaction.buy-bids", 0, 1L, Instant.parse("2026-05-07T10:00:02Z"));
    }

    private static MatchingAuditMatchableAskEntity askEntity() {
        MatchableAsk ask = MatchableAsk.newBuilder()
                .setAskQuoteId("ask-1")
                .setSymbol("BTC")
                .setAskPrice(new BigDecimal("99.00"))
                .setAskQuantity(new BigDecimal("0.50"))
                .setEventTime(Instant.parse("2026-05-07T10:00:01Z"))
                .setSourceVenue("binance")
                .build();
        return new MatchingAuditMatchableAskEntity(ask, "crypto.scout.matchable-asks", 0, 2L,
                Instant.parse("2026-05-07T10:00:03Z"));
    }

    private static MatchingAuditOrderMatchEntity matchEntity() {
        OrderMatched match = OrderMatched.newBuilder()
                .setTransactionId("tx-1")
                .setAskQuoteId("ask-1")
                .setSymbol("BTC")
                .setMatchedPrice(new BigDecimal("99.00"))
                .setMatchedQuantity(new BigDecimal("0.50"))
                .setBidCreatedAt(Instant.parse("2026-05-07T10:00:00Z"))
                .setAskEventTime(Instant.parse("2026-05-07T10:00:01Z"))
                .setSourceVenue("binance")
                .build();
        return new MatchingAuditOrderMatchEntity(match, "transaction.order-matched", 0, 3L,
                Instant.parse("2026-05-07T10:00:04Z"));
    }

    @SuppressWarnings("unchecked")
    private static <R, T> R repository(Class<R> repositoryType, List<T> rows) {
        return (R) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName()) && args != null && args.length == 1) {
                        if (args[0] instanceof Pageable) {
                            return new PageImpl<>(rows);
                        }
                        if (args[0] instanceof Sort) {
                            return rows;
                        }
                    }
                    if ("toString".equals(method.getName())) {
                        return "StubRepository(" + repositoryType.getSimpleName() + ")";
                    }
                    throw new UnsupportedOperationException("Unsupported repository method: " + method.getName());
                });
    }
}
