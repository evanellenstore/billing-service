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
}

