package com.store.billing.repository;

import com.store.billing.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByPurchaseId(Long purchaseId);
    
    Optional<Bill> findByBillId(String billId);
    
    List<Bill> findByCustomerIdOrderByBilledAtDesc(String customerId);
}
