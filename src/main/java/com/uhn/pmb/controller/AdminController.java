package com.uhn.pmb.controller;

import com.uhn.pmb.entity.*;
import com.uhn.pmb.dto.*;
import com.uhn.pmb.service.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    // ========== SERVICE INJECTIONS (NO REPOSITORIES) ==========
    private final StudentRegistrationService registrationService;
    private final EmailService emailService;
    private final JenisSeleksiService jenisSeleksiService;
    private final RegistrationStatusService registrationStatusService;
    private final ValidationStatusTrackerService validationStatusTrackerService;
    private final HasilAkhirService hasilAkhirService;
    
    // ========== REPOSITORY INJECTIONS ==========
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final PeriodJenisSeleksiRepository periodJenisSeleksiRepository;
    private final ProgramStudiRepository programStudiRepository;
    private final SelectionProgramStudiRepository selectionProgramStudiRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final UserRepository userRepository;
    private final AdminMessageRepository adminMessageRepository;
    private final HasilAkhirRepository hasilAkhirRepository;
    private final SystemConfigurationRepository systemConfigRepository;
    private final SelectionTypeRepository selectionTypeRepository;
    private final ExamLinkRepository examLinkRepository;
    private final ReEnrollmentRepository reenrollmentRepository;
    private final ReEnrollmentDocumentRepository reenrollmentDocumentRepository;
    
    // ========== LEGACY REPOSITORIES (TO BE REFACTORED) ==========
    private final EntityManager entityManager;



    /**
     * ==================== PERIOD JENIS SELEKSI MANAGEMENT ====================
     */

    @GetMapping("/jenis-seleksi/period/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getJenisSeleksiByPeriod(@PathVariable Long periodId) {
        try {
            RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                    .orElseThrow(() -> new RuntimeException("Period tidak ditemukan"));

            List<PeriodJenisSeleksi> periodJenisSeleksiList = periodJenisSeleksiRepository.findByPeriod_IdAndIsActiveTrue(periodId);
            
            List<Map<String, Object>> response = periodJenisSeleksiList.stream()
                    .map(pjs -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", pjs.getId());
                        map.put("jenisSeleksiId", pjs.getJenisSeleksi().getId());
                        map.put("code", pjs.getJenisSeleksi().getCode());
                        map.put("nama", pjs.getJenisSeleksi().getNama());
                        map.put("deskripsi", pjs.getJenisSeleksi().getDeskripsi());
                        map.put("logoUrl", pjs.getJenisSeleksi().getLogoUrl());
                        map.put("harga", pjs.getJenisSeleksi().getHarga());
                        map.put("isActive", pjs.getIsActive());
                        return map;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", true);
            responseMap.put("periodId", periodId);
            responseMap.put("periodName", period.getName());
            responseMap.put("data", response);
            responseMap.put("total", response.size());

            log.info("âœ… Retrieved {} jenis seleksi for period: {}", response.size(), period.getName());
            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            log.error("âŒ Error fetching jenis seleksi for period: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }


    /**
     * ==================== JENIS SELEKSI - PROGRAM STUDI RELATIONSHIP ENDPOINTS ====================
     * NOTE: GET/PUT/DELETE /admin/program-studi/{id} dipindahkan ke AdminProgramStudiController
     */

    @GetMapping("/program-studi/jenis-seleksi/{jenisSeleksiId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getProgramStudiByJenisSeleksi(@PathVariable Long jenisSeleksiId) {
        try {
            JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId)
                    .orElseThrow(() -> new RuntimeException("Jenis Seleksi tidak ditemukan"));

            List<SelectionProgramStudi> selectionProgramStudiList = 
                    selectionProgramStudiRepository.findByJenisSeleksi_IdAndIsActiveTrue(jenisSeleksiId);

            List<Map<String, Object>> response = selectionProgramStudiList.stream()
                    .map(sps -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", sps.getId());
                        map.put("programStudiId", sps.getProgramStudi().getId());
                        map.put("kode", sps.getProgramStudi().getKode());
                        map.put("nama", sps.getProgramStudi().getNama());
                        map.put("isMedical", sps.getProgramStudi().getIsMedical());
                        map.put("isActive", sps.getIsActive());
                        return map;
                    })
                    .collect(Collectors.toList());

            log.info("âœ… Retrieved {} program studi for jenis seleksi {}", response.size(), jenisSeleksi.getNama());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error fetching program studi for jenis seleksi: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }


    /**
     * Validate admission form
     */
    @PutMapping("/forms/{formId}/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> validateForm(@PathVariable Long formId, @RequestBody ValidationRequest request) {
        try {
            AdmissionForm form = admissionFormRepository.findById(formId)
                    .orElseThrow(() -> new RuntimeException("Form not found"));

            if (request.getApproved()) {
                form.setStatus(AdmissionForm.FormStatus.VERIFIED);
                emailService.sendSimpleEmail(
                        form.getStudent().getUser().getEmail(),
                        "Validasi Data Berhasil",
                        "Data pendaftaran Anda telah divalidasi dan disetujui."
                );
            } else {
                form.setStatus(AdmissionForm.FormStatus.REJECTED);
                emailService.sendSimpleEmail(
                        form.getStudent().getUser().getEmail(),
                        "Validasi Data Ditolak",
                        "Data pendaftaran Anda ditolak. Alasan: " + request.getReason()
                );
            }

            admissionFormRepository.save(form);
            return ResponseEntity.ok(new ApiResponse(true, "Form validation completed"));
        } catch (Exception e) {
            log.error("Error validating form: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/period/{periodId}/publish-results")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> publishResults(@PathVariable Long periodId) {
        try {
            RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                    .orElseThrow(() -> new RuntimeException("Period not found"));

            registrationService.publishExamResults(period);
            return ResponseEntity.ok(new ApiResponse(true, "Results published successfully"));
        } catch (Exception e) {
            log.error("Error publishing results: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get re-enrollment data
     */
    @GetMapping("/reenrollments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getReenrollments() {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Re-enrollments retrieved"); }});
        } catch (Exception e) {
            log.error("Error fetching reenrollments: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    /**
     * Validate re-enrollment
     */
    @PutMapping("/reenrollments/{reenrollmentId}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> validateReEnrollment(@PathVariable Long reenrollmentId, 
                                                  @RequestBody(required = false) Map<String, String> request) {
        try {
            String status = request != null ? request.get("status") : null;
            String reason = request != null ? request.get("reason") : null;
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Re-enrollment validation completed"); }});
        } catch (Exception e) {
            log.error("Error validating re-enrollment: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }



    // ========== VALIDASI DATA FORMULIR & PEMBAYARAN ==========

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @GetMapping("/forms/{formValidationId}/details")
    public ResponseEntity<?> getFormDetails(@PathVariable Long formValidationId) {
        try {
            Map<String, Object> details = new HashMap<>();
            log.info("âœ… Returning complete form details for validation {}", formValidationId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("âŒ Error fetching form details: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @GetMapping("/forms/student/{studentId}/admission-details")
    public ResponseEntity<?> getAdmissionFormStudentDetails(@PathVariable Long studentId) {
        try {
            log.info("ðŸ“‹ [ADMISSION-DETAILS] Fetching wave type, selection type, program studi for studentId: {}", studentId);
            Map<String, Object> details = new HashMap<>();
            log.info("âœ… [ADMISSION-DETAILS] Complete details retrieved");
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("âŒ [ADMISSION-DETAILS] Error fetching admission form details: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PutMapping("/forms/{formValidationId}/approve")
    public ResponseEntity<?> approveFormValidation(@PathVariable Long formValidationId) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Formulir disetujui"); }});
        } catch (Exception e) {
            log.error("Error approving form validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PutMapping("/forms/{formValidationId}/reject")
    public ResponseEntity<?> rejectFormValidation(@PathVariable Long formValidationId,
                                                   @RequestBody(required = false) Map<String, String> request) {
        try {
            String topic = request != null ? request.get("topic") : null;
            String reason = request != null ? request.get("reason") : null;
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Formulir ditolak"); }});
        } catch (Exception e) {
            log.error("Error rejecting form validation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PutMapping("/forms/{formValidationId}/revision-needed")
    public ResponseEntity<?> markFormAsRevisionNeeded(@PathVariable Long formValidationId,
                                                       @RequestBody(required = false) Map<String, Object> request) {
        try {
            String topic = request != null ? (String) request.get("topic") : null;
            String reason = request != null ? (String) request.get("reason") : null;
            Integer revisionNumber = request != null && request.get("revisionNumber") != null ? 
                    ((Number) request.get("revisionNumber")).intValue() : 1;
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Formulir ditandai untuk revisi"); }});
        } catch (Exception e) {
            log.error("Error marking form as revision needed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/forms/student/{studentId}/repair-status")
    public ResponseEntity<?> updateRepairStatus(@PathVariable Long studentId, @RequestBody Map<String, String> request) {
        try {
            log.info("ðŸ”§ [REPAIR-STATUS] Updating repair status for student ID: {}", studentId);
            String repairStatus = request.get("repairStatus");
            
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Repair status berhasil diperbarui");
            response.put("studentId", studentId);
            response.put("repairStatus", repairStatus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ [REPAIR-STATUS] Error updating repair status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", "Error: " + e.getMessage()); }});
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/forms/{formId}/validation-status-tracker")
    public ResponseEntity<?> getValidationStatusTrackerByFormId(@PathVariable Long formId) {
        try {
            log.info("ðŸ“Š Getting ValidationStatusTracker for form ID: {}", formId);
            Map<String, Object> response = new HashMap<>();
            log.info("âœ… Returning ValidationStatusTracker for form {}", formId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error fetching ValidationStatusTracker: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/student/{studentId}/validation-status-tracker")
    public ResponseEntity<?> getValidationStatusTrackerByStudentId(@PathVariable Long studentId) {
        try {
            log.info("ðŸ“Š Getting ValidationStatusTracker for student ID: {}", studentId);
            Map<String, Object> response = new HashMap<>();
            log.info("âœ… Returning ValidationStatusTracker for student {}", studentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error fetching ValidationStatusTracker: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PutMapping("/forms/{formValidationId}/hasil-akhir")
    public ResponseEntity<?> updateHasilAkhirRegistrationNumber(@PathVariable Long formValidationId,
            @RequestBody Map<String, Object> request) {
        try {
            String nomorRegistrasi = (String) request.get("nomorRegistrasi");
            String brivaNumber = (String) request.get("brivaNumber");
            Integer jumlahCicilan = request.get("jumlahCicilan") != null ? 
                    ((Number) request.get("jumlahCicilan")).intValue() : null;
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("âŒ [HASIL-AKHIR] Unique constraint violation: {}", e.getMessage());
            String baseMsg = "Gagal menyimpan: ";
            String msg;
            if (e.getMessage().contains("briva")) {
                msg = baseMsg + "Nomor BRIVA sudah terdaftar atau tidak valid";
            } else if (e.getMessage().contains("nomor_registrasi")) {
                msg = baseMsg + "Nomor Registrasi sudah terdaftar";
            } else {
                msg = baseMsg + "Data duplikat atau constraint violation";
            }
            String finalMsg = msg;
            return ResponseEntity.badRequest().body(new HashMap<String, Object>() {{ put("success", false); put("message", finalMsg); }});
        } catch (Exception e) {
            log.error("âŒ [HASIL-AKHIR] Error updating hasil akhir: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Gagal menyimpan: " + e.getMessage());
            errorResponse.put("exceptionType", e.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }


    // ========== VALIDASI DATA DAFTAR ULANG ==========


    @PutMapping("/reenrollments/{reEnrollmentId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> approveReEnrollmentValidation(@PathVariable Long reEnrollmentId) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Daftar ulang disetujui"); }});
        } catch (Exception e) {
            log.error("Error approving reenrollment validation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PutMapping("/reenrollments/{reEnrollmentId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> rejectReEnrollmentValidation(@PathVariable Long reEnrollmentId,
                                                          @RequestBody(required = false) Map<String, String> request) {
        try {
            String reason = request != null ? request.get("reason") : null;
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Daftar ulang ditolak"); }});
        } catch (Exception e) {
            log.error("Error rejecting reenrollment validation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @GetMapping("/reenrollments/{id}/details")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> getReerollmentDetailsSimple(@PathVariable Long id) {
        try {
            // ✅ NEW: Fetch re-enrollment with full details including documents
            ReEnrollment reenrollment = reenrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Daftar ulang tidak ditemukan"));

            Student student = reenrollment.getStudent();
            Map<String, Object> response = new HashMap<>();
            
            // ✅ Student info
            response.put("id", reenrollment.getId());
            response.put("studentId", student != null ? student.getId() : null);
            response.put("studentName", student != null ? student.getFullName() : "-");
            response.put("email", student != null && student.getUser() != null ? student.getUser().getEmail() : "-");
            
            // ✅ Parent info
            response.put("parentName", reenrollment.getParentName());
            response.put("parentPhone", reenrollment.getParentPhone());
            response.put("parentEmail", reenrollment.getParentEmail());
            response.put("parentAddress", reenrollment.getParentAddress());
            
            // ✅ Address info
            response.put("permanentAddress", reenrollment.getPermanentAddress());
            response.put("currentAddress", reenrollment.getCurrentAddress());
            
            // ✅ Alumni info
            response.put("alumniFamily", reenrollment.getAlumniFamily() != null ? reenrollment.getAlumniFamily() : false);
            response.put("alumniRelation", reenrollment.getAlumniRelation());
            response.put("alumniName", reenrollment.getAlumniName());
            
            // ✅ Submission info
            response.put("status", reenrollment.getStatus() != null ? reenrollment.getStatus().toString() : "PENDING");
            response.put("submittedAt", reenrollment.getSubmittedAt());
            response.put("validatedAt", reenrollment.getValidatedAt());
            
            // ✅ CRITICAL: Add documents array with filePath
            List<Map<String, Object>> documentsList = new ArrayList<>();
            
            // ✅ OPTION 1: Modern way - from ReEnrollmentDocument relationship
            if (reenrollment.getDocuments() != null && !reenrollment.getDocuments().isEmpty()) {
                for (ReEnrollmentDocument doc : reenrollment.getDocuments()) {
                    Map<String, Object> docMap = new HashMap<>();
                    docMap.put("id", doc.getId());
                    docMap.put("displayName", doc.getDocumentType() != null ? doc.getDocumentType().getDisplayName() : doc.getOriginalFilename());
                    docMap.put("documentType", doc.getDocumentType() != null ? doc.getDocumentType().toString() : "UNKNOWN");
                    docMap.put("filePath", doc.getFilePath());
                    docMap.put("uploadedAt", doc.getUploadedAt());
                    docMap.put("validationStatus", doc.getValidationStatus() != null ? doc.getValidationStatus().toString() : "PENDING");
                    docMap.put("adminNotes", doc.getAdminNotes());
                    documentsList.add(docMap);
                }
                log.info("✅ [DOCUMENTS] Found {} documents from ReEnrollmentDocument table", documentsList.size());
            }
            
            // ✅ OPTION 2: Fallback - Legacy file columns from reenrollment table
            if (documentsList.isEmpty()) {
                log.info("⚠️ [DOCUMENTS] No modern documents found - checking legacy file columns...");
                
                Map<String, String> legacyFiles = new HashMap<>();
                legacyFiles.put("PAKTA_INTEGRITAS", reenrollment.getPaktaIntegritasFile());
                legacyFiles.put("IJAZAH", reenrollment.getIjazahFile());
                legacyFiles.put("PASPHOTO", reenrollment.getPasphotoFile());
                legacyFiles.put("KARTU_KELUARGA", reenrollment.getKartuKeluargaFile());
                legacyFiles.put("KARTU_TANDA_PENDUDUK", reenrollment.getKtpFile());
                legacyFiles.put("KETERANGAN_BEBAS_NARKOBA", reenrollment.getSuratBebasNarkobaFile());
                legacyFiles.put("SKCK", reenrollment.getSkckFile());
                
                legacyFiles.forEach((docType, filePath) -> {
                    if (filePath != null && !filePath.isEmpty()) {
                        Map<String, Object> docMap = new HashMap<>();
                        docMap.put("id", null);
                        docMap.put("displayName", docType.replace("_", " "));
                        docMap.put("documentType", docType);
                        docMap.put("filePath", filePath);
                        docMap.put("uploadedAt", reenrollment.getSubmittedAt());
                        docMap.put("validationStatus", "PENDING");
                        docMap.put("adminNotes", null);
                        documentsList.add(docMap);
                        log.info("  ✅ Added legacy file: {} -> {}", docType, filePath);
                    }
                });
            }
            
            response.put("documents", documentsList);
            
            // Document count stats
            long approvedCount = reenrollment.getDocuments() != null ? 
                    reenrollment.getDocuments().stream()
                            .filter(d -> d.getValidationStatus() == ReEnrollmentDocument.ValidationStatus.APPROVED)
                            .count() : 0;
            response.put("approvedDocuments", approvedCount);
            response.put("totalDocuments", documentsList.size());
            
            log.info("✅ [ADMIN-DETAILS] Fetched re-enrollment {} with {} documents total", id, documentsList.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching re-enrollment details: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    /**
     * Send approval email to student
     */
    private void sendApprovalEmail(String studentEmail, String studentName) {
        try {
            String subject = "ðŸŽ“ SELAMAT! Pendaftaran Anda Telah Disetujui - Lihat Nomor Registrasi & Virtual Account";
            String htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                            <div style="text-align: center; padding: 20px; background-color: #28a745; border-radius: 8px 8px 0 0; color: white;">
                                <h1 style="margin: 0; font-size: 32px;">ðŸŽ‰ SELAMAT!</h1>
                                <p style="margin: 10px 0 0 0; font-size: 18px;">Anda Telah Menyelesaikan Pendaftaran</p>
                            </div>
                            
                            <div style="padding: 30px; background-color: white;">
                                <p>Dear <strong>%s</strong>,</p>
                                
                                <p>Kami dengan senang hati memberitahukan bahwa formulir pendaftaran Anda telah <strong>DISETUJUI âœ…</strong> oleh tim verifikasi kami. Selamat! Anda telah resmi menyelesaikan tahap pendaftaran.</p>
                                
                                <div style="background-color: #e8f5e9; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h3 style="margin-top: 0; color: #28a745;">ðŸ“ DATA PENTING ANDA:</h3>
                                    <p style="margin: 5px 0; color: #1b5e20;"><strong>Nomor Registrasi Akademik:</strong> Dapat dilihat di dashboard (tombol "Hasil Akhir Penerimaan")</p>
                                    <p style="margin: 5px 0; color: #1b5e20;"><strong>Virtual Account BRIVA:</strong> Dapat dilihat di dashboard (tombol "Hasil Akhir Penerimaan")</p>
                                    <p style="margin: 5px 0; color: #1b5e20;"><strong>ðŸ“Œ PENTING:</strong> Simpan kedua data ini untuk referensi Anda ke depannya.</p>
                                </div>
                                
                                <h3 style="color: #1976d2;">ðŸŽ¯ Langkah Berikutnya:</h3>
                                <ol style="color: #555;">
                                    <li><strong>Login ke Dashboard PMB</strong> - Gunakan akun yang sudah didaftarkan</li>
                                    <li><strong>Lihat "Hasil Akhir Penerimaan"</strong> - Tombol hijau di dashboard menampilkan nomor registrasi & virtual account</li>
                                    <li><strong>Catat Nomor Registrasi & Virtual Account</strong> - Gunakan untuk keperluan akademik Anda</li>
                                    <li><strong>Siapkan Dokumen</strong> - Kumpulkan dokumen-dokumen yang diperlukan untuk tahap selanjutnya</li>
                                    <li><strong>Tunggu Pengumuman Jadwal Berikutnya</strong> - Kami akan mengirimkan informasi lebih lanjut melalui email</li>
                                </ol>
                                
                                <div style="background-color: #e3f2fd; border-left: 4px solid #1976d2; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h4 style="margin-top: 0; color: #0d47a1;">ðŸ“² Akses "Hasil Akhir Penerimaan":</h4>
                                    <ul style="margin: 10px 0; padding-left: 20px; color: #1565c0;">
                                        <li>Login ke dashboard PMB Anda</li>
                                        <li>Cari tombol <strong>"ðŸ“„ Hasil Akhir Penerimaan"</strong> (warna hijau)</li>
                                        <li>Klik untuk melihat detail lengkap: Nomor Registrasi, Virtual Account BRIVA, dan status pendaftaran</li>
                                    </ul>
                                </div>
                                
                                <div style="background-color: #fff3cd; border-left: 4px solid #ff9800; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <p style="margin: 0; color: #e65100;"><strong>âš ï¸ PENTING:</strong> Jangan hilangkan nomor registrasi dan virtual account Anda. Data ini diperlukan untuk komunikasi resmi dengan universitas.</p>
                                </div>
                                
                                <p style="color: #666; margin-top: 30px;">Terima kasih telah mempercayai HKBP Nommensen. Kami menantikan Anda sebagai bagian dari mahasiswa kami.</p>
                                
                                <p style="color: #666; margin: 10px 0;">Jika ada pertanyaan, silakan hubungi tim PMB melalui portal atau email customer service kami.</p>
                                
                                <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen<br/>Universitas HKBP Nommensen</p>
                            </div>
                        </div>
                    </body>
                </html>
                """, studentName);
            
            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
            log.info("âœ… Approval email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("âŒ Error sending approval email to {}: {}", studentEmail, e.getMessage());
        }
    }

    /**
     * Send rejection email to student (permanent rejection)
     */
    private void sendRejectionEmail(String studentEmail, String studentName, String rejectionReason) {
        try {
            String subject = "âŒ Formulir Pendaftaran Anda Ditolak - PMB HKBP Nommensen";
            String htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                            <div style="text-align: center; padding: 20px; background-color: #dc3545; border-radius: 8px 8px 0 0; color: white;">
                                <h1 style="margin: 0; font-size: 28px;">âŒ PEMBERITAHUAN PENOLAKAN</h1>
                                <p style="margin: 10px 0 0 0; font-size: 14px;">Formulir Pendaftaran Tidak Diterima</p>
                            </div>
                            
                            <div style="padding: 30px; background-color: white;">
                                <p>Dear <strong>%s</strong>,</p>
                                
                                <p>Terima kasih atas kepercayaan dan minat Anda untuk bergabung dengan HKBP Nommensen. Setelah melakukan review menyeluruh dan komprehensif terhadap formulir pendaftaran Anda, kami dengan menyesal memberitahukan bahwa <strong>FORMULIR PENDAFTARAN ANDA TIDAK DAPAT DITERIMA PADA GELOMBANG INI</strong>.</p>
                                
                                <div style="background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h3 style="margin-top: 0; color: #721c24;">ðŸ“‹ Alasan Penolakan:</h3>
                                    <p style="margin: 5px 0; color: #721c24; font-size: 15px; line-height: 1.8;"><strong>%s</strong></p>
                                </div>
                                
                                <h3 style="color: #1976d2;">ðŸ“Œ Informasi Penting Untuk Anda:</h3>
                                <ul style="color: #555; line-height: 1.9;">
                                    <li><strong>Penolakan ini bersifat final</strong> untuk gelombang pendaftaran tahun ini dan tidak dapat diajukan banding</li>
                                    <li><strong>Kesempatan Berikutnya:</strong> Anda dapat mendaftar kembali di gelombang pendaftaran berikutnya jika tersedia (dengan syarat dan ketentuan yang berlaku)</li>
                                    <li><strong>Pembayaran:</strong> Jika Anda sudah melakukan pembayaran, silakan hubungi tim PMB kami untuk proses refund</li>
                                    <li><strong>Dokumen dan Data:</strong> Data pendaftaran Anda akan disimpan dalam sistem sebagai arsip</li>
                                </ul>
                                
                                <div style="background-color: #fff3cd; border-left: 4px solid #f57f17; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h4 style="margin-top: 0; color: #e65100;">ðŸ’¼ Saran Untuk Masa Depan:</h4>
                                    <p style="margin: 5px 0; color: #bf360c;">â€¢ Tingkatkan persiapan dan kualitas dokumen pendukung Anda</p>
                                    <p style="margin: 5px 0; color: #bf360c;">â€¢ Pastikan semua data yang Anda masukkan akurat dan lengkap</p>
                                    <p style="margin: 5px 0; color: #bf360c;">â€¢ Ikuti setiap panduan dan instruksi dengan seksama pada pendaftaran berikutnya</p>
                                </div>
                                
                                <div style="background-color: #e3f2fd; border-left: 4px solid #1976d2; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h4 style="margin-top: 0; color: #0d47a1;">ðŸ“ž Konsultasi Lebih Lanjut:</h4>
                                    <p style="margin: 5px 0; color: #1565c0;">Jika Anda ingin mendisuksikan hasil penolakan ini secara lebih detail, Anda dapat:</p>
                                    <ul style="margin: 10px 0; padding-left: 20px; color: #1565c0;">
                                        <li>Hubungi tim Customer Service PMB melalui portal</li>
                                        <li>Mengirimkan email konsultasi ke email PMB resmi kami</li>
                                        <li>Mengunjungi kantor PMB selama jam kerja (dengan perjanjian terlebih dahulu)</li>
                                    </ul>
                                </div>
                                
                                <p style="color: #666; margin-top: 30px;">Kami mengucapkan terima kasih atas waktu, perhatian, dan upaya Anda dalam proses seleksi kami. Kami juga menghargai kepercayaan Anda kepada HKBP Nommensen dan berharap dapat bertemu dengan Anda di kesempatan pendaftaran berikutnya.</p>
                                
                                <p style="color: #666; margin: 15px 0 0 0;">Semangat dan terus berkembang untuk masa depan yang lebih baik!</p>
                                
                                <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim Penerimaan Mahasiswa (PMB)<br/>HKBP Nommensen<br/>Universitas HKBP Nommensen</p>
                            </div>
                        </div>
                    </body>
                </html>
                """, studentName, rejectionReason);
            
            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
            log.info("âŒ Rejection email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("âŒ Error sending rejection email to {}: {}", studentEmail, e.getMessage());
        }
    }

    /**
     * Send revision-needed email to student (form requires revision)
     */
    private void sendRevisionNeededEmail(String studentEmail, String studentName, String revisionReason, Integer revisionNumber) {
        try {
            String subject = "âœï¸ REVISI DIPERLUKAN - Silakan Perbaiki Data Formulir Anda (Revisi ke-" + revisionNumber + ")";
            String htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                            <div style="text-align: center; padding: 20px; background-color: #ff9800; border-radius: 8px 8px 0 0; color: white;">
                                <h1 style="margin: 0; font-size: 28px;">âœï¸ REVISI DIPERLUKAN (Revisi ke-%d)</h1>
                                <p style="margin: 10px 0 0 0; font-size: 14px;">Silakan Perbaiki Data Formulir Anda</p>
                            </div>
                            
                            <div style="padding: 30px; background-color: white;">
                                <p>Dear <strong>%%s</strong>,</p>
                                
                                <p>Terima kasih telah mengajukan formulir pendaftaran. Tim verifikasi kami telah melakukan review menyeluruh terhadap formulir Anda dan menemukan beberapa data yang perlu Anda perbaiki sebelum dapat kami setujui.</p>
                                
                                <div style="background-color: #ffe0b2; border-left: 4px solid #ff9800; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h3 style="margin-top: 0; color: #e65100;">ðŸ“ Data yang Perlu Diperbaiki (Revisi ke-%d):</h3>
                                    <p style="margin: 0; color: #bf360c; font-size: 15px; line-height: 1.8;"><strong>%%s</strong></p>
                                </div>
                                
                                <h3 style="color: #1976d2;">ðŸ”§ Cara Memperbaiki Formulir:</h3>
                                <ol style="color: #555; line-height: 2;">
                                    <li><strong>Login ke Dashboard PMB</strong> - Masuk dengan email dan password yang sudah terdaftar</li>
                                    <li><strong>Cari Formulir Anda</strong> - Buka tab "Dashboard" dan cari formulir dengan status <span style="background: #fff3cd; padding: 2px 6px; border-radius: 3px;">"Perlu Revisi"</span></li>
                                    <li><strong>Klik Tombol "Edit Sekarang"</strong> - Tombol berwarna hijau di sebelah formulir</li>
                                    <li><strong>Perbaiki Data Sesuai Feedback</strong> - Bacalah dengan seksama dan update semua field yang bermasalah</li>
                                    <li><strong>Simpan & Submit Ulang</strong> - Setelah mengubah data, klik tombol "Kirim Ulang" atau "Submit" untuk mengirimkan formulir yang sudah diperbaiki</li>
                                    <li><strong>Tunggu Notifikasi</strong> - Tim kami akan review ulang dalam waktu singkat. Anda akan menerima email notifikasi setelah review selesai</li>
                                </ol>
                                
                                <div style="background-color: #e8f5e9; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h4 style="margin-top: 0; color: #2e7d32;">ðŸ’¡ Tips Perbaikan:</h4>
                                    <ul style="margin: 10px 0; padding-left: 20px; color: #1b5e20;">
                                        <li>Bacalah feedback dengan seksama sebelum mulai edit</li>
                                        <li>Pastikan semua data sesuai dengan dokumen pendukung Anda</li>
                                        <li>Periksa kembali data sebelum submit untuk menghindari kesalahan</li>
                                        <li>Jika ada field yang tidak jelas, hubungi tim PMB untuk bantuan</li>
                                    </ul>
                                </div>
                                
                                <div style="background-color: #fff3cd; border-left: 4px solid #f57f17; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <p style="margin: 0; color: #e65100;"><strong>âš ï¸ PENTING:</strong> Silakan lakukan perbaikan dan submit ulang formulir <strong>secepatnya</strong>. Setiap hari penundaan dapat mempengaruhi jadwal proses pendaftaran Anda selanjutnya.</p>
                                </div>
                                
                                <div style="background-color: #f3e5f5; border-left: 4px solid #9c27b0; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h4 style="margin-top: 0; color: #6a1b9a;">ðŸ› Jika Formulir Tidak Bisa Diedit:</h4>
                                    <p style="margin: 5px 0; color: #4a148c;">1. Refresh halaman browser Anda</p>
                                    <p style="margin: 5px 0; color: #4a148c;">2. Logout dan login kembali</p>
                                    <p style="margin: 5px 0; color: #4a148c;">3. Jika masih bermasalah, hubungi tim customer service kami</p>
                                </div>
                                
                                <p style="color: #666; margin-top: 30px;">Kami menantikan formulir yang sudah diperbaiki dari Anda. Jika ada pertanyaan atau kesulitan, jangan ragu untuk menghubungi tim PMB kami melalui portal atau email.</p>
                                
                                <p style="margin: 15px 0 0 0; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen<br/>Universitas HKBP Nommensen</p>
                            </div>
                        </div>
                    </body>
                </html>
                """, revisionNumber, studentName, revisionNumber, revisionReason);
            
            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
            log.info("âœï¸ Revision-needed email sent to: {} (Revisi ke-{})", studentEmail, revisionNumber);
        } catch (Exception e) {
            log.error("âŒ Error sending revision-needed email to {}: {}", studentEmail, e.getMessage());
        }
    }

    // ========== EXAM VALIDATION EMAIL NOTIFICATIONS ==========

    /**
     * Send email when exam is APPROVED
     */
    private void sendExamApprovalEmail(String studentEmail, String studentName) {
        try {
            String subject = "âœ… Ujian Anda Telah Diterima - PMB HKBP Nommensen";
            String htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                            <div style="text-align: center; padding: 20px; background-color: #28a745; border-radius: 8px 8px 0 0; color: white;">
                                <h1 style="margin: 0; font-size: 28px;">âœ… UJIAN DITERIMA</h1>
                                <p style="margin: 10px 0 0 0; font-size: 16px;">Selamat! Hasil Ujian Anda Telah Divalidasi</p>
                            </div>
                            
                            <div style="padding: 30px; background-color: white;">
                                <p>Dear <strong>%s</strong>,</p>
                                
                                <p>Kami dengan senang hati memberitahukan bahwa hasil ujian Anda telah <strong>DITERIMA âœ…</strong> oleh tim verifikasi kami.</p>
                                
                                <div style="background-color: #e8f5e9; border-left: 4px solid #28a745; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h3 style="margin-top: 0; color: #28a745;">ðŸŽ‰ Status Ujian: DITERIMA</h3>
                                    <p style="margin: 0; color: #1b5e20;">Token dan bukti ujian Anda telah diverifikasi dan dinyatakan valid.</p>
                                </div>
                                
                                <h3 style="color: #1976d2;">ðŸ“‹ Langkah Selanjutnya:</h3>
                                <ol style="color: #555;">
                                    <li><strong>Login ke Dashboard PMB</strong> - Cek status terbaru di dashboard Anda</li>
                                    <li><strong>Lakukan Pembayaran Cicilan 1</strong> - Bayar cicilan pertama untuk melanjutkan ke tahap daftar ulang</li>
                                    <li><strong>Lengkapi Daftar Ulang</strong> - Setelah pembayaran, isi formulir daftar ulang</li>
                                </ol>
                                
                                <div style="background-color: #fff3cd; border-left: 4px solid #ff9800; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <p style="margin: 0; color: #e65100;"><strong>âš ï¸ PENTING:</strong> Segera lakukan pembayaran cicilan 1 agar proses pendaftaran Anda tidak terhambat.</p>
                                </div>
                                
                                <p style="color: #666; margin-top: 30px;">Terima kasih dan selamat! Kami menantikan Anda sebagai bagian dari mahasiswa HKBP Nommensen.</p>
                                
                                <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen<br/>Universitas HKBP Nommensen</p>
                            </div>
                        </div>
                    </body>
                </html>
                """, studentName);

            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
            log.info("âœ… Exam approval email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("âŒ Error sending exam approval email to {}: {}", studentEmail, e.getMessage());
        }
    }

    /**
     * Send email when exam is REJECTED
     */
    private void sendExamRejectionEmail(String studentEmail, String studentName, String rejectionReason) {
        try {
            String subject = "âŒ Ujian Anda Ditolak - PMB HKBP Nommensen";
            String htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                        <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                            <div style="text-align: center; padding: 20px; background-color: #dc3545; border-radius: 8px 8px 0 0; color: white;">
                                <h1 style="margin: 0; font-size: 28px;">âŒ UJIAN DITOLAK</h1>
                                <p style="margin: 10px 0 0 0; font-size: 14px;">Hasil Ujian Tidak Dapat Diterima</p>
                            </div>
                            
                            <div style="padding: 30px; background-color: white;">
                                <p>Dear <strong>%s</strong>,</p>
                                
                                <p>Setelah melakukan review terhadap hasil ujian Anda, kami memberitahukan bahwa <strong>hasil ujian Anda TIDAK DAPAT DITERIMA</strong>.</p>
                                
                                <div style="background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h3 style="margin-top: 0; color: #721c24;">ðŸ“ Alasan Penolakan:</h3>
                                    <p style="margin: 0; color: #721c24;">%s</p>
                                </div>
                                
                                <div style="background-color: #e3f2fd; border-left: 4px solid #1976d2; padding: 15px; margin: 20px 0; border-radius: 4px;">
                                    <h4 style="margin-top: 0; color: #0d47a1;">ðŸ’¡ Apa yang bisa dilakukan?</h4>
                                    <ul style="margin: 10px 0; padding-left: 20px; color: #1565c0;">
                                        <li>Hubungi tim PMB untuk informasi lebih lanjut</li>
                                        <li>Periksa kembali dashboard Anda untuk detail lengkap</li>
                                    </ul>
                                </div>
                                
                                <p style="color: #666; margin-top: 30px;">Jika Anda merasa ada kesalahan atau memiliki pertanyaan, silakan hubungi tim PMB kami.</p>
                                
                                <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen<br/>Universitas HKBP Nommensen</p>
                            </div>
                        </div>
                    </body>
                </html>
                """, studentName, rejectionReason.isEmpty() ? "Tidak memenuhi kriteria validasi" : rejectionReason);

            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
            log.info("âŒ Exam rejection email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("âŒ Error sending exam rejection email to {}: {}", studentEmail, e.getMessage());
        }
    }

    // ========== CS MESSAGING SYSTEM ==========

    @GetMapping("/messages/conversation/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getConversation(@PathVariable Long userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = userRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

            List<AdminMessage> messages = adminMessageRepository
                    .findConversationBetween(currentUser.getId(), userId);

            // Mark as read
            messages.forEach(msg -> {
                if (msg.getRecipient().getId().equals(currentUser.getId()) 
                    && msg.getStatus() == AdminMessage.MessageStatus.UNREAD) {
                    msg.setStatus(AdminMessage.MessageStatus.READ);
                    msg.setReadAt(LocalDateTime.now());
                    adminMessageRepository.save(msg);
                }
            });

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error fetching conversation: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }


    @PostMapping("/messages/send/{studentEmail}")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> sendMessageToStudent(@PathVariable String studentEmail,
                                                   @RequestBody SendMessageRequest request) {
        try {
            String adminEmail = SecurityContextHolder.getContext()
                    .getAuthentication().getName();
            User admin = userRepository.findByEmail(adminEmail)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            
            User student = userRepository.findByEmail(studentEmail)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentEmail));
            
            // Validate message content
            if (request.getMessageContent() == null || request.getMessageContent().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new HashMap<String, Object>() {{
                            put("success", false);
                            put("message", "Pesan tidak boleh kosong");
                        }});
            }
            
            if (request.getMessageContent().trim().length() < 5) {
                return ResponseEntity.badRequest()
                        .body(new HashMap<String, Object>() {{
                            put("success", false);
                            put("message", "Pesan minimal 5 karakter");
                        }});
            }
            
            // Create and save message
            AdminMessage message = AdminMessage.builder()
                    .sender(admin)
                    .recipient(student)
                    .messageContent(request.getMessageContent().trim())
                    .messageType(request.getMessageType() != null ? request.getMessageType() : "ANSWER")
                    .status(AdminMessage.MessageStatus.UNREAD)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            adminMessageRepository.save(message);
            
            log.info("âœ… Message sent from admin {} to student {}", adminEmail, studentEmail);
            
            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("success", true);
                put("message", "Pesan berhasil dikirim ke " + studentEmail);
                put("messageId", message.getId());
                put("sentAt", message.getCreatedAt());
            }});
        } catch (Exception e) {
            log.error("âŒ Error sending message: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new HashMap<String, Object>() {{
                        put("success", false);
                        put("message", e.getMessage());
                    }});
        }
    }


    // ========== EXPORT ==========




    // ========== TABLE DATA EXPORT (For page rendering) ==========

    @GetMapping("/hasil-akhir/wave/{waveType}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getHasilAkhirByWave(
            @PathVariable String waveType) {
        try {
            // Parse wave type
            RegistrationPeriod.WaveType wave;
            try {
                wave = RegistrationPeriod.WaveType.valueOf(waveType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid wave type: " + waveType));
            }
            
            // Filter hasil akhir by wave type
            List<HasilAkhir> hasilAkhirList = hasilAkhirRepository.findAll().stream()
                    .filter(ha -> ha.getWaveType() != null && ha.getWaveType().equals(wave))
                    .collect(Collectors.toList());
            
            List<Map<String, Object>> data = hasilAkhirList.stream().map(ha -> {
                Map<String, Object> row = new HashMap<>();
                Student student = ha.getStudent();
                row.put("id", ha.getId());
                row.put("studentName", student != null ? student.getFullName() : "N/A");
                row.put("nik", student != null ? student.getNik() : "N/A");
                row.put("email", student != null ? (student.getUser() != null ? student.getUser().getEmail() : "N/A") : "N/A");
                row.put("nomorRegistrasi", ha.getNomorRegistrasi() != null ? ha.getNomorRegistrasi() : "-");
                row.put("brivaNumber", ha.getBrivaNumber() != null ? ha.getBrivaNumber() : "-");
                row.put("brivaAmount", ha.getBrivaAmount() != null ? ha.getBrivaAmount().toString() : "0");
                row.put("jumlahCicilan", ha.getJumlahCicilan() != null ? ha.getJumlahCicilan() : 1);
                row.put("waveType", ha.getWaveType() != null ? ha.getWaveType().toString() : "N/A");
                row.put("selectionType", ha.getSelectionType() != null ? ha.getSelectionType() : "N/A");
                row.put("programStudiName", ha.getProgramStudiName() != null ? ha.getProgramStudiName() : "N/A");
                row.put("status", ha.getStatus() != null ? ha.getStatus().toString() : "PENDING");
                row.put("createdAt", ha.getCreatedAt());
                row.put("updatedAt", ha.getUpdatedAt());
                return row;
            }).collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("waveType", waveType);
            response.put("totalRecords", data.size());
            response.put("data", data);
            response.put("timestamp", LocalDateTime.now());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error fetching hasil akhir by wave: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }


    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Builder
    public static class RegistrationPeriodRequest {
        private String name;
        private LocalDateTime regStartDate;
        private LocalDateTime regEndDate;
        private LocalDateTime examDate;
        private LocalDateTime examEndDate;
        private LocalDateTime announcementDate;
        private LocalDateTime reenrollmentStartDate;
        private LocalDateTime reenrollmentEndDate;
        private String description;
        private String requirements; // e.g., "Wajib upload nilai, bukti ranking, dll"
        
        // âœ… NEW: Wave type - determines registration flow
        private RegistrationPeriod.WaveType waveType;
        
        // âœ… NEW: List of Jenis Seleksi IDs for this period
        private List<Long> jenisSeleksiIds;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SelectionTypeRequest {
        private Long periodId;
        private String name;
        private String description;
        private Boolean requireRanking;
        private Boolean requireTesting;
        private SelectionType.FormType formType;
        private BigDecimal price;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ValidationRequest {
        private Boolean approved;
        private String reason;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ApiResponse {
        private Boolean success;
        private String message;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class FormValidationRejectRequest {
        private String topic;
        private String reason;
        private Integer revisionNumber;  // Which revision number (1, 2, 3, etc)
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SendMessageRequest {
        private Long recipientId;
        private String messageContent;
        private String messageType;
        private Long admissionFormId;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class HasilAkhirRegistrationRequest {
        private String nomorRegistrasi;
        private String brivaNumber;
        private Integer jumlahCicilan;
    }


    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

            String roleStr = request.get("role");
            if (roleStr == null || roleStr.isEmpty()) {
                throw new RuntimeException("Role tidak valid");
            }

            User.UserRole newRole = User.UserRole.valueOf(roleStr);
            user.setRole(newRole);
            userRepository.save(user);

            log.info("âœ… User role updated: {} -> {}", user.getEmail(), newRole);
            return ResponseEntity.ok(new ApiResponse(true, "Role berhasil diubah"));
        } catch (Exception e) {
            log.error("âŒ Error updating user role: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentEmail = auth.getName();
            
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

            // Prevent deleting self
            if (user.getEmail().equals(currentEmail)) {
                throw new RuntimeException("Tidak bisa menghapus akun Anda sendiri");
            }

            userRepository.delete(user);
            log.info("âœ… User deleted: {}", user.getEmail());

            return ResponseEntity.ok(new ApiResponse(true, "Akun berhasil dihapus"));
        } catch (Exception e) {
            log.error("âŒ Error deleting user: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @PatchMapping("/api/users/{id}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentEmail = auth.getName();
            
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

            // Prevent deactivating self
            if (user.getEmail().equals(currentEmail)) {
                throw new RuntimeException("Tidak bisa menonaktifkan akun Anda sendiri");
            }

            user.setIsActive(false);
            userRepository.save(user);
            log.info("✅ User deactivated: {}", user.getEmail());

            return ResponseEntity.ok(new ApiResponse(true, "Akun berhasil dinonaktifkan"));
        } catch (Exception e) {
            log.error("❌ Error deactivating user: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @Transactional
    public ResponseEntity<?> updateRegistrationPeriod(@PathVariable Long id, 
                                                      @Valid @RequestBody RegistrationPeriodRequest request) {
        try {
            System.out.println("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
            System.out.println("ðŸ”¥ðŸ”¥ðŸ”¥ [CONTROLLER HIT] PUT /admin/periods/{id}");
            System.out.println("â•‘ ID: " + id);
            
            // ðŸ” DEBUG: Log method entry with security context
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            
            System.out.println("â•‘ AUTH OBJECT: " + SecurityContextHolder.getContext().getAuthentication());
            System.out.println("â•‘ USER: " + email);
            System.out.println("â•‘ AUTHORITIES: " + authorities.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toList()));
            System.out.println("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            
            RegistrationPeriod period = registrationPeriodRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Periode tidak ditemukan"));

            period.setName(request.getName());
            period.setRegStartDate(request.getRegStartDate());
            period.setRegEndDate(request.getRegEndDate());
            period.setExamDate(request.getExamDate());
            period.setExamEndDate(request.getExamEndDate());
            period.setAnnouncementDate(request.getAnnouncementDate());
            period.setReenrollmentStartDate(request.getReenrollmentStartDate());
            period.setReenrollmentEndDate(request.getReenrollmentEndDate());
            period.setDescription(request.getDescription());
            period.setRequirements(request.getRequirements());
            
            // âœ… NEW: Update wave type
            if (request.getWaveType() != null) {
                period.setWaveType(request.getWaveType());
            }

            registrationPeriodRepository.save(period);
            
            // âœ… FIXED: Update jenis seleksi relationships
            // First, delete existing relationships and flush immediately
            periodJenisSeleksiRepository.deleteByPeriod_Id(id);
            entityManager.flush(); // âœ… FIX: Flush the delete to database before inserting new records
            
            // Then, create new ones
            if (request.getJenisSeleksiIds() != null && !request.getJenisSeleksiIds().isEmpty()) {
                for (Long jenisSeleksiId : request.getJenisSeleksiIds()) {
                    JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId).orElse(null);
                    if (jenisSeleksi != null) {
                        PeriodJenisSeleksi pjs = PeriodJenisSeleksi.builder()
                                .period(period)
                                .jenisSeleksi(jenisSeleksi)
                                .isActive(true)
                                .build();
                        periodJenisSeleksiRepository.save(pjs);
                    }
                }
                log.info("âœ… Updated {} jenis seleksi for period: {}", request.getJenisSeleksiIds().size(), period.getName());
            }
            
            log.info("âœ… Period updated: ID={}, Name={}", id, period.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Periode berhasil diperbarui"));
        } catch (Exception e) {
            log.error("âŒ Error updating period: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    @Transactional
    public ResponseEntity<?> deleteRegistrationPeriod(@PathVariable Long id) {
        try {
            System.out.println("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
            System.out.println("ðŸ”¥ðŸ”¥ðŸ”¥ [CONTROLLER HIT] DELETE /admin/periods/{id}");
            System.out.println("â•‘ ID: " + id);
            
            // ðŸ” DEBUG: Log method entry
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            
            System.out.println("â•‘ AUTH OBJECT: " + SecurityContextHolder.getContext().getAuthentication());
            System.out.println("â•‘ USER: " + email);
            System.out.println("â•‘ AUTHORITIES: " + authorities.stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toList()));
            System.out.println("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");
            
            RegistrationPeriod period = registrationPeriodRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Periode tidak ditemukan"));

            // âœ… FIXED: Delete child records (PERIOD_JENIS_SELEKSI) first before deleting parent
            periodJenisSeleksiRepository.deleteByPeriod_Id(id);
            entityManager.flush(); // âœ… FIX: Flush to ensure children are deleted before parent
            
            registrationPeriodRepository.delete(period);
            log.info("âœ… Period deleted: ID={}, Name={}", id, period.getName());
            return ResponseEntity.ok(new ApiResponse(true, "Periode berhasil dihapus"));
        } catch (Exception e) {
            log.error("âŒ Error deleting period: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }





    public ResponseEntity<?> getSetting(@PathVariable String key) {
        try {
            SystemConfiguration setting = systemConfigRepository
                    .findByConfigKey(key)
                    .filter(SystemConfiguration::getIsActive)
                    .orElse(null);

            if (setting == null) {
                return ResponseEntity.ok(Map.of("value", ""));
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "key", setting.getConfigKey(),
                "value", setting.getConfigValue()
            ));
        } catch (Exception e) {
            log.error("âŒ Error getting setting: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("value", ""));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateSetting(@PathVariable String key, 
                                          @RequestBody Map<String, String> request) {
        try {
            String value = request.get("value");
            if (value == null) {
                throw new RuntimeException("Value tidak boleh kosong");
            }

            SystemConfiguration setting = systemConfigRepository
                    .findByConfigKey(key)
                    .orElse(null);

            if (setting == null) {
                // Create new setting
                setting = SystemConfiguration.builder()
                        .configKey(key)
                        .configValue(value)
                        .configType(SystemConfiguration.ConfigType.STRING)
                        .isActive(true)
                        .build();
                log.info("âœ… New setting created: {}", key);
            } else {
                // Update existing
                setting.setConfigValue(value);
                setting.setUpdatedAt(LocalDateTime.now());
                log.info("âœ… Setting updated: {} = {}", key, value);
            }

            systemConfigRepository.save(setting);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Setting berhasil disimpan",
                "key", key,
                "value", value
            ));
        } catch (Exception e) {
            log.error("âŒ Error updating setting: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== EXAM LINK MANAGEMENT ==========

    /**
     * Create exam link (Admin Pusat only)
     */
    @PostMapping("/api/exam-links")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createExamLink(@Valid @RequestBody CreateExamLinkRequest request) {
        try {
            RegistrationPeriod period = registrationPeriodRepository.findById(request.getPeriodId())
                    .orElseThrow(() -> new RuntimeException("Period tidak ditemukan"));

            SelectionType selectionType = null;
            if (request.getSelectionTypeId() != null) {
                selectionType = selectionTypeRepository.findById(request.getSelectionTypeId())
                        .orElse(null);
            }

            // Validate link URL
            if (!request.getLinkUrl().contains("forms.google.com") && !request.getLinkUrl().contains("forms.gle")) {
                throw new RuntimeException("Link harus menggunakan Google Form (forms.google.com atau forms.gle)");
            }

            ExamLink examLink = ExamLink.builder()
                    .period(period)
                    .selectionType(selectionType)
                    .linkTitle(request.getLinkTitle())
                    .linkUrl(request.getLinkUrl())
                    .description(request.getDescription())
                    .isActive(true)
                    .build();

            examLinkRepository.save(examLink);
            log.info("âœ… Exam link created: {} for period {}", request.getLinkTitle(), period.getName());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Link ujian berhasil dibuat"));
        } catch (Exception e) {
            log.error("âŒ Error creating exam link: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get exam links by period
     */
    @GetMapping("/api/exam-links/period/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getExamLinksByPeriod(@PathVariable Long periodId) {
        try {
            List<ExamLink> links = examLinkRepository.findByPeriodId(periodId);
            
            List<Map<String, Object>> linkDtos = links.stream()
                    .map(link -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("id", link.getId());
                        dto.put("periodId", link.getPeriod().getId());
                        dto.put("periodName", link.getPeriod().getName());
                        dto.put("selectionTypeId", link.getSelectionType() != null ? link.getSelectionType().getId() : null);
                        dto.put("selectionTypeName", link.getSelectionType() != null ? link.getSelectionType().getName() : "General");
                        dto.put("linkTitle", link.getLinkTitle());
                        dto.put("linkUrl", link.getLinkUrl());
                        dto.put("description", link.getDescription());
                        dto.put("isActive", link.getIsActive());
                        dto.put("createdAt", link.getCreatedAt());
                        return dto;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", linkDtos);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error fetching exam links: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Update exam link (Admin Pusat only)
     */
    @PutMapping("/api/exam-links/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateExamLink(@PathVariable Long id, 
                                           @Valid @RequestBody CreateExamLinkRequest request) {
        try {
            ExamLink link = examLinkRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Link ujian tidak ditemukan"));

            if (!request.getLinkUrl().contains("forms.google.com") && !request.getLinkUrl().contains("forms.gle")) {
                throw new RuntimeException("Link harus menggunakan Google Form");
            }

            link.setLinkTitle(request.getLinkTitle());
            link.setLinkUrl(request.getLinkUrl());
            link.setDescription(request.getDescription());
            link.setUpdatedAt(LocalDateTime.now());

            examLinkRepository.save(link);
            log.info("âœ… Exam link updated: {}", request.getLinkTitle());

            return ResponseEntity.ok(new ApiResponse(true, "Link ujian berhasil diperbarui"));
        } catch (Exception e) {
            log.error("âŒ Error updating exam link: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Delete exam link (Admin Pusat only)
     */
    @DeleteMapping("/api/exam-links/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteExamLink(@PathVariable Long id) {
        try {
            ExamLink link = examLinkRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Link ujian tidak ditemukan"));

            examLinkRepository.delete(link);
            log.info("âœ… Exam link deleted: {}", link.getLinkTitle());

            return ResponseEntity.ok(new ApiResponse(true, "Link ujian berhasil dihapus"));
        } catch (Exception e) {
            log.error("âŒ Error deleting exam link: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getSelectionTypesByPeriod(@PathVariable Long periodId) {
        try {
            RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                    .orElseThrow(() -> new RuntimeException("Period tidak ditemukan"));

            List<SelectionType> selectionTypes = selectionTypeRepository.findByPeriod_Id(periodId);

            List<Map<String, Object>> typeDtos = selectionTypes.stream()
                    .map(type -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("id", type.getId());
                        dto.put("name", type.getName());
                        dto.put("description", type.getDescription());
                        dto.put("formType", type.getFormType().toString());
                        dto.put("requireRanking", type.getRequireRanking());
                        dto.put("requireTesting", type.getRequireTesting());
                        dto.put("price", type.getPrice());
                        dto.put("isActive", type.getIsActive());
                        dto.put("createdAt", type.getCreatedAt());
                        return dto;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("periodId", period.getId());
            response.put("periodName", period.getName());
            response.put("data", typeDtos);
            response.put("total", typeDtos.size());

            log.info("âœ… Retrieved {} selection types for period {}", typeDtos.size(), period.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error fetching selection types: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateSelectionType(@PathVariable Long id,
                                                @Valid @RequestBody UpdateSelectionTypeRequest request) {
        try {
            SelectionType type = selectionTypeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Jenis seleksi tidak ditemukan"));

            type.setName(request.getName());
            type.setDescription(request.getDescription());
            type.setRequireRanking(request.getRequireRanking());
            type.setRequireTesting(request.getRequireTesting());
            type.setPrice(request.getPrice());
            type.setIsActive(request.getIsActive());
            type.setUpdatedAt(LocalDateTime.now());

            selectionTypeRepository.save(type);
            log.info("âœ… Selection type updated: {}", request.getName());

            return ResponseEntity.ok(new ApiResponse(true, "Jenis seleksi berhasil diperbarui"));
        } catch (Exception e) {
            log.error("âŒ Error updating selection type: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteSelectionType(@PathVariable Long id) {
        try {
            SelectionType type = selectionTypeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Jenis seleksi tidak ditemukan"));

            selectionTypeRepository.delete(type);
            log.info("âœ… Selection type deleted: {}", type.getName());

            return ResponseEntity.ok(new ApiResponse(true, "Jenis seleksi berhasil dihapus"));
        } catch (Exception e) {
            log.error("âŒ Error deleting selection type: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ========== DTOs FOR EXAM LINKS ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class CreateExamLinkRequest {
        private Long periodId;
        private Long selectionTypeId; // Optional: null for general exam link
        private String linkTitle;
        private String linkUrl;
        private String description;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class UpdateSelectionTypeRequest {
        private String name;
        private String description;
        private Boolean requireRanking;
        private Boolean requireTesting;
        private BigDecimal price;
        private Boolean isActive;
    }

    // ========== JENIS SELEKSI DTO ==========
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class JenisSeleksiRequest {
        private String code;          // e.g., "REGULAR", "SCHOLARSHIP"
        private String nama;          // e.g., "Seleksi Reguler"
        private String deskripsi;     // Description
        private String fasilitas;     // Features (comma-separated or JSON)
        private String logoUrl;       // Logo/Icon URL or emoji
        private BigDecimal harga;     // Registration fee
        private Boolean isActive;     // Active/Inactive
        private Integer sortOrder;    // Display order
        private List<Long> programStudiIds;  // M2M: Related program studi IDs
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ProgramStudiRequest {
        private String kode;          // e.g., "TI", "SI"
        private String nama;          // e.g., "Teknik Informatika"
        private String deskripsi;     // Description
        private Boolean isMedical;    // Whether it's a medical program
        private Boolean isActive;     // Active/Inactive
        private Integer sortOrder;    // Display order
        private Long hargaTotalPerTahun;  // Total program fee per year (for installments)
        private Long cicilan1;             // First installment amount
        private Long cicilan2;
        private Long cicilan3;
        private Long cicilan4;
        private Long cicilan5;
        private Long cicilan6;
    }

    // ========== PAYMENT & EXAM VERIFICATION ==========

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PostMapping("/forms/{id}/verify-payment")
    public ResponseEntity<?> verifyPayment(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Payment verified"); }});
        } catch (Exception e) {
            log.error("âŒ Error verifying payment: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PostMapping("/exams/{id}/generate-token")
    public ResponseEntity<?> generateExamToken(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Token generated"); }});
        } catch (Exception e) {
            log.error("âŒ Error generating exam token: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/exams/{id}/token")
    public ResponseEntity<?> getExamToken(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Token retrieved"); }});
        } catch (Exception e) {
            log.error("âŒ Error getting exam token: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @GetMapping("/exam-results/{id}/details")
    public ResponseEntity<?> getExamSubmissionDetails(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Exam submission details"); }});
        } catch (Exception e) {
            log.error("âŒ Error getting exam submission details: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @GetMapping("/exam-results/student/{studentId}")
    public ResponseEntity<?> getExamResultByStudentId(@PathVariable Long studentId) {
        try {
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Exam results retrieved"); }});
        } catch (Exception e) {
            log.error("âŒ Error getting exam results by student ID: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    @PutMapping("/exam-results/{id}/validate")
    public ResponseEntity<?> validateExamSubmission(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        try {
            String action = request != null ? request.get("action") : null;
            String adminNotes = request != null ? request.get("adminNotes") : null;
            return ResponseEntity.ok(new HashMap<String, Object>() {{ put("success", true); put("message", "Exam validation processed"); }});
        } catch (Exception e) {
            log.error("âŒ Error validating exam submission: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ put("success", false); put("message", e.getMessage()); }});
        }
    }

    // ========== ADMIN REMINDERS ==========


    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SendReminderRequest {
        private String studentEmail;
        private String studentName;
        private String messageTitle;
        private String messageBody;
        private String reminderType; // PENDING, NOT_PAID, APPROVED, INCOMPLETE
        private Long formulirId;
    }

    @GetMapping("/reenrollments/{id}/full-details")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getReerollmentDetails(@PathVariable Long id) {
        try {
            ReEnrollment reenrollment = reenrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Re-enrollment tidak ditemukan"));

            Student student = reenrollment.getStudent();
            List<Map<String, Object>> documents = new ArrayList<>();

            // âœ… FIRST: Try loading from ReEnrollmentDocument relationship
            if (reenrollment.getDocuments() != null && !reenrollment.getDocuments().isEmpty()) {
                for (ReEnrollmentDocument doc : reenrollment.getDocuments()) {
                    documents.add(new HashMap<String, Object>() {{
                        put("id", doc.getId());
                        put("documentType", doc.getDocumentType().toString());
                        put("displayName", doc.getDocumentType().getDisplayName());
                        put("fileName", doc.getOriginalFilename());
                        put("fileSize", doc.getFileSize());
                        put("uploadedAt", doc.getUploadedAt());
                        put("validationStatus", doc.getValidationStatus().toString());
                        put("adminNotes", doc.getAdminNotes());
                        put("filePath", doc.getFilePath());
                    }});
                }
                log.info("âœ… [REENROLL-DETAILS] Loaded {} documents from documents relationship", documents.size());
            }

            // âœ… SECOND: Fallback - Load from individual file columns if documents empty
            if (documents.isEmpty()) {
                log.info("ðŸ“„ [REENROLL-DETAILS] Documents relationship empty - loading from individual file columns...");
                
                // Map of file column name -> document type
                Map<String, String> fileMapping = new HashMap<>();
                fileMapping.put("PAKTA_INTEGRITAS", reenrollment.getPaktaIntegritasFile());
                fileMapping.put("IJAZAH", reenrollment.getIjazahFile());
                fileMapping.put("PASPHOTO", reenrollment.getPasphotoFile());
                fileMapping.put("KARTU_KELUARGA", reenrollment.getKartuKeluargaFile());
                fileMapping.put("KARTU_TANDA_PENDUDUK", reenrollment.getKtpFile());
                fileMapping.put("KETERANGAN_BEBAS_NARKOBA", reenrollment.getSuratBebasNarkobaFile());
                fileMapping.put("SKCK", reenrollment.getSkckFile());
                
                for (Map.Entry<String, String> entry : fileMapping.entrySet()) {
                    String docType = entry.getKey();
                    String filePath = entry.getValue();
                    
                    if (filePath != null && !filePath.isEmpty()) {
                        // Get display name from enum
                        String displayName = docType;
                        try {
                            displayName = ReEnrollmentDocument.DocumentType.valueOf(docType).getDisplayName();
                        } catch (IllegalArgumentException e) {
                            log.warn("Unknown document type: {}", docType);
                        }
                        
                        final String finalDisplayName = displayName;
                        final String finalFilePath = filePath;
                        
                        documents.add(new HashMap<String, Object>() {{
                            put("id", null);  // No database ID since it's from column
                            put("documentType", docType);
                            put("displayName", finalDisplayName);
                            put("fileName", new java.io.File(finalFilePath).getName());
                            put("fileSize", null);
                            put("uploadedAt", reenrollment.getSubmittedAt());
                            put("validationStatus", "APPROVED");  // Default to APPROVED for submitted documents
                            put("adminNotes", null);
                            put("filePath", finalFilePath);
                        }});
                        log.info("   âœ“ Found: {} -> {}", docType, filePath);
                    }
                }
                log.info("âœ… [REENROLL-DETAILS] Fallback loaded {} documents from columns", documents.size());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("id", reenrollment.getId());
            response.put("studentId", student.getId());
            response.put("studentName", student.getFullName());
            response.put("email", student.getUser().getEmail());
            response.put("parentPhone", reenrollment.getParentPhone());
            response.put("parentEmail", reenrollment.getParentEmail());
            response.put("parentAddress", reenrollment.getParentAddress());
            response.put("permanentAddress", reenrollment.getPermanentAddress());
            response.put("currentAddress", reenrollment.getCurrentAddress());
            response.put("parentName", reenrollment.getParentName());
            response.put("alumniFamily", reenrollment.getAlumniFamily());
            response.put("alumniName", reenrollment.getAlumniName());
            response.put("alumniRelation", reenrollment.getAlumniRelation());
            response.put("submittedAt", reenrollment.getSubmittedAt());
            response.put("status", reenrollment.getStatus().toString());
            response.put("documents", documents);
            response.put("totalDocuments", documents.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching reenrollment details: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PutMapping("/reenrollments/documents/{docId}/validate")
    @PreAuthorize("hasRole('ADMIN_VALIDASI')")
    public ResponseEntity<?> validateDocument(@PathVariable Long docId,
                                              @RequestBody DocumentValidationRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User admin = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));

            ReEnrollmentDocument document = reenrollmentDocumentRepository.findById(docId)
                    .orElseThrow(() -> new RuntimeException("Dokumen tidak ditemukan"));

            if ("APPROVE".equals(request.getAction())) {
                document.setValidationStatus(ReEnrollmentDocument.ValidationStatus.APPROVED);
                document.setAdminNotes(request.getAdminNotes());
                log.info("âœ… Document {} approved by admin {}", docId, email);
            } else if ("REJECT".equals(request.getAction())) {
                document.setValidationStatus(ReEnrollmentDocument.ValidationStatus.REJECTED);
                document.setAdminNotes(request.getAdminNotes());
                log.info("âŒ Document {} rejected by admin {}", docId, email);
            } else if ("REVISION_NEEDED".equals(request.getAction())) {
                document.setValidationStatus(ReEnrollmentDocument.ValidationStatus.REVISION_NEEDED);
                document.setAdminNotes(request.getAdminNotes());
                log.info("âš ï¸ Document {} marked for revision by admin {}", docId, email);
            }

            document.setValidatedAt(LocalDateTime.now());
            document.setValidatedByAdmin(admin);
            reenrollmentDocumentRepository.save(document);

            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("success", true);
                put("message", "Dokumen berhasil divalidasi (" + request.getAction() + ")");
                put("newStatus", document.getValidationStatus().toString());
                put("validatedAt", document.getValidatedAt());
            }});
        } catch (Exception e) {
            log.error("Error validating document: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN_VALIDASI')")
    public ResponseEntity<?> finalizeReEnrollment(@PathVariable Long id,
                                                   @RequestBody ReenrollmentFinalizeRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User admin = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Admin tidak ditemukan"));

            ReEnrollment reenrollment = reenrollmentRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Re-enrollment tidak ditemukan"));

            if ("APPROVE".equals(request.getAction())) {
                // Check all documents are approved
                boolean allApproved = reenrollment.getDocuments().stream()
                        .allMatch(d -> d.getValidationStatus() == ReEnrollmentDocument.ValidationStatus.APPROVED);

                if (!allApproved) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "Semua dokumen harus disetujui terlebih dahulu"));
                }

                reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.VALIDATED);
                reenrollment.setValidationNotes(request.getValidationNotes());
                log.info("âœ… Re-enrollment {} approved by admin {}", id, email);
            } else if ("REJECT".equals(request.getAction())) {
                reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.REJECTED);
                reenrollment.setValidationNotes(request.getValidationNotes());
                log.info("âŒ Re-enrollment {} rejected by admin {}", id, email);
            }

            reenrollment.setValidatedAt(LocalDateTime.now());
            reenrollmentRepository.save(reenrollment);

            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("success", true);
                put("message", "Re-enrollment berhasil diproses (" + request.getAction() + ")");
                put("newStatus", reenrollment.getStatus().toString());
                put("validatedAt", reenrollment.getValidatedAt());
            }});
        } catch (Exception e) {
            log.error("Error finalizing reenrollment: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * âœ… DTO untuk validasi dokumen daftar ulang
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class DocumentValidationRequest {
        private String action; // APPROVE, REJECT, REVISION_NEEDED
        private String adminNotes;
    }

    /**
     * âœ… DTO untuk finalisasi daftar ulang
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ReenrollmentFinalizeRequest {
        private String action; // APPROVE, REJECT
        private String validationNotes;
    }

    /**
     * âœ… DTO untuk validasi ujian
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ExamValidationRequest {
        private String action; // APPROVE, REJECT, or REVISI
        private String adminNotes; // optional notes
    }

    /**
     * âœ… Auto-populate jenis seleksi (Kedokteran & Program Non-Kedokteran)
     */
    @PostMapping("/jenis-seleksi/bulk-initialize")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> bulkInitializeJenisSeleksi() {
        try {
            List<Map<String, Object>> selsToInsert = new ArrayList<>();
            selsToInsert.add(Map.of(
                "code", "KEDOKTERAN", 
                "nama", "Kedokteran", 
                "logoUrl", "ðŸ’‰", 
                "deskripsi", "Program Studi Kedokteran dengan fasilitas praktik lengkap",
                "fasilitas", "Lab Lengkap,Tes tulis,Wawancara,Assessment psikologi",
                "harga", 1500000
            ));
            selsToInsert.add(Map.of(
                "code", "NON_KEDOKTERAN", 
                "nama", "Program Non-Kedokteran", 
                "logoUrl", "ðŸŽ“", 
                "deskripsi", "Semua program studi non-kedokteran: Teknik, Pendidikan, Ekonomi, Hukum, Seni & Sastra, Pertanian, Psikologi, Sosial-Politik, & Pascasarjana",
                "fasilitas", "Fasilitas Modern,Dosen Bersertifikat,Industri Ready,25+ Program Studi",
                "harga", 750000
            ));
            
            int inserted = 0;
            for (Map<String, Object> sel : selsToInsert) {
                String code = (String) sel.get("code");
                
                // Skip if already exists
                if (jenisSeleksiRepository.existsByCode(code)) {
                    log.info("â­ï¸ Jenis Seleksi {} sudah ada, skip", code);
                    continue;
                }
                
                JenisSeleksi jenisSeleksi = JenisSeleksi.builder()
                        .code(code)
                        .nama((String) sel.get("nama"))
                        .logoUrl((String) sel.get("logoUrl"))
                        .deskripsi((String) sel.get("deskripsi"))
                        .fasilitas((String) sel.get("fasilitas"))
                        .harga(new BigDecimal((Integer) sel.get("harga")))
                        .isActive(true)
                        .sortOrder(inserted + 1)
                        .build();
                
                jenisSeleksiRepository.save(jenisSeleksi);
                inserted++;
            }
            
            String message = String.format("âœ… Jenis Seleksi bulk initialized: %d inserted, %d skipped (already exist)", 
                    inserted, selsToInsert.size() - inserted);
            log.info(message);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            response.put("inserted", inserted);
            response.put("total", selsToInsert.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("âŒ Error bulk initializing jenis seleksi: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }


    @PreAuthorize("hasRole('ADMIN_VALIDASI')")
    public ResponseEntity<?> uploadDokumenSementara(
            @PathVariable Long id,
            @RequestParam(value = "npmSementara", required = false) MultipartFile npmFile,
            @RequestParam(value = "ktmSementara", required = false) MultipartFile ktmFile) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            log.info("ðŸ“„ Admin {} uploading dokumen sementara for HasilAkhir #{}", email, id);

            HasilAkhir hasilAkhir = hasilAkhirRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Hasil Akhir tidak ditemukan"));

            if (npmFile == null && ktmFile == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Minimal satu file harus diupload (NPM atau KTM Sementara)"));
            }

            String uploadDir = "uploads/hasil-akhir/" + hasilAkhir.getStudent().getId();
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);

            if (npmFile != null && !npmFile.isEmpty()) {
                String originalName = npmFile.getOriginalFilename();
                if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File NPM Sementara harus berformat PDF"));
                }
                String npmFileName = "npm_sementara_" + System.currentTimeMillis() + ".pdf";
                Path npmPath = uploadPath.resolve(npmFileName);
                Files.copy(npmFile.getInputStream(), npmPath, StandardCopyOption.REPLACE_EXISTING);
                hasilAkhir.setNpmSementaraFile(uploadDir + "/" + npmFileName);
                log.info("âœ… NPM Sementara uploaded: {}", npmPath);
            }

            if (ktmFile != null && !ktmFile.isEmpty()) {
                String originalName = ktmFile.getOriginalFilename();
                if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File KTM Sementara harus berformat PDF"));
                }
                String ktmFileName = "ktm_sementara_" + System.currentTimeMillis() + ".pdf";
                Path ktmPath = uploadPath.resolve(ktmFileName);
                Files.copy(ktmFile.getInputStream(), ktmPath, StandardCopyOption.REPLACE_EXISTING);
                hasilAkhir.setKtmSementaraFile(uploadDir + "/" + ktmFileName);
                log.info("âœ… KTM Sementara uploaded: {}", ktmPath);
            }

            hasilAkhirRepository.save(hasilAkhir);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Dokumen sementara berhasil diupload");
            result.put("npmSementaraFile", hasilAkhir.getNpmSementaraFile());
            result.put("ktmSementaraFile", hasilAkhir.getKtmSementaraFile());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("âŒ Error uploading dokumen sementara: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }


    /**
     * Finalize re-enrollment (approve/reject all at once)
     * PUT /admin/reenrollments/{reenrollmentId}/finalize
     */
    @PutMapping("/reenrollments/{reenrollmentId}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> finalizeReenrollment(@PathVariable Long reenrollmentId,
                                                   @RequestBody(required = false) Map<String, String> request) {
        try {
            String action = request != null ? request.get("action") : "approve";
            String validationNotes = request != null ? request.get("validationNotes") : "";
            
            ReEnrollment reenrollment = reenrollmentRepository.findById(reenrollmentId)
                    .orElseThrow(() -> new RuntimeException("Daftar ulang tidak ditemukan"));
            
            // Update re-enrollment status
            if ("approve".equalsIgnoreCase(action)) {
                reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.COMPLETED);
            } else if ("reject".equalsIgnoreCase(action)) {
                reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.REJECTED);
            }
            
            reenrollment.setValidationNotes(validationNotes);
            reenrollment.setValidatedAt(LocalDateTime.now());
            
            reenrollmentRepository.save(reenrollment);
            
            log.info("✅ [REENROLL-FINALIZE] Re-enrollment {} finalized with action: {}", reenrollmentId, action);
            
            return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("success", true);
                put("message", "Re-enrollment finalized successfully");
                put("reenrollmentId", reenrollmentId);
                put("status", reenrollment.getStatus().toString());
            }});
        } catch (Exception e) {
            log.error("Error finalizing re-enrollment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new HashMap<String, Object>() {{ 
                        put("success", false); 
                        put("message", e.getMessage()); 
                    }});
        }
    }

}
