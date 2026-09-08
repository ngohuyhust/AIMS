package vn.aims.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods=false) @EnableScheduling
@ConditionalOnProperty(name="NOTIFICATIONS_ENABLED",havingValue="true")
public class NotificationWorker {
    private final NotificationDispatcher dispatcher;
    public NotificationWorker(NotificationDispatcher dispatcher) {this.dispatcher=dispatcher;}
    @Scheduled(fixedDelayString="${NOTIFICATION_POLL_MS:5000}")
    public void poll() {try {dispatcher.dispatchOne();} catch(Exception e) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Notification dispatch unavailable; retry on next poll");}}
}
