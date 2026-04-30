package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HasilAkhirRegistrationRequest {
    private String nomorRegistrasi;
    private String brivaNumber;
    private Integer jumlahCicilan;
}