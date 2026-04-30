package com.uhn.pmb.service;

import com.uhn.pmb.dto.CicilanRequestDTO;
import com.uhn.pmb.entity.CicilanRequest;
import com.uhn.pmb.entity.HasilAkhir;
import com.uhn.pmb.repository.CicilanRequestRepository;
import com.uhn.pmb.repository.HasilAkhirRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service untuk menangani admin cicilan request approval/rejection
 * Semua business logic approval ada di sini
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCicilanService {

    private final CicilanRequestRepository cicilanRequestRepository;
    private final HasilAkhirRepository hasilAkhirRepository;
    private final EmailService emailService;

    /**
     * Get pending cicilan requests (paginated)
     */
    public Page<CicilanRequestDTO> getPendingRequests(Pageable pageable) {
        Page<CicilanRequest> requests = cicilanRequestRepository.findPendingRequests(pageable);
        return requests.map(this::convertToDTO);
    }

    /**
     * Get cicilan requests by status (paginated)
     */
    public Page<CicilanRequestDTO> getByStatus(String statusStr, Pageable pageable) {
        CicilanRequest.CicilanRequestStatus status = CicilanRequest.CicilanRequestStatus.valueOf(statusStr.toUpperCase());
        Page<CicilanRequest> requests = cicilanRequestRepository.findByStatus(status, pageable);
        return requests.map(this::convertToDTO);
    }

    /**
     * Approve cicilan request
     * Can edit jumlah cicilan, harga cicilan 1, and BRIVA
     * BRIVA is now OPTIONAL - if not provided, use existing value
     */
    @Transactional
    public CicilanRequestDTO approveCicilanRequest(Long id, ApproveRequest request) {
        CicilanRequest cicilan = cicilanRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cicilan tidak ditemukan"));

        // Validate jumlah cicilan if provided
        if (request.getJumlahCicilan() != null) {
            if (request.getJumlahCicilan() < 1 || request.getJumlahCicilan() > 6) {
                throw new RuntimeException("Jumlah cicilan harus 1-6");
            }
            int newJumlah = request.getJumlahCicilan();
            cicilan.setJumlahCicilan(newJumlah);

            // Reset hargaCicilan2-6 based on ProgramStudi prices — enforces sequential selection
            // Cicilans beyond newJumlah are zeroed; within range use ProgramStudi preset prices
            com.uhn.pmb.entity.ProgramStudi ps = cicilan.getProgramStudi();
            cicilan.setHargaCicilan2(newJumlah >= 2 && ps.getCicilan2() != null && ps.getCicilan2() > 0 ? ps.getCicilan2() : 0L);
            cicilan.setHargaCicilan3(newJumlah >= 3 && ps.getCicilan3() != null && ps.getCicilan3() > 0 ? ps.getCicilan3() : 0L);
            cicilan.setHargaCicilan4(newJumlah >= 4 && ps.getCicilan4() != null && ps.getCicilan4() > 0 ? ps.getCicilan4() : 0L);
            cicilan.setHargaCicilan5(newJumlah >= 5 && ps.getCicilan5() != null && ps.getCicilan5() > 0 ? ps.getCicilan5() : 0L);
            cicilan.setHargaCicilan6(newJumlah >= 6 && ps.getCicilan6() != null && ps.getCicilan6() > 0 ? ps.getCicilan6() : 0L);
        }

        // Update harga cicilan 1 if provided; otherwise use ProgramStudi preset when jumlahCicilan changed
        if (request.getHargaCicilan1() != null) {
            cicilan.setHargaCicilan1(request.getHargaCicilan1());
        } else if (request.getJumlahCicilan() != null) {
            com.uhn.pmb.entity.ProgramStudi ps = cicilan.getProgramStudi();
            if (ps.getCicilan1() != null && ps.getCicilan1() > 0) {
                cicilan.setHargaCicilan1(ps.getCicilan1());
            }
        }

        // Recalculate hargaPerCicilan as sum of all selected (non-zero) cicilans
        if (cicilan.getJumlahCicilan() > 0) {
            long selectedTotal = (cicilan.getHargaCicilan1() != null ? cicilan.getHargaCicilan1() : 0L)
                    + (cicilan.getHargaCicilan2() != null ? cicilan.getHargaCicilan2() : 0L)
                    + (cicilan.getHargaCicilan3() != null ? cicilan.getHargaCicilan3() : 0L)
                    + (cicilan.getHargaCicilan4() != null ? cicilan.getHargaCicilan4() : 0L)
                    + (cicilan.getHargaCicilan5() != null ? cicilan.getHargaCicilan5() : 0L)
                    + (cicilan.getHargaCicilan6() != null ? cicilan.getHargaCicilan6() : 0L);
            cicilan.setHargaPerCicilan(selectedTotal);
        }

        // BRIVA is now optional
        if (request.getBriva() != null && !request.getBriva().trim().isEmpty()) {
            cicilan.setBriva(request.getBriva().trim());
        }

        // Set approval info
        cicilan.setStatus(CicilanRequest.CicilanRequestStatus.APPROVED);
        cicilan.setApprovedBy(request.getApprovedBy());
        cicilan.setApprovedAt(LocalDateTime.now());

        CicilanRequest saved = cicilanRequestRepository.save(cicilan);

        // Send approval email
        sendApprovalEmail(saved);

        // Copy BRIVA to hasil_akhir table
        copyBrivaToHasilAkhir(saved);

        return convertToDTO(saved);
    }

    /**
     * Reject cicilan request with reason
     */
    @Transactional
    public CicilanRequestDTO rejectCicilanRequest(Long id, RejectRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("Alasan penolakan harus diisi");
        }

        CicilanRequest cicilan = cicilanRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cicilan tidak ditemukan"));

        // Set rejection info
        cicilan.setStatus(CicilanRequest.CicilanRequestStatus.REJECTED);
        cicilan.setCatatan(request.getReason());
        cicilan.setApprovedAt(LocalDateTime.now());

        CicilanRequest saved = cicilanRequestRepository.save(cicilan);

        // Send rejection email
        sendRejectionEmail(saved, request.getReason(), request.getStudentEmail());

        return convertToDTO(saved);
    }

    /**
     * Delete cicilan request
     */
    public void deleteCicilanRequest(Long id) {
        CicilanRequest cicilan = cicilanRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cicilan tidak ditemukan"));
        cicilanRequestRepository.deleteById(id);
        log.info("✅ Cicilan {} deleted", id);
    }

    /**
     * Send approval email to student
     */
    private void sendApprovalEmail(CicilanRequest saved) {
        try {
            String studentEmail = saved.getStudent().getUser().getEmail();
            String studentName = saved.getStudent().getFullName();
            String programName = saved.getProgramStudi().getNama();
            
            String emailSubject = "✅ Request Cicilan Diterima - PMB HKBP Nommensen";
            String emailBody = String.format(
                "<html><body style='font-family: Arial, sans-serif; line-height: 1.6;'>" +
                "<div style='max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f5f5; border-radius: 8px;'>" +
                "<div style='background: #27ae60; color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center;'>" +
                "<h2 style='margin: 0;'>✅ Request Cicilan Diterima</h2>" +
                "</div>" +
                "<div style='background: white; padding: 30px; border-radius: 0 0 8px 8px;'>" +
                "<p>Halo <strong>%s</strong>,</p>" +
                "<p>Selamat! Request cicilan Anda telah <strong>DITERIMA</strong> oleh admin.</p>" +
                "<div style='background: #f0fdf4; border: 2px solid #27ae60; border-radius: 8px; padding: 20px; margin: 20px 0;'>" +
                "<h3 style='color: #27ae60; margin-top: 0;'>📋 Detail Cicilan Anda</h3>" +
                "<table style='width: 100%%; border-collapse: collapse;'>" +
                "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 10px; color: #666;'><strong>Program Studi</strong></td><td style='padding: 10px; text-align: right;'><strong>%s</strong></td></tr>" +
                "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 10px; color: #666;'><strong>Jumlah Cicilan</strong></td><td style='padding: 10px; text-align: right;'><strong>%d x</strong></td></tr>" +
                "<tr style='border-bottom: 1px solid #ddd;'><td style='padding: 10px; color: #666;'><strong>Total Harga</strong></td><td style='padding: 10px; text-align: right;'><strong style='color: #27ae60; font-size: 18px;'>Rp %s</strong></td></tr>" +
                "<tr><td style='padding: 10px; color: #666;'><strong>🏦 BRIVA Anda</strong></td><td style='padding: 10px; text-align: right;'><strong style='font-size: 16px; color: #e74c3c;'>%s</strong></td></tr>" +
                "</table></div>" +
                "</div></div></body></html>",
                studentName, programName, saved.getJumlahCicilan(),
                formatCurrency(saved.getHargaTotal()),
                saved.getBriva() != null ? saved.getBriva() : "Belum ditentukan"
            );
            
            emailService.sendHtmlEmail(studentEmail, emailSubject, emailBody);
        } catch (Exception e) {
            log.error("❌ Email send failed: {}", e.getMessage());
        }
    }

    /**
     * Send rejection email to student
     */
    private void sendRejectionEmail(CicilanRequest saved, String reason, String overrideEmail) {
        try {
            String studentEmail = overrideEmail != null ? overrideEmail : saved.getStudent().getUser().getEmail();
            String studentName = saved.getStudent().getFullName();
            
            String emailSubject = "❌ Notifikasi: Request Cicilan Ditolak - PMB HKBP Nommensen";
            String emailBody = String.format(
                "<html><body style='font-family: Arial, sans-serif; line-height: 1.6;'>" +
                "<div style='max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f5f5; border-radius: 8px;'>" +
                "<div style='background: #e74c3c; color: white; padding: 20px; border-radius: 8px 8px 0 0; text-align: center;'>" +
                "<h2 style='margin: 0;'>❌ Request Cicilan Ditolak</h2>" +
                "</div>" +
                "<div style='background: white; padding: 30px; border-radius: 0 0 8px 8px;'>" +
                "<p>Halo <strong>%s</strong>,</p>" +
                "<p>Maaf, request cicilan Anda telah <strong>DITOLAK</strong> oleh admin.</p>" +
                "<div style='background: #fef5f5; border: 2px solid #e74c3c; border-radius: 8px; padding: 20px; margin: 20px 0;'>" +
                "<h3 style='color: #e74c3c; margin-top: 0;'>📋 Alasan Penolakan</h3>" +
                "<p style='color: #666; white-space: pre-line;'>%s</p>" +
                "</div>" +
                "<div style='background: #f0f7ff; border-left: 4px solid #3498db; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "<p style='margin: 0; color: #2c3e50;'>" +
                "<strong>ℹ️ Langkah Selanjutnya:</strong><br>" +
                "Silahkan perbaiki data sesuai dengan alasan di atas dan lakukan kembali request cicilan. Kami siap membantu jika ada pertanyaan." +
                "</p></div>" +
                "</div></div></body></html>",
                studentName, reason.replace("\n", "<br>")
            );
            
            emailService.sendHtmlEmail(studentEmail, emailSubject, emailBody);
        } catch (Exception e) {
            log.error("❌ Email send failed: {}", e.getMessage());
        }
    }

    /**
     * Copy BRIVA from cicilan to hasil_akhir table
     */
    private void copyBrivaToHasilAkhir(CicilanRequest saved) {
        try {
            var student = saved.getStudent();
            if (student != null && saved.getBriva() != null) {
                Optional<HasilAkhir> hasilAkhirOpt = hasilAkhirRepository.findByStudent(student);
                HasilAkhir hasilAkhir;
                
                if (hasilAkhirOpt.isPresent()) {
                    hasilAkhir = hasilAkhirOpt.get();
                    log.info("📌 [BRIVA-COPY] Updating existing HASIL_AKHIR record for student: {}", student.getId());
                } else {
                    hasilAkhir = new HasilAkhir();
                    hasilAkhir.setStudent(student);
                    hasilAkhir.setUser(student.getUser());
                    log.info("📌 [BRIVA-COPY] Creating new HASIL_AKHIR record for student: {}", student.getId());
                }
                
                // Copy BRIVA from cicilan to hasil_akhir
                hasilAkhir.setBrivaNumber(saved.getBriva());
                hasilAkhir.setBrivaAmount(new BigDecimal(saved.getHargaTotal()));
                hasilAkhir.setStatus(HasilAkhir.HasilAkhirStatus.ACTIVE);
                hasilAkhir.setUpdatedAt(LocalDateTime.now());
                
                // Ensure nomorRegistrasi is not null for new records
                if (hasilAkhir.getNomorRegistrasi() == null || hasilAkhir.getNomorRegistrasi().isEmpty()) {
                    hasilAkhir.setNomorRegistrasi("REG-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + String.format("%06d", student.getId()));
                }
                
                hasilAkhirRepository.save(hasilAkhir);
                log.info("✅ [BRIVA-COPY] BRIVA '{}' copied to HASIL_AKHIR for student: {}", saved.getBriva(), student.getId());
            }
        } catch (Exception e) {
            log.error("⚠️ [BRIVA-COPY] Failed to copy BRIVA to HASIL_AKHIR: {}", e.getMessage());
        }
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

    /**
     * Format currency value to Indonesian format (Rp X.XXX.XXX)
     */
    private String formatCurrency(Long value) {
        if (value == null) return "0";
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0");
        df.setDecimalFormatSymbols(new java.text.DecimalFormatSymbols(new java.util.Locale("id", "ID")));
        return df.format(value);
    }

    // ===== REQUEST DTOs (moved from controller) =====
    
    public static class ApproveRequest {
        private Integer jumlahCicilan;
        private Long hargaCicilan1;
        private String briva;
        private String approvedBy;

        public ApproveRequest() {}
        public ApproveRequest(Integer jumlahCicilan, Long hargaCicilan1, String briva, String approvedBy) {
            this.jumlahCicilan = jumlahCicilan;
            this.hargaCicilan1 = hargaCicilan1;
            this.briva = briva;
            this.approvedBy = approvedBy;
        }

        public Integer getJumlahCicilan() { return jumlahCicilan; }
        public void setJumlahCicilan(Integer jumlahCicilan) { this.jumlahCicilan = jumlahCicilan; }
        public Long getHargaCicilan1() { return hargaCicilan1; }
        public void setHargaCicilan1(Long hargaCicilan1) { this.hargaCicilan1 = hargaCicilan1; }
        public String getBriva() { return briva; }
        public void setBriva(String briva) { this.briva = briva; }
        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    }

    public static class RejectRequest {
        private String reason;
        private String studentEmail;

        public RejectRequest() {}
        public RejectRequest(String reason, String studentEmail) {
            this.reason = reason;
            this.studentEmail = studentEmail;
        }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getStudentEmail() { return studentEmail; }
        public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    }
}
