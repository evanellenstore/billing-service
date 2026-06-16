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
        
        java.util.Map<String, Object> paymentMap = new java.util.HashMap<>();
        paymentMap.put("mode", billing.getPaymentMode() != null ? billing.getPaymentMode() : "");
        paymentMap.put("cashPaid", billing.getCashPaid() != null ? billing.getCashPaid() : 0);
        paymentMap.put("walletUsed", billing.getWalletUsed() != null ? billing.getWalletUsed() : 0);
        // Try to parse JSON string into object; if not available, return raw string/null
        Object paymentDetailsObj = null;
        try {
            if (billing.getPaymentDetails() != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                paymentDetailsObj = mapper.readValue(billing.getPaymentDetails(), java.util.Map.class);
            }
        } catch (Exception e) {
            paymentDetailsObj = billing.getPaymentDetails();
        }
        paymentMap.put("paymentDetails", paymentDetailsObj);

        // --- Automatic refund allocation and recommendation ---
        double paidCash = billing.getCashPaid() != null ? billing.getCashPaid() : 0.0;
        double paidWallet = billing.getWalletUsed() != null ? billing.getWalletUsed() : 0.0;
        double discountAmt = 0.0;
        if (bill != null && bill.getDiscount() != null) discountAmt = bill.getDiscount();

        String paymentType;
        if (paidWallet > 0 && paidCash > 0) paymentType = "MIXED";
        else if (paidWallet > 0) paymentType = "ALL_WALLET";
        else paymentType = "ALL_CASH";

        // Option A: revert discount to wallet (recommended) -> credit discount back to wallet
        double walletRefund_revert = paidWallet + discountAmt;
        double cashRefund_revert = paidCash;

        // Option B: keep discount (no revert) -> do not refund discount; refund only what customer paid
        double walletRefund_keep = paidWallet;
        double cashRefund_keep = paidCash;

        java.util.Map<String, Object> refundMap = new java.util.HashMap<>();
        refundMap.put("paymentType", paymentType);
        refundMap.put("discount", discountAmt);
        refundMap.put("recommended", "revertDiscountToWallet");
        refundMap.put("options", java.util.List.of(
            Map.of(
                "id", "revertDiscountToWallet",
                "label", "Revert discount to wallet (recommended)",
                "walletRefund", walletRefund_revert,
                "cashRefund", cashRefund_revert
            ),
            Map.of(
                "id", "keepDiscount",
                "label", "Keep discount (no revert)",
                "walletRefund", walletRefund_keep,
                "cashRefund", cashRefund_keep
            )
        ));

        // attach refund suggestion to top-level response


        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("billId", billing.getBillId()),
            Map.entry("status", billing.getStatus()),
            Map.entry("createdAt", billing.getCreatedAt()),
            Map.entry("createdBy", billing.getCreatedBy()),
            Map.entry("discount", bill != null ? (bill.getDiscount() != null ? bill.getDiscount() : 0) : 0),
            Map.entry("subTotal", bill != null ? (bill.getSubTotal() != null ? bill.getSubTotal() : 0) : 0),
            Map.entry("taxAmount", bill != null ? (bill.getTaxAmount() != null ? bill.getTaxAmount() : 0) : 0),
            Map.entry("totalAmount", bill != null ? (bill.getTotalAmount() != null ? bill.getTotalAmount() : 0) : 0),
            Map.entry("items", items),
            Map.entry("payment", paymentMap),
            Map.entry("refund", refundMap)
        ));
    }

    @GetMapping("/{billId}/check-refund")
    public ResponseEntity<?> checkBillRefund(@PathVariable String billId) {
        try {
            boolean isRefunded = billingService.isBillRefunded(billId);
            return ResponseEntity.ok(Map.of("billId", billId, "isRefunded", isRefunded));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{billId}/mark-refunded")
    public ResponseEntity<?> markBillRefunded(@PathVariable String billId, @RequestParam Double amount) {
        try {
            billingService.markBillAsRefunded(billId, amount);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "billId", billId,
                    "refundedAmount", amount,
                    "message", "Bill marked as refunded"
            ));
        } catch (RuntimeException e) {
            // If it's an idempotent or conflict situation, return 409 for conflicting amounts
            String msg = e.getMessage();
            if (msg != null && msg.contains("different amount")) {
                return ResponseEntity.status(409).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    @PostMapping("/{billId}/refund")
    public ResponseEntity<?> refundBill(@PathVariable String billId, @RequestBody(required = false) java.util.Map<String, Object> body) {
        try {
            String customerId = body != null && body.get("customerId") != null ? String.valueOf(body.get("customerId")) : null;
            Double walletCredit = body != null && body.get("walletCredit") != null ? Double.valueOf(String.valueOf(body.get("walletCredit"))) : 0.0;
            Double discountDebit = body != null && body.get("discountDebit") != null ? Double.valueOf(String.valueOf(body.get("discountDebit"))) : 0.0;
            Double cashRefund = body != null && body.get("cashRefund") != null ? Double.valueOf(String.valueOf(body.get("cashRefund"))) : 0.0;

            billingService.performRefund(billId, customerId, walletCredit, discountDebit, cashRefund);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "billId", billId,
                    "walletCredit", walletCredit,
                    "discountDebit", discountDebit,
                    "cashRefund", cashRefund,
                    "message", "Refund processed"
            ));
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("already been refunded for a different amount")) {
                return ResponseEntity.status(409).body(Map.of("error", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    // Single-item add and duplicate finalize endpoints removed in favour of batch add and single finalize above.
}
