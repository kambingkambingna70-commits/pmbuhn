package com.uhn.pmb.service;

import com.uhn.pmb.dto.HasilAkhirRegistrationRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class HasilAkhirService {

    private final HasilAkhirRepository hasilAkhirRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ReEnrollmentRepository reenrollmentRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final FormValidationRepository formValidationRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;

    /**
     * Create or update hasil akhir when student completes all steps
     * Called when validation is approved (DIVALIDASI)
     */
    public HasilAkhir createHasilAkhir(Long studentId, String brivaNumber, String nomorRegistrasi, BigDecimal brivaAmount,
                                       RegistrationPeriod.WaveType waveType, String selectionType, 
                                       String programStudiName, RegistrationPeriod selectionPeriod, Integer jumlahCicilan) {
        log.info("📋 [HASIL-AKHIR] Creating hasil akhir for student ID: {}", studentId);

        try {
            // Get student and user
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            User user = student.getUser();

            if (user == null) {
                throw new RuntimeException("User not found for student");
            }

            // Check if already exists
            Optional<HasilAkhir> existing = hasilAkhirRepository.findByStudent(student);
            if (existing.isPresent()) {
                log.warn("⚠️ [HASIL-AKHIR] Hasil akhir already exists for student {}, updating...", studentId);
                HasilAkhir hasilAkhir = existing.get();
                
                // CRITICAL FIX: Don't overwrite BRIVA if it already has a valid value
                if (brivaNumber != null && !brivaNumber.equals("N/A") && !brivaNumber.startsWith("PENDING_")) {
                    hasilAkhir.setBrivaNumber(brivaNumber);
                    log.info("✅ [HASIL-AKHIR] Updating BRIVA to: {}", brivaNumber);
                } else if (hasilAkhir.getBrivaNumber() == null || hasilAkhir.getBrivaNumber().equals("N/A")) {
                    // Only set to new value if current is null or N/A
                    hasilAkhir.setBrivaNumber(brivaNumber);
                    log.info("✅ [HASIL-AKHIR] Setting BRIVA to: {}", brivaNumber);
                } else {
                    // Existing BRIVA is valid, preserve it
                    log.info("🔒 [HASIL-AKHIR] Preserving existing BRIVA: {} (not overwriting with {})", 
                            hasilAkhir.getBrivaNumber(), brivaNumber);
                }
                
                hasilAkhir.setNomorRegistrasi(nomorRegistrasi);
                
                // Only update brivaAmount if new amount is provided and greater than zero
                if (brivaAmount != null && brivaAmount.compareTo(BigDecimal.ZERO) > 0) {
                    hasilAkhir.setBrivaAmount(brivaAmount);
                }
                
                // Handle jumlahCicilan - update if new value provided, otherwise preserve existing
                if (jumlahCicilan != null && jumlahCicilan > 0) {
                    hasilAkhir.setJumlahCicilan(jumlahCicilan);
                    log.info("✅ [HASIL-AKHIR] Updating jumlah cicilan to: {}", jumlahCicilan);
                } else if (hasilAkhir.getJumlahCicilan() == null) {
                    hasilAkhir.setJumlahCicilan(1); // Default to 1 if not set
                }
                
                hasilAkhir.setWaveType(waveType);
                hasilAkhir.setSelectionType(selectionType);
                hasilAkhir.setProgramStudiName(programStudiName);
                hasilAkhir.setSelectionPeriod(selectionPeriod);
                hasilAkhir.setStatus(HasilAkhir.HasilAkhirStatus.ACTIVE);
                return hasilAkhirRepository.save(hasilAkhir);
            }

            // Create new hasil akhir
            HasilAkhir hasilAkhir = HasilAkhir.builder()
                    .student(student)
                    .user(user)
                    .brivaNumber(brivaNumber)
                    .brivaAmount(brivaAmount)
                    .jumlahCicilan(jumlahCicilan != null && jumlahCicilan > 0 ? jumlahCicilan : 1)
                    .nomorRegistrasi(nomorRegistrasi)
                    .waveType(waveType)
                    .selectionType(selectionType)
                    .programStudiName(programStudiName)
                    .selectionPeriod(selectionPeriod)
                    .status(HasilAkhir.HasilAkhirStatus.ACTIVE)
                    .build();

            hasilAkhir = hasilAkhirRepository.save(hasilAkhir);
            log.info("✅ [HASIL-AKHIR] Hasil akhir created successfully for student {}, ID: {}", studentId, hasilAkhir.getId());

            return hasilAkhir;

        } catch (Exception e) {
            log.error("❌ [HASIL-AKHIR] Error creating hasil akhir for student {}: {}", studentId, e.getMessage());
            throw new RuntimeException("Gagal membuat hasil akhir: " + e.getMessage());
        }
    }

    /**
     * Auto-populate hasil akhir from existing cicilan and reenrollment data
     * Called when validation is approved
     */
    public HasilAkhir autoPopulateHasilAkhir(Long studentId) {
        log.info("📋 [HASIL-AKHIR-AUTO] Auto-populating hasil akhir for student ID: {}", studentId);

        try {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            // CRITICAL FIX: Check if HASIL_AKHIR already exists and has a valid BRIVA
            // If so, preserve it to avoid overwriting manually-entered BRIVAs during approval
            Optional<HasilAkhir> existingHasilAkhir = hasilAkhirRepository.findByStudent(student);
            if (existingHasilAkhir.isPresent() && existingHasilAkhir.get().getBrivaNumber() != null 
                    && !existingHasilAkhir.get().getBrivaNumber().equals("N/A") 
                    && !existingHasilAkhir.get().getBrivaNumber().startsWith("PENDING_")) {
                log.info("🔒 [HASIL-AKHIR-AUTO] BRIVA already exists and is valid: {}, skipping auto-population of BRIVA",
                        existingHasilAkhir.get().getBrivaNumber());
                // Don't overwrite existing valid BRIVA
            }

            String brivaNumber = "N/A";
            BigDecimal brivaAmount = BigDecimal.ZERO;
            Integer jumlahCicilan = 1; // Default to 1 installment

            // CRITICAL FIX: If HASIL_AKHIR already has valid BRIVA, preserve it
            if (brivaNumber.equals("N/A") && existingHasilAkhir.isPresent() 
                    && existingHasilAkhir.get().getBrivaNumber() != null
                    && !existingHasilAkhir.get().getBrivaNumber().equals("N/A")) {
                log.info("✅ [HASIL-AKHIR-AUTO] Preserving existing BRIVA: {}", 
                        existingHasilAkhir.get().getBrivaNumber());
                brivaNumber = existingHasilAkhir.get().getBrivaNumber();
            }

            if (brivaNumber.equals("N/A")) {
                log.warn("⚠️ [HASIL-AKHIR-AUTO] No BRIVA found for student {}", studentId);
            }

            // Get nomor registrasi from reenrollment
            Optional<ReEnrollment> reenrollOpt = reenrollmentRepository
                    .findAll()
                    .stream()
                    .filter(r -> r.getStudent().getId().equals(studentId))
                    .filter(r -> r.getStatus() == ReEnrollment.ReEnrollmentStatus.SUBMITTED)
                    .findFirst();

            // Generate nomor registrasi if not found in reenrollment
            String nomorRegistrasi = reenrollOpt
                    .map(r -> generateNomorRegistrasi(studentId, r.getId()))
                    .orElseGet(() -> generateNomorRegistrasi(studentId, null));

            // ===== NEW: Extract wave type, selection type, program studi =====
            RegistrationPeriod.WaveType waveType = RegistrationPeriod.WaveType.REGULAR_TEST;
            String selectionType = "N/A";
            String programStudiName = "N/A";
            RegistrationPeriod registrationPeriod = null;

            // Get admission form and validation to extract selection info
            Optional<AdmissionForm> formOpt = admissionFormRepository.findAll()
                    .stream()
                    .filter(f -> f.getStudent().getId().equals(studentId))
                    .findFirst();

            if (formOpt.isPresent()) {
                AdmissionForm form = formOpt.get();
                
                // Get wave type from registration period
                if (form.getPeriod() != null) {
                    registrationPeriod = form.getPeriod();
                    waveType = form.getPeriod().getWaveType() != null ? 
                            form.getPeriod().getWaveType() : RegistrationPeriod.WaveType.REGULAR_TEST;
                    log.debug("📝 [HASIL-AKHIR] Wave type: {}", waveType);
                }

                // Get selection type from jenis seleksi
                if (form.getJenisSeleksiId() != null) {
                    Optional<JenisSeleksi> jenisSeleksi = jenisSeleksiRepository.findById(form.getJenisSeleksiId());
                    if (jenisSeleksi.isPresent()) {
                        selectionType = jenisSeleksi.get().getNama();
                        log.debug("📝 [HASIL-AKHIR] Selection type: {}", selectionType);
                    }
                }

                // Get program studi name (first preference)
                if (form.getProgramStudi1() != null && !form.getProgramStudi1().isEmpty()) {
                    programStudiName = form.getProgramStudi1();
                    log.debug("📝 [HASIL-AKHIR] Program studi: {}", programStudiName);
                }
            }

            return createHasilAkhir(studentId, brivaNumber, nomorRegistrasi, brivaAmount, 
                    waveType, selectionType, programStudiName, registrationPeriod, jumlahCicilan);

        } catch (Exception e) {
            log.error("❌ [HASIL-AKHIR-AUTO] Error auto-populating hasil akhir: {}", e.getMessage());
            throw new RuntimeException("Gagal mengisi hasil akhir otomatis: " + e.getMessage());
        }
    }

    /**
     * Generate nomor registrasi (registration number)
     * Format: REG-YYYYMMDD-STUDENT_ID
     */
    private String generateNomorRegistrasi(Long studentId, Long reenrollmentId) {
        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
                .format(java.time.LocalDate.now());
        String nomorRegistrasi = String.format("REG-%s-%06d", timestamp, studentId);
        log.debug("📝 [HASIL-AKHIR] Generated nomor registrasi: {}", nomorRegistrasi);
        return nomorRegistrasi;
    }

    /**
     * Get hasil akhir for student
     */
    public Optional<HasilAkhir> getHasilAkhirByStudentId(Long studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return Optional.empty();
        }
        return hasilAkhirRepository.findByStudent(student);
    }

    /**
     * Get hasil akhir for user
     */
    public Optional<HasilAkhir> getHasilAkhirByUserId(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        return hasilAkhirRepository.findByUser(user);
    }

    /**
     * Update status
     */
    public void updateStatus(Long studentId, HasilAkhir.HasilAkhirStatus newStatus) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student != null) {
            Optional<HasilAkhir> hasil = hasilAkhirRepository.findByStudent(student);
            if (hasil.isPresent()) {
                HasilAkhir hasilAkhir = hasil.get();
                hasilAkhir.setStatus(newStatus);
                hasilAkhirRepository.save(hasilAkhir);
                log.info("✅ [HASIL-AKHIR] Status updated to {} for student {}", newStatus, studentId);
            }
        }
    }

    /**
     * Check if student has hasil akhir
     */
    public boolean studentHasHasilAkhir(Long studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            return false;
        }
        return hasilAkhirRepository.existsByStudent(student);
    }

    /**
     * Update nomor registrasi and BRIVA number on existing atau new hasil akhir.
     * Called from admin validation when manually assigning registration numbers.
     */
    @Transactional
    public HasilAkhir updateRegistrationNumberAndBriva(Long formValidationId, HasilAkhirRegistrationRequest request) {
        FormValidation validation = formValidationRepository.findById(formValidationId)
                .orElseThrow(() -> new RuntimeException("Form validation tidak ditemukan"));

        AdmissionForm form = validation.getAdmissionForm();
        if (form == null || form.getStudent() == null) {
            throw new RuntimeException("Student atau form tidak ditemukan");
        }

        log.info("🔍 [HASIL-AKHIR-REQUEST] nomorRegistrasi: '{}' | brivaNumber: '{}'",
                request.getNomorRegistrasi(), request.getBrivaNumber());

        Optional<HasilAkhir> hasilAkhirOpt = hasilAkhirRepository.findByStudent(form.getStudent());
        HasilAkhir hasilAkhir = hasilAkhirOpt.orElseGet(() -> {
            HasilAkhir ha = new HasilAkhir();
            ha.setStudent(form.getStudent());
            ha.setUser(form.getStudent().getUser());
            return ha;
        });

        if (request.getNomorRegistrasi() != null && !request.getNomorRegistrasi().isEmpty()) {
            hasilAkhir.setNomorRegistrasi(request.getNomorRegistrasi());
        } else if (hasilAkhir.getNomorRegistrasi() == null || hasilAkhir.getNomorRegistrasi().isEmpty()) {
            String autoReg = "REG-" + java.time.LocalDate.now().toString().replace("-", "")
                    + "-" + String.format("%06d", formValidationId);
            hasilAkhir.setNomorRegistrasi(autoReg);
        }

        String brivaNumberToSave = request.getBrivaNumber();
        if (brivaNumberToSave != null && !brivaNumberToSave.trim().isEmpty()
                && !brivaNumberToSave.equals("(Belum ada BRIVA)")) {
            hasilAkhir.setBrivaNumber(brivaNumberToSave.trim());
        } else if (hasilAkhir.getBrivaNumber() == null || hasilAkhir.getBrivaNumber().equals("N/A")) {
            hasilAkhir.setBrivaNumber("PENDING_" + System.currentTimeMillis());
        }

        if (request.getJumlahCicilan() != null && request.getJumlahCicilan() > 0) {
            hasilAkhir.setJumlahCicilan(request.getJumlahCicilan());
        } else if (hasilAkhir.getJumlahCicilan() == null) {
            hasilAkhir.setJumlahCicilan(1);
        }

        HasilAkhir saved = hasilAkhirRepository.save(hasilAkhir);
        log.info("✅ [HASIL-AKHIR] Saved registration number and BRIVA for formValidation {}", formValidationId);
        return saved;
    }
}
