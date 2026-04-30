package com.uhn.pmb.service;

import com.uhn.pmb.entity.Student;
import com.uhn.pmb.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final StudentRepository studentRepository;

    public List<Map<String, Object>> getAllStudents() {
        log.info("Fetching all students");
        List<Student> all = studentRepository.findAll();
        return all.stream().map(student -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", student.getId());
            data.put("fullName", student.getFullName());
            data.put("email", student.getUser() != null ? student.getUser().getEmail() : "");
            data.put("phoneNumber", student.getPhoneNumber());
            data.put("nik", student.getNik());
            data.put("gender", student.getGender());
            data.put("createdAt", student.getCreatedAt());
            return data;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getStudentById(Long id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found: " + id));
        Map<String, Object> data = new HashMap<>();
        data.put("id", student.getId());
        data.put("fullName", student.getFullName());
        data.put("email", student.getUser() != null ? student.getUser().getEmail() : "");
        data.put("phoneNumber", student.getPhoneNumber());
        data.put("nik", student.getNik());
        data.put("gender", student.getGender());
        data.put("birthDate", student.getBirthDate());
        data.put("birthPlace", student.getBirthPlace());
        return data;
    }
}