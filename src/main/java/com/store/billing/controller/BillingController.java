package com.store.billing.controller;

import com.store.billing.dto.BillingRequest;
import com.store.billing.entity.Bill;
import com.store.billing.repository.BillRepository;
import com.store.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/billings")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final BillRepository billRepository;

    @PostMapping
    public Bill generate(@RequestBody BillingRequest request) {
        return billingService.generateBill(request);
    }

    @GetMapping
    public List<Bill> getAll() {
        return billRepository.findAll();
    }

    @GetMapping("/{id}")
    public Bill getById(@PathVariable Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
    }

    @GetMapping("/purchase/{purchaseId}")
    public Bill getByPurchase(@PathVariable Long purchaseId) {
        return billRepository.findByPurchaseId(purchaseId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
    }
}
