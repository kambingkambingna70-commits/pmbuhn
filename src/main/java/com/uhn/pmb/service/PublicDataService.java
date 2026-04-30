package com.uhn.pmb.service;

import com.uhn.pmb.entity.JenisSeleksi;
import com.uhn.pmb.entity.ProgramStudi;
import com.uhn.pmb.entity.PublicationSchedule;
import com.uhn.pmb.entity.RegistrationPeriod;
import com.uhn.pmb.repository.JenisSeleksiRepository;
import com.uhn.pmb.repository.ProgramStudiRepository;
import com.uhn.pmb.repository.PublicationScheduleRepository;
import com.uhn.pmb.repository.RegistrationPeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicDataService {

    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final ProgramStudiRepository programStudiRepository;
    private final PublicationScheduleRepository publicationScheduleRepository;

    public List<RegistrationPeriod> getAllGelombang() {
        return registrationPeriodRepository.findAll().stream()
                .sorted(Comparator.comparing(RegistrationPeriod::getRegStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public List<JenisSeleksi> getAllActiveJenisSeleksi() {
        return jenisSeleksiRepository.findAll().stream()
                .filter(js -> js.getIsActive() == null || js.getIsActive())
                .sorted(Comparator.comparing(js -> js.getSortOrder() != null ? js.getSortOrder() : 999))
                .collect(Collectors.toList());
    }

    public List<ProgramStudi> getAllActiveProgramStudi() {
        return programStudiRepository.findAll().stream()
                .filter(ps -> ps.getIsActive() == null || ps.getIsActive())
                .sorted(Comparator.comparing(ps -> ps.getSortOrder() != null ? ps.getSortOrder() : 999))
                .collect(Collectors.toList());
    }

    public Optional<ProgramStudi> getProgramStudiById(Long id) {
        return programStudiRepository.findById(id);
    }

    public Map<String, Object> getPublicationStatus(Long periodId) {
        Optional<PublicationSchedule> schedule = publicationScheduleRepository.findByPeriodId(periodId);
        Map<String, Object> result = new HashMap<>();
        if (schedule.isPresent()) {
            PublicationSchedule s = schedule.get();
            result.put("hasSchedule", true);
            result.put("publishDateTime", s.getPublishDateTime().toString());
            result.put("isPublished", s.getIsPublished());
            result.put("resultsVisible", s.isResultsVisible());
        } else {
            result.put("hasSchedule", false);
            result.put("resultsVisible", false);
        }
        return result;
    }

    public List<String> getAllFakultas() {
        return programStudiRepository.findDistinctFakultasActive();
    }

    public Map<String, List<Map<String, Object>>> getProgramStudiByFakultas() {
        List<ProgramStudi> allActive = programStudiRepository.findByIsActiveTrueOrderBySortOrder();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (ProgramStudi ps : allActive) {
            String fak = ps.getFakultas() != null ? ps.getFakultas() : "Lainnya";
            grouped.computeIfAbsent(fak, k -> new ArrayList<>());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ps.getId());
            item.put("kode", ps.getKode());
            item.put("nama", ps.getNama());
            item.put("fakultas", fak);
            item.put("hargaTotalPerTahun", ps.getHargaTotalPerTahun());
            grouped.get(fak).add(item);
        }
        return grouped;
    }
}