package com.edu.subscription_service.service;

import com.edu.subscription_service.dto.SubscriptionPlanDto;
import com.edu.subscription_service.entity.SubscriptionPlan;
import com.edu.subscription_service.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanService {
    
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ModelMapper modelMapper;
    
    public List<SubscriptionPlanDto> getAllActivePlans() {
        log.info("Fetching all active subscription plans");
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findByIsActiveTrue();
        return plans.stream()
                .map(plan -> modelMapper.map(plan, SubscriptionPlanDto.class))
                .collect(Collectors.toList());
    }
    
    public Optional<SubscriptionPlanDto> getPlanById(UUID planId) {
        log.info("Fetching subscription plan with id: {}", planId);
        Optional<SubscriptionPlan> plan = subscriptionPlanRepository.findById(planId);
        return plan.map(p -> modelMapper.map(p, SubscriptionPlanDto.class));
    }
    
    public Optional<SubscriptionPlan> findByNameAndBillingCycle(String name, SubscriptionPlan.BillingCycle billingCycle) {
        return subscriptionPlanRepository.findByNameAndBillingCycleAndIsActiveTrue(name, billingCycle);
    }
    
    public Optional<SubscriptionPlan> findByStripePriceId(String stripePriceId) {
        return subscriptionPlanRepository.findByStripePriceId(stripePriceId);
    }
    
    public List<SubscriptionPlanDto> getPlansByName(String planName) {
        log.info("Fetching subscription plans for name: {}", planName);
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findByIsActiveTrue()
                .stream()
                .filter(plan -> plan.getName().equalsIgnoreCase(planName))
                .collect(Collectors.toList());
        
        return plans.stream()
                .map(plan -> modelMapper.map(plan, SubscriptionPlanDto.class))
                .collect(Collectors.toList());
    }
}
