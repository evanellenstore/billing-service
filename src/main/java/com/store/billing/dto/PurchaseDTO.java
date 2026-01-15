package com.store.billing.dto;

import lombok.Data;

@Data
public class PurchaseDTO {
    private Long id;
    private Integer quantity;
    private Double unitPrice;
    private Double totalPrice;
}
