package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.dto.ProgramStudiRequest;
import com.uhn.pmb.service.ProgramStudiManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminProgramStudiController {

    private final ProgramStudiManagementService programStudiManagementService;

    @PostMapping("/program-studi")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createProgramStudi(@Valid @RequestBody ProgramStudiRequest request) {
        try {
            programStudiManagementService.createProgramStudiFull(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Program Studi created successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/program-studi")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllProgramStudi() {
        try {
            return ResponseEntity.ok(programStudiManagementService.getAllProgramStudiWithDetails());
        } catch (Exception e) {
            log.error("Error fetching program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/program-studi/active")
    public ResponseEntity<?> getActiveProgramStudi() {
        try {
            return ResponseEntity.ok(programStudiManagementService.getActiveProgramStudiSimple());
        } catch (Exception e) {
            log.error("Error fetching active program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/program-studi/{id}")
    public ResponseEntity<?> getProgramStudiById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(programStudiManagementService.getProgramStudiById(id));
        } catch (Exception e) {
            log.error("Error fetching program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/program-studi/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateProgramStudi(@PathVariable Long id,
                                                @Valid @RequestBody ProgramStudiRequest request) {
        try {
            programStudiManagementService.updateProgramStudiById(id, request);
            return ResponseEntity.ok(new ApiResponse(true, "Program Studi updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/program-studi/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteProgramStudi(@PathVariable Long id) {
        try {
            programStudiManagementService.deleteProgramStudiById(id);
            return ResponseEntity.ok(new ApiResponse(true, "Program Studi deleted successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/program-studi/available")
    public ResponseEntity<?> getAvailableProgramStudi() {
        try {
            return ResponseEntity.ok(programStudiManagementService.getActiveProgramStudiSimple());
        } catch (Exception e) {
            log.error("Error fetching available program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/jenis-seleksi/{jenisSeleksiId}/program-studi")
    public ResponseEntity<?> getProgramStudiByJenisSeleksi(@PathVariable Long jenisSeleksiId) {
        try {
            return ResponseEntity.ok(programStudiManagementService.getProgramStudiByJenisSeleksi(jenisSeleksiId));
        } catch (Exception e) {
            log.error("Error fetching program studi for jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/program-studi/bulk-initialize")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> bulkInitializeProgramStudi() {
        try {
            return ResponseEntity.ok(programStudiManagementService.bulkInitializeProgramStudi());
        } catch (Exception e) {
            log.error("Error bulk initializing program studi: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
}