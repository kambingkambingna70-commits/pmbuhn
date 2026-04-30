package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.entity.RegistrationPeriod;
import com.uhn.pmb.repository.RegistrationPeriodRepository;
import com.uhn.pmb.service.AdminDataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminExportController {

    private final AdminDataExportService adminDataExportService;
    private final RegistrationPeriodRepository registrationPeriodRepository;

    @GetMapping("/api/wave-types")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getWaveTypes() {
        try {
            // Get unique wave types from registration_periods table
            List<RegistrationPeriod> periods = registrationPeriodRepository.findAll();
            
            List<Map<String, String>> waveTypes = periods.stream()
                    .filter(p -> p.getWaveType() != null)
                    .map(p -> p.getWaveType())
                    .distinct()
                    .map(waveType -> {
                        Map<String, String> map = new HashMap<>();
                        map.put("value", waveType.toString());
                        // Format label dengan mengganti underscore dan uppercase ke format readable
                        String label = waveType.toString()
                                .replace("_", " ")
                                .replaceAll("([A-Z])", " $1")
                                .trim()
                                .replaceAll(" +", " ");
                        map.put("label", label);
                        return map;
                    })
                    .sorted((a, b) -> a.get("value").compareTo(b.get("value")))
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", waveTypes);
            response.put("total", waveTypes.size());
            response.put("timestamp", LocalDateTime.now());
            
            log.info("✅ Wave types loaded: {} types", waveTypes.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error fetching wave types: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error fetching wave types: " + e.getMessage()));
        }
    }

    @GetMapping("/api/export/formulir-pembayaran")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportFormAndPayment() {
        try {
            List<Map<String, Object>> data = adminDataExportService.exportFormAndPayment();
            return ResponseEntity.ok(Map.of("success", true, "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Error exporting form and payment data: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/export/daftar-ulang")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportReEnrollmentData() {
        try {
            List<Map<String, Object>> data = adminDataExportService.exportReEnrollmentData();
            return ResponseEntity.ok(Map.of("success", true, "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Error exporting reenrollment data: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/export/hasil-akhir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportHasilAkhirData() {
        try {
            List<Map<String, Object>> data = adminDataExportService.exportHasilAkhirData();
            return ResponseEntity.ok(Map.of("success", true, "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("Error exporting hasil akhir data: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/table/admission-forms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAdmissionFormsTable() {
        try {
            List<Map<String, Object>> data = adminDataExportService.getAdmissionFormsTable();
            return ResponseEntity.ok(Map.of("success", true, "totalRecords", data.size(), "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("❌ Error fetching admission forms table: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/table/reenrollments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getReenrollmentsTable() {
        try {
            List<Map<String, Object>> data = adminDataExportService.getReenrollmentsTable();
            return ResponseEntity.ok(Map.of("success", true, "totalRecords", data.size(), "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("❌ Error fetching reenrollments table: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/table/hasil-akhir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getHasilAkhirTable() {
        try {
            List<Map<String, Object>> data = adminDataExportService.getHasilAkhirTable();
            return ResponseEntity.ok(Map.of("success", true, "totalRecords", data.size(), "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("❌ Error fetching hasil akhir table: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/table/hasil-akhir/by-wave/{waveType}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getHasilAkhirByWave(@PathVariable String waveType) {
        try {
            RegistrationPeriod.WaveType wave;
            try {
                wave = RegistrationPeriod.WaveType.valueOf(waveType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Invalid wave type: " + waveType));
            }
            List<Map<String, Object>> data = adminDataExportService.getHasilAkhirByWave(wave);
            return ResponseEntity.ok(Map.of("success", true, "waveType", waveType, "totalRecords", data.size(), "data", data, "timestamp", LocalDateTime.now()));
        } catch (Exception e) {
            log.error("❌ Error fetching hasil akhir by wave: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}