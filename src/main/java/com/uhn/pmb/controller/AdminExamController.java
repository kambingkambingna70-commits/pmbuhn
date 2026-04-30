package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApproveExamQuestionRequest;
import com.uhn.pmb.dto.GenerateExamQuestionRequest;
import com.uhn.pmb.entity.ExamQuestion;
import com.uhn.pmb.service.AdminExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/api/exam-questions")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminExamController {

    private final AdminExamService adminExamService;

    @PostMapping("/generate-ai")
    public ResponseEntity<?> generateExamQuestion(
            @RequestBody GenerateExamQuestionRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("error", "User belum login atau session sudah expired. Silakan login kembali."));
            }
            String email = authentication.getName();
            if (email == null || email.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "User email tidak ditemukan. Silakan login kembali."));
            }
            if (!adminExamService.isApiKeyConfigured()) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Gemini API Key belum dikonfigurasi.",
                        "type", "API_KEY_NOT_CONFIGURED"));
            }
            List<ExamQuestion> saved = adminExamService.generateQuestions(request, email);
            if (saved.isEmpty()) {
                return ResponseEntity.status(500).body(Map.of("error", "Gagal generate soal."));
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Berhasil generate " + saved.size() + " soal.", "count", saved.size(), "questions", saved));
        } catch (Exception e) {
            log.error("Error generating exam question: {}", e.getMessage(), e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log.error("Full stack trace: {}", sw.toString());
            String errorMsg = "Gagal generate soal: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
            if (e.getMessage() != null) {
                if (e.getMessage().contains("API key") || e.getMessage().contains("Configuration")) {
                    errorMsg = "Konfigurasi API key tidak valid.";
                } else if (e.getMessage().contains("quota")) {
                    errorMsg = "Quota Gemini API telah habis.";
                } else if (e.getMessage().contains("rate_limit")) {
                    errorMsg = "Rate limit Gemini API tercapai.";
                }
            }
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error", "userMessage", errorMsg));
        }
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingQuestions() {
        try {
            List<ExamQuestion> pendingQuestions = adminExamService.getPendingQuestions();
            return ResponseEntity.ok(Map.of("success", true, "count", pendingQuestions.size(), "questions", pendingQuestions));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Gagal mengambil soal pending"));
        }
    }

    @GetMapping("/by-category/{category}")
    public ResponseEntity<?> getQuestionsByCategory(@PathVariable String category) {
        try {
            List<ExamQuestion> questions = adminExamService.getQuestionsByCategory(category);
            return ResponseEntity.ok(Map.of("success", true, "category", category, "count", questions.size(), "questions", questions));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Gagal mengambil soal berdasarkan kategori"));
        }
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveQuestion(
            @PathVariable Long id,
            @RequestBody ApproveExamQuestionRequest request,
            Authentication authentication) {
        try {
            ExamQuestion updated = adminExamService.approveQuestion(id, request, authentication.getName());
            return ResponseEntity.ok(Map.of("success", true, "message", request.isApproved() ? "Soal disetujui" : "Soal ditolak", "question", updated));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("tidak ditemukan")) {
                return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("error", "Gagal meng-approve soal"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long id) {
        try {
            adminExamService.deleteQuestion(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Soal berhasil dihapus"));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("tidak ditemukan")) {
                return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("error", "Gagal menghapus soal"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getQuestion(@PathVariable Long id) {
        try {
            ExamQuestion question = adminExamService.getQuestion(id);
            return ResponseEntity.ok(Map.of("success", true, "question", question));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllQuestions(@RequestParam(required = false) String status) {
        try {
            List<ExamQuestion> questions = adminExamService.getAllQuestions(status);
            return ResponseEntity.ok(Map.of("success", true, "count", questions.size(), "questions", questions));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Gagal mengambil daftar soal"));
        }
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<?> getStatistics() {
        try {
            Map<String, Object> stats = adminExamService.getStatistics();
            java.util.Map<String, Object> response = new java.util.HashMap<>(stats);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Gagal mengambil statistik"));
        }
    }
}