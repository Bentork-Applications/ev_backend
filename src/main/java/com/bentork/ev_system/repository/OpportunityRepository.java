package com.bentork.ev_system.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.Opportunity;

@Repository
public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    Optional<Opportunity> findByOpportunityNumber(String opportunityNumber);

    List<Opportunity> findByStageInOrderByExpectedCloseDateAsc(List<String> stages);

    List<Opportunity> findByOwnerAdminIdAndStageInOrderByExpectedCloseDateAsc(Long adminId, List<String> stages);

    List<Opportunity> findByOwnerAdminIdOrderByCreatedAtDesc(Long adminId);

    List<Opportunity> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Opportunity> findByStageOrderByCreatedAtDesc(String stage);

    List<Opportunity> findAllByOrderByCreatedAtDesc();

    // Dashboard / Pipeline queries
    long countByStage(String stage);

    long countByOwnerAdminIdAndStage(Long adminId, String stage);

    @Query("SELECT SUM(o.value) FROM Opportunity o WHERE o.stage = :stage")
    Double sumValueByStage(@Param("stage") String stage);

    @Query("SELECT SUM(o.value * o.probability / 100.0) FROM Opportunity o WHERE o.stage NOT IN ('won', 'lost')")
    Double getWeightedPipelineValue();

    // ==================== DATE RANGE & ANALYTICS QUERIES ====================

    List<Opportunity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Opportunity> findByStageAndCreatedAtBetween(String stage, LocalDateTime start, LocalDateTime end);

    long countByStageAndCreatedAtBetween(String stage, LocalDateTime start, LocalDateTime end);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT o FROM Opportunity o WHERE o.stage = :stage AND ((o.updatedAt >= :start AND o.updatedAt < :end) OR (o.createdAt >= :start AND o.createdAt < :end))")
    List<Opportunity> findWonOpportunitiesInPeriod(@Param("stage") String stage, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o FROM Opportunity o WHERE o.ownerAdmin.id = :adminId AND o.stage = :stage AND ((o.updatedAt >= :start AND o.updatedAt < :end) OR (o.createdAt >= :start AND o.createdAt < :end))")
    List<Opportunity> findWonOpportunitiesInPeriodForAdmin(@Param("adminId") Long adminId, @Param("stage") String stage, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.value), 0.0) FROM Opportunity o WHERE o.stage = :stage AND ((o.updatedAt >= :start AND o.updatedAt < :end) OR (o.createdAt >= :start AND o.createdAt < :end))")
    Double sumWonValueInPeriod(@Param("stage") String stage, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.value), 0.0) FROM Opportunity o WHERE o.ownerAdmin.id = :adminId AND o.stage = :stage AND ((o.updatedAt >= :start AND o.updatedAt < :end) OR (o.createdAt >= :start AND o.createdAt < :end))")
    Double sumWonValueInPeriodForAdmin(@Param("adminId") Long adminId, @Param("stage") String stage, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(o.value), 0.0) FROM Opportunity o WHERE o.stage NOT IN ('won', 'lost') AND o.createdAt >= :start AND o.createdAt < :end")
    Double sumActivePipelineValueInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Modifying
    @Query("UPDATE Opportunity o SET o.contact = null WHERE o.contact.id = :contactId")
    void nullifyContact(@Param("contactId") Long contactId);
}
