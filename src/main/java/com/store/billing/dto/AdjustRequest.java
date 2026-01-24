package com.store.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustRequest {
    private Integer quantity;
    private String type;   
    private String remarks;
    private BigDecimal purchasePrice;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
    private String supplierName;
}
