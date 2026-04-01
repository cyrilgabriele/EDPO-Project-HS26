package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "confirmed_user")
public class ConfirmedUserEntity {

    @Id
    @Column(nullable = false)
    private String userId;

    @Column
    private String userName;

    @Column(nullable = false)
    private Instant confirmedAt;

    protected ConfirmedUserEntity() {}

    public ConfirmedUserEntity(String userId, String userName, Instant confirmedAt) {
        this.userId = userId;
        this.userName = userName;
        this.confirmedAt = confirmedAt;
    }
}
