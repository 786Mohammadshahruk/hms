package com.hms.appointment.repository;

import com.hms.appointment.entity.Appointment;
import com.hms.appointment.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByUuid(UUID uuid);

    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    Page<Appointment> findByDoctorId(Long doctorId, Pageable pageable);

    Page<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status,
                                               Pageable pageable);

    Page<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate date,
                                                        Pageable pageable);

    /**
     * Checks for overlapping appointments for the same doctor on the same date.
     * Used for conflict resolution during booking.
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.doctorId = :doctorId
          AND a.appointmentDate = :date
          AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
          AND a.startTime < :endTime
          AND a.endTime > :startTime
        """)
    List<Appointment> findConflicting(
            @Param("doctorId")   Long      doctorId,
            @Param("date")       LocalDate date,
            @Param("startTime")  LocalTime startTime,
            @Param("endTime")    LocalTime endTime);

    /**
     * Finds all booked slots for a doctor on a specific date (for slot availability).
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.doctorId = :doctorId
          AND a.appointmentDate = :date
          AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
        """)
    List<Appointment> findBookedSlotsForDate(
            @Param("doctorId") Long      doctorId,
            @Param("date")     LocalDate date);

    /**
     * Finds upcoming appointments for reminder scheduling.
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.appointmentDate = :date
          AND a.status IN ('SCHEDULED', 'CONFIRMED')
        """)
    List<Appointment> findUpcomingForDate(@Param("date") LocalDate date);

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.doctorId = :doctorId
          AND a.appointmentDate = :date
          AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
        """)
    long countByDoctorIdAndDate(
            @Param("doctorId") Long      doctorId,
            @Param("date")     LocalDate date);
}
