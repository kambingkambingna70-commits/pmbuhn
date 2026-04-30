package com.uhn.pmb.service;

import com.uhn.pmb.dto.ApproveExamQuestionRequest;
import com.uhn.pmb.dto.GenerateExamQuestionRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.ExamQuestionRepository;
import com.uhn.pmb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminExamService {

    private final ExamQuestionRepository examQuestionRepository;
    private final UserRepository userRepository;
    private final GeminiAIService geminiAIService;

    public boolean isApiKeyConfigured() {
        return geminiAIService.isApiKeyConfigured();
    }

    public List<ExamQuestion> generateQuestions(GenerateExamQuestionRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
        List<ExamQuestion> questions = geminiAIService.generateMultipleExamQuestions(request, user);
        return examQuestionRepository.saveAll(questions);
    }

    public List<ExamQuestion> getPendingQuestions() {
        return examQuestionRepository.findAllPending();
    }

    public List<ExamQuestion> getQuestionsByCategory(String category) {
        QuestionCategory cat = QuestionCategory.valueOf(category.toUpperCase());
        return examQuestionRepository.findByCategoryApproved(cat);
    }

    public ExamQuestion approveQuestion(Long id, ApproveExamQuestionRequest request, String email) {
        ExamQuestion question = examQuestionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Soal tidak ditemukan"));
        User approver = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

        if (request.isApproved()) {
            question.setApprovalStatus(ApprovalStatus.APPROVED);
            question.setApprovedAt(LocalDateTime.now());
            question.setApprovedBy(approver);
        } else {
            question.setApprovalStatus(ApprovalStatus.REJECTED);
            question.setRejectionReason(request.getRejectionReason());
        }
        return examQuestionRepository.save(question);
    }

    public void deleteQuestion(Long id) {
        if (!examQuestionRepository.existsById(id)) {
            throw new RuntimeException("Soal tidak ditemukan");
        }
        examQuestionRepository.deleteById(id);
    }

    public ExamQuestion getQuestion(Long id) {
        return examQuestionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Soal tidak ditemukan"));
    }

    public List<ExamQuestion> getAllQuestions(String status) {
        if (status != null && !status.isEmpty()) {
            ApprovalStatus approvalStatus = ApprovalStatus.valueOf(status.toUpperCase());
            return examQuestionRepository.findByApprovalStatus(approvalStatus);
        }
        return examQuestionRepository.findAll();
    }

    public Map<String, Object> getStatistics() {
        Long pendingCount = examQuestionRepository.countPendingQuestions();
        List<ExamQuestion> approved = examQuestionRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
        List<ExamQuestion> rejected = examQuestionRepository.findByApprovalStatus(ApprovalStatus.REJECTED);
        return Map.of(
                "pending", pendingCount,
                "approved", approved.size(),
                "rejected", rejected.size(),
                "total", approved.size() + rejected.size() + pendingCount
        );
    }
}