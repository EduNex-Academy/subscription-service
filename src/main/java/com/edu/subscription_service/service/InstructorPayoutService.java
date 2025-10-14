package com.edu.subscription_service.service;

import com.edu.subscription_service.entity.InstructorEarning;
import com.edu.subscription_service.entity.InstructorPayout;
import com.edu.subscription_service.repository.InstructorEarningRepository;
import com.edu.subscription_service.repository.InstructorPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstructorPayoutService {

    private final InstructorPayoutRepository payoutRepository;
    private final InstructorEarningRepository earningRepository;

    @Transactional
    public void recordEarning(UUID instructorId, UUID courseId, BigDecimal amount,
                             InstructorEarning.EarningType earningType, String description) {
        recordEarning(instructorId, courseId, null, amount, earningType, description);
    }

    @Transactional
    public void recordEarning(UUID instructorId, UUID courseId, UUID subscriptionId, BigDecimal amount,
                             InstructorEarning.EarningType earningType, String description) {
        log.info("Recording earning for instructor: {} - Amount: {} - Type: {}",
                instructorId, amount, earningType);

        InstructorEarning earning = new InstructorEarning();
        earning.setInstructorId(instructorId);
        earning.setCourseId(courseId);
        earning.setSubscriptionId(subscriptionId);
        earning.setAmount(amount);
        earning.setEarningType(earningType);
        earning.setDescription(description);

        earningRepository.save(earning);
        log.info("✅ Earning recorded: {}", earning.getId());
    }

    @Transactional
    public void recordSubscriptionRevenueShare(UUID subscriptionId, BigDecimal totalRevenue) {
        log.info("Recording subscription revenue share for subscription: {} - Total Revenue: {}", 
                subscriptionId, totalRevenue);
        
        // Calculate 70% for instructors, 30% for platform
        BigDecimal instructorShare = totalRevenue.multiply(new BigDecimal("0.70"));
        
        // For now, record a general earning that can be distributed to instructors
        // In a real-world scenario, you would distribute this based on course enrollments or instructor metrics
        InstructorEarning earning = new InstructorEarning();
        earning.setInstructorId(null); // Will be assigned during distribution
        earning.setCourseId(null); // Will be assigned during distribution
        earning.setSubscriptionId(subscriptionId);
        earning.setAmount(instructorShare);
        earning.setEarningType(InstructorEarning.EarningType.SUBSCRIPTION_REVENUE_SHARE);
        earning.setDescription("Revenue share pool from subscription payment");

        earningRepository.save(earning);
        log.info("✅ Revenue share pool created: {}", earning.getId());
    }

    @Transactional
    public InstructorPayout createPayout(UUID instructorId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        log.info("Creating payout for instructor: {} for period {} to {}",
                instructorId, periodStart, periodEnd);

        // Get all unpaid earnings for the period
        List<InstructorEarning> earnings = earningRepository
                .findByInstructorIdAndPeriod(instructorId, periodStart, periodEnd);

        if (earnings.isEmpty()) {
            log.warn("No earnings found for instructor in period");
            throw new RuntimeException("No earnings available for payout");
        }

        // Calculate total amount
        BigDecimal totalAmount = earnings.stream()
                .map(InstructorEarning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create payout
        InstructorPayout payout = new InstructorPayout();
        payout.setInstructorId(instructorId);
        payout.setAmount(totalAmount);
        payout.setPeriodStart(periodStart);
        payout.setPeriodEnd(periodEnd);
        payout.setStatus(InstructorPayout.PayoutStatus.PENDING);
        payout.setDescription(String.format("Payout for %d earnings", earnings.size()));

        InstructorPayout savedPayout = payoutRepository.save(payout);

        // Link earnings to payout
        earnings.forEach(earning -> {
            earning.setPayoutId(savedPayout.getId());
            earningRepository.save(earning);
        });

        log.info("✅ Payout created: {} - Amount: {}", savedPayout.getId(), totalAmount);
        return savedPayout;
    }

    @Transactional
    public void processPayout(UUID payoutId, String stripePayoutId) {
        log.info("Processing payout: {}", payoutId);

        InstructorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        payout.setStatus(InstructorPayout.PayoutStatus.PROCESSING);
        payout.setStripePayoutId(stripePayoutId);

        payoutRepository.save(payout);
        log.info("✅ Payout marked as processing");
    }

    @Transactional
    public void completePayout(UUID payoutId) {
        log.info("Completing payout: {}", payoutId);

        InstructorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        payout.setStatus(InstructorPayout.PayoutStatus.PAID);
        payout.setPaidAt(LocalDateTime.now());

        payoutRepository.save(payout);
        log.info("✅ Payout completed");
    }

    @Transactional
    public void failPayout(UUID payoutId, String reason) {
        log.info("Failing payout: {} - Reason: {}", payoutId, reason);

        InstructorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        payout.setStatus(InstructorPayout.PayoutStatus.FAILED);
        payout.setFailureReason(reason);

        payoutRepository.save(payout);

        // Unlink earnings so they can be included in next payout
        List<InstructorEarning> earnings = earningRepository.findByInstructorIdAndPayoutIdIsNull(payout.getInstructorId());
        earnings.forEach(earning -> {
            if (payoutId.equals(earning.getPayoutId())) {
                earning.setPayoutId(null);
                earningRepository.save(earning);
            }
        });

        log.info("✅ Payout marked as failed");
    }

    public BigDecimal getPendingEarnings(UUID instructorId) {
        BigDecimal pending = earningRepository.getPendingEarningsByInstructor(instructorId);
        return pending != null ? pending : BigDecimal.ZERO;
    }

    public Page<InstructorPayout> getInstructorPayouts(UUID instructorId, Pageable pageable) {
        return payoutRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId, pageable);
    }

    public Page<InstructorEarning> getInstructorEarnings(UUID instructorId, Pageable pageable) {
        return earningRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId, pageable);
    }
}

