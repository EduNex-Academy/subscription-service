package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.UserPointsWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPointsWalletRepository extends JpaRepository<UserPointsWallet, UUID> {
    
    Optional<UserPointsWallet> findByUserId(UUID userId);
}
