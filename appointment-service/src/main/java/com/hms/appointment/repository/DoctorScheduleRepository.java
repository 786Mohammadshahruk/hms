package com.hms.appointment.repository;

import com.hms.appointment.entity.DoctorSchedule;
import com.hms.appointment.enums.ScheduleDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    List<DoctorSchedule> findByDoctorIdAndActiveTrue(Long doctorId);

    Optional<DoctorSchedule> findByDoctorIdAndDayOfWeek(Long doctorId, ScheduleDay day);

    boolean existsByDoctorIdAndDayOfWeek(Long doctorId, ScheduleDay day);
}
