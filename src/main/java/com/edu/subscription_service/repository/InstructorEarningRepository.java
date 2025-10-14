package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.InstructorEarning;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InstructorEarningRepository extends JpaRepository<InstructorEarning, UUID> {

    Page<InstructorEarning> findByInstructorIdOrderByCreatedAtDesc(UUID instructorId, Pageable pageable);

    List<InstructorEarning> findByInstructorIdAndPayoutIdIsNull(UUID instructorId);

    @Query("SELECT e FROM InstructorEarning e WHERE e.instructorId = :instructorId AND e.createdAt BETWEEN :startDate AND :endDate")
    List<InstructorEarning> findByInstructorIdAndPeriod(UUID instructorId, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT SUM(e.amount) FROM InstructorEarning e WHERE e.instructorId = :instructorId AND e.payoutId IS NULL")
    java.math.BigDecimal getPendingEarningsByInstructor(UUID instructorId);
}
