package com.edu.subscription_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_points_wallet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPointsWallet {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;
    
    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;
    
    @Column(name = "lifetime_earned", nullable = false)
    private Integer lifetimeEarned = 0;
    
    @Column(name = "lifetime_spent", nullable = false)
    private Integer lifetimeSpent = 0;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
