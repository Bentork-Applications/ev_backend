package com.bentork.ev_system.repository.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.followup.LeadFollowUpMessage;

@Repository
public interface LeadFollowUpMessageRepository extends JpaRepository<LeadFollowUpMessage, Long> {

    Optional<LeadFollowUpMessage> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<LeadFollowUpMessage> findByRoundIdOrderByCreatedAtDesc(Long roundId, Pageable pageable);

    List<LeadFollowUpMessage> findByRoundIdAndStatus(Long roundId, String status);

    long countByRoundIdAndStatus(Long roundId, String status);

    long countByRoundId(Long roundId);
}

