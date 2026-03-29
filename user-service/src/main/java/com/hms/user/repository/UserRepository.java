package com.hms.user.repository;

import com.hms.user.entity.User;
import com.hms.user.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUuid(UUID uuid);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Page<User> findAllByRole(Role role, Pageable pageable);

    Page<User> findAllByActiveTrue(Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE (:role IS NULL OR u.role = :role)
          AND (:active IS NULL OR u.active = :active)
          AND (
              LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :search, '%'))
          )
        """)
    Page<User> searchUsers(@Param("search") String search,
                           @Param("role")   Role role,
                           @Param("active") Boolean active,
                           Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.active = true")
    long countActiveByRole(@Param("role") Role role);
}
