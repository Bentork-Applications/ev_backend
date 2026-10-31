package com.bentork.ev_system.repository.followup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.followup.LeadFollowUpAttachment;

@Repository
public interface LeadFollowUpAttachmentRepository extends JpaRepository<LeadFollowUpAttachment, Long> {

    List<LeadFollowUpAttachment> findByRoundIdOrderByCreatedAtAsc(Long roundId);

    long countByRoundId(Long roundId);
}

