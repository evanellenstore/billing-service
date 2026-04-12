package com.store.billing.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.store.billing.client.PurchaseServiceClient;
import com.store.billing.dto.BillingRequest;
import com.store.billing.dto.PurchaseDTO;
import com.store.billing.dto.ProductReportDTO;
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
import java.util.stream.Collectors;

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
                    ReserveRequest req = new ReserveRequest();
                    req.setQuantity(qty);
                    req.setReason("BILL_CREATE");
                    req.setReferenceId(billId);  // 🔑 Link to this bill for future release if needed
                    inventoryClient.reserve(productId, batchNo, req);
                } else {
                    ReserveRequest req = new ReserveRequest();
                    req.setQuantity(qty);
                    req.setReason("BILL_CREATE");
                     req.setReferenceId(billId);
                    inventoryClient.reserve(productId, "", req);
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
                ReserveRequest req = new ReserveRequest();
                req.setQuantity(qty);
                req.setReason("BILL_CREATE");
                 req.setReferenceId(billId);
                inventoryClient.reserve(productId, batchNo, req);
            } else {
                ReserveRequest req = new ReserveRequest();
                req.setQuantity(qty);
                req.setReason("BILL_CREATE");
                 req.setReferenceId(billId);
                inventoryClient.reserve(productId, "", req);
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
        // Extract data from payment payload
        String customerId = payment != null ? (String) payment.get("customerId") : null;
        Double discountAmount = payment != null && payment.get("discount") != null ? 
                Double.valueOf(String.valueOf(payment.get("discount"))) : 0.0;
        Double gstAmount = payment != null && payment.get("gst") != null ? 
                Double.valueOf(String.valueOf(payment.get("gst"))) : 0.0;
        Double grandTotal = payment != null && payment.get("grandTotal") != null ?
                Double.valueOf(String.valueOf(payment.get("grandTotal"))) : null;
        
        // find items and adjust stock (OUT)
        List<BillItem> items = billItemRepository.findByBillId(billId);
        
        // Calculate subtotal from items
        Double subTotal = items.stream()
                .mapToDouble(item -> (item.getPrice() != null ? item.getPrice() : 0.0) * 
                        (item.getQuantity() != null ? item.getQuantity() : 0))
                .sum();
        
        // If no items or subTotal is 0, use grandTotal from payload as subTotal
        if (subTotal == 0 && grandTotal != null) {
            subTotal = grandTotal;
        }
        
        // Calculate total amount: subTotal - discount + gst
        Double totalAmount = subTotal - discountAmount + gstAmount;
        
        // Log for debugging
        System.out.println("=== FINALIZE BILL DEBUG ===");
        System.out.println("billId: " + billId);
        System.out.println("customerId: " + customerId);
        System.out.println("subTotal: " + subTotal);
        System.out.println("discount: " + discountAmount);
        System.out.println("gst: " + gstAmount);
        System.out.println("totalAmount: " + totalAmount);
        System.out.println("items count: " + items.size());
        
        // Adjust stock for each item
        for (BillItem it : items) {
            AdjustRequest adj = new AdjustRequest();
            adj.setQuantity(it.getQuantity());
            adj.setType("OUT");
            adj.setRemarks("SALE_FINALIZE");
            adj.setExpiryDate(it.getExpiryDate());
            adj.setReferenceId(billId);  // 🔑 Link to RESERVE transaction
            inventoryClient.adjustStock(it.getProductId(), adj);
        }

        // mark billing completed
        Billing billing = billingRepository.findByBillId(billId)
                .orElseThrow(() -> new RuntimeException("Billing not found"));
        billing.setStatus(com.store.billing.entity.BillStatus.PAID);
        billingRepository.save(billing);
        
        // ✅ SUCCESS MESSAGE
        System.out.println("✅ PAYMENT DONE SUCCESSFULLY");
        System.out.println("Bill ID: " + billId);
        System.out.println("Total Amount: ₹" + totalAmount);
        System.out.println("Items Sold: " + items.size());
        System.out.println("============================");
        
        // Create and save Bill entity with all details
        if (customerId != null && !customerId.isEmpty()) {
            // Check if bill already exists
            java.util.Optional<Bill> existingBill = billRepository.findAll().stream()
                    .filter(b -> billId.equals(b.getBillId()))
                    .findFirst();
            
            Bill bill;
            if (existingBill.isPresent()) {
                // Update existing bill
                bill = existingBill.get();
                bill.setCustomerId(customerId);
                bill.setSubTotal(subTotal > 0 ? subTotal : null);
                bill.setDiscount(discountAmount > 0 ? discountAmount : null);
                bill.setTaxAmount(gstAmount > 0 ? gstAmount : null);
                bill.setTotalAmount(totalAmount > 0 ? totalAmount : null);
            } else {
                // Create new bill
                bill = Bill.builder()
                        .billId(billId)
                        .customerId(customerId)
                        .subTotal(subTotal > 0 ? subTotal : null)
                        .discount(discountAmount > 0 ? discountAmount : null)
                        .taxAmount(gstAmount > 0 ? gstAmount : null)
                        .totalAmount(totalAmount > 0 ? totalAmount : null)
                        .billedAt(LocalDateTime.now())
                        .build();
            }
            billRepository.save(bill);
            System.out.println("Bill saved: " + bill);
        }
    }

    private String generateBillId() {
        return "BILL_" + LocalDate.now() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    // =====================================
    // 🔄 CANCEL BILL - Release all reserved items
    // =====================================
    @Transactional
    public void cancelBill(String billId) {
        // Get all items in this bill
        List<BillItem> items = billItemRepository.findByBillId(billId);
        
        if (items.isEmpty()) {
            System.out.println("⚠️ No items found in bill: " + billId);
            return;
        }

        // Release (unreserve) each item
        for (BillItem item : items) {
            try {
                ReserveRequest releaseReq = new ReserveRequest();
                releaseReq.setQuantity(item.getQuantity());
                releaseReq.setReferenceId(billId);  // Match the reference ID from reservation
                
                // Call inventory service to release (convert RESERVE to IN)
                inventoryClient.releaseStock(item.getProductId(), releaseReq);
                
                System.out.println("✅ Released product " + item.getProductId() + ", qty: " + item.getQuantity());
            } catch (Exception e) {
                System.err.println("❌ Error releasing product " + item.getProductId() + ": " + e.getMessage());
            }
        }

        // Update bill status to CANCELLED
        Billing billing = billingRepository.findByBillId(billId)
                .orElseThrow(() -> new RuntimeException("Billing not found: " + billId));
        billing.setStatus(BillStatus.CANCELLED);
        billingRepository.save(billing);

        System.out.println("✅ BILL CANCELLED: " + billId);
        System.out.println("All reserved items released back to inventory");
    }

    /**
     * Get aggregated product report data (for reporting service)
     * Groups BillItems by productId and calculates total quantity and revenue
     */
    public List<ProductReportDTO> getProductReports() {
        var billItemsByProduct = billItemRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(BillItem::getProductId));

        return billItemsByProduct.entrySet().stream()
                .map(entry -> ProductReportDTO.builder()
                        .productId(entry.getKey())
                        .totalPurchased((int) entry.getValue().stream()
                                .mapToLong(BillItem::getQuantity)
                                .sum())
                        .totalRevenue(entry.getValue().stream()
                                .mapToDouble(item -> (item.getPrice() != null ? item.getPrice() : 0) * (item.getQuantity() != null ? item.getQuantity() : 0))
                                .sum())
                        .build())
                .collect(Collectors.toList());
    }
}

