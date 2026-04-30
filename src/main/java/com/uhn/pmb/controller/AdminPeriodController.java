package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.dto.RegistrationPeriodRequest;
import com.uhn.pmb.dto.SelectionTypeRequest;
import com.uhn.pmb.service.PeriodManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminPeriodController {

    private final PeriodManagementService periodManagementService;

    @PostMapping("/periods")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createRegistrationPeriod(@Valid @RequestBody RegistrationPeriodRequest request) {
        try {
            periodManagementService.createPeriod(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Registration period created successfully"));
        } catch (Exception e) {
            log.error("Error creating registration period: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllPeriods() {
        try {
            return ResponseEntity.ok(periodManagementService.getAllPeriods());
        } catch (Exception e) {
            log.error("Error fetching periods: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/periods/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @Transactional
    public ResponseEntity<?> updateRegistrationPeriod(@PathVariable Long id,
                                                      @Valid @RequestBody RegistrationPeriodRequest request) {
        try {
            periodManagementService.updatePeriod(id, request);
            return ResponseEntity.ok(new ApiResponse(true, "Periode berhasil diperbarui"));
        } catch (Exception e) {
            log.error("Error updating period: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/periods/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @Transactional
    public ResponseEntity<?> deleteRegistrationPeriod(@PathVariable Long id) {
        try {
            periodManagementService.deletePeriod(id);
            return ResponseEntity.ok(new ApiResponse(true, "Periode berhasil dihapus"));
        } catch (Exception e) {
            log.error("Error deleting period: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/jenis-seleksi/available")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllAvailableJenisSeleksi() {
        try {
            return ResponseEntity.ok(periodManagementService.getAllAvailableJenisSeleksi());
        } catch (Exception e) {
            log.error("Error fetching available jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/periods/{periodId}/jenis-seleksi")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getJenisSeleksiByPeriod(@PathVariable Long periodId) {
        try {
            List<Map<String, Object>> data = periodManagementService.getJenisSeleksiByPeriod(periodId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("periodId", periodId);
            response.put("data", data);
            response.put("total", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching jenis seleksi for period: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/selection-types")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createSelectionType(@Valid @RequestBody SelectionTypeRequest request) {
        try {
            periodManagementService.createSelectionType(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Selection type created successfully"));
        } catch (Exception e) {
            log.error("Error creating selection type: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/selection-types/period/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getSelectionTypesByPeriod(@PathVariable Long periodId) {
        try {
            List<Map<String, Object>> data = periodManagementService.getSelectionTypesByPeriod(periodId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("periodId", periodId);
            response.put("data", data);
            response.put("total", data.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching selection types: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/api/selection-types/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteSelectionType(@PathVariable Long id) {
        try {
            periodManagementService.deleteSelectionType(id);
            return ResponseEntity.ok(new ApiResponse(true, "Jenis seleksi berhasil dihapus"));
        } catch (Exception e) {
            log.error("Error deleting selection type: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}