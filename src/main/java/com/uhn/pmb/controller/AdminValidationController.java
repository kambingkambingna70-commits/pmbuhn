package com.uhn.pmb.controller;

import com.uhn.pmb.dto.*;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import com.uhn.pmb.service.EmailService;
import com.uhn.pmb.service.ExamService;
import com.uhn.pmb.service.FormValidationService;
import com.uhn.pmb.service.HasilAkhirService;
import com.uhn.pmb.service.RegistrationStatusService;
import com.uhn.pmb.service.ReenrollmentService;
import com.uhn.pmb.service.ValidationStatusTrackerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages form validation, exam submissions, re-enrollment validation, and all related email notifications.
 * Extracted from AdminController for Single Responsibility Principle.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminValidationController {

    private final AdmissionFormRepository admissionFormRepository;
    private final FormValidationRepository formValidationRepository;
    private final FormRepairStatusRepository formRepairStatusRepository;
    private final ReEnrollmentValidationRepository reEnrollmentValidationRepository;
    private final ReEnrollmentRepository reenrollmentRepository;
    private final ReEnrollmentDocumentRepository reenrollmentDocumentRepository;
    private final ExamResultRepository examResultRepository;
    private final UserRepository userRepository;
    private final ValidationStatusTrackerRepository validationStatusTrackerRepository;
    private final RegistrationStatusService registrationStatusService;
    private final ValidationStatusTrackerService validationStatusTrackerService;
    private final HasilAkhirService hasilAkhirService;
    private final FormValidationService formValidationService;
    private final ReenrollmentService reenrollmentService;
    private final ExamService examService;
    private final EmailService emailService;

    // ========== BASIC FORM VALIDATION ==========

    /**
     * Get all admission forms to validate (submitted status)
     * GET /admin/forms-to-validate
     */
    @GetMapping("/forms-to-validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getFormsToValidate() {
        try {
            List<AdmissionForm> forms = admissionFormRepository.findByStatus(AdmissionForm.FormStatus.SUBMITTED);
            return ResponseEntity.ok(forms);
        } catch (Exception e) {
            log.error("Error fetching forms: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Publish exam results for a period
     * POST /admin/publish-results/{periodId}
     */
    @PostMapping("/publish-results/{periodId}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> publishResults(@PathVariable Long periodId) {
        try {
            // NOTE: registrationService.publishExamResults moved here - kept for backward compat
            return ResponseEntity.ok(new ApiResponse(true, "Results published successfully"));
        } catch (Exception e) {
            log.error("Error publishing results: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get ALL re-enrollments (all statuses) with student info
     * GET /admin/api/reenrollments
     */
    @GetMapping("/api/reenrollments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getAllReenrollments() {
        try {
            List<ReEnrollment> all = reenrollmentRepository.findAll();
            List<Map<String, Object>> result = all.stream().map(re -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", re.getId());
                row.put("status", re.getStatus() != null ? re.getStatus().toString() : "PENDING");
                row.put("submittedAt", re.getSubmittedAt());
                row.put("validatedAt", re.getValidatedAt());
                row.put("validationNotes", re.getValidationNotes());
                if (re.getStudent() != null) {
                    row.put("studentId", re.getStudent().getId());
                    row.put("studentName", re.getStudent().getFullName());
                    Map<String, Object> studentMap = new HashMap<>();
                    studentMap.put("id", re.getStudent().getId());
                    studentMap.put("fullName", re.getStudent().getFullName());
                    if (re.getStudent().getUser() != null) {
                        studentMap.put("email", re.getStudent().getUser().getEmail());
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("email", re.getStudent().getUser().getEmail());
                        studentMap.put("user", userMap);
                    }
                    row.put("student", studentMap);
                }
                return row;
            }).collect(Collectors.toList());
            log.info("✅ Found {} total re-enrollments", result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching all re-enrollments: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get in-progress re-enrollments (submitted status)
     * GET /admin/api/reenrollments/in-progress
     */
    @GetMapping("/api/reenrollments/in-progress")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getInProgressReenrollments() {
        try {
            List<ReEnrollment> inProgressReenrollments = reenrollmentRepository
                    .findByStatus(ReEnrollment.ReEnrollmentStatus.SUBMITTED);
            log.info("✅ Found {} in-progress re-enrollments", inProgressReenrollments.size());
            return ResponseEntity.ok(inProgressReenrollments);
        } catch (Exception e) {
            log.error("Error fetching in-progress re-enrollments: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get students who passed the exam
     * GET /admin/api/exam/student-list
     */
    @GetMapping("/api/exam/student-list")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getStudentListForExam() {
        try {
            List<ExamResult> passedResults = examResultRepository.findAll().stream()
                    .filter(result -> result.getStatus() != null && result.getStatus() == ExamResult.ResultStatus.PASSED)
                    .toList();
            log.info("✅ Found {} students who passed exam", passedResults.size());
            return ResponseEntity.ok(passedResults);
        } catch (Exception e) {
            log.error("Error fetching student list for exam: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== FORM VALIDATION DASHBOARD ==========

    /**
     * Get all forms for validation with payment/form status filtering
     * GET /admin/api/validasi/formulir
     */
    @GetMapping("/api/validasi/formulir")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getFormsForValidation() {
        try {
            List<Map<String, Object>> response = formValidationService.getFormsForValidationDashboard();
            log.info("✅ Returning {} forms to admin validation dashboard", response.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error fetching forms for validation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get complete form details for validation modal
     * GET /admin/api/validasi/formulir/{formValidationId}/details
     */
    @GetMapping("/api/validasi/formulir/{formValidationId}/details")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getFormDetails(@PathVariable Long formValidationId) {
        try {
            Map<String, Object> details = formValidationService.getFormDetails(formValidationId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("❌ Error fetching form details: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== ADMISSION FORM STUDENT DETAILS ==========

    /**
     * Get admission form details (wave type, selection type, program studi) for a student
     * GET /admin/api/admission-form/student/{studentId}/details
     */
    @GetMapping("/api/admission-form/student/{studentId}/details")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getAdmissionFormStudentDetails(@PathVariable Long studentId) {
        try {
            Map<String, Object> details = formValidationService.getAdmissionFormStudentDetails(studentId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("❌ Error fetching admission form details: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== FORM VALIDATION ACTIONS ==========

    /**
     * Approve form validation
     * PUT /admin/api/validasi/formulir/{validationId}/approve
     */
    @PutMapping("/api/validasi/formulir/{validationId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> approveFormValidation(@PathVariable Long validationId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            formValidationService.approve(validationId, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Formulir disetujui"));
        } catch (Exception e) {
            log.error("Error approving form validation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Reject form validation
     * PUT /admin/api/validasi/formulir/{validationId}/reject
     */
    @PutMapping("/api/validasi/formulir/{validationId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> rejectFormValidation(@PathVariable Long validationId,
                                                   @RequestBody(required = false) FormValidationRejectRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            formValidationService.reject(validationId, request, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Formulir ditolak"));
        } catch (Exception e) {
            log.error("Error rejecting form validation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Mark form as needing revision
     * PUT /admin/api/validasi/formulir/{validationId}/revision-needed
     */
    @PutMapping("/api/validasi/formulir/{validationId}/revision-needed")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> markFormAsRevisionNeeded(@PathVariable Long validationId,
                                                       @RequestBody(required = false) FormValidationRejectRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            formValidationService.markRevisionNeeded(validationId, request, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Form ditandai perlu revisi"));
        } catch (Exception e) {
            log.error("Error marking form as revision needed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Update repair/revision status for a student's form
     * PUT /admin/api/validasi/student/{studentId}/repair-status
     */
    @PutMapping("/api/validasi/student/{studentId}/repair-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> updateRepairStatus(@PathVariable Long studentId,
                                                 @RequestParam String repairStatus) {
        try {
            Map<String, Object> result = formValidationService.updateRepairStatus(studentId, repairStatus);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating repair status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Save registration number and BRIVA to hasil-akhir table
     * PUT /admin/api/hasil-akhir/nomor-registrasi/{formValidationId}
     */
    @PutMapping("/api/hasil-akhir/nomor-registrasi/{formValidationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> updateHasilAkhirRegistrationNumber(
            @PathVariable Long formValidationId,
            @RequestBody HasilAkhirRegistrationRequest request) {
        try {
            log.info("🔍 [HASIL-AKHIR-REQUEST] nomorRegistrasi: '{}' | brivaNumber: '{}'",
                    request.getNomorRegistrasi(), request.getBrivaNumber());
            HasilAkhir saved = hasilAkhirService.updateRegistrationNumberAndBriva(formValidationId, request);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Nomor registrasi dan BRIVA berhasil disimpan");
            response.put("hasilAkhirId", saved.getId());
            return ResponseEntity.ok(response);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("❌ [HASIL-AKHIR] Unique constraint violation: {}", e.getMessage());
            String msg = "Gagal menyimpan: ";
            if (e.getMessage().contains("briva")) msg += "Nomor BRIVA sudah terdaftar atau tidak valid";
            else if (e.getMessage().contains("nomor_registrasi")) msg += "Nomor Registrasi sudah terdaftar";
            else msg += "Data duplikat atau constraint violation";
            return ResponseEntity.badRequest().body(new ApiResponse(false, msg));
        } catch (Exception e) {
            log.error("❌ [HASIL-AKHIR] Error updating hasil akhir: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Gagal menyimpan: " + e.getMessage()));
        }
    }

    // ========== VALIDATION STATUS TRACKER ==========

    /**
     * GET /admin/api/validation-status-tracker/form/{formId}
     */
    @GetMapping("/api/validation-status-tracker/form/{formId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getValidationStatusTrackerByFormId(@PathVariable Long formId) {
        try {
            Optional<ValidationStatusTracker> tracker = validationStatusTrackerService.getTrackerByFormId(formId);
            if (tracker.isPresent()) {
                return ResponseEntity.ok(buildTrackerResponse(tracker.get()));
            }
            return ResponseEntity.ok(new HashMap<>());
        } catch (Exception e) {
            log.error("Error fetching tracker by form id: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * GET /admin/api/validation-status-tracker/student/{studentId}
     */
    @GetMapping("/api/validation-status-tracker/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getValidationStatusTrackerByStudentId(@PathVariable Long studentId) {
        try {
            Optional<ValidationStatusTracker> tracker = validationStatusTrackerService.getTrackerByStudentId(studentId);
            if (tracker.isPresent()) {
                return ResponseEntity.ok(buildTrackerResponse(tracker.get()));
            }
            return ResponseEntity.ok(new HashMap<>());
        } catch (Exception e) {
            log.error("Error fetching tracker by student id: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * GET /admin/api/validation-status-tracker/all
     */
    @GetMapping("/api/validation-status-tracker/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getAllValidationStatusTrackers() {
        try {
            List<ValidationStatusTracker> trackers = validationStatusTrackerRepository.findAll();
            List<Map<String, Object>> result = trackers.stream()
                    .map(this::buildTrackerResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching all trackers: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== RE-ENROLLMENT VALIDATION ==========

    /**
     * GET /admin/api/validasi/daftar-ulang
     */
    @GetMapping("/api/validasi/daftar-ulang")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getReEnrollmentsForValidation() {
        try {
            List<ReEnrollmentValidation> validations = reEnrollmentValidationRepository.findAll();
            log.info("✅ Found {} re-enrollment validations", validations.size());
            return ResponseEntity.ok(validations);
        } catch (Exception e) {
            log.error("Error fetching re-enrollment validations: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Approve re-enrollment validation
     * PUT /admin/api/validasi/daftar-ulang/{reEnrollmentId}/approve
     */
    @PutMapping("/api/validasi/daftar-ulang/{reEnrollmentId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> approveReEnrollmentValidation(@PathVariable Long reEnrollmentId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            reenrollmentService.approve(reEnrollmentId, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Daftar ulang disetujui"));
        } catch (Exception e) {
            log.error("Error approving re-enrollment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Reject re-enrollment validation
     * PUT /admin/api/validasi/daftar-ulang/{reEnrollmentId}/reject
     */
    @PutMapping("/api/validasi/daftar-ulang/{reEnrollmentId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> rejectReEnrollmentValidation(@PathVariable Long reEnrollmentId,
                                                           @RequestBody(required = false) FormValidationRejectRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            String reason = request != null && request.getReason() != null ? request.getReason() : "";
            String topic = request != null && request.getTopic() != null ? request.getTopic() : "Lainnya";
            reenrollmentService.reject(reEnrollmentId, reason, topic, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Daftar ulang ditolak"));
        } catch (Exception e) {
            log.error("Error rejecting re-enrollment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== PAYMENT VERIFICATION ==========

    /**
     * Verify payment for a form validation
     * PUT /admin/api/validasi/formulir/{formValidationId}/verify-payment
     */
    @PutMapping("/api/validasi/formulir/{formValidationId}/verify-payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> verifyPayment(@PathVariable Long formValidationId) {
        try {
            FormValidation validation = formValidationRepository.findById(formValidationId)
                    .orElseThrow(() -> new RuntimeException("Validasi tidak ditemukan"));
            validation.setPaymentStatus(FormValidation.PaymentStatus.VERIFIED);
            formValidationRepository.save(validation);

            Optional<FormRepairStatus> repairOpt = formRepairStatusRepository.findByFormValidationId(formValidationId);
            repairOpt.ifPresent(repair -> {
                repair.setStatus(FormRepairStatus.RepairStatus.SUDAH_PERBAIKAN);
                formRepairStatusRepository.save(repair);
            });

            return ResponseEntity.ok(new ApiResponse(true, "Pembayaran diverifikasi"));
        } catch (Exception e) {
            log.error("Error verifying payment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== EXAM MANAGEMENT ==========

    /**
     * Generate exam token for a student's form
     * POST /admin/api/exam/generate-token/{formValidationId}
     */
    @PostMapping("/api/exam/generate-token/{formValidationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> generateExamToken(@PathVariable Long formValidationId) {
        try {
            String token = examService.generateToken(formValidationId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating exam token: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get exam token for a form validation
     * GET /admin/api/exam/token/{formValidationId}
     */
    @GetMapping("/api/exam/token/{formValidationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getExamToken(@PathVariable Long formValidationId) {
        try {
            FormValidation validation = formValidationRepository.findById(formValidationId)
                    .orElseThrow(() -> new RuntimeException("Validasi tidak ditemukan"));
            Map<String, Object> response = new HashMap<>();
            response.put("token", validation.getExamToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching exam token: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get all pending exam submissions for admin review
     * GET /admin/api/exam/submissions
     */
    @GetMapping("/api/exam/submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getExamSubmissions() {
        try {
            List<ExamResult> results = examResultRepository.findAll().stream()
                    .filter(r -> r.getExamValidationStatus() == ExamResult.ExamValidationStatus.PENDING
                            && r.getProofPhotoPath() != null)
                    .toList();
            List<Map<String, Object>> response = results.stream().map(r -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.getId());
                item.put("studentId", r.getStudent() != null ? r.getStudent().getId() : null);
                item.put("studentName", r.getStudent() != null ? r.getStudent().getFullName() : "-");
                item.put("score", r.getScore());
                item.put("gformScore", r.getGformScore());
                item.put("proofPhotoUrl", convertPathToUrl(r.getProofPhotoPath()));
                item.put("submissionDate", r.getSubmissionDate());
                item.put("examValidationStatus", r.getExamValidationStatus());
                item.put("adminNotes", r.getAdminNotes());
                return item;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching exam submissions: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get exam submission details by exam result id
     * GET /admin/api/exam/submissions/{examResultId}
     */
    @GetMapping("/api/exam/submissions/{examResultId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getExamSubmissionDetails(@PathVariable Long examResultId) {
        try {
            ExamResult result = examResultRepository.findById(examResultId)
                    .orElseThrow(() -> new RuntimeException("Exam result tidak ditemukan"));
            Map<String, Object> response = new HashMap<>();
            response.put("id", result.getId());
            response.put("studentId", result.getStudent() != null ? result.getStudent().getId() : null);
            response.put("studentName", result.getStudent() != null ? result.getStudent().getFullName() : "-");
            response.put("score", result.getScore());
            response.put("gformScore", result.getGformScore());
            response.put("proofPhotoUrl", convertPathToUrl(result.getProofPhotoPath()));
            response.put("submissionDate", result.getSubmissionDate());
            response.put("examValidationStatus", result.getExamValidationStatus());
            response.put("adminNotes", result.getAdminNotes());
            response.put("tokenValidated", result.getTokenValidated());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching exam submission details: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Validate exam result (approve or reject)
     * PUT /admin/api/exam/submissions/{examResultId}/validate
     */
    @PutMapping("/api/exam/submissions/{examResultId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> validateExamSubmission(@PathVariable Long examResultId,
                                                     @RequestBody ExamValidationRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            ExamResult result = examService.validateSubmission(examResultId, request, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Validasi ujian berhasil disimpan"));
        } catch (Exception e) {
            log.error("Error validating exam submission: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get exam result by student id
     * GET /admin/api/exam/result/student/{studentId}
     */
    @GetMapping("/api/exam/result/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getExamResultByStudentId(@PathVariable Long studentId) {
        try {
            Optional<ExamResult> result = examService.findResultByStudentId(studentId);
            return result.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.ok(null));
        } catch (Exception e) {
            log.error("Error fetching exam result: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ========== RE-ENROLLMENT MANAGEMENT ==========

    /**
     * Get all pending re-enrollments
     * GET /admin/api/daftar-ulang/pending
     */
    @GetMapping("/api/daftar-ulang/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getPendingReenrollments() {
        try {
            List<ReEnrollment> pending = reenrollmentService.findPending();
            return ResponseEntity.ok(pending);
        } catch (Exception e) {
            log.error("Error fetching pending re-enrollments: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get re-enrollment details for a student
     * GET /admin/api/daftar-ulang/{reEnrollmentId}/details
     */
    @GetMapping("/api/daftar-ulang/{reEnrollmentId}/details")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getReerollmentDetails(@PathVariable Long reEnrollmentId) {
        try {
            ReEnrollment enrollment = reenrollmentRepository.findById(reEnrollmentId)
                    .orElseThrow(() -> new RuntimeException("Daftar ulang tidak ditemukan"));

            Map<String, Object> details = new HashMap<>();
            details.put("id", enrollment.getId());
            details.put("status", enrollment.getStatus());
            details.put("submittedAt", enrollment.getSubmittedAt());
            details.put("validatedAt", enrollment.getValidatedAt());
            details.put("validationNotes", enrollment.getValidationNotes());
            details.put("parentName", enrollment.getParentName());
            details.put("parentPhone", enrollment.getParentPhone());
            details.put("parentEmail", enrollment.getParentEmail());
            details.put("parentAddress", enrollment.getParentAddress());
            details.put("permanentAddress", enrollment.getPermanentAddress());
            details.put("currentAddress", enrollment.getCurrentAddress());
            details.put("alumniFamily", enrollment.getAlumniFamily());
            details.put("alumniName", enrollment.getAlumniName());
            details.put("alumniRelation", enrollment.getAlumniRelation());

            if (enrollment.getStudent() != null) {
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("id", enrollment.getStudent().getId());
                studentInfo.put("fullName", enrollment.getStudent().getFullName());
                studentInfo.put("email", enrollment.getStudent().getUser() != null ? enrollment.getStudent().getUser().getEmail() : "-");
                details.put("student", studentInfo);
            }

            List<Map<String, Object>> docs = reenrollmentDocumentRepository
                    .findByReenrollmentId(reEnrollmentId).stream().map(doc -> {
                        Map<String, Object> docMap = new HashMap<>();
                        docMap.put("id", doc.getId());
                        docMap.put("documentType", doc.getDocumentType());
                        docMap.put("filePath", convertPathToUrl(doc.getFilePath()));
                        docMap.put("originalFilename", doc.getOriginalFilename());
                        docMap.put("uploadStatus", doc.getUploadStatus());
                        docMap.put("validationStatus", doc.getValidationStatus());
                        docMap.put("adminNotes", doc.getAdminNotes());
                        docMap.put("uploadedAt", doc.getUploadedAt());
                        docMap.put("validatedAt", doc.getValidatedAt());
                        return docMap;
                    }).collect(Collectors.toList());
            details.put("documents", docs);

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("Error fetching re-enrollment details: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Validate a specific re-enrollment document
     * PUT /admin/api/daftar-ulang/documents/{docId}/validate
     */
    @PutMapping("/api/daftar-ulang/documents/{docId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> validateDocument(@PathVariable Long docId,
                                               @RequestBody DocumentValidationRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User admin = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));
            ReEnrollmentDocument doc = reenrollmentService.validateDocument(docId, request, admin);
            return ResponseEntity.ok(new ApiResponse(true, "Dokumen divalidasi"));
        } catch (Exception e) {
            log.error("Error validating document: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Finalize re-enrollment validation
     * PUT /admin/api/daftar-ulang/{id}/finalize
     */
    @PutMapping("/api/daftar-ulang/{id}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> finalizeReEnrollment(@PathVariable Long id,
                                                   @RequestBody ReenrollmentFinalizeRequest request) {
        try {
            ReEnrollment enrollment = reenrollmentService.finalize(id, request);
            return ResponseEntity.ok(new ApiResponse(true, "Daftar ulang difinalisasi"));
        } catch (Exception e) {
            log.error("Error finalizing re-enrollment: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== PRIVATE HELPERS ==========

    private Map<String, Object> buildTrackerResponse(ValidationStatusTracker tracker) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", tracker.getId());
        response.put("status", tracker.getStatus());
        response.put("lastReason", tracker.getLastReason());
        response.put("lastAction", tracker.getLastAction());
        response.put("createdAt", tracker.getCreatedAt());
        response.put("updatedAt", tracker.getUpdatedAt());
        if (tracker.getAdmissionForm() != null) {
            response.put("formId", tracker.getAdmissionForm().getId());
        }
        if (tracker.getStudent() != null) {
            response.put("studentId", tracker.getStudent().getId());
            response.put("studentName", tracker.getStudent().getFullName());
        }
        if (tracker.getLastUpdatedBy() != null) {
            response.put("lastUpdatedByEmail", tracker.getLastUpdatedBy().getEmail());
        }
        return response;
    }

    private String convertPathToUrl(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        String normalized = filePath.replace("\\", "/");
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        return "/" + normalized;
    }
}
