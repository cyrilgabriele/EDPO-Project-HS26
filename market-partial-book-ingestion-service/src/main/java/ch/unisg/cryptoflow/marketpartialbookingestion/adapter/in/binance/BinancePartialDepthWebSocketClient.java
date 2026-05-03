package ch.unisg.cryptoflow.marketpartialbookingestion.adapter.in.binance;

import ch.unisg.cryptoflow.marketpartialbookingestion.adapter.out.kafka.RawOrderBookDepthKafkaProducer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Subscribes to Binance USD-M Futures partial book depth streams and publishes
 * the raw top-N depth event to Kafka for downstream stream processing.
 */
@Component
@ConditionalOnProperty(name = "binance.partial-depth.enabled", havingValue = "true", matchIfMissing = true)
public class BinancePartialDepthWebSocketClient extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BinancePartialDepthWebSocketClient.class);
    private static final long INITIAL_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final RawOrderBookDepthKafkaProducer kafkaProducer;
    private final BinancePartialDepthEventMapper eventMapper;
    private final String streamUrl;
    private final List<String> symbols;
    private final int depth;
    private final String updateSpeed;

    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile WebSocketSession session;
    private volatile long currentBackoffMs = INITIAL_BACKOFF_MS;
    private volatile boolean shuttingDown = false;

    public BinancePartialDepthWebSocketClient(
            RawOrderBookDepthKafkaProducer kafkaProducer,
            BinancePartialDepthEventMapper eventMapper,
            @Value("${binance.partial-depth.stream-url}") String streamUrl,
            @Value("${binance.partial-depth.symbols}") List<String> symbols,
            @Value("${binance.partial-depth.depth:20}") int depth,
            @Value("${binance.partial-depth.update-speed:500ms}") String updateSpeed) {
        this.kafkaProducer = kafkaProducer;
        this.eventMapper = eventMapper;
        this.streamUrl = streamUrl;
        this.symbols = symbols;
        this.depth = depth;
        this.updateSpeed = updateSpeed;
    }

    @PostConstruct
    public void connect() {
        if (symbols.isEmpty()) {
            log.warn("No symbols configured; partial depth WebSocket connection will not be opened");
            return;
        }
        doConnect();
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        reconnectExecutor.shutdownNow();
        closeSession();
    }

    private void doConnect() {
        String streams = symbols.stream()
                .map(symbol -> symbol.toLowerCase() + "@depth" + depth + "@" + updateSpeed)
                .collect(Collectors.joining("/"));
        String uri = streamUrl + streams;

        log.info("Connecting to Binance partial depth streams: {}", uri);

        try {
            var client = new StandardWebSocketClient();
            client.execute(this, new WebSocketHttpHeaders(), URI.create(uri));
        } catch (Exception e) {
            log.error("Failed to initiate Binance partial depth WebSocket connection: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        this.currentBackoffMs = INITIAL_BACKOFF_MS;
        log.info("Binance partial depth WebSocket connected (session: {}, symbols: {}, depth: {})",
                session.getId(), symbols, depth);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            var event = eventMapper.map(message.getPayload());
            if (event == null) {
                log.debug("Ignoring partial depth message without symbol: {}", message.getPayload());
                return;
            }

            kafkaProducer.publish(event);
        } catch (Exception e) {
            log.warn("Failed to process Binance partial depth message: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Binance partial depth WebSocket transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.session = null;
        if (!shuttingDown) {
            log.warn("Binance partial depth WebSocket disconnected (status: {}). Scheduling reconnect.", status);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown) {
            return;
        }

        log.info("Reconnecting to Binance partial depth streams in {} ms", currentBackoffMs);
        reconnectExecutor.schedule(this::doConnect, currentBackoffMs, TimeUnit.MILLISECONDS);
        currentBackoffMs = Math.min(currentBackoffMs * 2, MAX_BACKOFF_MS);
    }

    private void closeSession() {
        var activeSession = this.session;
        if (activeSession != null && activeSession.isOpen()) {
            try {
                activeSession.close();
            } catch (Exception e) {
                log.debug("Error closing Binance partial depth WebSocket session: {}", e.getMessage());
            }
        }
    }
}
