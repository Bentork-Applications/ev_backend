package com.bentork.ev_system.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.SupportRequest;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findBySubmitterEmailOrderByCreatedAtDesc(String email);

    List<SupportRequest> findAllByOrderByCreatedAtDesc();

    List<SupportRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<SupportRequest> findByCustomerTypeOrderByCreatedAtDesc(String customerType);
}
