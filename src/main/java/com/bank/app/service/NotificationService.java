package com.bank.app.service;

import com.bank.app.dto.NotificationResponse;
import com.bank.app.entity.Notification;
import com.bank.app.entity.User;
import com.bank.app.exception.ResourceNotFoundException;
import com.bank.app.exception.UnauthorizedException;
import com.bank.app.repository.NotificationRepository;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void createNotification(User user, Notification.NotificationType type, String message) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .read(false)
                .build();
        notificationRepository.save(notification);
    }

    public List<NotificationResponse> getUserNotifications(String userEmail, boolean unreadOnly) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserAndReadOrderByCreatedAtDesc(user, false)
                : notificationRepository.findByUserOrderByCreatedAtDesc(user);

        return notifications.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public long getUnreadCount(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return notificationRepository.countUnread(user);
    }

    public void markAsRead(String userEmail, Long notificationId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied to this notification");
        }

        if (!notification.getRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    public void markAllAsRead(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Notification> notifications = notificationRepository.findByUserAndReadOrderByCreatedAtDesc(user, false);
        if (notifications.isEmpty()) return;
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .type(notification.getType())
                .read(Boolean.TRUE.equals(notification.getRead()))
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
