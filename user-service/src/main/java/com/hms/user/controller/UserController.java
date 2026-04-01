package com.hms.user.controller;

import com.hms.user.dto.request.AdminCreateUserRequest;
import com.hms.user.dto.request.AdminResetPasswordRequest;
import com.hms.user.dto.request.ChangePasswordRequest;
import com.hms.user.dto.request.ChangeRoleRequest;
import com.hms.user.dto.request.UpdateProfileRequest;
import com.hms.user.dto.response.ApiResponse;
import com.hms.user.dto.response.PagedResponse;
import com.hms.user.dto.response.UserResponse;
import com.hms.user.enums.Role;
import com.hms.user.security.UserDetailsImpl;
import com.hms.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User controller — profile retrieval, updates, password change,
 * admin operations, and paginated user/doctor listings.
 *
 * <p>All endpoints require a valid JWT except GET /doctors (public directory).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile management and admin operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ── My profile ─────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get authenticated user's own profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        UserResponse profile = userService.getMyProfile(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile fetched", profile));
    }

    @PutMapping("/me")
    @Operation(summary = "Update authenticated user's profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse updated = userService.updateProfile(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Change authenticated user's password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetailsImpl currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    // ── Get by ID / UUID ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get user by internal ID (admin or self)")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.success("User fetched", userService.getById(id)));
    }

    @GetMapping("/uuid/{uuid}")
    @Operation(summary = "Get user by UUID")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','CASHIER')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUuid(
            @PathVariable UUID uuid) {

        return ResponseEntity.ok(
            ApiResponse.success("User fetched", userService.getByUuid(uuid)));
    }

    // ── Doctors (public directory) ─────────────────────────────────────────────

    @GetMapping("/doctors")
    @Operation(summary = "List all active doctors (public — no auth required)",
               description = "Supports filtering by specialization and department. Paginated.")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getDoctors(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<UserResponse> doctors =
            userService.getDoctors(specialization, department, pageable);
        return ResponseEntity.ok(ApiResponse.success("Doctors fetched", doctors));
    }

    // ── Admin operations ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all users with optional search/filter (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @RequestParam(required = false)  String  search,
            @RequestParam(required = false)  Role    role,
            @RequestParam(required = false)  Boolean active,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {

        Pageable pageable = PageRequest.of(page, size,
            Sort.by(Sort.Direction.DESC, sortBy));
        PagedResponse<UserResponse> users =
            userService.getAllUsers(search, role, active, pageable);
        return ResponseEntity.ok(ApiResponse.success("Users fetched", users));
    }

    @PatchMapping("/admin/{id}/deactivate")
    @Operation(summary = "Deactivate a user account (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        userService.deactivateUser(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully"));
    }

    @PatchMapping("/admin/{id}/activate")
    @Operation(summary = "Re-activate a user account (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable Long id) {
        userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User activated successfully"));
    }

    @PostMapping("/admin/create")
    @Operation(summary = "Create a user with any role (admin only)",
               description = "Admins can create users with DOCTOR, CASHIER, ADMIN, or PATIENT roles.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> adminCreateUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        UserResponse created = userService.adminCreateUser(request, currentUser.getId());
        return ResponseEntity.status(201).body(
            ApiResponse.success("User created successfully", created));
    }

    @PatchMapping("/admin/{id}/role")
    @Operation(summary = "Change a user's role (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        UserResponse updated = userService.changeUserRole(id, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", updated));
    }

    @PostMapping("/admin/{id}/reset-password")
    @Operation(summary = "Reset a user's password (admin only)",
               description = "Admin sets a new password for the target user without needing the current one.")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adminResetPassword(
            @PathVariable Long id,
            @Valid @RequestBody AdminResetPasswordRequest request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        userService.adminResetPassword(id, request.getNewPassword(), currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }
}
