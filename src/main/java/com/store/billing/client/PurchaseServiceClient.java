package com.store.billing.client;

import com.store.billing.dto.PurchaseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "purchase-service", url = "${purchase.service.url}")
public interface PurchaseServiceClient {

    @GetMapping("/purchases/{id}")
    PurchaseDTO getPurchase(@PathVariable("id") Long id);
}
