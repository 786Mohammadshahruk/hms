package com.hms.appointment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Ad-hoc time blocks on specific dates (e.g., doctor on leave, lunch break).
 * These override the regular DoctorSchedule when computing available slots.
 */
@Entity
@Table(name = "blocked_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "blocked_date", nullable = false)
    private LocalDate blockedDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(length = 255)
    private String reason;
}
