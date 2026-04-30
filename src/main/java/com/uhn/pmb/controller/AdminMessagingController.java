package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.dto.SendMessageRequest;
import com.uhn.pmb.dto.SendReminderRequest;
import com.uhn.pmb.entity.AdminMessage;
import com.uhn.pmb.service.AdminMessagingService;
import com.uhn.pmb.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminMessagingController {

    private final AdminMessagingService adminMessagingService;
    private final EmailService emailService;

    @GetMapping("/api/messages/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getUnreadMessageCount() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            com.uhn.pmb.entity.User user = adminMessagingService.getUserByEmail(auth.getName());
            Long unreadCount = adminMessagingService.countUnread(user.getId());
            return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
        } catch (Exception e) {
            log.error("Error getting unread count: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/api/messages/send")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            com.uhn.pmb.entity.User sender = adminMessagingService.getUserByEmail(auth.getName());
            adminMessagingService.send(sender, request);
            return ResponseEntity.ok(new ApiResponse(true, "Pesan dikirim"));
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/messages/conversation/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getConversation(@PathVariable Long userId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            com.uhn.pmb.entity.User currentUser = adminMessagingService.getUserByEmail(auth.getName());
            List<AdminMessage> messages = adminMessagingService.findConversation(currentUser.getId(), userId);
            adminMessagingService.markConversationRead(messages, currentUser.getId());
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error fetching conversation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/messages")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT', 'CAMABA')")
    public ResponseEntity<?> getMessages() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            com.uhn.pmb.entity.User user = adminMessagingService.getUserByEmail(auth.getName());
            log.info("🔍 Getting messages for user: {} (ID: {})", auth.getName(), user.getId());
            List<AdminMessage> messages = adminMessagingService.findAll();
            List<Map<String, Object>> responseMsgs = new ArrayList<>();
            for (AdminMessage msg : messages) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("id", msg.getId());
                com.uhn.pmb.entity.User sender = msg.getSender();
                Long senderId = sender != null ? sender.getId() : null;
                String senderEmail = sender != null ? sender.getEmail() : "Unknown";
                String senderType = (sender != null && sender.getRole() == com.uhn.pmb.entity.User.UserRole.CAMABA) ? "STUDENT" : "ADMIN";
                msgMap.put("senderId", senderId);
                msgMap.put("senderEmail", senderEmail);
                msgMap.put("senderType", senderType);
                msgMap.put("senderRole", sender != null ? sender.getRole() : null);
                com.uhn.pmb.entity.User recipient = msg.getRecipient();
                msgMap.put("recipientId", recipient != null ? recipient.getId() : null);
                msgMap.put("recipientEmail", recipient != null ? recipient.getEmail() : "Unknown");
                msgMap.put("messageContent", msg.getMessageContent());
                msgMap.put("messageType", msg.getMessageType());
                msgMap.put("status", msg.getStatus().toString());
                msgMap.put("createdAt", msg.getCreatedAt());
                msgMap.put("readAt", msg.getReadAt());
                responseMsgs.add(msgMap);
            }
            return ResponseEntity.ok(responseMsgs);
        } catch (Exception e) {
            log.error("❌ Error fetching messages: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/api/messages/send-to-student/{studentEmail}")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> sendMessageToStudent(@PathVariable String studentEmail,
                                                   @RequestBody SendMessageRequest request) {
        try {
            String adminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            com.uhn.pmb.entity.User admin = adminMessagingService.getUserByEmail(adminEmail);
            AdminMessage message = adminMessagingService.sendToStudent(admin, studentEmail, request);
            log.info("✅ Message sent from admin {} to student {}", adminEmail, studentEmail);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Pesan berhasil dikirim ke " + studentEmail);
            result.put("messageId", message.getId());
            result.put("sentAt", message.getCreatedAt());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error sending message: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    @PostMapping("/api/messages/mark-all-read")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT')")
    public ResponseEntity<?> markAllMessagesAsRead() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            com.uhn.pmb.entity.User currentAdmin = adminMessagingService.getUserByEmail(auth.getName());
            int count = adminMessagingService.markAllRead(currentAdmin.getId());
            log.info("✅ Marked {} messages as read for admin {}", count, auth.getName());
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Semua pesan telah ditandai sebagai dibaca");
            result.put("markedCount", count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error marking messages as read: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/api/send-reminder")
    @PreAuthorize("hasAnyRole('ADMIN_VALIDASI', 'ADMIN_PUSAT', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<?> sendReminderToStudent(@RequestBody SendReminderRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            log.info("🔐 [REMINDER] Authenticated as: {}, Authorities: {}", auth != null ? auth.getName() : "null", auth != null ? auth.getAuthorities() : "NONE");
            if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
                log.info("   Granted Authorities: {}", ud.getAuthorities());
            }
            emailService.sendHtmlEmail(request.getStudentEmail(), request.getMessageTitle(), request.getMessageBody());
            log.info("✅ Reminder sent to {}", request.getStudentEmail());
            return ResponseEntity.ok(new ApiResponse(true, "Reminder berhasil dikirim ke " + request.getStudentEmail()));
        } catch (Exception e) {
            log.error("❌ Error sending reminder: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Gagal mengirim reminder: " + e.getMessage()));
        }
    }
}