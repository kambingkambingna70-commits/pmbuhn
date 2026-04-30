package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExamValidationRequest {
    private String action; // APPROVE, REJECT, REVISI
    private String adminNotes;
}