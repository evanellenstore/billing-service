package com.store.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.store.billing.entity.Billing;

public interface BillingRepository extends JpaRepository<Billing, Long>{
	java.util.Optional<com.store.billing.entity.Billing> findByBillId(String billId);

}
