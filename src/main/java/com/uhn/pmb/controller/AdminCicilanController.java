package com.uhn.pmb.controller;

import com.uhn.pmb.dto.CicilanRequestDTO;
import com.uhn.pmb.service.AdminCicilanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller untuk admin cicilan request management
 * HANYA menggunakan AdminCicilanService - tidak ada repository access di sini
 */
@Slf4j
@RestController
@RequestMapping("/admin/cicilan")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
public class AdminCicilanController {

    private final AdminCicilanService adminCicilanService;

    /**
     * Get pending cicilan requests (paginated)
     * GET /admin/cicilan/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<CicilanRequestDTO> dtos = adminCicilanService.getPendingRequests(pageable);
            
            return ResponseEntity.ok(new PageResponse(
                dtos.getContent(),
                dtos.getTotalElements(),
                dtos.getTotalPages()
            ));
        } catch (Exception e) {
            log.error("Error getting pending requests", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Get cicilan requests by status (paginated)
     * GET /admin/cicilan/by-status/{status}
     */
    @GetMapping("/by-status/{status}")
    public ResponseEntity<?> getByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<CicilanRequestDTO> dtos = adminCicilanService.getByStatus(status, pageable);
            
            return ResponseEntity.ok(new PageResponse(
                dtos.getContent(),
                dtos.getTotalElements(),
                dtos.getTotalPages()
            ));
        } catch (Exception e) {
            log.error("Error getting cicilan by status", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Approve cicilan request
     * PUT /admin/cicilan/{id}/approve
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveCicilanRequest(
            @PathVariable Long id,
            @RequestBody AdminCicilanService.ApproveRequest request) {
        try {
            CicilanRequestDTO dto = adminCicilanService.approveCicilanRequest(id, request);
            return ResponseEntity.ok(new SuccessResponse("Cicilan request disetujui", dto));
        } catch (RuntimeException e) {
            log.error("Error approving cicilan request", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error approving cicilan request", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Reject cicilan request
     * PUT /admin/cicilan/{id}/reject
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectCicilanRequest(
            @PathVariable Long id,
            @RequestBody AdminCicilanService.RejectRequest request) {
        try {
            CicilanRequestDTO dto = adminCicilanService.rejectCicilanRequest(id, request);
            return ResponseEntity.ok(new SuccessResponse("Cicilan request ditolak dan email telah dikirim", dto));
        } catch (RuntimeException e) {
            log.error("Error rejecting cicilan request", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error rejecting cicilan request", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * Delete cicilan request
     * DELETE /admin/cicilan/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCicilanRequest(@PathVariable Long id) {
        try {
            adminCicilanService.deleteCicilanRequest(id);
            return ResponseEntity.ok(new SuccessResponse("Cicilan request berhasil dihapus", null));
        } catch (RuntimeException e) {
            log.error("Error deleting cicilan request", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting cicilan request", e);
            return ResponseEntity.badRequest().body(new ErrorResponse("Error: " + e.getMessage()));
        }
    }

    // ===== RESPONSE DTOs =====
    
    public static class ErrorResponse {
        public String message;
        public ErrorResponse(String message) { this.message = message; }
    }

    public static class SuccessResponse {
        public String message;
        public Object data;
        public SuccessResponse(String message, Object data) {
            this.message = message;
            this.data = data;
        }
    }

    public static class PageResponse {
        public Object content;
        public long totalElements;
        public int totalPages;
        public PageResponse(Object content, long totalElements, int totalPages) {
            this.content = content;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }
    }
}
