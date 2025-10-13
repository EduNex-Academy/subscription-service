package com.edu.subscription_service.service;

import com.edu.subscription_service.dto.PointsTransactionDto;
import com.edu.subscription_service.dto.UserPointsWalletDto;
import com.edu.subscription_service.dto.request.RedeemPointsRequest;
import com.edu.subscription_service.dto.response.PointsValidationResponse;
import com.edu.subscription_service.entity.PointsTransaction;
import com.edu.subscription_service.entity.UserPointsWallet;
import com.edu.subscription_service.repository.PointsTransactionRepository;
import com.edu.subscription_service.repository.UserPointsWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsService {
    
    private final UserPointsWalletRepository walletRepository;
    private final PointsTransactionRepository transactionRepository;
    private final ModelMapper modelMapper;
    
    @Transactional
    public UserPointsWalletDto getOrCreateWallet(UUID userId) {
        log.info("Getting or creating wallet for user: {}", userId);
        
        Optional<UserPointsWallet> existingWallet = walletRepository.findByUserId(userId);
        
        if (existingWallet.isPresent()) {
            return modelMapper.map(existingWallet.get(), UserPointsWalletDto.class);
        }
        
        UserPointsWallet newWallet = new UserPointsWallet();
        newWallet.setUserId(userId);
        newWallet.setTotalPoints(0);
        newWallet.setLifetimeEarned(0);
        newWallet.setLifetimeSpent(0);
        
        UserPointsWallet savedWallet = walletRepository.save(newWallet);
        log.info("Created new wallet for user: {} with id: {}", userId, savedWallet.getId());
        
        return modelMapper.map(savedWallet, UserPointsWalletDto.class);
    }
    
    @Transactional
    public void awardPoints(UUID userId, Integer points, String description, String referenceType, UUID referenceId) {
        log.info("Awarding {} points to user: {} for: {}", points, userId, description);
        
        UserPointsWallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPointsWallet newWallet = new UserPointsWallet();
                    newWallet.setUserId(userId);
                    newWallet.setTotalPoints(0);
                    newWallet.setLifetimeEarned(0);
                    newWallet.setLifetimeSpent(0);
                    return walletRepository.save(newWallet);
                });
        
        // Update wallet
        wallet.setTotalPoints(wallet.getTotalPoints() + points);
        wallet.setLifetimeEarned(wallet.getLifetimeEarned() + points);
        walletRepository.save(wallet);
        
        // Create transaction record
        PointsTransaction transaction = new PointsTransaction();
        transaction.setWallet(wallet);
        transaction.setUserId(userId);
        transaction.setTransactionType(PointsTransaction.TransactionType.EARN);
        transaction.setPoints(points);
        transaction.setDescription(description);
        transaction.setReferenceType(referenceType);
        transaction.setReferenceId(referenceId);
        
        transactionRepository.save(transaction);
        log.info("Points awarded successfully. New balance: {}", wallet.getTotalPoints());
    }
    
    /**
     * Validates if user has enough points for a course resource
     * This is used by Course Service before allowing access
     */
    @Transactional(readOnly = true)
    public PointsValidationResponse validatePoints(UUID userId, Integer requiredPoints) {
        log.info("Validating points for user: {} - Required: {}", userId, requiredPoints);

        Optional<UserPointsWallet> walletOpt = walletRepository.findByUserId(userId);

        if (walletOpt.isEmpty()) {
            log.warn("Wallet not found for user: {}", userId);
            return PointsValidationResponse.insufficient(0, requiredPoints);
        }

        UserPointsWallet wallet = walletOpt.get();

        if (wallet.getTotalPoints() >= requiredPoints) {
            return PointsValidationResponse.sufficient(wallet.getTotalPoints(), requiredPoints);
        } else {
            return PointsValidationResponse.insufficient(wallet.getTotalPoints(), requiredPoints);
        }
    }

    /**
     * Deducts points for course resource access
     * Called by Course Service when user accesses a module or quiz
     */
    @Transactional
    public boolean deductPointsForResource(UUID userId, Integer points, String resourceType, UUID resourceId, String description) {
        log.info("Deducting {} points from user: {} for {} - {}", points, userId, resourceType, resourceId);

        Optional<UserPointsWallet> walletOpt = walletRepository.findByUserId(userId);

        if (walletOpt.isEmpty()) {
            log.warn("Wallet not found for user: {}", userId);
            return false;
        }

        UserPointsWallet wallet = walletOpt.get();

        if (wallet.getTotalPoints() < points) {
            log.warn("Insufficient points. Required: {}, Available: {}", points, wallet.getTotalPoints());
            return false;
        }

        // Update wallet
        wallet.setTotalPoints(wallet.getTotalPoints() - points);
        wallet.setLifetimeSpent(wallet.getLifetimeSpent() + points);
        walletRepository.save(wallet);

        // Create transaction record
        PointsTransaction transaction = new PointsTransaction();
        transaction.setWallet(wallet);
        transaction.setUserId(userId);
        transaction.setTransactionType(PointsTransaction.TransactionType.REDEEM);
        transaction.setPoints(points);
        transaction.setDescription(description);
        transaction.setReferenceType(resourceType);
        transaction.setReferenceId(resourceId);

        transactionRepository.save(transaction);
        log.info("Points deducted successfully. New balance: {}", wallet.getTotalPoints());

        return true;
    }

    @Transactional
    public boolean redeemPoints(UUID userId, RedeemPointsRequest request) {
        log.info("Redeeming {} points for user: {}", request.getPoints(), userId);
        
        Optional<UserPointsWallet> walletOpt = walletRepository.findByUserId(userId);
        
        if (walletOpt.isEmpty()) {
            log.warn("Wallet not found for user: {}", userId);
            return false;
        }
        
        UserPointsWallet wallet = walletOpt.get();
        
        if (wallet.getTotalPoints() < request.getPoints()) {
            log.warn("Insufficient points. Required: {}, Available: {}", request.getPoints(), wallet.getTotalPoints());
            return false;
        }
        
        // Update wallet
        wallet.setTotalPoints(wallet.getTotalPoints() - request.getPoints());
        wallet.setLifetimeSpent(wallet.getLifetimeSpent() + request.getPoints());
        walletRepository.save(wallet);
        
        // Create transaction record
        PointsTransaction transaction = new PointsTransaction();
        transaction.setWallet(wallet);
        transaction.setUserId(userId);
        transaction.setTransactionType(PointsTransaction.TransactionType.REDEEM);
        transaction.setPoints(request.getPoints());
        transaction.setDescription(request.getDescription());
        transaction.setReferenceType(request.getReferenceType());
        transaction.setReferenceId(request.getReferenceId());
        
        transactionRepository.save(transaction);
        log.info("Points redeemed successfully. New balance: {}", wallet.getTotalPoints());
        
        return true;
    }
    
    public Page<PointsTransactionDto> getUserTransactionHistory(UUID userId, Pageable pageable) {
        log.info("Fetching transaction history for user: {}", userId);
        Page<PointsTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return transactions.map(transaction -> modelMapper.map(transaction, PointsTransactionDto.class));
    }
    
    public Optional<UserPointsWalletDto> getUserWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> modelMapper.map(wallet, UserPointsWalletDto.class));
    }

    public Integer getUserPointsBalance(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(UserPointsWallet::getTotalPoints)
                .orElse(0);
    }
}
