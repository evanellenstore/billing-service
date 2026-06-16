package com.store.billing.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "user-service", url = "${user.service.url}")
public interface UserServiceClient {

    @PostMapping("/users/customers/{customerId}/wallet/add")
    Map<String, Object> addToWallet(@PathVariable("customerId") String customerId, @RequestBody Map<String, Object> payload);

    @PostMapping("/users/customers/{customerId}/wallet/deduct")
    Map<String, Object> deductFromWallet(@PathVariable("customerId") String customerId, @RequestBody Map<String, Object> payload);
}
