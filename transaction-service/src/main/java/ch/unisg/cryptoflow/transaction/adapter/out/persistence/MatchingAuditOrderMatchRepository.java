package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingAuditOrderMatchRepository extends JpaRepository<MatchingAuditOrderMatchEntity, String> {
}
