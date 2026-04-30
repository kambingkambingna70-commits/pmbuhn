package com.uhn.pmb.service;

import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for student-facing payment operations (virtual account, simulate, cicilan).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CamabaPaymentService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final BrivaService brivaService;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final RegistrationStatusRepository registrationStatusRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final FormValidationRepository formValidationRepository;
    private final ExamTokenService examTokenService;
    private final ExamTokenRepository tokenRepository;
    private final EmailService emailService;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final StudentRegistrationService registrationService;

    /**
     * After successful payment, update FormValidation to PAID and auto-generate exam token.
     */
    private void updateFormValidationAfterPayment(VirtualAccount va) {
        try {
            if (va.getAdmissionForm() == null) {
                log.warn("⚠️ [PAYMENT] Cannot update FormValidation: AdmissionForm is null");
                return;
            }
            if (va.getStudent() == null) {
                log.warn("⚠️ [PAYMENT] Cannot update FormValidation: Student is null");
                return;
            }

            Optional<FormValidation> validationOpt = formValidationRepository
                    .findByAdmissionFormId(va.getAdmissionForm().getId());

            if (validationOpt.isPresent()) {
                FormValidation validation = validationOpt.get();
                validation.setPaymentStatus(FormValidation.PaymentStatus.PAID);
                validation.setPaymentDate(LocalDateTime.now());
                validation.setUpdatedAt(LocalDateTime.now());
                formValidationRepository.save(validation);
                log.info("✅ [PAYMENT] FormValidation #{} updated to PAID for VA: {}",
                        validation.getId(), va.getVaNumber());

                // Auto-generate exam token
                try {
                    List<ExamToken> existingTokens = tokenRepository.findAllByStudentId(va.getStudent().getId());
                    ExamToken activeToken = existingTokens.stream()
                            .filter(ExamToken::isActive).findFirst().orElse(null);

                    ExamToken tokenToUse;
                    if (activeToken != null) {
                        log.info("✅ [TOKEN] Active token already exists: {}", activeToken.getTokenValue());
                        tokenToUse = activeToken;
                    } else {
                        tokenToUse = examTokenService.generateToken(
                                va.getStudent().getId(), va.getAdmissionForm().getId(), 120);
                    }

                    if (tokenToUse != null) {
                        validation.setExamToken(tokenToUse.getTokenValue());
                        formValidationRepository.save(validation);
                        log.info("✅ [SYNC] Exam token synced to FormValidation record");
                    }
                } catch (Exception tokenGenError) {
                    log.error("❌ [TOKEN-GEN] Error generating token: {}", tokenGenError.getMessage(), tokenGenError);
                }
            } else {
                log.warn("⚠️ [PAYMENT] FormValidation not found for AdmissionForm ID: {}",
                        va.getAdmissionForm().getId());
            }
        } catch (Exception e) {
            log.error("❌ [PAYMENT] Error updating FormValidation: {}", e.getMessage(), e);
        }
    }

    /**
     * Verify payment - mark VA as PAID and update RegistrationStatus.
     */
    public Map<String, Object> verifyPayment(String userEmail, String vaNumber) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VirtualAccount va = virtualAccountRepository.findByVaNumber(vaNumber)
                .orElseThrow(() -> new RuntimeException("Virtual Account not found"));

        va.setPaidAt(LocalDateTime.now());
        va.setStatus(VirtualAccount.VAStatus.PAID);
        va.setUpdatedAt(LocalDateTime.now());
        virtualAccountRepository.save(va);
        log.info("✅ Virtual Account {} marked as PAID", vaNumber);

        Optional<RegistrationStatus> existingStatus = registrationStatusRepository
                .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PAYMENT_BRIVA);

        RegistrationStatus paymentStatus;
        if (existingStatus.isPresent()) {
            paymentStatus = existingStatus.get();
            paymentStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
            paymentStatus.setUpdatedAt(LocalDateTime.now());
        } else {
            paymentStatus = new RegistrationStatus();
            paymentStatus.setUser(user);
            paymentStatus.setStage(RegistrationStatus.RegistrationStage.PAYMENT_BRIVA);
            paymentStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
            paymentStatus.setCreatedAt(LocalDateTime.now());
            paymentStatus.setUpdatedAt(LocalDateTime.now());
        }
        registrationStatusRepository.save(paymentStatus);
        log.info("✅ Payment status updated to SELESAI for user: {}", userEmail);

        updateFormValidationAfterPayment(va);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Pembayaran berhasil diverifikasi dan disimpan di database");
        response.put("vaNumber", vaNumber);
        response.put("status", "SELESAI");
        response.put("paidAt", LocalDateTime.now());
        return response;
    }

    /**
     * Buy form and create virtual account (for VERIFIED forms).
     */
    public Map<String, Object> buyForm(Long formId) {
        AdmissionForm form = admissionFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Form not found"));

        if (form.getStatus() == AdmissionForm.FormStatus.WAITING_PAYMENT) {
            throw new RuntimeException("Form ini sudah dalam tahap pembayaran");
        }
        if (form.getStatus() != AdmissionForm.FormStatus.VERIFIED) {
            throw new RuntimeException("Formulir belum diverifikasi. Status saat ini: " + form.getStatus() +
                    ". Silahkan menunggu hingga data Anda diverifikasi (3-5 hari kerja).");
        }

        form.setStatus(AdmissionForm.FormStatus.WAITING_PAYMENT);
        admissionFormRepository.save(form);

        VirtualAccount va;
        try {
            va = registrationService.buyFormAndCreateVA(form);
        } catch (Exception e) {
            throw new RuntimeException("Gagal membuat virtual account: " + e.getMessage(), e);
        }

        emailService.sendVirtualAccountInfo(
                form.getStudent().getUser().getEmail(),
                va.getVaNumber(),
                va.getAmount().toString(),
                va.getExpiredAt().toString()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Form siap untuk pembayaran");
        response.put("vaNumber", va.getVaNumber());
        response.put("amount", va.getAmount());
        response.put("expiredAt", va.getExpiredAt());
        return response;
    }

    /**
     * Create a new virtual account for payment.
     */
    public Map<String, Object> createVirtualAccount(String userEmail, Long selectionTypeId,
                                                      Long periodId, BigDecimal amount) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student profile not found"));

        jenisSeleksiRepository.findById(selectionTypeId)
                .orElseThrow(() -> new RuntimeException("Jenis seleksi not found with ID: " + selectionTypeId));
        registrationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Registration period not found"));

        VirtualAccount va = VirtualAccount.builder()
                .student(student)
                .amount(amount)
                .paymentType(VirtualAccount.PaymentType.REGISTRATION_FORM)
                .status(VirtualAccount.VAStatus.ACTIVE)
                .build();

        String vaNumber;
        try {
            vaNumber = brivaService.generateVirtualAccount(va);
        } catch (IOException e) {
            throw new RuntimeException("Gagal generate virtual account: " + e.getMessage(), e);
        }
        va.setVaNumber(vaNumber);
        VirtualAccount savedVA = virtualAccountRepository.save(va);

        log.info("✅ Virtual Account created for student {}: {} (Rp {})", student.getId(), vaNumber, amount);

        // Update FormValidation with VA info
        try {
            List<FormValidation> validations = formValidationRepository.findAll().stream()
                    .filter(fv -> fv.getStudent().getId().equals(student.getId())
                            && fv.getValidationStatus() == FormValidation.ValidationStatus.PENDING)
                    .limit(1).toList();
            if (!validations.isEmpty()) {
                FormValidation fv = validations.get(0);
                fv.setVirtualAccountNumber(savedVA.getVaNumber());
                fv.setPaymentAmount(amount.longValue());
                fv.setUpdatedAt(LocalDateTime.now());
                formValidationRepository.save(fv);
            }
        } catch (Exception e) {
            log.warn("⚠️ Warning updating FormValidation with VA info: {}", e.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", savedVA.getId());
        data.put("vaNumber", savedVA.getVaNumber());
        data.put("amount", savedVA.getAmount());
        data.put("status", savedVA.getStatus());
        data.put("createdAt", savedVA.getCreatedAt());
        data.put("expiredAt", savedVA.getExpiredAt());
        data.put("paymentType", savedVA.getPaymentType());
        return Map.of("success", true, "message", "Virtual Account berhasil dibuat", "data", data);
    }

    /**
     * Check payment status realtime.
     */
    public Map<String, Object> checkPaymentStatus(String userEmail, String vaNumber) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student profile not found"));

        VirtualAccount va = virtualAccountRepository.findByVaNumber(vaNumber)
                .orElseThrow(() -> new RuntimeException("Virtual Account not found"));

        if (!va.getStudent().getId().equals(student.getId())) {
            throw new SecurityException("Access denied");
        }

        if (LocalDateTime.now().isAfter(va.getExpiredAt()) && va.getStatus() != VirtualAccount.VAStatus.PAID) {
            va.setStatus(VirtualAccount.VAStatus.EXPIRED);
            virtualAccountRepository.save(va);
        }

        if (va.getStatus() == VirtualAccount.VAStatus.PAID && va.getAdmissionForm() != null) {
            AdmissionForm form = va.getAdmissionForm();
            form.setStatus(AdmissionForm.FormStatus.VERIFIED);
            admissionFormRepository.save(form);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", va.getStatus().toString());
        response.put("vaNumber", vaNumber);
        response.put("amount", va.getAmount());
        response.put("paidAt", va.getPaidAt());
        response.put("expiredAt", va.getExpiredAt());
        response.put("createdAt", va.getCreatedAt());
        return response;
    }

    /**
     * Simulate payment (for testing).
     */
    public Map<String, Object> simulatePayment(String vaNumber) {
        VirtualAccount va = virtualAccountRepository.findByVaNumber(vaNumber)
                .orElseThrow(() -> new RuntimeException("Virtual Account not found"));

        brivaService.updatePaymentStatus(vaNumber, UUID.randomUUID().toString(), va.getAmount());

        VirtualAccount updated = virtualAccountRepository.findByVaNumber(vaNumber)
                .orElseThrow(() -> new RuntimeException("VA not found after update"));

        log.info("✅ Payment simulated for VA: {} - Status: {}", vaNumber, updated.getStatus());
        updateFormValidationAfterPayment(updated);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Payment simulated successfully");
        response.put("status", updated.getStatus());
        response.put("paidAt", updated.getPaidAt());
        return response;
    }

    /**
     * Confirm cicilan (installment) payment and unlock DAFTAR_ULANG stage.
     */
    public Map<String, Object> confirmCicilanPayment(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));
        Long userId = user.getId();

        log.info("🔄 [CICILAN-CONFIRM] Processing cicilan payment for user: {}", userEmail);

        // Update PAYMENT_CICILAN_1 stage
        Optional<RegistrationStatus> existingCicilanStatus = registrationStatusRepository
                .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PAYMENT_CICILAN_1);

        RegistrationStatus cicilanStatus;
        if (existingCicilanStatus.isPresent()) {
            cicilanStatus = existingCicilanStatus.get();
            cicilanStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
            cicilanStatus.setUpdatedAt(LocalDateTime.now());
        } else {
            cicilanStatus = new RegistrationStatus();
            cicilanStatus.setUser(user);
            cicilanStatus.setStage(RegistrationStatus.RegistrationStage.PAYMENT_CICILAN_1);
            cicilanStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
            cicilanStatus.setCreatedAt(LocalDateTime.now());
            cicilanStatus.setUpdatedAt(LocalDateTime.now());
        }
        registrationStatusRepository.save(cicilanStatus);

        // Unlock DAFTAR_ULANG stage
        Optional<RegistrationStatus> existingDaftarUlangStatus = registrationStatusRepository
                .findByUserAndStage(user, RegistrationStatus.RegistrationStage.DAFTAR_ULANG);

        RegistrationStatus daftarUlangStatus;
        if (existingDaftarUlangStatus.isPresent()) {
            daftarUlangStatus = existingDaftarUlangStatus.get();
            if (daftarUlangStatus.getStatus() != RegistrationStatus.RegistrationStatus_Enum.SELESAI) {
                daftarUlangStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.MENUNGGU_VERIFIKASI);
            }
            daftarUlangStatus.setUpdatedAt(LocalDateTime.now());
        } else {
            daftarUlangStatus = new RegistrationStatus();
            daftarUlangStatus.setUser(user);
            daftarUlangStatus.setStage(RegistrationStatus.RegistrationStage.DAFTAR_ULANG);
            daftarUlangStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.MENUNGGU_VERIFIKASI);
            daftarUlangStatus.setCreatedAt(LocalDateTime.now());
            daftarUlangStatus.setUpdatedAt(LocalDateTime.now());
        }
        registrationStatusRepository.save(daftarUlangStatus);

        log.info("✅ [CICILAN-CONFIRM] Daftar Ulang unlocked for user: {}", userEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Pembayaran cicilan berhasil diproses");
        response.put("cicilanStatus", "SELESAI");
        response.put("daftarUlangUnlocked", true);
        response.put("timestamp", LocalDateTime.now());
        response.put("userId", userId);
        return response;
    }
}
