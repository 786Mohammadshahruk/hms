package com.hms.user.enums;

/**
 * Roles supported in the HMS system.
 * Used for JWT claims and Spring Security role-based access control.
 */
public enum Role {
    PATIENT,
    DOCTOR,
    CASHIER,
    ADMIN
}
