package com.store.billing.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Billing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", unique = true, nullable = false)
    private String billId;

    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    private BillStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Payment breakdown fields
    @Column(name = "payment_mode")
    private String paymentMode; // CASH | WALLET | MIXED

    @Column(name = "cash_paid")
    private Double cashPaid;

    @Column(name = "wallet_used")
    private Double walletUsed;

    @Lob
    @Column(name = "payment_details")
    private String paymentDetails; // raw payment JSON as string (stored as text/blob)

    // Refund metadata (set when a refund is processed)
    @Column(name = "refunded_amount")
    private Double refundedAmount;

    @Column(name = "refunded_at")
    private java.time.LocalDateTime refundedAt;
}

