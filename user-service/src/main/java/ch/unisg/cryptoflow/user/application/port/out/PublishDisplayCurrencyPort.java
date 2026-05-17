package ch.unisg.cryptoflow.user.application.port.out;

/**
 * Outbound port for emitting a UserDisplayCurrencyUpdated event onto the
 * user.display-currency compacted topic. Implemented by the Kafka adapter.
 */
public interface PublishDisplayCurrencyPort {

    void publish(String userId, String displayCurrency);
}
