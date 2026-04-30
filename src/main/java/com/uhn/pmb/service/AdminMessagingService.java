package com.uhn.pmb.service;

import com.uhn.pmb.dto.SendMessageRequest;
import com.uhn.pmb.entity.AdminMessage;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.repository.AdminMessageRepository;
import com.uhn.pmb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMessagingService {

    private final AdminMessageRepository adminMessageRepository;
    private final UserRepository userRepository;

    public Long countUnread(Long userId) {
        return adminMessageRepository.countUnreadMessages(userId);
    }

    public List<AdminMessage> findAll() {
        return adminMessageRepository.findAll();
    }

    public List<AdminMessage> findConversation(Long userA, Long userB) {
        return adminMessageRepository.findConversationBetween(userA, userB);
    }

    public AdminMessage send(User sender, SendMessageRequest request) {
        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new RuntimeException("Recipient tidak ditemukan"));

        AdminMessage msg = AdminMessage.builder()
                .sender(sender)
                .recipient(recipient)
                .messageContent(request.getMessageContent())
                .messageType(request.getMessageType() != null ? request.getMessageType() : "QUESTION")
                .admissionFormId(request.getAdmissionFormId())
                .status(AdminMessage.MessageStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();

        adminMessageRepository.save(msg);
        log.info("✅ Message sent from {} to {}", sender.getEmail(), recipient.getEmail());
        return msg;
    }

    public AdminMessage sendToStudent(User admin, String studentEmail, SendMessageRequest request) {
        if (request.getMessageContent() == null || request.getMessageContent().trim().length() < 5) {
            throw new RuntimeException("Pesan minimal 5 karakter");
        }

        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentEmail));

        AdminMessage msg = AdminMessage.builder()
                .sender(admin)
                .recipient(student)
                .messageContent(request.getMessageContent().trim())
                .messageType(request.getMessageType() != null ? request.getMessageType() : "ANSWER")
                .status(AdminMessage.MessageStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();

        adminMessageRepository.save(msg);
        log.info("✅ Admin {} sent message to student {}", admin.getEmail(), studentEmail);
        return msg;
    }

    public int markAllRead(Long recipientId) {
        List<AdminMessage> unread = adminMessageRepository.findAll().stream()
                .filter(m -> m.getRecipient().getId().equals(recipientId)
                        && m.getStatus() == AdminMessage.MessageStatus.UNREAD)
                .toList();
        unread.forEach(m -> {
            m.setStatus(AdminMessage.MessageStatus.READ);
            m.setReadAt(LocalDateTime.now());
            adminMessageRepository.save(m);
        });
        log.info("✅ Marked {} messages as read for user {}", unread.size(), recipientId);
        return unread.size();
    }

    public void markConversationRead(List<AdminMessage> messages, Long currentUserId) {
        messages.forEach(msg -> {
            if (msg.getRecipient().getId().equals(currentUserId)
                    && msg.getStatus() == AdminMessage.MessageStatus.UNREAD) {
                msg.setStatus(AdminMessage.MessageStatus.READ);
                msg.setReadAt(LocalDateTime.now());
                adminMessageRepository.save(msg);
            }
        });
    }

    public com.uhn.pmb.entity.User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan: " + email));
    }
}