/*
 * Production notification service — would dispatch to a real external system.
 *
 * Active only when the "prod" profile is enabled. The @Profile("!dev") expression
 * would work equally well here, but being explicit about "prod" is safer — it
 * avoids accidental activation in test or staging environments.
 */
package com.example.profiles.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("prod")
public class ProdNotificationService implements NotificationService {

    @Override
    public void send(String message) {
        // In a real application: call an email or push notification API here.
        System.out.println("[PROD] Dispatching notification: " + message);
    }
}
