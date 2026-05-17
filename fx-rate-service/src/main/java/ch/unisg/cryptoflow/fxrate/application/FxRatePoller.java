package ch.unisg.cryptoflow.fxrate.application;

import ch.unisg.cryptoflow.events.avro.FxRate;
import ch.unisg.cryptoflow.fxrate.adapter.in.provider.FxRateFetch;
import ch.unisg.cryptoflow.fxrate.adapter.in.provider.FxRateProvider;
import ch.unisg.cryptoflow.fxrate.adapter.out.kafka.FxRateKafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scope-01 stateless pipeline: scheduled fetch, then filter, translate, publish.
 * One bean orchestrates the four steps documented in 01-fx-rate-ingestion.md.
 */
@Component
public class FxRatePoller {

    private static final Logger log = LoggerFactory.getLogger(FxRatePoller.class);

    private final FxRateProvider provider;
    private final FxRateValidator validator;
    private final FxRateMapper mapper;
    private final FxRateKafkaProducer producer;

    public FxRatePoller(FxRateProvider provider,
                        FxRateValidator validator,
                        FxRateMapper mapper,
                        FxRateKafkaProducer producer) {
        this.provider = provider;
        this.validator = validator;
        this.mapper = mapper;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${fx.poll-interval}", initialDelayString = "PT10S")
    public void poll() {
        try {
            FxRateFetch fetch = provider.fetch();
            if (!validator.isValid(fetch)) {
                log.warn("Skipping FX response that failed validation");
                return;
            }
            List<FxRate> events = mapper.toEvents(fetch);
            events.forEach(producer::publish);
            log.info("Published {} FxRate events (base={}, source={})",
                    events.size(), fetch.baseCurrency(), fetch.source());
        } catch (Exception ex) {
            log.error("FX poll failed", ex);
        }
    }
}
