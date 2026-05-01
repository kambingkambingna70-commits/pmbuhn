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
        String subject = "Konfirmasi Registrasi - PMB HKBP Nommensen";
        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Selamat datang, %s!</h2>
                    <p>Akun Anda telah berhasil terdaftar di sistem PMB HKBP Nommensen.</p>
                    <p>Anda dapat login dengan email: <strong>%s</strong></p>
                    <p>Silahkan akses sistem melalui portal PMB untuk melanjutkan pendaftaran.</p>
                    <p>Terima kasih,</p>
                    <p>Tim PMB HKBP Nommensen</p>
                </body>
            </html>
            """, fullName, email);
        
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void sendVirtualAccountInfo(String email, String vaNumber, String amount, String dueDate) {
        String subject = "Informasi Virtual Account Pembayaran PMB";
        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Informasi Virtual Account</h2>
                    <p>Virtual Account Anda telah berhasil dibuat.</p>
                    <table style="border-collapse: collapse; width: 100%%; max-width: 500px;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Nomor VA:</strong></td>
                            <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Jumlah:</strong></td>
                            <td style="padding: 8px; border: 1px solid #ddd;">Rp %s</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Jatuh Tempo:</strong></td>
                            <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                        </tr>
                    </table>
                    <p style="margin-top: 20px;"><strong>Petunjuk Pembayaran:</strong></p>
                    <ol>
                        <li>Transfer melalui ATM BRI atau e-Banking BRI</li>
                        <li>Nominal harus sesuai dengan jumlah di atas</li>
                        <li>Pastikan pembayaran selesai sebelum jatuh tempo</li>
                    </ol>
                    <p>Terima kasih,<br/>Tim PMB HKBP Nommensen</p>
                </body>
            </html>
            """, vaNumber, amount, dueDate);
        
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void sendExamNotification(String email, String examNumber, String examDate, String gformUrl) {
        String subject = "Notifikasi: Jadwal Ujian Anda";
        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Notifikasi Jadwal Ujian</h2>
                    <p>Anda memiliki jadwal ujian sebagai berikut:</p>
                    <table style="border-collapse: collapse; width: 100%%; max-width: 500px;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Nomor Ujian:</strong></td>
                            <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Tanggal/Waktu:</strong></td>
                            <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                        </tr>
                    </table>
                    <p style="margin-top: 20px;"><strong>Link Ujian:</strong> <a href="%s">%s</a></p>
                    <p style="color: red;"><strong>Penting:</strong> Pastikan Anda login ke akun sebelum waktu ujian dimulai.</p>
                    <p>Terima kasih,<br/>Tim PMB HKBP Nommensen</p>
                </body>
            </html>
            """, examNumber, examDate, gformUrl, "Klik di sini untuk ujian");
        
        sendHtmlEmail(email, subject, htmlContent);
    }

    public void sendResultNotification(String email, Boolean passed, String admissionNumber, String password) {
        String subject = passed ? "Selamat! Anda Dinyatakan Lulus" : "Pengumuman Hasil Ujian PMB";
        String htmlContent;
        
        if (passed) {
            htmlContent = String.format("""
                <html>
                    <body style="font-family: Arial, sans-serif;">
                        <h2 style="color: green;">🎉 Selamat! Anda Dinyatakan LULUS</h2>
                        <p>Kami dengan senang hati memberitahukan bahwa Anda telah dinyatakan lulus dalam seleksi PMB.</p>
                        <table style="border-collapse: collapse; width: 100%%; max-width: 500px;">
                            <tr>
                                <td style="padding: 8px; border: 1px solid #ddd;"><strong>Nomor Pendaftaran:</strong></td>
                                <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding: 8px; border: 1px solid #ddd;"><strong>Password Awal:</strong></td>
                                <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                            </tr>
                        </table>
                        <p style="margin-top: 20px;"><strong>Langkah Selanjutnya:</strong></p>
                        <ol>
                            <li>Login menggunakan Nomor Pendaftaran dan password awal (tanggal lahir)</li>
                            <li>Lengkapi formulir pendaftaran ulang</li>
                            <li>Lakukan pembayaran cicilan</li>
                        </ol>
                        <p style="color: red;"><strong>Batas waktu daftar ulang:</strong> Silakan periksa portal untuk deadline.</p>
                        <p>Terima kasih,<br/>Tim PMB HKBP Nommensen</p>
                    </body>
                </html>
                """, admissionNumber, password);
        } else {
            htmlContent = """
                <html>
                    <body style="font-family: Arial, sans-serif;">
                        <h2>Pengumuman Hasil Ujian PMB</h2>
                        <p>Kami dengan hormat memberitahukan hasil ujian Anda:</p>
                        <p style="color: red; font-weight: bold; font-size: 18px;">Maaf, Anda dinyatakan TIDAK LULUS</p>
                        <p>Anda dapat mencoba lagi pada gelombang pendaftaran berikutnya.</p>
                        <p>Terima kasih atas partisipasi Anda,<br/>Tim PMB HKBP Nommensen</p>
                    </body>
                </html>
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

    /**
     * Kirim email notifikasi formulir diapprove + token ujian
     */
    public void sendFormApprovedEmail(String recipientEmail, String studentName, String token, String expiresAt) {
        String subject = "✅ Formulir Anda Telah Disetujui - Token Ujian Terlampir";
        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6;">
                    <h2 style="color: #27ae60;">✅ Selamat %s!</h2>
                    <p>Pendaftaran Anda telah <strong>disetujui</strong> oleh Admin! 🎉</p>
                    
                    <div style="background: #e8f5e9; padding: 15px; border-radius: 5px; margin: 20px 0;">
                        <h3 style="color: #1a472a;">📋 TOKEN UJIAN ONLINE ANDA</h3>
                        <p style="font-size: 18px; font-weight: bold; letter-spacing: 2px; background: white; padding: 10px; border-left: 4px solid #27ae60;">
                            %s
                        </p>
                        <p><strong>Berlaku hingga:</strong> %s</p>
                    </div>
                    
                    <h3 style="color: #1a472a;">📝 CARA MENGGUNAKAN TOKEN:</h3>
                    <ol style="font-size: 14px;">
                        <li>Buka halaman ujian: <a href="http://localhost:9500/ujian.html" style="color: #27ae60;">http://localhost:9500/ujian.html</a></li>
                        <li>Input token di atas pada form yang tersedia</li>
                        <li>Google Form ujian akan muncul</li>
                        <li>Selesaikan ujian dan submit nilai Anda</li>
                        <li>Tunggu konfirmasi hasil dari admin</li>
                    </ol>
                    
                    <div style="background: #fff3cd; padding: 15px; border-radius: 5px; margin: 20px 0;">
                        <p style="color: #ff6b6b; font-weight: bold;">
                            ⏰ PENTING: Token hanya berlaku <strong>2 jam</strong> dari waktu ini!
                        </p>
                    </div>
                    
                    <h3 style="color: #1a472a;">📞 HUBUNGI KAMI</h3>
                    <p>Jika ada pertanyaan, hubungi customer service:</p>
                    <p>
                        <strong>WhatsApp:</strong> <a href="https://wa.me/6283872746279" style="color: #27ae60;">+62-838-7274-6279</a><br/>
                        <strong>Email:</strong> %s
                    </p>
                    
                    <p style="margin-top: 30px; color: #999; font-size: 12px;">
                        Terima kasih,<br/>
                        <strong>Tim PMB HKBP Nommensen</strong>
                    </p>
                </body>
            </html>
            """, studentName, token, expiresAt, fromEmail);
        
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    /**
     * Kirim email notifikasi formulir ditolak
     */
    public void sendFormRejectedEmail(String recipientEmail, String studentName, String reason) {
        String subject = "⚠️ Update Formulir Pendaftaran Anda";
        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6;">
                    <h2 style="color: #e74c3c;">⚠️ Halo %s,</h2>
                    <p>Kami telah mereview formulir pendaftaran Anda, namun sayangnya belum dapat disetujui saat ini.</p>
                    
                    <div style="background: #fadbd8; padding: 15px; border-radius: 5px; margin: 20px 0; border-left: 4px solid #e74c3c;">
                        <h3 style="color: #c0392b; margin-top: 0;">ALASAN PENOLAKAN:</h3>
                        <p style="font-size: 14px;">%s</p>
                    </div>
                    
                    <h3 style="color: #1a472a;">📝 LANGKAH SELANJUTNYA:</h3>
                    <ol style="font-size: 14px;">
                        <li>Silakan hubungi customer service untuk diskusi lebih lanjut</li>
                        <li>Anda dapat melakukan perbaikan pada data dan submit ulang</li>
                        <li>Tim kami siap membantu proses perbaikan formulir Anda</li>
                    </ol>
                    
                    <h3 style="color: #1a472a;">📞 HUBUNGI KAMI</h3>
                    <p>Hubungi customer service untuk bantuan:</p>
                    <p>
                        <strong>WhatsApp:</strong> <a href="https://wa.me/6283872746279" style="color: #27ae60;">+62-838-7274-6279</a><br/>
                        <strong>Email:</strong> %s
                    </p>
                    
                    <p style="margin-top: 30px; color: #999; font-size: 12px;">
                        Kami yakin Anda bisa!<br/>
                        <strong>Tim PMB HKBP Nommensen</strong>
                    </p>
                </body>
            </html>
            """, studentName, reason, fromEmail);
        
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    /**
     * Kirim email notifikasi hasil ujian ke mahasiswa
     */
    public void sendExamCompletedEmail(String recipientEmail, String studentName, int score, boolean passed) {
        String subject = passed ? "✅ Ujian Anda LULUS!" : "⚠️ Hasil Ujian";
        String status = passed ? "LULUS 🎉" : "BELUM LULUS";
        String htmlContent = String.format("""
            <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6;">
                    <h2 style="color: %s;">%s Halo %s,</h2>
                    <p>Ujian online Anda telah selesai diproses!</p>
                    
                    <div style="background: %s; padding: 20px; border-radius: 5px; margin: 20px 0; border-left: 4px solid %s;">
                        <h3 style="margin-top: 0;">📊 HASIL UJIAN ANDA:</h3>
                        <p style="font-size: 1.2rem;">
                            <strong>Nilai:</strong> %d / 100<br/>
                            <strong>Status:</strong> <span style="font-weight: bold;">%s</span>
                        </p>
                    </div>
                    
                    <p>%s</p>
                    
                    <h3 style="color: #1a472a;">📞 HUBUNGI KAMI</h3>
                    <p>Pertanyaan? Hubungi customer service:</p>
                    <p>
                        <strong>WhatsApp:</strong> <a href="https://wa.me/6283872746279" style="color: #27ae60;">+62-838-7274-6279</a><br/>
                        <strong>Email:</strong> %s
                    </p>
                    
                    <p style="margin-top: 30px; color: #999; font-size: 12px;">
                        Terima kasih,<br/>
                        <strong>Tim PMB HKBP Nommensen</strong>
                    </p>
                </body>
            </html>
            """, 
            passed ? "#27ae60" : "#e74c3c",
            passed ? "✅" : "⚠️",
            studentName,
            passed ? "#e8f5e9" : "#fadbd8",
            passed ? "#27ae60" : "#e74c3c",
            score, 
            status,
            passed ? "Selamat! Anda lulus dan bisa lanjut ke tahap berikutnya." : 
                    "Silakan coba lagi atau hubungi admin untuk informasi lebih lanjut.",
            fromEmail
        );
        
        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }
}
