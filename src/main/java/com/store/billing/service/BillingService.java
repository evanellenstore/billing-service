package com.store.billing.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.store.billing.client.PurchaseServiceClient;
import com.store.billing.dto.BillingRequest;
import com.store.billing.dto.PurchaseDTO;
import com.store.billing.entity.Bill;
import com.store.billing.entity.BillStatus;
import com.store.billing.entity.Billing;
import com.store.billing.repository.BillRepository;
import com.store.billing.repository.BillingRepository;
import com.store.billing.repository.BillItemRepository;
import com.store.billing.entity.BillItem;
import com.store.billing.client.InventoryServiceClient;
import com.store.billing.dto.ReserveRequest;
import com.store.billing.dto.AdjustRequest;
import java.util.Map;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final PurchaseServiceClient purchaseClient;

    private final BillingRepository billingRepository;
    private final BillItemRepository billItemRepository;
    private final InventoryServiceClient inventoryClient;

    private static final double TAX_RATE = 0.18; // 18% GST

    @Transactional
    public Bill generateBill(BillingRequest request) {

        // Prevent duplicate billing
        billRepository.findByPurchaseId(request.getPurchaseId())
                .ifPresent(b -> {
                    throw new RuntimeException("Bill already generated");
                });

        PurchaseDTO purchase = purchaseClient.getPurchase(request.getPurchaseId());

        double tax = purchase.getTotalPrice() * TAX_RATE;
        double total = purchase.getTotalPrice() + tax;

        Bill bill = Bill.builder()
                .purchaseId(purchase.getId())
                .subTotal(purchase.getTotalPrice())
                .taxAmount(tax)
                .totalAmount(total)
                .billedAt(LocalDateTime.now())
                .build();

        return billRepository.save(bill);
    }



    public Billing startBill(String shopId, String userName) {
        Billing billing = Billing.builder()
                .billId(generateBillId())
                .shopId(shopId)
                .createdBy(userName)
                .status(BillStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        return billingRepository.save(billing);
    }

    @Transactional
    public void addItemToBill(String billId, Map<String, Object> payload) {
        // payload can be:
        // 1. Single-batch: { productId, batchNo, name, sku, price, quantity, expiryDate }
        // 2. Multi-batch: { productId, name, sku, price, batches: [ { batchNo, quantity, expiryDate }, ... ] }
        
        Long productId = Long.valueOf(String.valueOf(payload.get("productId")));
        String name = (String) payload.getOrDefault("name", null);
        String sku = (String) payload.getOrDefault("sku", null);
        Double price = Double.valueOf(String.valueOf(payload.getOrDefault("price", 0)));

        // Check if multi-batch format (batches array) or single-batch format
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> batchList = (java.util.List<Map<String, Object>>) payload.get("batches");
        
        if (batchList != null && !batchList.isEmpty()) {
            // Multi-batch scenario: iterate each batch allocation and create separate BillItem
            for (Map<String, Object> batchSpec : batchList) {
                String batchNo = (String) batchSpec.getOrDefault("batchNo", null);
                Integer qty = Integer.valueOf(String.valueOf(batchSpec.getOrDefault("quantity", 0)));
                LocalDate expiryDate = batchSpec.get("expiryDate") != null 
                    ? LocalDate.parse((String) batchSpec.get("expiryDate")) 
                    : null;

                // Save individual batch allocation as separate BillItem
                BillItem item = BillItem.builder()
                        .billId(billId)
                        .productId(productId)
                        .batchNo(batchNo)
                        .name(name)
                        .sku(sku)
                        .quantity(qty)
                        .price(price)
                        .expiryDate(expiryDate)
                        .build();
                billItemRepository.save(item);

                // Reserve stock for this batch allocation
                if (batchNo != null) {
                    inventoryClient.reserve(productId, batchNo, new ReserveRequest(qty, "BILL_CREATE"));
                } else {
                    inventoryClient.reserve(productId, "", new ReserveRequest(qty, "BILL_CREATE"));
                }
            }
        } else {
            // Single-batch scenario: backward compatible
            String batchNo = (String) payload.getOrDefault("batchNo", null);
            Integer qty = Integer.valueOf(String.valueOf(payload.getOrDefault("quantity", 0)));
            LocalDate expiryDate = payload.get("expiryDate") != null 
                ? LocalDate.parse((String) payload.get("expiryDate")) 
                : null;

            BillItem item = BillItem.builder()
                    .billId(billId)
                    .productId(productId)
                    .batchNo(batchNo)
                    .name(name)
                    .sku(sku)
                    .quantity(qty)
                    .price(price)
                    .expiryDate(expiryDate)
                    .build();
            billItemRepository.save(item);

            // Reserve stock
            if (batchNo != null) {
                inventoryClient.reserve(productId, batchNo, new ReserveRequest(qty, "BILL_CREATE"));
            } else {
                inventoryClient.reserve(productId, "", new ReserveRequest(qty, "BILL_CREATE"));
            }
        }
    }

    @Transactional
    public void addItemsBatch(String billId, java.util.List<Map<String, Object>> items) {
        // process items one by one to preserve order and server-side consistency
        for (Map<String, Object> p : items) {
            addItemToBill(billId, p);
        }
    }

    @Transactional
    public void finalizeBill(String billId, Map<String, Object> payment) {
        // find items and adjust stock (OUT)
        List<BillItem> items = billItemRepository.findByBillId(billId);
        for (BillItem it : items) {
            AdjustRequest adj = new AdjustRequest();
            adj.setQuantity(it.getQuantity());
            adj.setType("OUT");
            adj.setRemarks("SALE_FINALIZE");
            adj.setExpiryDate(it.getExpiryDate());
            inventoryClient.adjustStock(it.getProductId(), adj);
        }

        // mark billing completed
        Billing billing = billingRepository.findByBillId(billId)
                .orElseThrow(() -> new RuntimeException("Billing not found"));
    billing.setStatus(com.store.billing.entity.BillStatus.PAID);
        billingRepository.save(billing);
    }



    private String generateBillId() {
        return "BILL_" + LocalDate.now() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }
}
