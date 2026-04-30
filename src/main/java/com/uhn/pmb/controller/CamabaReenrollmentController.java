package com.uhn.pmb.controller;

import com.uhn.pmb.entity.HasilAkhir;
import com.uhn.pmb.entity.Student;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.repository.StudentRepository;
import com.uhn.pmb.repository.UserRepository;
import com.uhn.pmb.service.CamabaReenrollmentService;
import com.uhn.pmb.service.HasilAkhirService;
import jakarta.servlet.http.HttpServletRequest;
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
 * Handles student re-enrollment endpoints. Delegates business logic to CamabaReenrollmentService.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaReenrollmentController {

    private final CamabaReenrollmentService camabaReenrollmentService;
    private final HasilAkhirService hasilAkhirService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/reenrollment/submit")
    public ResponseEntity<?> submitReenrollment(
            @RequestParam String parentPhone,
            @RequestParam String parentEmail,
            @RequestParam String parentAddress,
            @RequestParam String permanentAddress,
            @RequestParam(defaultValue = "") String currentAddress,
            @RequestParam(defaultValue = "false") Boolean alumniFamily,
            @RequestParam(required = false) String alumniName,
            @RequestParam(required = false) String alumniRelation,
            @RequestParam Map<String, MultipartFile> documents) {
        try {
            return ResponseEntity.ok(camabaReenrollmentService.submitReenrollment(
                    currentUserEmail(), parentPhone, parentEmail, parentAddress,
                    permanentAddress, currentAddress, alumniFamily, alumniName,
                    alumniRelation, documents));
        } catch (Exception e) {
            log.error("Error submitting re-enrollment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", "Gagal memproses daftar ulang: " + e.getMessage(),
                    "error", e.getClass().getSimpleName()));
        }
    }

    @GetMapping("/reenrollment/status")
    public ResponseEntity<?> getReenrollmentStatus() {
        try {
            return ResponseEntity.ok(camabaReenrollmentService.getReenrollmentStatus(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error getting reenrollment status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reenrollment/complete")
    public ResponseEntity<?> completeReenrollment(@RequestBody(required = false) Map<String, Object> request) {
        try {
            String submittedAt = (request != null && request.containsKey("submittedAt"))
                    ? String.valueOf(request.get("submittedAt")) : null;
            return ResponseEntity.ok(camabaReenrollmentService.completeReenrollment(currentUserEmail(), submittedAt));
        } catch (Exception e) {
            log.error("Error completing reenrollment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Gagal menandai daftar ulang sebagai selesai: " + e.getMessage(),
                    "error", e.getClass().getSimpleName()));
        }
    }

    @GetMapping("/reenrollment")
    public ResponseEntity<?> getReenrollmentData() {
        try {
            return ResponseEntity.ok(camabaReenrollmentService.getReenrollmentData(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error getting reenrollment data: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping(value = "/reenrollment/{id}", consumes = "multipart/form-data")
    public ResponseEntity<?> updateReenrollmentData(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(camabaReenrollmentService.updateReenrollmentData(
                    currentUserEmail(), id, request));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        } catch (Exception e) {
            log.error("Error updating reenrollment: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reenrollment/{id}/documents")
    public ResponseEntity<?> getReenrollmentDocuments(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(camabaReenrollmentService.getReenrollmentDocuments(
                    currentUserEmail(), id));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        } catch (Exception e) {
            log.error("Error getting documents: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/hasil-akhir")
    public ResponseEntity<?> getHasilAkhir() {
        try {
            String email = currentUserEmail();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Student student = studentRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            Optional<HasilAkhir> hasilAkhirOpt = hasilAkhirService.getHasilAkhirByStudentId(student.getId());
            if (hasilAkhirOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("exists", false, "message", "Hasil akhir belum tersedia"));
            }
            HasilAkhir hasilAkhir = hasilAkhirOpt.get();
            return ResponseEntity.ok(Map.of(
                    "exists", true,
                    "id", hasilAkhir.getId(),
                    "brivaNumber", String.valueOf(hasilAkhir.getBrivaNumber()),
                    "brivaAmount", hasilAkhir.getBrivaAmount(),
                    "nomorRegistrasi", String.valueOf(hasilAkhir.getNomorRegistrasi()),
                    "status", hasilAkhir.getStatus().toString(),
                    "npmSementaraFile", String.valueOf(hasilAkhir.getNpmSementaraFile()),
                    "ktmSementaraFile", String.valueOf(hasilAkhir.getKtmSementaraFile()),
                    "createdAt", hasilAkhir.getCreatedAt(),
                    "updatedAt", hasilAkhir.getUpdatedAt()
            ));
        } catch (Exception e) {
            log.error("Error getting hasil akhir: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}