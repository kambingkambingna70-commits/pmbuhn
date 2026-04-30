package com.uhn.pmb.service;

import com.uhn.pmb.dto.CicilanRequestDTO;
import com.uhn.pmb.request.CicilanRequestSubmitRequest;
import com.uhn.pmb.entity.AdmissionForm;
import com.uhn.pmb.entity.CicilanRequest;
import com.uhn.pmb.entity.ProgramStudi;
import com.uhn.pmb.entity.RegistrationStatus;
import com.uhn.pmb.entity.Student;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.repository.AdmissionFormRepository;
import com.uhn.pmb.repository.CicilanRequestRepository;
import com.uhn.pmb.repository.ProgramStudiRepository;
import com.uhn.pmb.repository.RegistrationStatusRepository;
import com.uhn.pmb.repository.StudentRepository;
import com.uhn.pmb.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service untuk menangani business logic cicilan request
 * Semua repository access dan kompleks logic ada di sini
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CicilanService {
    
    private final CicilanRequestRepository cicilanRequestRepository;
    private final StudentRepository studentRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final ProgramStudiRepository programStudiRepository;
    private final UserRepository userRepository;
    private final RegistrationStatusRepository registrationStatusRepository;

    /**
     * Get cicilan by admission form ID
     */
    public Optional<CicilanRequestDTO> getCicilanByAdmissionFormId(Long admissionFormId) {
        return cicilanRequestRepository.findAll().stream()
                .filter(c -> c.getAdmissionForm() != null && c.getAdmissionForm().getId().equals(admissionFormId))
                .findFirst()
                .map(this::convertToDTO);
    }

    /**
     * Get cicilan by user email
     * Finds user → student → most recent cicilan
     */
    public Optional<CicilanRequestDTO> getMyCicilan(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("User not found by email: {}", email);
            return Optional.empty();
        }

        Student student = studentRepository.findByUser_Id(user.getId()).orElse(null);
        if (student == null) {
            log.warn("Student not found for user ID: {}", user.getId());
            return Optional.empty();
        }

        List<CicilanRequest> cicilans = cicilanRequestRepository.findByStudentId(student.getId());
        if (cicilans.isEmpty()) {
            log.warn("No cicilan found for student ID: {}", student.getId());
            return Optional.empty();
        }

        // Return most recent (sorted DESC by createdAt in repository)
        return Optional.of(convertToDTO(cicilans.get(0)));
    }

    /**
     * Submit cicilan request (Student)
     * Complex business logic: validates, creates entity, saves
     */
    public CicilanRequestDTO submitCicilanRequest(String email, CicilanRequestSubmitRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan: " + email));

        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Validate and process cicilan selection
        List<Integer> selectedCicilans = request.getSelectedCicilans();
        if (selectedCicilans == null || selectedCicilans.isEmpty()) {
            // Legacy fallback: treat jumlahCicilan as "first N cicilans"
            int jml = request.getJumlahCicilan() != null ? request.getJumlahCicilan() : 1;
            if (jml < 1 || jml > 6) {
                throw new RuntimeException("Jumlah cicilan harus 1-6");
            }
            selectedCicilans = new java.util.ArrayList<>();
            for (int i = 1; i <= jml; i++) {
                selectedCicilans.add(i);
            }
        } else {
            // Validate selectedCicilans values
            for (Integer c : selectedCicilans) {
                if (c == null || c < 1 || c > 6) {
                    throw new RuntimeException("Cicilan harus bernilai 1-6");
                }
            }
        }
        int jumlahCicilan = selectedCicilans.size();

        // Validate programStudiId
        if (request.getProgramStudiId() == null || request.getProgramStudiId() <= 0) {
            throw new RuntimeException("Program studi ID tidak valid");
        }

        ProgramStudi programStudi = programStudiRepository.findById(request.getProgramStudiId())
                .orElseThrow(() -> new RuntimeException("Program studi tidak ditemukan"));

        // Check if cicilan request already exists for this student
        Optional<CicilanRequest> existingCicilan = cicilanRequestRepository.findAll().stream()
                .filter(cr -> cr.getStudent().getId().equals(student.getId()))
                .filter(cr -> !cr.getStatus().equals(CicilanRequest.CicilanRequestStatus.REJECTED))
                .findFirst();
        
        if (existingCicilan.isPresent()) {
            throw new RuntimeException("Request cicilan sudah pernah dibuat");
        }

        // Get admission form if available
        AdmissionForm admissionForm = admissionFormRepository.findByStudent_Id(student.getId())
                .stream()
                .findFirst()
                .orElse(null);

        // Parse payment method
        String paymentMethodStr = request.getPaymentMethod() != null ? request.getPaymentMethod() : "SIMULATION";
        CicilanRequest.PaymentMethod paymentMethod;
        try {
            paymentMethod = CicilanRequest.PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            paymentMethod = CicilanRequest.PaymentMethod.SIMULATION;
        }

        // Create cicilan request
        CicilanRequest cicilan = CicilanRequest.builder()
                .student(student)
                .programStudi(programStudi)
                .admissionForm(admissionForm)
                .jumlahCicilan(jumlahCicilan)
                .hargaCicilan1(selectedCicilans.contains(1) && programStudi.getCicilan1() != null ? programStudi.getCicilan1() : 0L)
                .hargaCicilan2(selectedCicilans.contains(2) && programStudi.getCicilan2() != null ? programStudi.getCicilan2() : 0L)
                .hargaCicilan3(selectedCicilans.contains(3) && programStudi.getCicilan3() != null ? programStudi.getCicilan3() : 0L)
                .hargaCicilan4(selectedCicilans.contains(4) && programStudi.getCicilan4() != null ? programStudi.getCicilan4() : 0L)
                .hargaCicilan5(selectedCicilans.contains(5) && programStudi.getCicilan5() != null ? programStudi.getCicilan5() : 0L)
                .hargaCicilan6(selectedCicilans.contains(6) && programStudi.getCicilan6() != null ? programStudi.getCicilan6() : 0L)
                .hargaTotal(programStudi.getHargaTotalPerTahun() != null ? programStudi.getHargaTotalPerTahun() : 0L)
                .status(CicilanRequest.CicilanRequestStatus.PENDING)
                .paymentMethod(paymentMethod)
                .build();

        // Calculate hargaPerCicilan as total of selected cicilans
        long selectedTotal = cicilan.getHargaCicilan1() + cicilan.getHargaCicilan2() + cicilan.getHargaCicilan3()
                + cicilan.getHargaCicilan4() + cicilan.getHargaCicilan5() + cicilan.getHargaCicilan6();
        cicilan.setHargaPerCicilan(selectedTotal);

        CicilanRequest saved = cicilanRequestRepository.save(cicilan);
        return convertToDTO(saved);
    }

    /**
     * Mark cicilan payment as submitted (after manual payment proof upload)
     * Updates REGISTRATION_STAGES so dashboard detects change
     */
    @Transactional
    public CicilanRequestDTO markPaymentSubmitted(Long id, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan: " + email));

        CicilanRequest cicilan = cicilanRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cicilan request tidak ditemukan"));

        // Verify ownership
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student tidak ditemukan"));
        
        if (!cicilan.getStudent().getId().equals(student.getId())) {
            throw new RuntimeException("Cicilan bukan milik Anda");
        }

        // Update REGISTRATION_STAGES
        log.info("📝 [CICILAN-PAYMENT] Updating REGISTRATION_STAGES for student {}", student.getId());
        
        RegistrationStatus cicilanPaymentStatus = registrationStatusRepository
                .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PAYMENT_CICILAN_1)
                .orElse(null);
        
        if (cicilanPaymentStatus != null) {
            cicilanPaymentStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
            cicilanPaymentStatus.setUpdatedAt(LocalDateTime.now());
            registrationStatusRepository.save(cicilanPaymentStatus);
            log.info("✅ Registration status PAYMENT_CICILAN_1 updated to SELESAI");
        } else {
            // Create new PAYMENT_CICILAN_1 status if doesn't exist
            cicilanPaymentStatus = new RegistrationStatus();
            cicilanPaymentStatus.setUser(user);
            cicilanPaymentStatus.setStage(RegistrationStatus.RegistrationStage.PAYMENT_CICILAN_1);
            cicilanPaymentStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.SELESAI);
            cicilanPaymentStatus.setCreatedAt(LocalDateTime.now());
            cicilanPaymentStatus.setUpdatedAt(LocalDateTime.now());
            registrationStatusRepository.save(cicilanPaymentStatus);
            log.info("✅ NEW: Created PAYMENT_CICILAN_1 registration status with SELESAI");
        }

        log.info("✅ Cicilan {} marked as payment-submitted - Dashboard will detect on next polling", id);
        return convertToDTO(cicilan);
    }

    /**
     * Utility: Convert entity to DTO
     */
    private CicilanRequestDTO convertToDTO(CicilanRequest cr) {
        return CicilanRequestDTO.builder()
                .id(cr.getId())
                .studentId(cr.getStudent().getId())
                .studentName(cr.getStudent().getFullName())
                .studentEmail(cr.getStudent().getUser().getEmail())
                .programStudiId(cr.getProgramStudi().getId())
                .programStudiName(cr.getProgramStudi().getNama())
                .admissionFormId(cr.getAdmissionForm() != null ? cr.getAdmissionForm().getId() : null)
                .jumlahCicilan(cr.getJumlahCicilan())
                .hargaCicilan1(cr.getHargaCicilan1())
                .hargaCicilan2(cr.getHargaCicilan2())
                .hargaCicilan3(cr.getHargaCicilan3())
                .hargaCicilan4(cr.getHargaCicilan4())
                .hargaCicilan5(cr.getHargaCicilan5())
                .hargaCicilan6(cr.getHargaCicilan6())
                .hargaTotal(cr.getHargaTotal())
                .hargaPerCicilan(cr.getHargaPerCicilan())
                .status(cr.getStatus().name())
                .statusLabel(cr.getStatus().getLabel())
                .catatan(cr.getCatatan())
                .briva(cr.getBriva())
                .paymentMethod(cr.getPaymentMethod().name())
                .paymentMethodLabel(cr.getPaymentMethod().getLabel())
                .approvedBy(cr.getApprovedBy())
                .approvedAt(cr.getApprovedAt())
                .createdAt(cr.getCreatedAt())
                .updatedAt(cr.getUpdatedAt())
                .build();
    }
}
