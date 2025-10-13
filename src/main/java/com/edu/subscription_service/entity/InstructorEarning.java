package com.edu.subscription_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "instructor_earnings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstructorEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "instructor_id", nullable = false)
    private UUID instructorId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 3, nullable = false)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "earning_type", nullable = false, length = 30)
    private EarningType earningType;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "payout_id")
    private UUID payoutId;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EarningType {
        COURSE_ENROLLMENT,
        SUBSCRIPTION_REVENUE_SHARE,
        BONUS,
        ADJUSTMENT
    }
}
