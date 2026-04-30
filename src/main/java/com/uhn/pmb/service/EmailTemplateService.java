package com.uhn.pmb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final EmailService emailService;

    public void sendApprovalEmail(String studentEmail, String studentName) {
        try {
            String subject = "🎓 SELAMAT! Pendaftaran Anda Telah Disetujui";
            String html = String.format("""
                <html><body style="font-family:Arial,sans-serif;color:#333">
                <div style="max-width:600px;margin:0 auto;padding:20px">
                    <div style="background:#28a745;padding:20px;border-radius:8px 8px 0 0;color:white;text-align:center">
                        <h1>🎉 SELAMAT!</h1>
                        <p>Anda Telah Menyelesaikan Pendaftaran</p>
                    </div>
                    <div style="background:white;padding:30px">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Formulir pendaftaran Anda telah <strong>DISETUJUI ✅</strong>.</p>
                        <div style="background:#e8f5e9;border-left:4px solid #28a745;padding:15px;margin:20px 0">
                            <p><strong>Nomor Registrasi & Virtual Account BRIVA</strong> dapat dilihat
                            di dashboard pada tombol "Hasil Akhir Penerimaan".</p>
                        </div>
                        <p style="color:#999;font-size:12px">--- Tim PMB HKBP Nommensen</p>
                    </div>
                </div>
                </body></html>
                """, studentName);
            emailService.sendHtmlEmail(studentEmail, subject, html);
            log.info("✅ Approval email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("❌ Error sending approval email to {}: {}", studentEmail, e.getMessage());
        }
    }

    public void sendRejectionEmail(String studentEmail, String studentName, String reason) {
        try {
            String subject = "❌ Formulir Pendaftaran Anda Ditolak - PMB HKBP Nommensen";
            String html = String.format("""
                <html><body style="font-family:Arial,sans-serif;color:#333">
                <div style="max-width:600px;margin:0 auto;padding:20px">
                    <div style="background:#dc3545;padding:20px;border-radius:8px 8px 0 0;color:white;text-align:center">
                        <h1>❌ PEMBERITAHUAN PENOLAKAN</h1>
                    </div>
                    <div style="background:white;padding:30px">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Formulir pendaftaran Anda <strong>TIDAK DAPAT DITERIMA</strong>.</p>
                        <div style="background:#f8d7da;border-left:4px solid #dc3545;padding:15px;margin:20px 0">
                            <strong>Alasan:</strong> %s
                        </div>
                        <p style="color:#999;font-size:12px">--- Tim PMB HKBP Nommensen</p>
                    </div>
                </div>
                </body></html>
                """, studentName, reason != null ? reason : "-");
            emailService.sendHtmlEmail(studentEmail, subject, html);
            log.info("✅ Rejection email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("❌ Error sending rejection email to {}: {}", studentEmail, e.getMessage());
        }
    }

    public void sendRevisionNeededEmail(String studentEmail, String studentName,
                                        String reason, Integer revisionNumber) {
        try {
            String subject = "✏️ REVISI DIPERLUKAN (Revisi ke-" + revisionNumber + ") - PMB HKBP Nommensen";
            String html = String.format("""
                <html><body style="font-family:Arial,sans-serif;color:#333">
                <div style="max-width:600px;margin:0 auto;padding:20px">
                    <div style="background:#ff9800;padding:20px;border-radius:8px 8px 0 0;color:white;text-align:center">
                        <h1>✏️ REVISI DIPERLUKAN (Revisi ke-%d)</h1>
                    </div>
                    <div style="background:white;padding:30px">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Ada data yang perlu diperbaiki:</p>
                        <div style="background:#ffe0b2;border-left:4px solid #ff9800;padding:15px;margin:20px 0">
                            <strong>%s</strong>
                        </div>
                        <p>Silakan login ke dashboard dan perbaiki formulir Anda.</p>
                        <p style="color:#999;font-size:12px">--- Tim PMB HKBP Nommensen</p>
                    </div>
                </div>
                </body></html>
                """, revisionNumber, studentName, reason != null ? reason : "-");
            emailService.sendHtmlEmail(studentEmail, subject, html);
            log.info("✅ Revision email sent to: {} (ke-{})", studentEmail, revisionNumber);
        } catch (Exception e) {
            log.error("❌ Error sending revision email to {}: {}", studentEmail, e.getMessage());
        }
    }

    public void sendExamApprovalEmail(String studentEmail, String studentName) {
        try {
            String subject = "✅ Ujian Anda Telah Diterima - PMB HKBP Nommensen";
            String html = String.format("""
                <html><body style="font-family:Arial,sans-serif;color:#333">
                <div style="max-width:600px;margin:0 auto;padding:20px">
                    <div style="background:#28a745;padding:20px;border-radius:8px 8px 0 0;color:white;text-align:center">
                        <h1>✅ UJIAN DITERIMA</h1>
                    </div>
                    <div style="background:white;padding:30px">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Hasil ujian Anda telah <strong>DITERIMA ✅</strong>.</p>
                        <p>Silakan lanjutkan ke tahap pembayaran cicilan 1 dan daftar ulang.</p>
                        <p style="color:#999;font-size:12px">--- Tim PMB HKBP Nommensen</p>
                    </div>
                </div>
                </body></html>
                """, studentName);
            emailService.sendHtmlEmail(studentEmail, subject, html);
            log.info("✅ Exam approval email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("❌ Error sending exam approval email to {}: {}", studentEmail, e.getMessage());
        }
    }

    public void sendExamRejectionEmail(String studentEmail, String studentName, String reason) {
        try {
            String subject = "❌ Ujian Anda Ditolak - PMB HKBP Nommensen";
            String html = String.format("""
                <html><body style="font-family:Arial,sans-serif;color:#333">
                <div style="max-width:600px;margin:0 auto;padding:20px">
                    <div style="background:#dc3545;padding:20px;border-radius:8px 8px 0 0;color:white;text-align:center">
                        <h1>❌ UJIAN DITOLAK</h1>
                    </div>
                    <div style="background:white;padding:30px">
                        <p>Dear <strong>%s</strong>,</p>
                        <p>Hasil ujian Anda <strong>TIDAK DAPAT DITERIMA</strong>.</p>
                        <div style="background:#f8d7da;border-left:4px solid #dc3545;padding:15px">
                            <strong>Alasan:</strong> %s
                        </div>
                        <p style="color:#999;font-size:12px">--- Tim PMB HKBP Nommensen</p>
                    </div>
                </div>
                </body></html>
                """, studentName, reason != null && !reason.isEmpty() ? reason : "Tidak memenuhi kriteria");
            emailService.sendHtmlEmail(studentEmail, subject, html);
            log.info("✅ Exam rejection email sent to: {}", studentEmail);
        } catch (Exception e) {
            log.error("❌ Error sending exam rejection email to {}: {}", studentEmail, e.getMessage());
        }
    }
}