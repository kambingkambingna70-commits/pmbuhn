package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.entity.Sma;
import com.uhn.pmb.service.SmaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class SmaController {

    private final SmaService smaService;

    @GetMapping("/api/sma/search")
    public ResponseEntity<List<Sma>> searchSma(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(smaService.search(q));
    }

    @GetMapping("/api/sma")
    public ResponseEntity<List<Sma>> getAllActiveSma() {
        return ResponseEntity.ok(smaService.findAllActive());
    }

    @GetMapping("/admin/api/sma")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<List<Sma>> getAllSmaForAdmin() {
        return ResponseEntity.ok(smaService.findAll());
    }

    @PostMapping("/admin/api/sma")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createSma(@RequestBody Map<String, String> body) {
        try {
            Sma sma = smaService.create(body);
            return ResponseEntity.ok(sma);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating SMA: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/admin/api/sma/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateSma(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Sma sma = smaService.update(id, body);
            return ResponseEntity.ok(sma);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating SMA: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/admin/api/sma/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteSma(@PathVariable Long id) {
        try {
            smaService.deactivate(id);
            return ResponseEntity.ok(new ApiResponse(true, "SMA berhasil dinonaktifkan"));
        } catch (Exception e) {
            log.error("Error deleting SMA: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(new ApiResponse(false, e.getMessage()));
        }
    }
}