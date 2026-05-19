package ch.unisg.cryptoflow.user.adapter.out.persistence;

import ch.unisg.cryptoflow.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
// Dedicated table name to avoid the reserved postgres keyword `user`
@Table(name = "cryptoflow_user")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "user_name", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "display_currency", nullable = false, length = 3)
    private String displayCurrency;

    public static UserEntity fromDomain(User user) {
        String dc = user.displayCurrency() == null ? User.DEFAULT_DISPLAY_CURRENCY : user.displayCurrency();
        return new UserEntity(user.userId(), user.username(), user.password(), user.email(), dc);
    }

    public User toDomain() {
        return new User(username, password, userId, email, displayCurrency);
    }
}
