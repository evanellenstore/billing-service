package com.store.billing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bills", uniqueConstraints = {@UniqueConstraint(columnNames = "bill_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long purchaseId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "bill_id", unique = true, nullable = false)
    private String billId;

    private Double subTotal;
    private Double discount;
    private Double taxAmount;
    private Double totalAmount;

    private LocalDateTime billedAt;
    
    @Column(name = "refunded_amount")
    private Double refundedAmount;
    
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
