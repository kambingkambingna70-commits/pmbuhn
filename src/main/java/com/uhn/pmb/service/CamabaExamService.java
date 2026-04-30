package com.uhn.pmb.service;

import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for student-facing exam operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CamabaExamService {

    private final ExamRepository examRepository;
    private final ExamResultRepository examResultRepository;
    private final ExamTokenService examTokenService;
    private final ExamTokenRepository tokenRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final FormValidationRepository formValidationRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final RegistrationStatusRepository registrationStatusRepository;

    private String generateExamNumber() {
        String examNumber;
        do {
            long timestamp = System.currentTimeMillis() % 100000;
            long random = (long) (Math.random() * 100000);
            examNumber = String.format("UJI%05d%05d", timestamp, random);
        } while (examRepository.findByExamNumber(examNumber).isPresent());
        return examNumber;
    }

    private Student resolveStudent(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student profile not found"));
    }

    /**
     * Submit basic exam record.
     */
    public Map<String, Object> submitExam(String userEmail) {
        Student student = resolveStudent(userEmail);

        Exam exam = new Exam();
        exam.setStudent(student);
        exam.setStartedAt(LocalDateTime.now());
        exam.setStatus(Exam.ExamStatus.COMPLETED);
        exam.setCreatedAt(LocalDateTime.now());
        examRepository.save(exam);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Ujian berhasil disubmit");
        response.put("examId", exam.getId());
        log.info("Student {} submitted exam", userEmail);
        return response;
    }

    /**
     * Get exam details for student.
     */
    @Transactional(readOnly = true)
    public Optional<Exam> getExamDetails(String userEmail) {
        Student student = resolveStudent(userEmail);
        return examRepository.findByStudent_Id(student.getId());
    }

    /**
     * Get exam validation status.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExamValidationStatus(String userEmail) {
        Student student = resolveStudent(userEmail);
        Optional<Exam> examOpt = examRepository.findByStudent_Id(student.getId());
        if (examOpt.isEmpty()) {
            return Map.of("status", "NOT_STARTED", "message", "Ujian belum dimulai");
        }
        Optional<ExamResult> resultOpt = examResultRepository.findByExam_Id(examOpt.get().getId());
        if (resultOpt.isEmpty()) {
            return Map.of("status", "PENDING", "message", "Ujian belum diselesaikan");
        }
        ExamResult result = resultOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("status", result.getExamValidationStatus().toString());
        response.put("validationStatus", result.getExamValidationStatus().toString()); // alias for frontend compatibility
        response.put("score", result.getGformScore());
        response.put("tokenValidated", result.getTokenValidated());
        response.put("adminNotes", result.getAdminNotes());
        response.put("submittedAt", result.getSubmissionDate());
        response.put("validatedAt", result.getExamValidatedAt());
        return response;
    }

    /**
     * Get active exam token for student.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getExamToken(String userEmail) {
        Student student = resolveStudent(userEmail);
        List<ExamToken> tokens = tokenRepository.findAllByStudentId(student.getId());
        ExamToken activeToken = tokens.stream().filter(ExamToken::isActive).findFirst().orElse(null);

        Map<String, Object> response = new HashMap<>();
        if (activeToken != null) {
            response.put("success", true);
            response.put("tokenValue", activeToken.getTokenValue());
            response.put("expiresAt", activeToken.getExpiresAt());
            response.put("studentId", student.getId());
            response.put("message", "Token berhasil diambil");
            log.info("✅ Exam token retrieved for student: {} (ID: {})", student.getFullName(), student.getId());
        } else if (!tokens.isEmpty()) {
            response.put("success", false);
            response.put("message", "Token sudah expired atau tidak aktif");
        } else {
            response.put("success", false);
            response.put("message", "Token ujian belum tersedia. Tunggu pembayaran Anda diproses.");
        }
        return response;
    }

    /**
     * Manual trigger to generate exam token after verifying payment.
     */
    public Map<String, Object> triggerTokenGeneration(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        List<FormValidation> validations = formValidationRepository.findAll().stream()
                .filter(fv -> fv.getStudent().getId().equals(student.getId()))
                .toList();

        if (validations.isEmpty()) {
            throw new RuntimeException("Tidak ada formulir pendaftaran");
        }

        FormValidation validation = validations.get(0);
        AdmissionForm form = validation.getAdmissionForm();

        // Sync payment status from RegistrationStatus if needed
        Optional<RegistrationStatus> paymentStatusOpt = registrationStatusRepository
                .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PAYMENT_BRIVA);
        if (paymentStatusOpt.isPresent() &&
                paymentStatusOpt.get().getStatus() == RegistrationStatus.RegistrationStatus_Enum.SELESAI) {
            validation.setPaymentStatus(FormValidation.PaymentStatus.PAID);
            validation.setPaymentDate(LocalDateTime.now());
            validation.setUpdatedAt(LocalDateTime.now());
            formValidationRepository.save(validation);
            log.info("✅ [TRIGGER] FormValidation synced to PAID");
        } else if (!FormValidation.PaymentStatus.PAID.equals(validation.getPaymentStatus())) {
            throw new RuntimeException("Pembayaran belum selesai. Status: " + validation.getPaymentStatus());
        }

        List<ExamToken> existingTokens = tokenRepository.findAllByStudentId(student.getId());
        ExamToken activeToken = existingTokens.stream().filter(ExamToken::isActive).findFirst().orElse(null);
        if (activeToken != null) {
            return Map.of("success", true, "message", "Token sudah tersedia",
                    "tokenValue", activeToken.getTokenValue(), "expiresAt", activeToken.getExpiresAt());
        }

        ExamToken newToken = examTokenService.generateToken(student.getId(), form.getId(), 120);
        if (newToken != null) {
            validation.setExamToken(newToken.getTokenValue());
            formValidationRepository.save(validation);
            return Map.of("success", true, "message", "Token berhasil di-generate",
                    "tokenValue", newToken.getTokenValue(), "expiresAt", newToken.getExpiresAt());
        }
        throw new RuntimeException("Gagal generate token");
    }

    /**
     * Mark PSYCHO_EXAM stage as started (MENUNGGU_VERIFIKASI).
     */
    public Map<String, Object> markExamAsStarted(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<RegistrationStatus> statusOpt = registrationStatusRepository
                .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PSYCHO_EXAM);

        RegistrationStatus psychoStatus;
        if (statusOpt.isEmpty()) {
            psychoStatus = RegistrationStatus.builder()
                    .user(user)
                    .stage(RegistrationStatus.RegistrationStage.PSYCHO_EXAM)
                    .status(RegistrationStatus.RegistrationStatus_Enum.MENUNGGU_VERIFIKASI)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            psychoStatus = registrationStatusRepository.save(psychoStatus);
        } else {
            psychoStatus = statusOpt.get();
            if (!psychoStatus.getStatus().equals(RegistrationStatus.RegistrationStatus_Enum.SELESAI)) {
                psychoStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.MENUNGGU_VERIFIKASI);
                psychoStatus.setUpdatedAt(LocalDateTime.now());
                psychoStatus = registrationStatusRepository.save(psychoStatus);
            }
        }

        final String statusText = psychoStatus.getStatus().toString();
        return Map.of("success", true, "message", "Exam marked as started",
                "stage", "PSYCHO_EXAM", "status", statusText);
    }

    /**
     * Submit exam results after completing the exam form.
     */
    public Map<String, Object> submitExamResults(String userEmail, String examToken,
                                                  Double gformScore, MultipartFile proofPhoto) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (gformScore < 0 || gformScore > 100) {
            throw new RuntimeException("Nilai harus antara 0-100");
        }

        FormValidation validation = formValidationRepository.findByStudentId(student.getId())
                .orElseThrow(() -> new RuntimeException("Data formulir tidak ditemukan"));

        String generatedToken = validation.getExamToken();
        boolean tokenValid = (generatedToken != null && generatedToken.equalsIgnoreCase(examToken.trim()));

        log.info("🔐 [EXAM-SUBMISSION] Student {} submitted token. Generated: {}, Submitted: {}, Valid: {}",
                student.getId(), generatedToken, examToken, tokenValid);

        // Upload proof photo if provided
        String proofPhotoPath = null;
        if (proofPhoto != null && !proofPhoto.isEmpty()) {
            try {
                String uploadsDir = "uploads/exam-proofs/";
                new File(uploadsDir).mkdirs();
                String filename = "exam-proof-" + student.getId() + "-" + System.currentTimeMillis()
                        + "-" + UUID.randomUUID() + ".jpg";
                Path path = Paths.get(uploadsDir, filename);
                Files.write(path, proofPhoto.getBytes());
                proofPhotoPath = "/" + uploadsDir + filename;
                log.info("✅ Proof photo uploaded: {}", proofPhotoPath);
            } catch (Exception photoError) {
                log.warn("⚠️ Failed to upload proof photo: {}", photoError.getMessage());
            }
        }

        // Get or create Exam record
        Optional<Exam> examOpt = examRepository.findByStudent_Id(student.getId());
        Exam exam;
        if (examOpt.isPresent()) {
            exam = examOpt.get();
        } else {
            List<AdmissionForm> admissionForms = admissionFormRepository.findByStudent_Id(student.getId());
            if (admissionForms.isEmpty()) {
                throw new RuntimeException("Data pendaftaran siswa tidak ditemukan");
            }
            AdmissionForm admissionForm = admissionForms.get(0);
            RegistrationPeriod period = admissionForm.getPeriod();
            if (period == null) {
                throw new RuntimeException("Periode ujian tidak ditemukan");
            }
            exam = Exam.builder()
                    .examNumber(generateExamNumber())
                    .student(student)
                    .period(period)
                    .status(Exam.ExamStatus.PENDING)
                    .startedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            exam = examRepository.save(exam);
        }

        // Get or create ExamResult
        final Exam finalExam = exam;
        Optional<ExamResult> resultOpt = examResultRepository.findByExam_Id(exam.getId());
        ExamResult result = resultOpt.orElseGet(() -> ExamResult.builder()
                .exam(finalExam)
                .student(student)
                .status(ExamResult.ResultStatus.PENDING)
                .examValidationStatus(ExamResult.ExamValidationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        result.setStudentInputToken(examToken.trim().toUpperCase());
        result.setGeneratedToken(generatedToken);
        result.setGformScore(gformScore);
        result.setProofPhotoPath(proofPhotoPath);
        result.setTokenValidated(tokenValid);
        result.setSubmissionDate(LocalDateTime.now());
        result.setScore(gformScore);
        result.setStatus(gformScore >= 70 ? ExamResult.ResultStatus.PASSED : ExamResult.ResultStatus.FAILED);

        // Selalu PENDING dulu — admin validasi yang berhak approve/reject
        result.setExamValidationStatus(ExamResult.ExamValidationStatus.PENDING);
        if (!tokenValid) {
            // Token tidak cocok: tandai di notes agar admin bisa verifikasi manual, tapi JANGAN auto-reject
            result.setAdminNotes("⚠️ Token tidak cocok — mohon verifikasi manual");
            log.warn("⚠️ Token mismatch for student {}: input={} vs generated={}", student.getId(), examToken, generatedToken);
        }
        result.setUpdatedAt(LocalDateTime.now());
        result = examResultRepository.save(result);

        exam.setStatus(Exam.ExamStatus.COMPLETED);
        exam.setCompletedAt(LocalDateTime.now());
        exam.setUpdatedAt(LocalDateTime.now());
        examRepository.save(exam);

        // Update PSYCHO_EXAM stage status
        try {
            Optional<RegistrationStatus> regStatusOpt = registrationStatusRepository
                    .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PSYCHO_EXAM);
            if (regStatusOpt.isPresent()) {
                RegistrationStatus psychoStatus = regStatusOpt.get();
                psychoStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
                psychoStatus.setSubmissionDate(LocalDateTime.now());
                psychoStatus.setUpdatedAt(LocalDateTime.now());
                registrationStatusRepository.save(psychoStatus);
            }
        } catch (Exception regStatusError) {
            log.warn("⚠️ Failed to update RegistrationStatus: {}", regStatusError.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", tokenValid
                ? "Hasil ujian berhasil disubmit dan menunggu validasi admin"
                : "⚠️ Hasil ujian disubmit tapi token tidak cocok - akan ditinjau admin");
        response.put("examResultId", result.getId());
        response.put("tokenValidated", tokenValid);
        response.put("score", gformScore);
        response.put("validationStatus", result.getExamValidationStatus().toString());
        response.put("submittedAt", LocalDateTime.now());
        log.info("📤 Exam results submitted by student {}: score={}, tokenValid={}",
                student.getId(), gformScore, tokenValid);
        return response;
    }
}
