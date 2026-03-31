package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmedUserRepository extends JpaRepository<ConfirmedUserEntity, String> {
}
