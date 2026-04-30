package com.uhn.pmb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendReminderRequest {
    private String studentEmail;
    private String studentName;
    private String messageTitle;
    private String messageBody;
    private String reminderType;
    private Long formulirId;
}