package com.hms.notification.repository;

import com.hms.notification.entity.Notification;
import com.hms.notification.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndStatus(Long recipientId, NotificationStatus status, Pageable pageable);

    long countByRecipientIdAndStatus(Long recipientId, NotificationStatus status);
}
