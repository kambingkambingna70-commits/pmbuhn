package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSelectionTypeRequest {
    private String name;
    private String description;
    private Boolean requireRanking;
    private Boolean requireTesting;
    private BigDecimal price;
    private Boolean isActive;
}