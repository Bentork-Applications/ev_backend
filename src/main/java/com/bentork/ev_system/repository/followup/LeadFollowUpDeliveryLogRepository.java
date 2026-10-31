package com.bentork.ev_system.repository.followup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.followup.LeadFollowUpDeliveryLog;

@Repository
public interface LeadFollowUpDeliveryLogRepository extends JpaRepository<LeadFollowUpDeliveryLog, Long> {

    List<LeadFollowUpDeliveryLog> findByMessageIdOrderByCreatedAtAsc(Long messageId);
}

