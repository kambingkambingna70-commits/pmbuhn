package com.uhn.pmb.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Sma represents a high school (SMA/SMK/MA) record that can be managed by admin
 * and presented as autocomplete in the registration form.
 */
@Entity
@Table(name = "sma", indexes = {
    @Index(name = "idx_sma_nama", columnList = "nama"),
    @Index(name = "idx_sma_npsn", columnList = "npsn", unique = true)
})
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sma {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nama;

    @Column(length = 20)
    private String bentuk; // SMA, SMK, MA, SMP, dsb.

    @Column(length = 20, unique = true)
    private String npsn; // Nomor Pokok Sekolah Nasional

    @Column(length = 100)
    private String kota;

    @Column(length = 100)
    private String provinsi;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
