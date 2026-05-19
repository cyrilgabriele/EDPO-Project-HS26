package ch.unisg.cryptoflow.user.domain;

public record User(String username, String password, String userId, String email, String displayCurrency) {

    public static final String DEFAULT_DISPLAY_CURRENCY = "USD";

    public User withDisplayCurrency(String newDisplayCurrency) {
        return new User(username, password, userId, email, newDisplayCurrency);
    }
}
