package com.uhn.pmb.service;

import com.uhn.pmb.entity.PublicationSchedule;
import com.uhn.pmb.entity.RegistrationPeriod;
import com.uhn.pmb.repository.PublicationScheduleRepository;
import com.uhn.pmb.repository.RegistrationPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicationScheduleService {

    private final PublicationScheduleRepository publicationScheduleRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;

    public List<Map<String, Object>> getAllSchedules() {
        List<PublicationSchedule> schedules = publicationScheduleRepository.findAllByOrderByPublishDateTimeDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (PublicationSchedule s : schedules) {
            result.add(toMap(s));
        }
        return result;
    }

    public Map<String, Object> getScheduleByPeriod(Long periodId) {
        Optional<PublicationSchedule> schedule = publicationScheduleRepository.findByPeriodId(periodId);
        if (schedule.isEmpty()) {
            return Map.of("exists", false);
        }
        Map<String, Object> map = toMap(schedule.get());
        map.put("exists", true);
        return map;
    }

    public Map<String, Object> createOrUpdate(Long periodId, String publishDateTimeStr, String notes, String createdBy) {
        RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period not found"));
        LocalDateTime publishDateTime = LocalDateTime.parse(publishDateTimeStr);

        PublicationSchedule schedule = publicationScheduleRepository.findByPeriodId(periodId)
                .orElse(PublicationSchedule.builder()
                        .period(period)
                        .isPublished(false)
                        .build());

        schedule.setPublishDateTime(publishDateTime);
        schedule.setNotes(notes);
        schedule.setCreatedBy(createdBy);

        if (LocalDateTime.now().isAfter(publishDateTime) && !Boolean.TRUE.equals(schedule.getIsPublished())) {
            schedule.setIsPublished(true);
            schedule.setPublishedAt(LocalDateTime.now());
        }

        schedule = publicationScheduleRepository.save(schedule);
        log.info("Schedule saved for period {} at {}", period.getName(), publishDateTime);
        return Map.of("success", true, "message", "Jadwal publikasi berhasil disimpan", "id", schedule.getId());
    }

    public Map<String, Object> publishNow(Long periodId, String createdBy) {
        RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period not found"));
        PublicationSchedule schedule = publicationScheduleRepository.findByPeriodId(periodId)
                .orElse(PublicationSchedule.builder()
                        .period(period)
                        .publishDateTime(LocalDateTime.now())
                        .build());
        schedule.setIsPublished(true);
        schedule.setPublishedAt(LocalDateTime.now());
        schedule.setCreatedBy(createdBy);
        publicationScheduleRepository.save(schedule);
        log.info("Results published NOW for period {} by {}", period.getName(), createdBy);
        return Map.of("success", true, "message", "Hasil kelulusan berhasil dipublikasikan");
    }

    public void deleteSchedule(Long id) {
        publicationScheduleRepository.deleteById(id);
        log.info("Schedule {} deleted", id);
    }

    private Map<String, Object> toMap(PublicationSchedule s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("periodId", s.getPeriod().getId());
        map.put("periodName", s.getPeriod().getName());
        map.put("publishDateTime", s.getPublishDateTime().toString());
        map.put("isPublished", s.getIsPublished());
        map.put("publishedAt", s.getPublishedAt() != null ? s.getPublishedAt().toString() : null);
        map.put("createdBy", s.getCreatedBy());
        map.put("notes", s.getNotes());
        map.put("resultsVisible", s.isResultsVisible());
        return map;
    }
}