package com.hms.user.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Extended profile for PATIENT-role users.
 * One-to-one with User; stored in patient_profiles table.
 */
@Entity
@Table(name = "patient_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Column(name = "emergency_contact", length = 20)
    private String emergencyContact;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(name = "chronic_conditions", columnDefinition = "TEXT")
    private String chronicConditions;

    @Column(name = "insurance_provider", length = 150)
    private String insuranceProvider;

    @Column(name = "insurance_number", length = 100)
    private String insuranceNumber;
}
