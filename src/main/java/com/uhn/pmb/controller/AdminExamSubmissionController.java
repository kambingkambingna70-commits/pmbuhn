package com.uhn.pmb.controller;

import com.uhn.pmb.entity.ExamResult;
import com.uhn.pmb.repository.ExamResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@CrossOrigin(origins = "*", maxAge = 3600)
public class AdminExamSubmissionController {

    private final ExamResultRepository examResultRepository;

    @GetMapping("/exam-submissions")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getExamSubmissions(
            @RequestParam(defaultValue = "PENDING") String status) {
        try {
            ExamResult.ExamValidationStatus validationStatus;
            try {
                validationStatus = ExamResult.ExamValidationStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Status tidak valid: " + status));
            }

            List<ExamResult> results = examResultRepository.findByExamValidationStatus(validationStatus);
            List<Map<String, Object>> response = results.stream()
                    .map(this::toResponseMap)
                    .collect(Collectors.toList());

            log.info("[EXAM-SUBMISSION] GET status={} → {} records", status, response.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting exam submissions: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Gagal mengambil data: " + e.getMessage()));
        }
    }

    @GetMapping("/exam-submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getExamSubmissionDetail(@PathVariable Long id) {
        try {
            ExamResult r = examResultRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Data tidak ditemukan: " + id));
            return ResponseEntity.ok(toResponseMap(r));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting exam submission detail id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Gagal mengambil data"));
        }
    }

    @PostMapping("/exam-submissions/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> validateExamSubmission(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        try {
            ExamResult r = examResultRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Data tidak ditemukan: " + id));

            String action = body.getOrDefault("action", "").toUpperCase();
            String adminNotes = body.getOrDefault("adminNotes", "").trim();

            if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Action tidak valid. Gunakan APPROVE atau REJECT."));
            }

            if ("REJECT".equals(action) && adminNotes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Alasan penolakan wajib diisi."));
            }

            if ("APPROVE".equals(action)) {
                r.setExamValidationStatus(ExamResult.ExamValidationStatus.APPROVED);
                log.info("[EXAM-SUBMISSION] id={} APPROVED by {}", id, authentication.getName());
            } else {
                r.setExamValidationStatus(ExamResult.ExamValidationStatus.REJECTED);
                log.info("[EXAM-SUBMISSION] id={} REJECTED by {}: {}", id, authentication.getName(), adminNotes);
            }

            if (!adminNotes.isEmpty()) {
                r.setAdminNotes(adminNotes);
            }
            r.setExamValidatedAt(LocalDateTime.now());
            examResultRepository.save(r);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "APPROVE".equals(action) ? "Ujian berhasil diterima" : "Ujian berhasil ditolak",
                    "validationStatus", r.getExamValidationStatus().name()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error validating exam submission id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Gagal memvalidasi: " + e.getMessage()));
        }
    }

    private Map<String, Object> toResponseMap(ExamResult r) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", r.getId());
        if (r.getStudent() != null) {
            item.put("studentId", r.getStudent().getId());
            item.put("studentName", r.getStudent().getFullName());
            item.put("studentEmail",
                    r.getStudent().getUser() != null ? r.getStudent().getUser().getEmail() : null);
        } else {
            item.put("studentId", null);
            item.put("studentName", null);
            item.put("studentEmail", null);
        }
        item.put("gformScore", r.getGformScore());
        item.put("generatedToken", r.getGeneratedToken());
        item.put("studentInputToken", r.getStudentInputToken());
        item.put("tokenValidated", r.getTokenValidated());
        item.put("proofPhotoPath", r.getProofPhotoPath());
        item.put("submissionDate", r.getSubmissionDate());
        item.put("adminNotes", r.getAdminNotes());
        item.put("validationStatus",
                r.getExamValidationStatus() != null ? r.getExamValidationStatus().name() : "PENDING");
        return item;
    }
}
