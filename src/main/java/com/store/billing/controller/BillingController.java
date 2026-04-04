package com.store.billing.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.store.billing.dto.BillingRequest;
import com.store.billing.entity.Bill;
import com.store.billing.entity.Billing;
import com.store.billing.repository.BillRepository;
import com.store.billing.service.BillingService;

import lombok.RequiredArgsConstructor;

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

    @GetMapping("/customer/{customerId}")
    public List<Bill> getByCustomerId(@PathVariable String customerId) {
        return billRepository.findByCustomerIdOrderByBilledAtDesc(customerId);
    }


    @PostMapping("/start")
    public ResponseEntity<?> startBill(@RequestParam String userName) {
        String shopId = "SHOP_01";
        Billing billing = billingService.startBill(shopId, userName);
        return ResponseEntity.ok(
                Map.of(
                        "billId", billing.getBillId(),
                        "status", billing.getStatus(),
                        "createdAt", billing.getCreatedAt()));
    }

    @PostMapping("/{billId}/items")
    public ResponseEntity<?> addItemsBatch(@PathVariable String billId, @RequestBody java.util.List<java.util.Map<String, Object>> items) {
        billingService.addItemsBatch(billId, items);
        return ResponseEntity.ok(Map.of("status", "reserved", "count", items.size()));
    }

    @PostMapping("/{billId}/finalize")
    public ResponseEntity<?> finalize(@PathVariable String billId, @RequestBody(required = false) java.util.Map<String, Object> payment) {
        billingService.finalizeBill(billId, payment == null ? java.util.Map.of() : payment);
        return ResponseEntity.ok(Map.of("status", "completed", "billId", billId));
    }

    // Single-item add and duplicate finalize endpoints removed in favour of batch add and single finalize above.
}
