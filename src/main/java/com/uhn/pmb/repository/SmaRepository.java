package com.uhn.pmb.repository;

import com.uhn.pmb.entity.Sma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SmaRepository extends JpaRepository<Sma, Long> {

    List<Sma> findByIsActiveTrue();

    @Query("SELECT s FROM Sma s WHERE s.isActive = true AND " +
           "(LOWER(s.nama) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.kota) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.provinsi) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Sma> searchByQuery(@Param("query") String query);

    Optional<Sma> findByNpsn(String npsn);

    boolean existsByNpsn(String npsn);
}
