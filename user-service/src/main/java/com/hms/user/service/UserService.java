package com.hms.user.service;

import com.hms.user.dto.request.AdminCreateUserRequest;
import com.hms.user.dto.request.ChangePasswordRequest;
import com.hms.user.dto.request.ChangeRoleRequest;
import com.hms.user.dto.request.UpdateProfileRequest;
import com.hms.user.dto.response.PagedResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.enums.Role;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserResponse getById(Long id);

    UserResponse getByUuid(UUID uuid);

    UserResponse getMyProfile(Long authenticatedUserId);

    UserResponse updateProfile(Long authenticatedUserId, UpdateProfileRequest request);

    void changePassword(Long authenticatedUserId, ChangePasswordRequest request);

    void deactivateUser(Long targetUserId, Long requestingUserId);

    void activateUser(Long targetUserId);

    PagedResponse<UserResponse> getAllUsers(String search, Role role,
                                           Boolean active, Pageable pageable);

    PagedResponse<UserResponse> getDoctors(String specialization,
                                           String department, Pageable pageable);

    // ── Admin use cases ────────────────────────────────────────────────────────

    UserResponse adminCreateUser(AdminCreateUserRequest request, Long adminId);

    UserResponse changeUserRole(Long targetUserId, ChangeRoleRequest request, Long adminId);

    void adminResetPassword(Long targetUserId, String newPassword, Long adminId);
}
