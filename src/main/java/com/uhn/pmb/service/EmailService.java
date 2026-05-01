package com.uhn.pmb.service;

import com.uhn.pmb.entity.Notification;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.repository.NotificationRepository;
import com.uhn.pmb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EmailService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Value("${brevo.sender.email:noreply@pmb-uhn.ac.id}")
    private String fromEmail;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.sender.name:PMB HKBP Nommensen}")
    private String senderName;

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private void sendViaBrevo(String to, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("⚠️ [MAIL-SKIP] BREVO_API_KEY not set — skipping email to: {}", to);
            return;
        }
        try {
            log.info("📧 [BREVO-SEND] Sending email to: {} | Subject: {}", to, subject);
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("sender", Map.of("name", senderName, "email", fromEmail));
            body.put("to", List.of(Map.of("email", to)));
            body.put("subject", subject);
            body.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            log.info("✅ [BREVO-SUCCESS] Email sent to: {} | Status: {}", to, response.getStatusCode());
        } catch (Exception e) {
            log.error("❌ [BREVO-ERROR] Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }

    public void sendSimpleEmail(String to, String subject, String text) {
        String htmlContent = "<pre style='font-family:Arial,sans-serif'>" + text + "</pre>";
        sendViaBrevo(to, subject, htmlContent);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        sendViaBrevo(to, subject, htmlContent);
    }

    public void sendRegistrationConfirmation(String email, String fullName) {
        String subject = "Registrasi Berhasil - PMB HKBP Nommensen";
        String htmlContent = String.format("""
            <html><body style="font-family:Arial,sans-serif;line-height:1.6">
            <p>Halo <strong>%s</strong>,</p>
            <p>Akun Anda berhasil terdaftar. Silakan login dengan email <strong>%s</strong> dan lanjutkan proses pendaftaran.</p>
            <p>— Tim PMB HKBP Nommensen</p>
            </body></html>
            """, fullName, email);
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void sendVirtualAccountInfo(String email, String vaNumber, String amount, String dueDate) {
        String subject = "Informasi Virtual Account - PMB HKBP Nommensen";
        String htmlContent = String.format("""
            <html><body style="font-family:Arial,sans-serif;line-height:1.6">
            <p>Pembayaran pendaftaran Anda telah diproses. Berikut informasi Virtual Account:</p>
            <table style="border-collapse:collapse">
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Nomor VA</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%s</td></tr>
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Jumlah</strong></td><td style="padding:6px 12px;border:1px solid #ddd">Rp %s</td></tr>
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Jatuh Tempo</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%s</td></tr>
            </table>
            <p>Transfer melalui ATM BRI / e-Banking BRI sesuai nominal di atas sebelum jatuh tempo.</p>
            <p>— Tim PMB HKBP Nommensen</p>
            </body></html>
            """, vaNumber, amount, dueDate);
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void sendExamNotification(String email, String examNumber, String examDate, String gformUrl) {
        String subject = "Jadwal Ujian PMB - " + examDate;
        String htmlContent = String.format("""
            <html><body style="font-family:Arial,sans-serif;line-height:1.6">
            <p>Anda memiliki jadwal ujian:</p>
            <table style="border-collapse:collapse">
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Nomor Ujian</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%s</td></tr>
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Tanggal</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%s</td></tr>
            </table>
            <p>Silakan login ke portal PMB dan akses menu Ujian untuk mengerjakan soal.</p>
            <p>— Tim PMB HKBP Nommensen</p>
            </body></html>
            """, examNumber, examDate);
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void sendResultNotification(String email, Boolean passed, String admissionNumber, String password) {
        String subject = passed ? "Selamat! Anda Dinyatakan LULUS - PMB HKBP Nommensen" : "Pengumuman Hasil Ujian PMB";
        String htmlContent;
        if (passed) {
            htmlContent = String.format("""
                <html><body style="font-family:Arial,sans-serif;line-height:1.6">
                <p>Selamat! Anda dinyatakan <strong>LULUS</strong> seleksi PMB HKBP Nommensen.</p>
                <table style="border-collapse:collapse">
                  <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Nomor Pendaftaran</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%s</td></tr>
                  <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Password Awal</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%s</td></tr>
                </table>
                <p>Login menggunakan Nomor Pendaftaran dan password di atas, lalu lengkapi formulir daftar ulang.</p>
                <p>— Tim PMB HKBP Nommensen</p>
                </body></html>
                """, admissionNumber, password);
        } else {
            htmlContent = """
                <html><body style="font-family:Arial,sans-serif;line-height:1.6">
                <p>Terima kasih telah mengikuti seleksi PMB HKBP Nommensen.</p>
                <p>Mohon maaf, Anda belum dinyatakan lulus pada seleksi ini. Anda dapat mencoba kembali pada gelombang berikutnya.</p>
                <p>— Tim PMB HKBP Nommensen</p>
                </body></html>
                """;
        }
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void recordNotification(User user, String subject, String message,
                                  Notification.NotificationType type) {
        Notification notification = Notification.builder()
                .user(user)
                .subject(subject)
                .message(message)
                .type(type)
                .status(Notification.NotificationStatus.PENDING)
                .build();
        notificationRepository.save(notification);
    }

    public void sendFormApprovedEmail(String recipientEmail, String studentName, String token, String expiresAt) {
        String subject = "Formulir & Pembayaran Disetujui - Token Ujian Anda";
        String htmlContent = String.format("""
            <html><body style="font-family:Arial,sans-serif;line-height:1.6">
            <p>Halo <strong>%s</strong>,</p>
            <p>Formulir pendaftaran dan pembayaran Anda telah <strong>disetujui</strong>. Silakan lanjutkan untuk melakukan ujian.</p>
            <p><strong>Token Ujian:</strong></p>
            <p style="font-size:20px;font-weight:bold;letter-spacing:3px;background:#f0f0f0;padding:12px;border-left:4px solid #27ae60;display:inline-block">%s</p>
            <p style="color:#888">Berlaku hingga: %s (2 jam)</p>
            <p>Login ke portal PMB, masuk ke menu Ujian, dan masukkan token di atas untuk memulai.</p>
            <p>— Tim PMB HKBP Nommensen</p>
            </body></html>
            """, studentName, token, expiresAt);
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    public void sendFormRejectedEmail(String recipientEmail, String studentName, String reason) {
        String subject = "Update Status Formulir Pendaftaran Anda";
        String htmlContent = String.format("""
            <html><body style="font-family:Arial,sans-serif;line-height:1.6">
            <p>Halo <strong>%s</strong>,</p>
            <p>Formulir pendaftaran Anda belum dapat disetujui dengan alasan berikut:</p>
            <p style="background:#fff3cd;padding:12px;border-left:4px solid #e74c3c">%s</p>
            <p>Silakan login ke portal PMB, perbaiki data yang diperlukan, dan submit ulang. Hubungi kami jika butuh bantuan.</p>
            <p>— Tim PMB HKBP Nommensen</p>
            </body></html>
            """, studentName, reason);
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    public void sendExamCompletedEmail(String recipientEmail, String studentName, int score, boolean passed) {
        String subject = passed ? "Ujian Selesai - Anda LULUS!" : "Ujian Selesai - Hasil Ujian Anda";
        String htmlContent = String.format("""
            <html><body style="font-family:Arial,sans-serif;line-height:1.6">
            <p>Halo <strong>%s</strong>,</p>
            <p>Ujian Anda telah selesai diproses.</p>
            <table style="border-collapse:collapse">
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Nilai</strong></td><td style="padding:6px 12px;border:1px solid #ddd">%d / 100</td></tr>
              <tr><td style="padding:6px 12px;border:1px solid #ddd"><strong>Status</strong></td><td style="padding:6px 12px;border:1px solid #ddd"><strong>%s</strong></td></tr>
            </table>
            <p>%s</p>
            <p>— Tim PMB HKBP Nommensen</p>
            </body></html>
            """,
            studentName, score,
            passed ? "LULUS ✅" : "BELUM LULUS",
            passed ? "Selamat! Silakan login ke portal untuk melihat langkah selanjutnya."
                   : "Anda dapat menghubungi admin untuk informasi lebih lanjut."
        );
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }
}
