package com.store.billing.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.store.billing.dto.AdjustRequest;
import com.store.billing.dto.ReserveRequest;

@FeignClient(name = "inventory-service", url = "${inventory.service.url}")
public interface InventoryServiceClient {

    @PutMapping("/inventory/{productId}/reserve")
    void reserve(@PathVariable Long productId, @RequestParam String batchNo, @RequestBody ReserveRequest req);

    @PutMapping("/inventory/{productId}/adjust")
    void adjustStock(@PathVariable Long productId, @RequestBody AdjustRequest req);

    @PutMapping("/inventory/{productId}/release")
    void releaseStock(@PathVariable Long productId, @RequestBody ReserveRequest req);
}
