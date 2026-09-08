package vn.aims.notification;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class NotificationDispatcher {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final List<NotificationProvider> providers;
    public NotificationDispatcher(JdbcTemplate jdbc,ObjectMapper json,List<NotificationProvider> providers) {this.jdbc=jdbc;this.json=json;this.providers=providers;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean dispatchOne() {
        var ready=providers.stream().filter(NotificationProvider::available).toList();if(ready.isEmpty())return false;
        var rows=jdbc.queryForList("SELECT event_id,channel,payload::text,attempts FROM notification_outbox WHERE status='PENDING' AND available_at<=now() AND channel IN ("+String.join(",",Collections.nCopies(ready.size(),"?"))+") ORDER BY created_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED",ready.stream().map(NotificationProvider::channel).toArray());
        if(rows.isEmpty())return false;var row=rows.getFirst();Object id=row.get("event_id");int attempts=((Number)row.get("attempts")).intValue()+1;
        try {
            var payload=json.readTree((String)row.get("payload"));
            if(payload.path("delivery").path("email").asText("").isBlank()) {jdbc.update("UPDATE notification_outbox SET status='SKIPPED',completed_at=now(),last_error='NO_RECIPIENT' WHERE event_id=?",id);return true;}
            ready.stream().filter(p->p.channel().equals(row.get("channel"))).findFirst().orElseThrow().send(payload);
            jdbc.update("UPDATE notification_outbox SET status='SENT',attempts=?,completed_at=now(),last_error=NULL WHERE event_id=?",attempts,id);
        } catch(Exception e) {
            boolean retry=!(e instanceof NotificationProvider.DeliveryFailure failure) || failure.retryable;
            String error=e instanceof NotificationProvider.DeliveryFailure?e.getMessage():"DELIVERY_ERROR";
            if(error==null || !error.matches("[A-Z0-9_]{1,80}"))error="DELIVERY_ERROR";
            jdbc.update("UPDATE notification_outbox SET status=?,attempts=?,last_error=?,available_at=now()+(? * interval '1 second'),completed_at=CASE WHEN ? THEN now() ELSE NULL END WHERE event_id=?",retry && attempts<6?"PENDING":"FAILED",attempts,error,Math.min(3600,30*(1<<Math.min(attempts,6))),!retry || attempts>=6,id);
        }
        return true;
    }
}
