package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentValidationRequest {
    private String action; // APPROVE, REJECT, REVISION_NEEDED
    private String adminNotes;
}