package com.uhn.pmb.dto;

import com.uhn.pmb.entity.SelectionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SelectionTypeRequest {
    private Long periodId;
    private String name;
    private String description;
    private Boolean requireRanking;
    private Boolean requireTesting;
    private SelectionType.FormType formType;
    private BigDecimal price;
}