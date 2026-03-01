package ch.unisg.kafka.spring.service;

import ch.unisg.kafka.spring.model.MarketRollingWindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
public class ConsumerService {

    private final Logger logger = LoggerFactory.getLogger(getClass());


    @KafkaListener(topics = {"${spring.kafka.topic}"}, containerFactory = "kafkaListenerStringFactory", groupId = "group_id")
    public void consumeMessage(String message) {
        logger.info("**** -> Consumed message -> {}", message);
    }
    @KafkaListener(topics = "${app.kafka.market-rolling-topic}", containerFactory = "kafkaListenerMarketRollingFactory", groupId = "${app.kafka.market-rolling-group-id:market-rolling}")
    public void consumeMarketRollingEvent(MarketRollingWindowEvent event) {
        logger.info("**** -> Consumed rolling delta for {} with close price {}", event.symbol(), event.ticker().closePrice());
    }

}
