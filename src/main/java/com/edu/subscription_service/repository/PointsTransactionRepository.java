package com.edu.subscription_service.repository;

import com.edu.subscription_service.entity.PointsTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, UUID> {
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.userId = :userId ORDER BY pt.createdAt DESC")
    Page<PointsTransaction> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.wallet.id = :walletId ORDER BY pt.createdAt DESC")
    List<PointsTransaction> findByWalletIdOrderByCreatedAtDesc(@Param("walletId") UUID walletId);
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.userId = :userId AND pt.transactionType = :transactionType")
    List<PointsTransaction> findByUserIdAndTransactionType(
            @Param("userId") UUID userId, 
            @Param("transactionType") PointsTransaction.TransactionType transactionType
    );
}
