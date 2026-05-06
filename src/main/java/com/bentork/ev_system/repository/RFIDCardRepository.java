package com.bentork.ev_system.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.RFIDCard;

@Repository
public interface RFIDCardRepository extends JpaRepository<RFIDCard, Long> {
    Optional<RFIDCard> findByCardNumber(String cardNumber);

    // Efficient count queries — replace findAll().stream().filter()
    long countByActiveTrue();
    long countByActiveFalse();
    long countByCreatedAtAfter(LocalDateTime after);
}
