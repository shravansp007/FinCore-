package com.bank.app.repository;

import com.bank.app.entity.User;
import com.bank.app.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByUserAndDeviceHash(User user, String deviceHash);
}
