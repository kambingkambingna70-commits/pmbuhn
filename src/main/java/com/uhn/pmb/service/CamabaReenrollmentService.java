package com.uhn.pmb.service;

import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for student-facing re-enrollment (daftar ulang) operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CamabaReenrollmentService {

    private final ReEnrollmentRepository reenrollmentRepository;
    private final ReEnrollmentDocumentRepository reenrollmentDocumentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final RegistrationStatusRepository registrationStatusRepository;
    private final RegistrationStatusService registrationStatusService;
    private final ExamRepository examRepository;
    private final ExamResultRepository examResultRepository;
    private final FileStorageService fileStorageService;

    private Student resolveStudent(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));
    }

    /**
     * Submit re-enrollment with document uploads.
     */
    public Map<String, Object> submitReenrollment(String userEmail, String parentPhone,
                                                   String parentEmail, String parentAddress,
                                                   String permanentAddress, String currentAddress,
                                                   Boolean alumniFamily, String alumniName,
                                                   String alumniRelation,
                                                   Map<String, MultipartFile> documents) throws IOException {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        log.info("📝 [REENROLL-SUBMIT] Processing re-enrollment for student: {} (ID: {})",
                userEmail, student.getId());

        // Get exam result if exists
        ExamResult examResult = null;
        try {
            Optional<Exam> examOpt = examRepository.findByStudent_Id(student.getId());
            if (examOpt.isPresent()) {
                examResult = examResultRepository.findByExam_Id(examOpt.get().getId())
                        .filter(result -> result.getStatus() == ExamResult.ResultStatus.PASSED)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("⚠️ [REENROLL-SUBMIT] Could not fetch exam result: {}", e.getMessage());
        }

        // Check if already submitted (non-rejected)
        Optional<ReEnrollment> existing = reenrollmentRepository.findAll().stream()
                .filter(r -> r.getStudent().getId().equals(student.getId()))
                .filter(r -> r.getStatus() != ReEnrollment.ReEnrollmentStatus.REJECTED)
                .findFirst();

        if (existing.isPresent()) {
            throw new RuntimeException("Anda sudah melakukan daftar ulang sebelumnya");
        }

        // Create upload directory
        String uploadDir = System.getProperty("user.dir") + java.io.File.separator + "uploads"
                + java.io.File.separator + "reenrollment";
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            log.error("❌ [REENROLL-SUBMIT] Failed to create upload directory: {}", e.getMessage());
            throw new RuntimeException("Gagal membuat direktori upload");
        }

        // Build ReEnrollment record
        ReEnrollment reenrollment = ReEnrollment.builder()
                .examResult(examResult)
                .student(student)
                .parentName(student.getFullName())
                .parentPhone(parentPhone)
                .parentEmail(parentEmail)
                .parentAddress(parentAddress)
                .permanentAddress(permanentAddress)
                .currentAddress(currentAddress == null || currentAddress.isEmpty()
                        ? permanentAddress : currentAddress)
                .alumniFamily(alumniFamily)
                .alumniName(alumniName)
                .alumniRelation(alumniRelation)
                .status(ReEnrollment.ReEnrollmentStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .documents(new ArrayList<>())
                .build();

        reenrollment = reenrollmentRepository.save(reenrollment);
        log.info("✅ [REENROLL-SUBMIT] ReEnrollment saved, ID: {}", reenrollment.getId());

        // Process document uploads
        Map<String, String> documentPaths = new HashMap<>();
        for (Map.Entry<String, MultipartFile> entry : documents.entrySet()) {
            String key = entry.getKey();
            MultipartFile file = entry.getValue();
            if (file == null || file.isEmpty()) continue;
            String docTypeStr = key.replace("documents[", "").replace("]", "").toUpperCase();
            if (file.getSize() > 5 * 1024 * 1024) {
                log.warn("⚠️ [REENROLL-SUBMIT] File too large: {} ({} bytes)",
                        file.getOriginalFilename(), file.getSize());
                continue;
            }
            try {
                ReEnrollmentDocument.DocumentType.valueOf(docTypeStr);
                String filename = UUID.randomUUID() + "_" +
                        (file.getOriginalFilename() != null
                                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") : "file");
                String filepath = uploadDir + java.io.File.separator + filename;
                Files.write(Paths.get(filepath), file.getBytes());
                log.info("✅ [REENROLL-SUBMIT] File saved: {} -> {}", file.getOriginalFilename(), filepath);
                documentPaths.put(docTypeStr, filepath);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ [REENROLL-SUBMIT] Invalid document type: {} - {}", docTypeStr, e.getMessage());
            } catch (IOException e) {
                log.error("❌ [REENROLL-SUBMIT] Failed to write file: {}", e.getMessage());
            }
        }

        // Update reenrollment with document file paths
        if (!documentPaths.isEmpty()) {
            if (documentPaths.containsKey("PAKTA_INTEGRITAS"))
                reenrollment.setPaktaIntegritasFile(documentPaths.get("PAKTA_INTEGRITAS"));
            if (documentPaths.containsKey("IJAZAH"))
                reenrollment.setIjazahFile(documentPaths.get("IJAZAH"));
            if (documentPaths.containsKey("PASPHOTO"))
                reenrollment.setPasphotoFile(documentPaths.get("PASPHOTO"));
            if (documentPaths.containsKey("KARTU_KELUARGA"))
                reenrollment.setKartuKeluargaFile(documentPaths.get("KARTU_KELUARGA"));
            if (documentPaths.containsKey("KARTU_TANDA_PENDUDUK"))
                reenrollment.setKtpFile(documentPaths.get("KARTU_TANDA_PENDUDUK"));
            if (documentPaths.containsKey("KETERANGAN_BEBAS_NARKOBA"))
                reenrollment.setSuratBebasNarkobaFile(documentPaths.get("KETERANGAN_BEBAS_NARKOBA"));
            if (documentPaths.containsKey("SKCK"))
                reenrollment.setSkckFile(documentPaths.get("SKCK"));
            reenrollment = reenrollmentRepository.save(reenrollment);
            log.info("✅ [REENROLL-SUBMIT] Document paths saved: {} files", documentPaths.size());
        }

        // Update DAFTAR_ULANG stage
        try {
            Optional<RegistrationStatus> daftarUlangStatus = registrationStatusRepository
                    .findByUserAndStage(user, RegistrationStatus.RegistrationStage.DAFTAR_ULANG);
            if (daftarUlangStatus.isPresent()) {
                RegistrationStatus status = daftarUlangStatus.get();
                status.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
                status.setUpdatedAt(LocalDateTime.now());
                registrationStatusRepository.save(status);
                log.info("✅ [REENROLL-SUBMIT] DAFTAR_ULANG marked SELESAI");
            } else {
                log.warn("⚠️ [REENROLL-SUBMIT] DAFTAR_ULANG not found in REGISTRATION_STAGES");
            }
        } catch (Exception e) {
            log.error("❌ [REENROLL-SUBMIT] Failed to update REGISTRATION_STAGES: {}", e.getMessage());
        }

        log.info("✅ [REENROLL-SUBMIT] Submission complete - Re-enrollment ID: {}, Documents: {}",
                reenrollment.getId(), documentPaths.size());

        return Map.of(
                "success", true,
                "message", "Daftar ulang berhasil dikirim!",
                "reenrollmentId", reenrollment.getId(),
                "status", reenrollment.getStatus().toString(),
                "documentsCount", documentPaths.size()
        );
    }

    /**
     * Get re-enrollment submission status for student.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReenrollmentStatus(String userEmail) {
        Student student = resolveStudent(userEmail);

        Optional<ReEnrollment> reenrollment = reenrollmentRepository.findAll().stream()
                .filter(r -> r.getStudent().getId().equals(student.getId()))
                .max(Comparator.comparing(ReEnrollment::getSubmittedAt));

        if (reenrollment.isEmpty()) {
            return Map.of("status", "NOT_STARTED", "message", "Belum ada daftar ulang");
        }

        ReEnrollment re = reenrollment.get();
        int totalDocs = re.getDocuments().size();
        int approvedDocs = (int) re.getDocuments().stream()
                .filter(d -> d.getValidationStatus() == ReEnrollmentDocument.ValidationStatus.APPROVED)
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("status", re.getStatus().toString());
        response.put("submittedAt", re.getSubmittedAt());
        response.put("validatedAt", re.getValidatedAt());
        response.put("totalDocuments", totalDocs);
        response.put("approvedDocuments", approvedDocs);
        response.put("validationNotes", re.getValidationNotes());
        return response;
    }

    /**
     * Mark DAFTAR_ULANG stage as SELESAI.
     */
    public Map<String, Object> completeReenrollment(String userEmail, String submittedAt) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        log.info("📝 [REENROLL-COMPLETE] Marking DAFTAR_ULANG as SELESAI for user: {}", userEmail);

        RegistrationStatus daftarUlangStatus = registrationStatusService.getOrCreateStatus(
                user, RegistrationStatus.RegistrationStage.DAFTAR_ULANG);

        daftarUlangStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
        daftarUlangStatus.setSubmissionDate(LocalDateTime.now());
        daftarUlangStatus.setUpdatedAt(LocalDateTime.now());
        if (submittedAt != null) {
            daftarUlangStatus.setDataJson("Daftar ulang submitted at: " + submittedAt);
        }
        RegistrationStatus savedStatus = registrationStatusRepository.save(daftarUlangStatus);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Daftar ulang berhasil ditandai sebagai selesai");
        response.put("stage", "DAFTAR_ULANG");
        response.put("status", savedStatus.getStatus().toString());
        response.put("statusId", savedStatus.getId());
        response.put("completedAt", LocalDateTime.now());
        response.put("editDeadline", savedStatus.getEditDeadline());
        return response;
    }

    /**
     * Get re-enrollment data with documents for student.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReenrollmentData(String userEmail) {
        Student student = resolveStudent(userEmail);

        Optional<ReEnrollment> reenrollmentOpt = reenrollmentRepository.findAll().stream()
                .filter(r -> r.getStudent().getId().equals(student.getId()))
                .max(Comparator.comparing(ReEnrollment::getSubmittedAt));

        if (reenrollmentOpt.isEmpty()) {
            return Map.of("exists", false, "message", "Belum ada data daftar ulang");
        }

        ReEnrollment reenrollment = reenrollmentOpt.get();
        if (reenrollment.getDocuments() == null) {
            reenrollment.setDocuments(new ArrayList<>());
        }

        Map<String, Object> files = new HashMap<>();
        if (reenrollment.getDocuments() != null && !reenrollment.getDocuments().isEmpty()) {
            for (ReEnrollmentDocument doc : reenrollment.getDocuments()) {
                files.put(doc.getDocumentType().toString(),
                        fileStorageService.convertPathToUrl(doc.getFilePath()));
            }
        }
        // Fallback to old columns
        if (reenrollment.getPaktaIntegritasFile() != null && !files.containsKey("PAKTA_INTEGRITAS"))
            files.put("PAKTA_INTEGRITAS", fileStorageService.convertPathToUrl(reenrollment.getPaktaIntegritasFile()));
        if (reenrollment.getIjazahFile() != null && !files.containsKey("IJAZAH"))
            files.put("IJAZAH", fileStorageService.convertPathToUrl(reenrollment.getIjazahFile()));
        if (reenrollment.getPasphotoFile() != null && !files.containsKey("PASPHOTO"))
            files.put("PASPHOTO", fileStorageService.convertPathToUrl(reenrollment.getPasphotoFile()));
        if (reenrollment.getKartuKeluargaFile() != null && !files.containsKey("KARTU_KELUARGA"))
            files.put("KARTU_KELUARGA", fileStorageService.convertPathToUrl(reenrollment.getKartuKeluargaFile()));
        if (reenrollment.getKtpFile() != null && !files.containsKey("KARTU_TANDA_PENDUDUK"))
            files.put("KARTU_TANDA_PENDUDUK", fileStorageService.convertPathToUrl(reenrollment.getKtpFile()));
        if (reenrollment.getSuratBebasNarkobaFile() != null && !files.containsKey("KETERANGAN_BEBAS_NARKOBA"))
            files.put("KETERANGAN_BEBAS_NARKOBA",
                    fileStorageService.convertPathToUrl(reenrollment.getSuratBebasNarkobaFile()));
        if (reenrollment.getSkckFile() != null && !files.containsKey("SKCK"))
            files.put("SKCK", fileStorageService.convertPathToUrl(reenrollment.getSkckFile()));

        Map<String, Object> response = new HashMap<>();
        response.put("exists", true);
        response.put("id", reenrollment.getId());
        response.put("parentName", reenrollment.getParentName());
        response.put("parentPhone", reenrollment.getParentPhone());
        response.put("parentEmail", reenrollment.getParentEmail());
        response.put("parentAddress", reenrollment.getParentAddress());
        response.put("permanentAddress", reenrollment.getPermanentAddress());
        response.put("currentAddress", reenrollment.getCurrentAddress());
        response.put("alumniFamily", reenrollment.getAlumniFamily());
        response.put("alumniName", reenrollment.getAlumniName());
        response.put("alumniRelation", reenrollment.getAlumniRelation());
        response.put("status", reenrollment.getStatus().toString());
        response.put("submittedAt", reenrollment.getSubmittedAt());
        response.put("validatedAt", reenrollment.getValidatedAt());
        response.put("validationNotes", reenrollment.getValidationNotes());
        response.put("documents", files);
        response.put("documentsCount", files.size());
        return response;
    }

    /**
     * Update re-enrollment data with file uploads.
     */
    public Map<String, Object> updateReenrollmentData(String userEmail, Long id,
                                                       HttpServletRequest request) throws IOException {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        ReEnrollment reenrollment = reenrollmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Re-enrollment not found"));

        if (!reenrollment.getStudent().getId().equals(student.getId())) {
            throw new SecurityException("Unauthorized");
        }
        if (reenrollment.getStatus() == ReEnrollment.ReEnrollmentStatus.VALIDATED ||
                reenrollment.getStatus() == ReEnrollment.ReEnrollmentStatus.REJECTED) {
            throw new RuntimeException("Tidak dapat mengedit data yang sudah divalidasi");
        }

        if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipart) {
            String parentName = multipart.getParameter("parentName");
            String parentPhone = multipart.getParameter("parentPhone");
            String parentEmail = multipart.getParameter("parentEmail");
            String parentAddress = multipart.getParameter("parentAddress");
            String permanentAddress = multipart.getParameter("permanentAddress");
            String currentAddress = multipart.getParameter("currentAddress");
            String alumniFamily = multipart.getParameter("alumniFamily");
            String alumniRelation = multipart.getParameter("alumniRelation");
            String alumniName = multipart.getParameter("alumniName");

            if (parentName != null && !parentName.isEmpty()) reenrollment.setParentName(parentName);
            if (parentPhone != null && !parentPhone.isEmpty()) reenrollment.setParentPhone(parentPhone);
            if (parentEmail != null && !parentEmail.isEmpty()) reenrollment.setParentEmail(parentEmail);
            if (parentAddress != null && !parentAddress.isEmpty()) reenrollment.setParentAddress(parentAddress);
            if (permanentAddress != null && !permanentAddress.isEmpty())
                reenrollment.setPermanentAddress(permanentAddress);
            if (currentAddress != null && !currentAddress.isEmpty()) reenrollment.setCurrentAddress(currentAddress);
            if (alumniFamily != null && !alumniFamily.isEmpty())
                reenrollment.setAlumniFamily(Boolean.parseBoolean(alumniFamily));
            if (alumniRelation != null && !alumniRelation.isEmpty()) reenrollment.setAlumniRelation(alumniRelation);
            if (alumniName != null && !alumniName.isEmpty()) reenrollment.setAlumniName(alumniName);

            // Handle file uploads
            String uploadsPath = "uploads/reenrollment/" + student.getId();
            Files.createDirectories(Paths.get(uploadsPath));

            String[] docTypes = {"PAKTA_INTEGRITAS", "IJAZAH", "PASPHOTO", "KARTU_KELUARGA",
                    "KARTU_TANDA_PENDUDUK", "KETERANGAN_BEBAS_NARKOBA", "SKCK"};
            for (String docType : docTypes) {
                MultipartFile file = multipart.getFile("documents[" + docType + "]");
                if (file != null && !file.isEmpty()) {
                    String fileName = docType + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
                    Path filePath = Paths.get(uploadsPath, fileName);
                    Files.write(filePath, file.getBytes());
                    reenrollment.getDocuments().removeIf(d -> d.getDocumentType().toString().equals(docType));
                    ReEnrollmentDocument doc = ReEnrollmentDocument.builder()
                            .reenrollment(reenrollment)
                            .documentType(ReEnrollmentDocument.DocumentType.valueOf(docType))
                            .filePath("uploads/reenrollment/" + student.getId() + "/" + fileName)
                            .originalFilename(file.getOriginalFilename())
                            .fileSize(file.getSize())
                            .fileMimeType(file.getContentType())
                            .uploadStatus(ReEnrollmentDocument.UploadStatus.COMPLETED)
                            .uploadedAt(LocalDateTime.now())
                            .validationStatus(ReEnrollmentDocument.ValidationStatus.PENDING)
                            .build();
                    reenrollment.getDocuments().add(doc);
                    log.info("✅ Document uploaded {} for reenrollment {}: {}", docType, id, fileName);
                }
            }
        }

        reenrollment = reenrollmentRepository.save(reenrollment);
        final ReEnrollment saved = reenrollment;
        return Map.of(
                "success", true,
                "message", "✅ Data daftar ulang berhasil diperbarui dengan dokumen",
                "id", saved.getId(),
                "status", saved.getStatus().toString()
        );
    }

    /**
     * Get document list for a re-enrollment record.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReenrollmentDocuments(String userEmail, Long id) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        ReEnrollment reenrollment = reenrollmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Re-enrollment not found"));

        if (!reenrollment.getStudent().getId().equals(student.getId())) {
            throw new SecurityException("Unauthorized");
        }

        List<Map<String, Object>> docs = new ArrayList<>();
        for (ReEnrollmentDocument doc : reenrollment.getDocuments()) {
            Map<String, Object> docMap = new HashMap<>();
            docMap.put("id", doc.getId());
            docMap.put("documentType", doc.getDocumentType().toString());
            docMap.put("displayName", doc.getDocumentType().getDisplayName());
            docMap.put("fileName", doc.getOriginalFilename());
            docMap.put("uploadedAt", doc.getUploadedAt());
            docMap.put("validationStatus", doc.getValidationStatus().toString());
            docs.add(docMap);
        }
        return docs;
    }
}
