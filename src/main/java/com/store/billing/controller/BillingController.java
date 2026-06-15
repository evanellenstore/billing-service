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
import com.store.billing.dto.PaginatedResponse;
import com.store.billing.entity.Bill;
import com.store.billing.entity.Billing;
import com.store.billing.entity.BillItem;
import com.store.billing.repository.BillRepository;
import com.store.billing.repository.BillingRepository;
import com.store.billing.service.BillingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/billings")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final BillRepository billRepository;
    private final BillingRepository billingRepository;

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

    @GetMapping("/customer/{customerId}/paginated")
    public ResponseEntity<?> getByCustomerIdPaginated(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<Bill> response = billingService.getCustomerBillingsPaginated(customerId, page, size);
        return ResponseEntity.ok(response);
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

    @PostMapping("/{billId}/cancel")
    public ResponseEntity<?> cancelBill(@PathVariable String billId) {
        billingService.cancelBill(billId);
        return ResponseEntity.ok(Map.of("status", "cancelled", "billId", billId, "message", "Bill cancelled. All items released."));
    }

    @GetMapping("/report")
    public ResponseEntity<?> getProductReports() {
        return ResponseEntity.ok(billingService.getProductReports());
    }

    @GetMapping("/{billId}/summary")
    public ResponseEntity<?> getBillSummary(@PathVariable String billId) {
        Billing billing = billingRepository.findByBillId(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found with id: " + billId));
        
        // Also fetch the Bill entity to get discount information
        Bill bill = billRepository.findByBillId(billId)
                .orElse(null);
        
        List<BillItem> items = billingService.getBillItems(billId);
        
        return ResponseEntity.ok(Map.of(
                "billId", billing.getBillId(),
                "status", billing.getStatus(),
                "createdAt", billing.getCreatedAt(),
                "createdBy", billing.getCreatedBy(),
                "discount", bill != null ? (bill.getDiscount() != null ? bill.getDiscount() : 0) : 0,
                "subTotal", bill != null ? (bill.getSubTotal() != null ? bill.getSubTotal() : 0) : 0,
                "taxAmount", bill != null ? (bill.getTaxAmount() != null ? bill.getTaxAmount() : 0) : 0,
                "totalAmount", bill != null ? (bill.getTotalAmount() != null ? bill.getTotalAmount() : 0) : 0,
                "items", items
        ));
    }

    // Single-item add and duplicate finalize endpoints removed in favour of batch add and single finalize above.
}
