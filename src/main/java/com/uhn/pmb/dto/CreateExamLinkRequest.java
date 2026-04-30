package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateExamLinkRequest {
    private Long periodId;
    private Long selectionTypeId;
    private String linkTitle;
    private String linkUrl;
    private String description;
}