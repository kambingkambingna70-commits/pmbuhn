package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.dto.JenisSeleksiRequest;
import com.uhn.pmb.service.JenisSeleksiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminJenisSeleksiController {

    private final JenisSeleksiService jenisSeleksiService;

    @PostMapping("/jenis-seleksi")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createJenisSeleksi(@Valid @RequestBody JenisSeleksiRequest request) {
        try {
            jenisSeleksiService.createWithProgramStudi(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Jenis Seleksi created successfully"));
        } catch (Exception e) {
            log.error("Error creating jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllJenisSeleksi() {
        try {
            return ResponseEntity.ok(jenisSeleksiService.getAll());
        } catch (Exception e) {
            log.error("Error fetching jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi/active")
    public ResponseEntity<?> getActiveJenisSeleksi() {
        try {
            return ResponseEntity.ok(jenisSeleksiService.getAllActive());
        } catch (Exception e) {
            log.error("Error fetching active jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi/{id}")
    public ResponseEntity<?> getJenisSeleksiById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(jenisSeleksiService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Jenis Seleksi not found")));
        } catch (Exception e) {
            log.error("Error fetching jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/jenis-seleksi/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @Transactional
    public ResponseEntity<?> updateJenisSeleksi(@PathVariable Long id,
                                                @Valid @RequestBody JenisSeleksiRequest request) {
        try {
            jenisSeleksiService.updateWithProgramStudi(id, request);
            return ResponseEntity.ok(new ApiResponse(true, "Jenis Seleksi updated successfully"));
        } catch (Exception e) {
            log.error("Error updating jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/jenis-seleksi/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @Transactional
    public ResponseEntity<?> deleteJenisSeleksi(@PathVariable Long id) {
        try {
            jenisSeleksiService.delete(id);
            return ResponseEntity.ok(new ApiResponse(true, "Jenis Seleksi deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}