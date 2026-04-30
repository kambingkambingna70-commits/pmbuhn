package com.uhn.pmb.controller;

import com.uhn.pmb.dto.*;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles student messaging with admin customer service.
 * Extracted from CamabaController for Single Responsibility Principle.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaMessagingController {

    private final AdminMessageRepository adminMessageRepository;
    private final UserRepository userRepository;

    /**
     * Mahasiswa mengirim pesan ke admin customer service
     * POST /api/camaba/messages/send-to-admin
     */
    @PostMapping("/messages/send-to-admin")
    public ResponseEntity<?> sendMessageToAdmin(@RequestBody SendMessageRequest request) {
        try {
            String senderEmail = SecurityContextHolder.getContext()
                    .getAuthentication().getName();
            User sender = userRepository.findByEmail(senderEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (request.getMessageContent() == null || request.getMessageContent().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Pesan tidak boleh kosong"));
            }

            String messageContent = request.getMessageContent().trim();
            if (messageContent.length() < 5) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Pesan minimal 5 karakter"));
            }

            // Get a CS admin - prefer ADMIN_PUSAT, fallback to ADMIN_VALIDASI
            User recipient = null;
            List<User> adminList = userRepository.findByRole(User.UserRole.ADMIN_PUSAT);
            if (adminList != null && !adminList.isEmpty()) {
                recipient = adminList.get(0);
            }
            if (recipient == null) {
                adminList = userRepository.findByRole(User.UserRole.ADMIN_VALIDASI);
                if (adminList != null && !adminList.isEmpty()) {
                    recipient = adminList.get(0);
                }
            }
            if (recipient == null) {
                log.warn("⚠️ No admin found to receive messages");
                return ResponseEntity.status(500).body(Map.of("success", false,
                        "message", "Maaf, tidak ada admin yang tersedia untuk menerima pesan. Hubungi admin secara langsung."));
            }

            AdminMessage message = AdminMessage.builder()
                    .sender(sender)
                    .recipient(recipient)
                    .messageContent(messageContent)
                    .messageType(request.getMessageType() != null ? request.getMessageType() : "QUESTION")
                    .status(AdminMessage.MessageStatus.UNREAD)
                    .createdAt(LocalDateTime.now())
                    .build();

            adminMessageRepository.save(message);

            log.info("✅ Message sent from student {} to admin {}", senderEmail, recipient.getEmail());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Pesan berhasil dikirim ke Admin Customer Service");
            response.put("messageId", message.getId());
            response.put("sentAt", message.getCreatedAt());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error sending message: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Mahasiswa melihat pesan-pesan mereka dengan admin (conversation)
     * GET /api/camaba/messages
     */
    @GetMapping("/messages")
    public ResponseEntity<?> getStudentMessages() {
        try {
            String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<AdminMessage> messages = adminMessageRepository.findConversationWith(user.getId());

            List<Map<String, Object>> responseMsgs = new ArrayList<>();
            for (AdminMessage msg : messages) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("id", msg.getId());
                Long senderId = msg.getSender() != null ? msg.getSender().getId() : null;
                String sEmail = msg.getSender() != null ? msg.getSender().getEmail() : "Unknown";
                msgMap.put("senderId", senderId);
                msgMap.put("senderEmail", sEmail);
                msgMap.put("senderName", sEmail);
                msgMap.put("senderType", msg.getSender().getId().equals(user.getId()) ? "STUDENT" : "ADMIN");
                msgMap.put("senderRole", msg.getSender().getRole());
                Long recipientId = msg.getRecipient() != null ? msg.getRecipient().getId() : null;
                String recipientEmail = msg.getRecipient() != null ? msg.getRecipient().getEmail() : "Unknown";
                msgMap.put("recipientId", recipientId);
                msgMap.put("recipientEmail", recipientEmail);
                msgMap.put("messageContent", msg.getMessageContent());
                msgMap.put("messageType", msg.getMessageType());
                msgMap.put("status", msg.getStatus().toString());
                msgMap.put("createdAt", msg.getCreatedAt());
                msgMap.put("readAt", msg.getReadAt());
                responseMsgs.add(msgMap);
            }

            // Mark messages as read
            for (AdminMessage msg : messages) {
                if (msg.getRecipient().getId().equals(user.getId()) &&
                        msg.getStatus() == AdminMessage.MessageStatus.UNREAD) {
                    msg.setStatus(AdminMessage.MessageStatus.READ);
                    msg.setReadAt(LocalDateTime.now());
                    adminMessageRepository.save(msg);
                }
            }

            log.info("✅ Retrieved {} messages for student {}", responseMsgs.size(), userEmail);
            return ResponseEntity.ok(responseMsgs);
        } catch (Exception e) {
            log.error("❌ Error fetching messages: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Hitung jumlah pesan yang belum dibaca untuk student
     * GET /api/camaba/messages/unread-count
     */
    @GetMapping("/messages/unread-count")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> getStudentUnreadCount() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return ResponseEntity.ok(Map.of("unreadCount", 0));
            }
            String userEmail = auth.getName();
            var userOpt = userRepository.findByEmail(userEmail);
            if (userOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("unreadCount", 0));
            }
            User student = userOpt.get();
            long unreadCount = adminMessageRepository.findAll().stream()
                    .filter(msg -> msg.getRecipient().getId().equals(student.getId()) &&
                                   msg.getStatus() == AdminMessage.MessageStatus.UNREAD)
                    .count();
            log.info("✅ Unread count for student {}: {}", userEmail, unreadCount);
            return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
        } catch (Exception e) {
            log.error("❌ Error getting unread count: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("unreadCount", 0));
        }
    }

    /**
     * Tandai semua pesan sebagai dibaca untuk student
     * POST /api/camaba/messages/mark-read
     */
    @PostMapping("/messages/mark-read")
    public ResponseEntity<?> markStudentMessagesAsRead() {
        try {
            String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            User student = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<AdminMessage> unreadMessages = adminMessageRepository.findAll().stream()
                    .filter(msg -> msg.getRecipient().getId().equals(student.getId()) &&
                                   msg.getStatus() == AdminMessage.MessageStatus.UNREAD)
                    .collect(Collectors.toList());

            unreadMessages.forEach(msg -> {
                msg.setStatus(AdminMessage.MessageStatus.READ);
                msg.setReadAt(LocalDateTime.now());
                adminMessageRepository.save(msg);
            });

            log.info("✅ Marked {} messages as read for student {}", unreadMessages.size(), userEmail);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Semua pesan telah ditandai sebagai dibaca",
                    "markedCount", unreadMessages.size()
            ));
        } catch (Exception e) {
            log.error("❌ Error marking messages as read: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
