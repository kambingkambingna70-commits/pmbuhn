package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JenisSeleksiRequest {
    private String code;
    private String nama;
    private String deskripsi;
    private String fasilitas;
    private String logoUrl;
    private BigDecimal harga;
    private Boolean isActive;
    private Integer sortOrder;
    private List<Long> programStudiIds;
}