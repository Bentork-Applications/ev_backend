package com.bentork.ev_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.SalesTarget;

@Repository
public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    Optional<SalesTarget> findByYearAndMonthAndAdminId(int year, int month, Long adminId);

    Optional<SalesTarget> findByYearAndMonthAndAdminIdIsNull(int year, int month);

    List<SalesTarget> findByYearAndAdminIdIsNullOrderByMonthAsc(int year);

    List<SalesTarget> findByYearAndAdminIdOrderByMonthAsc(int year, Long adminId);

    List<SalesTarget> findByYearOrderByMonthAsc(int year);

    List<SalesTarget> findByYearAndMonth(int year, int month);
}
