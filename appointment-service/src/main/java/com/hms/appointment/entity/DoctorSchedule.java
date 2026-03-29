package com.hms.appointment.entity;

import com.hms.appointment.enums.ScheduleDay;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Weekly recurring schedule for a doctor.
 * Used to derive available time slots for a given date.
 */
@Entity
@Table(name = "doctor_schedules",
       uniqueConstraints = @UniqueConstraint(columnNames = {"doctor_id", "day_of_week"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private ScheduleDay dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Duration of each appointment slot in minutes. */
    @Builder.Default
    @Column(name = "slot_duration", nullable = false)
    private Integer slotDuration = 30;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
