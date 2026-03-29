package com.hms.appointment.repository;

import com.hms.appointment.entity.BlockedSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, Long> {

    List<BlockedSlot> findByDoctorIdAndBlockedDate(Long doctorId, LocalDate date);

    /**
     * Checks if a given time range overlaps any existing blocked slot.
     */
    @Query("""
        SELECT bs FROM BlockedSlot bs
        WHERE bs.doctorId = :doctorId
          AND bs.blockedDate = :date
          AND bs.startTime < :endTime
          AND bs.endTime > :startTime
        """)
    List<BlockedSlot> findOverlapping(
            @Param("doctorId")  Long      doctorId,
            @Param("date")      LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime")   LocalTime endTime);
}
