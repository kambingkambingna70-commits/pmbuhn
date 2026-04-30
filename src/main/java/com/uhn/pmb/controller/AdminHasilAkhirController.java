package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.service.AdminHasilAkhirService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminHasilAkhirController {

    private final AdminHasilAkhirService adminHasilAkhirService;

    @PostMapping("/api/hasil-akhir/{id}/upload-dokumen")
    @PreAuthorize("hasRole('ADMIN_VALIDASI')")
    public ResponseEntity<?> uploadDokumenSementara(
            @PathVariable Long id,
            @RequestParam(value = "npmSementara", required = false) MultipartFile npmFile,
            @RequestParam(value = "ktmSementara", required = false) MultipartFile ktmFile) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            log.info("📄 Admin {} uploading dokumen sementara for HasilAkhir #{}", email, id);
            Map<String, Object> result = adminHasilAkhirService.uploadDokumenSementara(id, npmFile, ktmFile);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error uploading dokumen sementara: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/hasil-akhir/all")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllHasilAkhir() {
        try {
            List<Map<String, Object>> results = adminHasilAkhirService.getAllHasilAkhirForAdmin();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("❌ Error getting all hasil akhir: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
}