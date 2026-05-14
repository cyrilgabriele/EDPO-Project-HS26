package ch.unisg.cryptoflow.transaction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingAuditMatchableAskRepository extends JpaRepository<MatchingAuditMatchableAskEntity, String> {
}
