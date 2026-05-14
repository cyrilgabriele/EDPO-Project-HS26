package ch.unisg.cryptoflow.transaction.adapter.in.web;

import ch.unisg.cryptoflow.transaction.adapter.out.persistence.ConfirmedUserRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditBuyBidRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditMatchableAskRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.MatchingAuditOrderMatchRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.SpringDataOutboxEventRepository;
import ch.unisg.cryptoflow.transaction.adapter.out.persistence.SpringDataTransactionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes dashboard data for the static demo page at {@code /}.
 * Surfaces recent transactions, outbox state, and the confirmed-user read-model.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class TransactionDashboardController {

    private final SpringDataTransactionRecordRepository transactionRepo;
    private final SpringDataOutboxEventRepository outboxRepo;
    private final ConfirmedUserRepository confirmedUserRepo;
    private final MatchingAuditBuyBidRepository buyBidRepo;
    private final MatchingAuditMatchableAskRepository matchableAskRepo;
    private final MatchingAuditOrderMatchRepository orderMatchRepo;

    @GetMapping
    public Map<String, Object> dashboard() {
        var transactions = transactionRepo.findAll(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "placedAt"))
        ).stream().map(t -> Map.of(
                "transactionId", t.getTransactionId(),
                "userId",        t.getUserId(),
                "symbol",        t.getSymbol(),
                "amount",        t.getAmount().toPlainString(),
                "targetPrice",   t.getTargetPrice().toPlainString(),
                "status",        t.getStatus().name(),
                "placedAt",      t.getPlacedAt().toString(),
                "resolvedAt",    t.getResolvedAt() != null ? t.getResolvedAt().toString() : "",
                "matchedPrice",  t.getMatchedPrice() != null ? t.getMatchedPrice().toPlainString() : ""
        )).toList();

        var outbox = outboxRepo.findAll(
                PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).stream().map(o -> Map.of(
                "eventId",       o.getEventId(),
                "transactionId", o.getTransactionId(),
                "topic",         o.getTopic(),
                "createdAt",     o.getCreatedAt().toString(),
                "publishedAt",   o.getPublishedAt() != null ? o.getPublishedAt().toString() : "",
                "published",     o.isPublished()
        )).toList();

        var confirmedUsers = confirmedUserRepo.findAll(
                Sort.by(Sort.Direction.DESC, "confirmedAt")
        ).stream().map(u -> Map.of(
                "userId",      u.getUserId(),
                "userName",    u.getUserName() != null ? u.getUserName() : "",
                "confirmedAt", u.getConfirmedAt().toString()
        )).toList();

        var buyBids = buyBidRepo.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "consumedAt"))
        ).stream().map(b -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionId", b.getTransactionId());
            row.put("userId", b.getUserId());
            row.put("symbol", b.getSymbol());
            row.put("bidPrice", b.getBidPrice().toPlainString());
            row.put("bidQuantity", b.getBidQuantity().toPlainString());
            row.put("createdAt", b.getCreatedAt().toString());
            row.put("topic", b.getTopic());
            row.put("partition", b.getPartition());
            row.put("offset", b.getOffset());
            row.put("consumedAt", b.getConsumedAt().toString());
            return row;
        }).toList();

        var matchableAsks = matchableAskRepo.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "consumedAt"))
        ).stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("askQuoteId", a.getAskQuoteId());
            row.put("symbol", a.getSymbol());
            row.put("askPrice", a.getAskPrice().toPlainString());
            row.put("askQuantity", a.getAskQuantity().toPlainString());
            row.put("eventTime", a.getEventTime().toString());
            row.put("sourceVenue", a.getSourceVenue());
            row.put("topic", a.getTopic());
            row.put("partition", a.getPartition());
            row.put("offset", a.getOffset());
            row.put("consumedAt", a.getConsumedAt().toString());
            return row;
        }).toList();

        var orderMatches = orderMatchRepo.findAll(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "consumedAt"))
        ).stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionId", m.getTransactionId());
            row.put("askQuoteId", m.getAskQuoteId());
            row.put("symbol", m.getSymbol());
            row.put("matchedPrice", m.getMatchedPrice().toPlainString());
            row.put("matchedQuantity", m.getMatchedQuantity().toPlainString());
            row.put("bidCreatedAt", m.getBidCreatedAt().toString());
            row.put("askEventTime", m.getAskEventTime().toString());
            row.put("sourceVenue", m.getSourceVenue());
            row.put("topic", m.getTopic());
            row.put("partition", m.getPartition());
            row.put("offset", m.getOffset());
            row.put("consumedAt", m.getConsumedAt().toString());
            return row;
        }).toList();

        return Map.of(
                "service",        "transaction-service",
                "transactions",   transactions,
                "outbox",         outbox,
                "confirmedUsers", confirmedUsers,
                "buyBids",        buyBids,
                "matchableAsks",  matchableAsks,
                "orderMatches",   orderMatches
        );
    }
}
