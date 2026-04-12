package com.store.billing.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveRequest {
    private Integer quantity;
    private String reason;
    private String referenceId;  // 🔑 To link reserve/release to bill
}
