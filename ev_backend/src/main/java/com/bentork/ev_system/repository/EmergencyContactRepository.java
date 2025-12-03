package com.bentork.ev_system.repository;

import com.bentork.ev_system.model.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {
}