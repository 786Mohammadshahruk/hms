package com.hms.notification.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private Long   userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}
