package vn.aims.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.aims.notification.event.*;
import vn.aims.notification.provider.*;
import vn.aims.notification.service.*;

@Component
public class SendGridEmailProvider implements NotificationProvider {
    private final EmailTemplateService templates;private final String from,name,key;private final boolean enabled;
    private final SendGrid sendgrid;private final org.apache.http.impl.client.CloseableHttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public SendGridEmailProvider(EmailTemplateService templates,@Value("${SENDGRID_API_KEY:}") String key,@Value("${SENDGRID_FROM_EMAIL:}") String from,
        @Value("${SENDGRID_FROM_NAME:AIMS Store}") String name,@Value("${NOTIFICATIONS_ENABLED:false}") boolean enabled) {
        this.templates=templates;this.key=key;this.from=from;this.name=name;this.enabled=enabled;
        http=org.apache.http.impl.client.HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
            .setDefaultRequestConfig(org.apache.http.client.config.RequestConfig.custom().setConnectTimeout(3000).setConnectionRequestTimeout(3000).setSocketTimeout(10000).build()).build();
        sendgrid=new SendGrid(key,new Client(http));
    }
    public SendGridEmailProvider(EmailTemplateService templates,SendGrid client) {this.templates=templates;sendgrid=client;http=null;key="synthetic-key";from="sender@example.test";name="AIMS Store";enabled=true;}
    @jakarta.annotation.PreDestroy public void close() throws java.io.IOException {if(http!=null)http.close();}
    @Override public String channel() {return "EMAIL";}
    @Override public boolean available() {return enabled && !key.isBlank() && !from.isBlank();}
    @Override public void send(JsonNode snapshot) throws DeliveryFailure {
        if(!available())throw new DeliveryFailure("NOT_CONFIGURED",true);
        var email=templates.render(snapshot);var mail=new Mail();mail.setFrom(new com.sendgrid.helpers.mail.objects.Email(from,name));mail.setSubject(email.subject());
        var personalization=new Personalization();personalization.addTo(new com.sendgrid.helpers.mail.objects.Email(snapshot.path("delivery").path("email").asText()));mail.addPersonalization(personalization);
        mail.addContent(new Content("text/plain",email.text()));mail.addContent(new Content("text/html",email.html()));
        var request=new Request();request.setMethod(Method.POST);request.setEndpoint("mail/send");
        try {
            request.setBody(mail.build());int status=sendgrid.api(request).getStatusCode();
            if(status<200 || status>=300)throw new DeliveryFailure("HTTP_"+status,status==429 || status>=500);
        }catch(java.io.IOException e){throw new DeliveryFailure("TRANSPORT_ERROR",true);}
    }
}
