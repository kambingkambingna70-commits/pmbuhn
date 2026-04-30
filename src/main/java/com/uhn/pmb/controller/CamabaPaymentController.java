package com.uhn.pmb.controller;

import com.uhn.pmb.repository.UserRepository;
import com.uhn.pmb.service.CamabaPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Handles student payment endpoints. Delegates business logic to CamabaPaymentService.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaPaymentController {

    private final CamabaPaymentService camabaPaymentService;
    private final UserRepository userRepository;

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> request) {
        try {
            String vaNumber = request.get("vaNumber");
            return ResponseEntity.ok(camabaPaymentService.verifyPayment(currentUserEmail(), vaNumber));
        } catch (Exception e) {
            log.error("Error verifying payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/buy-form/{formId}")
    public ResponseEntity<?> buyForm(@PathVariable Long formId) {
        try {
            return ResponseEntity.ok(camabaPaymentService.buyForm(formId));
        } catch (Exception e) {
            log.error("Error buying form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/create-virtual-account")
    public ResponseEntity<?> createVirtualAccount(@RequestBody Map<String, Object> request) {
        try {
            Long selectionTypeId = request.containsKey("selectionTypeId")
                    ? ((Number) request.get("selectionTypeId")).longValue() : null;
            Long periodId = request.containsKey("periodId")
                    ? ((Number) request.get("periodId")).longValue() : null;
            BigDecimal amount = request.containsKey("amount")
                    ? new BigDecimal(String.valueOf(request.get("amount"))) : null;
            return ResponseEntity.ok(camabaPaymentService.createVirtualAccount(
                    currentUserEmail(), selectionTypeId, periodId, amount));
        } catch (Exception e) {
            log.error("Error creating virtual account: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/check-payment-status")
    public ResponseEntity<?> checkPaymentStatus(@RequestParam String vaNumber) {
        try {
            return ResponseEntity.ok(camabaPaymentService.checkPaymentStatus(currentUserEmail(), vaNumber));
        } catch (Exception e) {
            log.error("Error checking payment status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/simulate-payment")
    public ResponseEntity<?> simulatePayment(@RequestParam String vaNumber) {
        try {
            return ResponseEntity.ok(camabaPaymentService.simulatePayment(vaNumber));
        } catch (Exception e) {
            log.error("Error simulating payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/payment/cicilan-confirm")
    public ResponseEntity<?> confirmCicilanPayment() {
        try {
            return ResponseEntity.ok(camabaPaymentService.confirmCicilanPayment(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error confirming cicilan payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}