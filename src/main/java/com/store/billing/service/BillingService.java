package com.store.billing.service;

import com.store.billing.client.PurchaseServiceClient;
import com.store.billing.dto.BillingRequest;
import com.store.billing.dto.PurchaseDTO;
import com.store.billing.entity.Bill;
import com.store.billing.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final PurchaseServiceClient purchaseClient;

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
}
