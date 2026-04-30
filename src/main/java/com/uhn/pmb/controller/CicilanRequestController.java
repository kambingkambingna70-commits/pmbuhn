package com.uhn.pmb.controller;

import com.uhn.pmb.dto.CicilanRequestDTO;
import com.uhn.pmb.request.CicilanRequestSubmitRequest;
import com.uhn.pmb.service.CicilanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * REST Controller untuk Cicilan Request (Student)
 * HANYA menggunakan CicilanService - tidak ada repository access di sini
 */
@Slf4j
@RestController
@RequestMapping("/api/cicilan")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class CicilanRequestController {

    private final CicilanService cicilanService;

    /**
     * Get cicilan by admission form ID
     * GET /api/cicilan/status/{admissionFormId}
     */
    @GetMapping("/status/{admissionFormId}")
    public ResponseEntity<?> getCicilanStatus(@PathVariable Long admissionFormId) {
        try {
            Optional<CicilanRequestDTO> cicilan = cicilanService.getCicilanByAdmissionFormId(admissionFormId);
            if (cicilan.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Belum ada request cicilan"
                ));
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", cicilan.get()
            ));
        } catch (Exception e) {
            log.error("Error getting cicilan status", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Get cicilan status for authenticated user (no admissionFormId needed)
     * GET /api/cicilan/my-status
     */
    @GetMapping("/my-status")
    public ResponseEntity<?> getMyCicilanStatus(Authentication auth) {
        try {
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }

            String email = extractEmail(auth);
            Optional<CicilanRequestDTO> cicilan = cicilanService.getMyCicilan(email);
            
            if (cicilan.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Belum ada request cicilan"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", cicilan.get()
            ));
        } catch (Exception e) {
            log.error("Error getting my cicilan status", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Submit cicilan request (Student)
     * POST /api/cicilan/submit
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitCicilanRequest(@RequestBody CicilanRequestSubmitRequest request, Authentication auth) {
        try {
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }

            String email = extractEmail(auth);
            CicilanRequestDTO dto = cicilanService.submitCicilanRequest(email, request);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Request cicilan berhasil disubmit",
                "data", dto
            ));
        } catch (RuntimeException e) {
            log.error("Error submitting cicilan request", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Unexpected error submitting cicilan request", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Mark cicilan payment as submitted (after manual payment proof upload)
     * PUT /api/cicilan/{id}/payment-submitted
     */
    @PutMapping("/{id}/payment-submitted")
    public ResponseEntity<?> markPaymentSubmitted(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> request,
            Authentication auth) {
        try {
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }

            String email = extractEmail(auth);
            CicilanRequestDTO dto = cicilanService.markPaymentSubmitted(id, email);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Status cicilan diperbarui - menunggu verifikasi admin",
                "data", dto
            ));
        } catch (RuntimeException e) {
            log.error("Error marking payment as submitted", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Unexpected error marking payment as submitted", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Extract email from authentication principal
     */
    private String extractEmail(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        return principal.toString();
    }
}
