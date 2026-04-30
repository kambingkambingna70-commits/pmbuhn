package com.uhn.pmb.controller;

import com.uhn.pmb.entity.Exam;
import com.uhn.pmb.repository.UserRepository;
import com.uhn.pmb.service.CamabaExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * Handles student exam endpoints. Delegates business logic to CamabaExamService.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaExamController {

    private final CamabaExamService camabaExamService;
    private final UserRepository userRepository;

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/submit-exam")
    public ResponseEntity<?> submitExam(@RequestBody Map<String, Object> request) {
        try {
            return ResponseEntity.ok(camabaExamService.submitExam(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error submitting exam: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/exam")
    public ResponseEntity<?> getExamDetails() {
        try {
            Optional<Exam> examOpt = camabaExamService.getExamDetails(currentUserEmail());
            if (examOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "No exam found"));
            }
            return ResponseEntity.ok(examOpt.get());
        } catch (Exception e) {
            log.error("Error fetching exam: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/exam-validation-status")
    public ResponseEntity<?> getExamValidationStatus() {
        try {
            return ResponseEntity.ok(camabaExamService.getExamValidationStatus(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error fetching exam validation status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/exam-token")
    public ResponseEntity<?> getExamToken() {
        try {
            Map<String, Object> result = camabaExamService.getExamToken(currentUserEmail());
            Boolean success = (Boolean) result.getOrDefault("success", false);
            if (Boolean.FALSE.equals(success)) {
                return ResponseEntity.status(404).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting exam token: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/trigger-token-generation")
    public ResponseEntity<?> triggerTokenGeneration() {
        try {
            Map<String, Object> result = camabaExamService.triggerTokenGeneration(currentUserEmail());
            Boolean success = (Boolean) result.getOrDefault("success", false);
            if (Boolean.FALSE.equals(success)) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error triggering token generation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/exam/start")
    public ResponseEntity<?> markExamAsStarted() {
        try {
            return ResponseEntity.ok(camabaExamService.markExamAsStarted(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error marking exam as started: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/exam/submit-results")
    public ResponseEntity<?> submitExamResults(
            @RequestParam String examToken,
            @RequestParam Double gformScore,
            @RequestParam(required = false) MultipartFile proofPhoto) {
        try {
            return ResponseEntity.ok(camabaExamService.submitExamResults(
                    currentUserEmail(), examToken, gformScore, proofPhoto));
        } catch (Exception e) {
            log.error("Error submitting exam results: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false,
                    "message", "Gagal submit hasil ujian: " + e.getMessage()));
        }
    }
}