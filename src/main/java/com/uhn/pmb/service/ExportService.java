package com.uhn.pmb.service;

import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final AdmissionFormRepository admissionFormRepository;
    private final ReEnrollmentRepository reenrollmentRepository;
    private final HasilAkhirRepository hasilAkhirRepository;
    private final FormValidationRepository formValidationRepository;

    /**
     * Export admission form payment data as CSV
     */
    public byte[] exportFormulirPembayaran(Long periodId) {
        log.info("📋 Exporting formulir pembayaran for period: {}", periodId);

        List<AdmissionForm> forms = admissionFormRepository.findByPeriod_Id(periodId);
        StringBuilder csv = buildFormulirPembayaranCsv(forms);

        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
        log.info("✅ Exported {} formulir pembayaran records", forms.size());

        return data;
    }

    /**
     * Export re-enrollment data as CSV
     */
    public byte[] exportDaftarUlang(Long periodId) {
        log.info("📋 Exporting daftar ulang for period: {}", periodId);

        List<ReEnrollment> reEnrollments = reenrollmentRepository.findAll();
        StringBuilder csv = buildDaftarUlangCsv(reEnrollments);

        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
        log.info("✅ Exported {} daftar ulang records", reEnrollments.size());

        return data;
    }

    /**
     * Export final results (hasil akhir) as CSV
     */
    public byte[] exportHasilAkhir(Long periodId) {
        log.info("📋 Exporting hasil akhir for period: {}", periodId);

        List<HasilAkhir> results = hasilAkhirRepository.findAll();
        StringBuilder csv = buildHasilAkhirCsv(results);

        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
        log.info("✅ Exported {} hasil akhir records", results.size());

        return data;
    }

    /**
     * Export validation summary report
     */
    public byte[] exportValidationReport(Long periodId) {
        log.info("📋 Exporting validation report for period: {}", periodId);

        List<FormValidation> validations = formValidationRepository.findAll();
        StringBuilder csv = buildValidationReportCsv(validations);

        byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
        log.info("✅ Exported validation report with {} records", validations.size());

        return data;
    }

    // ============ CSV Building Methods ============

    private StringBuilder buildFormulirPembayaranCsv(List<AdmissionForm> forms) {
        StringBuilder csv = new StringBuilder();

        // Headers
        csv.append("No Registrasi,Nama Siswa,Email,Telepon,Program Studi,Jenis Seleksi,");
        csv.append("Status Pembayaran,Tanggal Pendaftaran,Tanggal Update\n");

        // Data rows
        for (int i = 0; i < forms.size(); i++) {
            AdmissionForm form = forms.get(i);
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    i + 1,
                    escapeQuotes(form.getStudent().getFullName()),
                    form.getStudent().getUser().getEmail(),
                    form.getStudent().getPhoneNumber() != null ? form.getStudent().getPhoneNumber() : "-",
                    form.getProgramStudi1() != null ? form.getProgramStudi1() : "-",
                    form.getJenisSeleksiId() != null ? form.getJenisSeleksiId().toString() : "-",
                    form.getStatus(),
                    form.getCreatedAt(),
                    form.getUpdatedAt()
            ));
        }

        return csv;
    }

    private StringBuilder buildDaftarUlangCsv(List<ReEnrollment> reEnrollments) {
        StringBuilder csv = new StringBuilder();

        // Headers
        csv.append("No,Nama Siswa,Email,Status Daftar Ulang,Tanggal Submit,");
        csv.append("Tanggal Validasi,Catatan\n");

        // Data rows
        for (int i = 0; i < reEnrollments.size(); i++) {
            ReEnrollment re = reEnrollments.get(i);
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s\n",
                    i + 1,
                    escapeQuotes(re.getStudent().getFullName()),
                    re.getStudent().getUser().getEmail(),
                    re.getStatus(),
                    re.getCreatedAt(),
                    re.getValidatedAt() != null ? re.getValidatedAt() : "-",
                    re.getValidationNotes() != null ? escapeQuotes(re.getValidationNotes()) : "-"
            ));
        }

        return csv;
    }

    private StringBuilder buildHasilAkhirCsv(List<HasilAkhir> results) {
        StringBuilder csv = new StringBuilder();

        // Headers
        csv.append("No Registrasi,Nama Siswa,Program Studi,Jenis Seleksi,");
        csv.append("Status Hasil,Tanggal Hasil,Virtual Account\n");

        // Data rows
        for (int i = 0; i < results.size(); i++) {
            HasilAkhir result = results.get(i);
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s\n",
                    i + 1,
                    escapeQuotes(result.getStudent().getFullName()),
                    result.getProgramStudiName() != null ? result.getProgramStudiName() : "-",
                    result.getSelectionType() != null ? result.getSelectionType() : "-",
                    result.getStatus(),
                    result.getCreatedAt(),
                    result.getBrivaNumber() != null ? result.getBrivaNumber() : "-"
            ));
        }

        return csv;
    }

    private StringBuilder buildValidationReportCsv(List<FormValidation> validations) {
        StringBuilder csv = new StringBuilder();

        // Headers
        csv.append("No,Nama Siswa,Email,Status Validasi,Tanggal Submit,");
        csv.append("Tanggal Validasi,Validator,Alasan Penolakan\n");

        // Data rows
        long approved = validations.stream().filter(v -> v.getValidationStatus() == FormValidation.ValidationStatus.APPROVED).count();
        long rejected = validations.stream().filter(v -> v.getValidationStatus() == FormValidation.ValidationStatus.REJECTED).count();
        long pending = validations.stream().filter(v -> v.getValidationStatus() == FormValidation.ValidationStatus.PENDING).count();

        csv.append("\nRingkasan:\n");
        csv.append("Total: ").append(validations.size()).append("\n");
        csv.append("Disetujui: ").append(approved).append("\n");
        csv.append("Ditolak: ").append(rejected).append("\n");
        csv.append("Pending: ").append(pending).append("\n\n");

        // Detailed rows
        for (int i = 0; i < validations.size(); i++) {
            FormValidation val = validations.get(i);
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s\n",
                    i + 1,
                    escapeQuotes(val.getStudent().getFullName()),
                    val.getStudent().getUser().getEmail(),
                    val.getValidationStatus(),
                    val.getCreatedAt(),
                    val.getUpdatedAt(),
                    val.getValidatedBy() != null ? val.getValidatedBy().getUsername() : "-",
                    val.getRejectionReason() != null ? escapeQuotes(val.getRejectionReason()) : "-"
            ));
        }

        return csv;
    }

    /**
     * Escape quotes in CSV fields to prevent parsing errors
     */
    private String escapeQuotes(String value) {
        if (value == null) return "-";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
