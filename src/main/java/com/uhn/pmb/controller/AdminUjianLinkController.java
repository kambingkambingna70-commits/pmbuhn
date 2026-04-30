package com.uhn.pmb.controller;

import com.uhn.pmb.dto.UjianLinkRequest;
import com.uhn.pmb.entity.GelombangLinkUjian;
import com.uhn.pmb.service.AdminUjianLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * REST Controller untuk admin ujian links
 * HANYA menggunakan AdminUjianLinkService - tidak ada repository access di sini
 */
@Slf4j
@RestController
@RequestMapping("/admin/api/ujian-links")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class AdminUjianLinkController {

    private final AdminUjianLinkService ujianLinkService;

    /**
     * Get all ujian links
     * GET /admin/api/ujian-links
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllUjianLinks(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to getAllUjianLinks");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            var links = ujianLinkService.getAllLinks();
            log.info("✅ Retrieved {} ujian links", links.size());
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", links
            ));
        } catch (Exception e) {
            log.error("❌ Error retrieving ujian links", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Get ujian link by period ID
     * GET /admin/api/ujian-links/by-period/{periodId}
     * Accessible to CAMABA role as well
     */
    @GetMapping("/by-period/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getUjianLinkByPeriod(
            @PathVariable Long periodId,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to getUjianLinkByPeriod");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            Optional<GelombangLinkUjian> link = ujianLinkService.getByPeriodId(periodId);

            if (link.isEmpty()) {
                log.warn("⚠️ Ujian link not found for period ID: {}", periodId);
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Ujian link not found for this period"
                ));
            }

            log.info("✅ Retrieved ujian link for period ID: {}", periodId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", link.get()
            ));
        } catch (Exception e) {
            log.error("❌ Error retrieving ujian link for period {}", periodId, e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Save new ujian link (Online or Offline)
     * POST /admin/api/ujian-links
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> saveUjianLink(
            @RequestBody UjianLinkRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to saveUjianLink");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            GelombangLinkUjian saved = ujianLinkService.createLink(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ujian link saved successfully",
                "data", saved
            ));
        } catch (RuntimeException e) {
            log.error("❌ Error saving ujian link", e);
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Unexpected error saving ujian link", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Update ujian link
     * PUT /admin/api/ujian-links
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> updateUjianLink(
            @RequestBody UjianLinkRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to updateUjianLink");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            GelombangLinkUjian updated = ujianLinkService.updateLink(request);
            
            log.info("✅ Ujian link updated successfully for period: {}", request.getPeriodId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ujian link updated successfully",
                "data", updated
            ));
        } catch (RuntimeException e) {
            log.error("❌ Error updating ujian link", e);
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Unexpected error updating ujian link", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete ujian link
     * DELETE /admin/api/ujian-links/{periodId}
     */
    @DeleteMapping("/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> deleteUjianLink(
            @PathVariable Long periodId,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to deleteUjianLink");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            ujianLinkService.deleteByPeriodId(periodId);
            
            log.info("✅ Ujian link deleted successfully for period: {}", periodId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ujian link deleted successfully"
            ));
        } catch (RuntimeException e) {
            log.error("❌ Error deleting ujian link", e);
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Unexpected error deleting ujian link", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Save offline exam details
     * POST /admin/api/ujian-links/offline-exams
     */
    @PostMapping("/offline-exams")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> saveOfflineExam(
            @RequestBody UjianLinkRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to saveOfflineExam");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            GelombangLinkUjian saved = ujianLinkService.createOfflineExam(request);
            
            log.info("✅ Offline exam saved successfully for period: {}", request.getPeriodId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Offline exam saved successfully",
                "data", saved
            ));
        } catch (RuntimeException e) {
            log.error("❌ Error saving offline exam", e);
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Unexpected error saving offline exam", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete offline exam
     * DELETE /admin/api/ujian-links/offline-exams/{periodId}
     */
    @DeleteMapping("/offline-exams/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> deleteOfflineExam(
            @PathVariable Long periodId,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized access to deleteOfflineExam");
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Unauthorized"
                ));
            }

            ujianLinkService.deleteOfflineExam(periodId);
            
            log.info("✅ Offline exam deleted successfully for period: {}", periodId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Offline exam deleted successfully"
            ));
        } catch (RuntimeException e) {
            log.error("❌ Error deleting offline exam", e);
            return ResponseEntity.status(400).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Unexpected error deleting offline exam", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error: " + e.getMessage()
            ));
        }
    }
}
