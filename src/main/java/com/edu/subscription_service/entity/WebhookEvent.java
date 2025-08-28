package com.edu.subscription_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "stripe_event_id", unique = true, nullable = false)
    private String stripeEventId;
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    
    @Column(nullable = false)
    private Boolean processed = false;
    
    @Column(name = "processing_attempts")
    private Integer processingAttempts = 0;
    
    @Column(name = "last_processing_error", columnDefinition = "TEXT")
    private String lastProcessingError;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
