package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgramStudiRequest {
    private String kode;
    private String nama;
    private String deskripsi;
    private Boolean isMedical;
    private Boolean isActive;
    private Integer sortOrder;
    private Long hargaTotalPerTahun;
    private Long cicilan1;
    private Long cicilan2;
    private Long cicilan3;
    private Long cicilan4;
    private Long cicilan5;
    private Long cicilan6;
}   