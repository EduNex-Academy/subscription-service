package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.InstructorPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstructorPayoutRepository extends JpaRepository<InstructorPayout, UUID> {

    Page<InstructorPayout> findByInstructorIdOrderByCreatedAtDesc(UUID instructorId, Pageable pageable);

    List<InstructorPayout> findByInstructorIdAndStatus(UUID instructorId, InstructorPayout.PayoutStatus status);

    Optional<InstructorPayout> findByStripePayoutId(String stripePayoutId);

    @Query("SELECT SUM(p.amount) FROM InstructorPayout p WHERE p.instructorId = :instructorId AND p.status = :status")
    Optional<java.math.BigDecimal> getTotalByInstructorAndStatus(UUID instructorId, InstructorPayout.PayoutStatus status);
}

