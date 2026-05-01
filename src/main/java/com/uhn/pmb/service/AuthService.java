package com.uhn.pmb.service;

import com.uhn.pmb.dto.LoginRequest;
import com.uhn.pmb.dto.RegisterRequest;
import com.uhn.pmb.dto.AuthResponse;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.entity.Student;
import com.uhn.pmb.entity.PasswordResetToken;
import com.uhn.pmb.repository.UserRepository;
import com.uhn.pmb.repository.StudentRepository;
import com.uhn.pmb.repository.PasswordResetTokenRepository;
import com.uhn.pmb.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    @Value("${app.frontend.url:http://localhost:9500}")
    private String frontendUrl;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:noreply@pmb-uhn.ac.id}")
    private String fromEmail;

    @Value("${brevo.sender.name:PMB HKBP Nommensen}")
    private String senderName;

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @PostConstruct
    public void init() {
        // Auto-detect Railway deployment: RAILWAY_PUBLIC_DOMAIN is injected automatically by Railway
        String railwayDomain = System.getenv("RAILWAY_PUBLIC_DOMAIN");
        if (railwayDomain != null && !railwayDomain.isBlank()) {
            frontendUrl = "https://" + railwayDomain;
            log.info("🚀 Railway detected — frontendUrl set to: {}", frontendUrl);
        } else {
            log.info("💻 Local mode — frontendUrl: {}", frontendUrl);
        }
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email sudah terdaftar");
        }

        // Determine role from request, default to CAMABA if not specified
        User.UserRole role = User.UserRole.CAMABA;
        if (request.getRole() != null && !request.getRole().isEmpty()) {
            try {
                role = User.UserRole.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role provided: {}, defaulting to CAMABA", request.getRole());
                role = User.UserRole.CAMABA;
            }
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .isActive(true)
                .emailVerified(false)
                .emailVerificationToken(UUID.randomUUID().toString())
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {} with role: {}", request.getEmail(), role);

        // Create student profile only for CAMABA users
        if (role == User.UserRole.CAMABA) {
            Student student = Student.builder()
                    .user(user)
                    .fullName(request.getFullName() != null ? request.getFullName() : request.getEmail().split("@")[0])
                    .nik(UUID.randomUUID().toString().substring(0, 12))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            studentRepository.save(student);
            log.info("Student profile created for: {}", request.getEmail());
        }

        // Send verification email
        sendVerificationEmail(user.getEmail(), user.getEmailVerificationToken());

        return AuthResponse.builder()
                .message("Registrasi berhasil! Silakan cek email Anda untuk verifikasi sebelum login.")
                .success(true)
                .build();
    }

    public AuthResponse verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Token verifikasi tidak valid"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return AuthResponse.builder()
                    .message("Email sudah diverifikasi sebelumnya. Silakan login.")
                    .success(true)
                    .build();
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        log.info("✅ Email verified successfully for: {}", user.getEmail());

        return AuthResponse.builder()
                .message("Email berhasil diverifikasi! Silakan login.")
                .success(true)
                .build();
    }

    public AuthResponse resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email tidak ditemukan"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return AuthResponse.builder()
                    .message("Email sudah diverifikasi. Silakan login.")
                    .success(true)
                    .build();
        }

        // Generate new token
        String newToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(newToken);
        userRepository.save(user);

        sendVerificationEmail(user.getEmail(), newToken);
        log.info("📧 Verification email resent to: {}", email);

        return AuthResponse.builder()
                .message("Email verifikasi telah dikirim ulang. Silakan cek email Anda.")
                .success(true)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        try {
            log.info("═══════════════════════════════════════════════════════════");
            log.info("🔐 [LOGIN] Starting authentication for: {}", request.getEmail());
            
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            
            log.info("✅ [LOGIN] Authentication successful for: {}", request.getEmail());
            log.info("👮‍♂️ [LOGIN] Authorities in Authentication object: {}", 
                authentication.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .reduce("", (acc, a) -> acc.isEmpty() ? a : acc + ", " + a));
            log.info("═══════════════════════════════════════════════════════════");

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

            if (!user.getIsActive()) {
                throw new RuntimeException("Akun telah dinonaktifkan");
            }

            if (user.getRole() == User.UserRole.CAMABA && !Boolean.TRUE.equals(user.getEmailVerified())) {
                throw new RuntimeException("Email belum diverifikasi. Silakan cek email Anda untuk link verifikasi.");
            }

            String token = jwtTokenProvider.generateToken(authentication);
            log.info("User login successfully: {}", request.getEmail());

            return AuthResponse.builder()
                    .token(token)
                    .email(user.getEmail())
                    .role(user.getRole().toString())
                    .message("Login berhasil")
                    .success(true)
                    .build();

        } catch (AuthenticationException e) {
            log.error("Login failed: {}", e.getMessage());
            throw new RuntimeException("Email atau password salah");
        }
    }

    public AuthResponse forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email tidak ditemukan"));

        // Generate reset token
        String resetToken = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);

        // Invalidate previous tokens
        tokenRepository.findByUserAndIsUsedFalse(user).ifPresent(token -> {
            token.setIsUsed(true);
            tokenRepository.save(token);
        });

        PasswordResetToken token = PasswordResetToken.builder()
                .token(resetToken)
                .user(user)
                .expiryDate(expiryDate)
                .isUsed(false)
                .build();

        tokenRepository.save(token);

        // Send email
        sendResetPasswordEmail(user.getEmail(), resetToken);
        log.info("Password reset token sent to: {}", email);

        return AuthResponse.builder()
                .message("Link verifikasi telah dikirim ke email Anda. Silahkan cek email Anda dalam 1 jam.")
                .success(true)
                .build();
    }

    public AuthResponse resetPassword(String token, String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new RuntimeException("Password tidak cocok");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token tidak valid"));

        if (resetToken.isExpired()) {
            throw new RuntimeException("Token sudah kadaluarsa. Silahkan request ulang.");
        }

        if (resetToken.getIsUsed()) {
            throw new RuntimeException("Token sudah digunakan");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        resetToken.setIsUsed(true);
        tokenRepository.save(resetToken);

        log.info("Password reset successfully for: {}", user.getEmail());

        return AuthResponse.builder()
                .message("Password berhasil diubah")
                .success(true)
                .build();
    }

    public Boolean validateToken(String token) {
        try {
            return jwtTokenProvider.validateToken(token);
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    private void sendVerificationEmail(String email, String token) {
        String verifyLink = frontendUrl + "/verify-email.html?token=" + token;
        String subject = "Verifikasi Email - PMB HKBP Nommensen";
        String text = "Halo,\n\n" +
                "Terima kasih telah mendaftar di PMB HKBP Nommensen.\n\n" +
                "Silakan klik link berikut untuk memverifikasi email Anda:\n\n" +
                verifyLink + "\n\n" +
                "Jika Anda tidak mendaftar, abaikan email ini.\n\n" +
                "Terima kasih,\nTim PMB HKBP Nommensen";

        sendViaBrevo(email, subject, text);
        log.info("📧 Verification email sent to: {}", email);
        log.info("🔗 Verify Link: {}", verifyLink);
    }

    private void sendResetPasswordEmail(String email, String token) {
        String resetLink = frontendUrl + "/reset-password.html?token=" + token;
        String subject = "Reset Password - PMB HKBP Nommensen";
        String text = "Halo,\n\n" +
                "Kami menerima permintaan untuk reset password akun Anda. Silahkan klik link di bawah ini untuk membuat password baru:\n\n" +
                resetLink + "\n\n" +
                "Link ini berlaku selama 1 jam. Jika Anda tidak melakukan permintaan ini, abaikan email ini.\n\n" +
                "Terima kasih,\nTim PMB HKBP Nommensen";

        sendViaBrevo(email, subject, text);
        log.info("Reset password email sent to: {}", email);
        log.info("Reset Link: {}", resetLink);
    }

    private void sendViaBrevo(String to, String subject, String text) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("⚠️ [MAIL-SKIP] BREVO_API_KEY not set — skipping email to: {}", to);
            return;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            String htmlText = "<pre style='font-family:Arial,sans-serif;white-space:pre-wrap'>" + text + "</pre>";

            Map<String, Object> body = new HashMap<>();
            body.put("sender", Map.of("name", senderName, "email", fromEmail));
            body.put("to", List.of(Map.of("email", to)));
            body.put("subject", subject);
            body.put("htmlContent", htmlText);

            log.info("📧 [BREVO-SEND] Sending email to: {} | Subject: {}", to, subject);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);
            log.info("✅ [BREVO-SUCCESS] Email sent to: {} | Status: {}", to, response.getStatusCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("❌ [BREVO-ERROR] status: {}, body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Gagal mengirim email: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("❌ [BREVO-ERROR] Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Gagal mengirim email. Silahkan coba lagi.");
        }
    }
}