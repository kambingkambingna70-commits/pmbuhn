package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReenrollmentFinalizeRequest {
    private String action; // APPROVE, REJECT
    private String validationNotes;
}