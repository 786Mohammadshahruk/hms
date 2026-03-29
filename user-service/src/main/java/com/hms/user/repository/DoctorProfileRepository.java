package com.hms.user.repository;

import com.hms.user.entity.DoctorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {

    Optional<DoctorProfile> findByUserId(Long userId);

    boolean existsByLicenseNumber(String licenseNumber);

    @Query("""
        SELECT dp FROM DoctorProfile dp
        JOIN dp.user u
        WHERE u.active = true
          AND (:specialization IS NULL
               OR LOWER(dp.specialization) LIKE LOWER(CONCAT('%', :specialization, '%')))
          AND (:department IS NULL
               OR LOWER(dp.department) LIKE LOWER(CONCAT('%', :department, '%')))
        """)
    Page<DoctorProfile> findBySpecializationAndDepartment(
            @Param("specialization") String specialization,
            @Param("department")     String department,
            Pageable pageable);
}
