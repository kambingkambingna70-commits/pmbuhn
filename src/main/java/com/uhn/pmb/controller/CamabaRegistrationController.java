package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.entity.RegistrationPeriod;
import com.uhn.pmb.entity.SelectionType;
import com.uhn.pmb.repository.RegistrationPeriodRepository;
import com.uhn.pmb.repository.SelectionTypeRepository;
import com.uhn.pmb.repository.UserRepository;
import com.uhn.pmb.service.CamabaRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles public and authenticated registration lookups. Delegates business logic to CamabaRegistrationService.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaRegistrationController {

    private final CamabaRegistrationService camabaRegistrationService;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final SelectionTypeRepository selectionTypeRepository;
    private final UserRepository userRepository;

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/select-gelombang")
    public ResponseEntity<?> selectGelombang(@RequestBody Map<String, Long> request) {
        try {
            Long gelombangId = request.get("gelombangId");
            if (gelombangId == null) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Gelombang ID is required"));
            }
            String email = currentUserEmail();
            userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
            log.info("Student {} selected gelombang {}", email, gelombangId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Gelombang berhasil dipilih", "gelombangId", gelombangId));
        } catch (Exception e) {
            log.error("Error selecting gelombang: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/registration-periods")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getOpenRegistrationPeriods() {
        try {
            List<RegistrationPeriod> periods = registrationPeriodRepository
                    .findByStatusOrderByRegStartDateDesc(RegistrationPeriod.Status.OPEN);
            return ResponseEntity.ok(periods);
        } catch (Exception e) {
            log.error("Error fetching periods: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/registration-periods/{id}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getRegistrationPeriodById(@PathVariable Long id) {
        try {
            return registrationPeriodRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error fetching period {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/all-gelombang")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getAllGelombang() {
        try {
            return ResponseEntity.ok(camabaRegistrationService.getAllGelombang());
        } catch (Exception e) {
            log.error("Error fetching all gelombang: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/all-formulas")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getAllFormulas(@RequestParam(value = "periodId", required = false) Long periodId) {
        try {
            return ResponseEntity.ok(camabaRegistrationService.getAllFormulas(periodId));
        } catch (Exception e) {
            log.error("Error fetching formulas: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error fetching formulas: " + e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi/{jenisSeleksiId}/program-studi")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getProgramStudiByJenisSeleksi(@PathVariable Long jenisSeleksiId) {
        try {
            return ResponseEntity.ok(camabaRegistrationService.getProgramStudiByJenisSeleksi(jenisSeleksiId));
        } catch (Exception e) {
            log.error("Error fetching program studi: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error fetching program studi: " + e.getMessage()));
        }
    }

    @GetMapping("/selection-types/{periodId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSelectionTypes(@PathVariable Long periodId) {
        try {
            List<SelectionType> types = selectionTypeRepository.findByPeriod_IdAndIsActiveTrue(periodId);
            return ResponseEntity.ok(types);
        } catch (Exception e) {
            log.error("Error fetching selection types: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/selection-types-detail/{selectionTypeId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getSelectionTypeDetail(@PathVariable Long selectionTypeId) {
        try {
            return ResponseEntity.ok(camabaRegistrationService.getSelectionTypeDetail(selectionTypeId));
        } catch (Exception e) {
            log.error("Error fetching selection type detail: {}", e.getMessage(), e);
            return ResponseEntity.status(404).body(new ApiResponse(false, "Selection type not found: " + e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi-detail/{jenisSeleksiId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getJenisSeleksiDetail(@PathVariable Long jenisSeleksiId) {
        try {
            return ResponseEntity.ok(camabaRegistrationService.getJenisSeleksiDetail(jenisSeleksiId));
        } catch (Exception e) {
            log.error("Error fetching jenis seleksi detail: {}", e.getMessage(), e);
            return ResponseEntity.status(404).body(new ApiResponse(false, "Jenis seleksi not found: " + e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi/{id}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getJenisSeleksiById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(camabaRegistrationService.getJenisSeleksiById(id));
        } catch (Exception e) {
            log.error("Error fetching jenis seleksi {}: {}", id, e.getMessage());
            return ResponseEntity.status(404).body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/select-formula")
    public ResponseEntity<?> selectFormula(@RequestBody Map<String, String> request) {
        try {
            String formula = request.get("formula");
            if (formula == null || formula.isEmpty()) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Formula is required"));
            }
            String email = currentUserEmail();
            userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Formula " + formula + " berhasil dipilih");
            response.put("formula", formula);
            response.put("price", formula.equals("Kedokteran") ? 1000000 : 250000);
            log.info("Student {} selected formula {}", email, formula);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error selecting formula: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}