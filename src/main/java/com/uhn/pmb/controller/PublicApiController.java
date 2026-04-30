package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.service.PublicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class PublicApiController {

    private final PublicDataService publicDataService;

    @GetMapping("/gelombang")
    public ResponseEntity<?> getAllGelombang() {
        try {
            return ResponseEntity.ok(publicDataService.getAllGelombang());
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error loading gelombang: " + e.getMessage()));
        }
    }

    @GetMapping("/jenis-seleksi")
    public ResponseEntity<?> getAllJenisSeleksi() {
        try {
            return ResponseEntity.ok(publicDataService.getAllActiveJenisSeleksi());
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error loading jenis seleksi: " + e.getMessage()));
        }
    }

    @GetMapping("/program-studi")
    public ResponseEntity<?> getAllProgramStudi() {
        try {
            return ResponseEntity.ok(publicDataService.getAllActiveProgramStudi());
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error loading program studi: " + e.getMessage()));
        }
    }

    @GetMapping("/program-studi/{id}")
    public ResponseEntity<?> getProgramStudiById(@PathVariable Long id) {
        try {
            var opt = publicDataService.getProgramStudiById(id);
            if (opt.isPresent()) {
                return ResponseEntity.ok(opt.get());
            }
            return ResponseEntity.ok(new ApiResponse(false, "Program studi tidak ditemukan"));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error loading program studi: " + e.getMessage()));
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getPublicSettings() {
        try {
            return ResponseEntity.ok(Map.of("videoTutorialUrl", "https://www.youtube.com/embed/dQw4w9WgXcQ"));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error loading settings: " + e.getMessage()));
        }
    }

    @GetMapping("/publication-status/{periodId}")
    public ResponseEntity<?> getPublicationStatus(@PathVariable Long periodId) {
        try {
            return ResponseEntity.ok(publicDataService.getPublicationStatus(periodId));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/fakultas")
    public ResponseEntity<?> getAllFakultas() {
        try {
            return ResponseEntity.ok(publicDataService.getAllFakultas());
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/program-studi/by-fakultas")
    public ResponseEntity<?> getProgramStudiByFakultas() {
        try {
            return ResponseEntity.ok(publicDataService.getProgramStudiByFakultas());
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
}