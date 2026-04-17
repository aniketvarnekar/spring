/*
 * Development notification service — logs to console instead of sending real notifications.
 *
 * Active only when the "dev" profile is enabled. Using @Profile on a @Component class
 * prevents this bean from being registered in any other environment, which avoids
 * accidentally sending development-noise to real notification channels.
 */
package com.example.profiles.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class DevNotificationService implements NotificationService {

    @Override
    public void send(String message) {
        System.out.println("[DEV] Notification (not sent): " + message);
    }
}
