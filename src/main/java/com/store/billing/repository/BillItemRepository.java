package com.store.billing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.store.billing.entity.BillItem;

public interface BillItemRepository extends JpaRepository<BillItem, Long> {
    List<BillItem> findByBillId(String billId);
}
