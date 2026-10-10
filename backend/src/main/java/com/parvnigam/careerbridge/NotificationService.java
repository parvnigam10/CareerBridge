package com.parvnigam.careerbridge;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

@Service
public class NotificationService {
    final Repositories.Notices notices;final Repositories.Accounts accounts;final MailDelivery delivery;
    public NotificationService(Repositories.Notices notices,Repositories.Accounts accounts,MailDelivery delivery) {this.notices=notices;this.accounts=accounts;this.delivery=delivery;}
    public void send(Long uid,String title,String message,String link) {Model.Notice n=new Model.Notice();n.userId=uid;n.title=title;n.message=message;n.link=link;notices.save(n);accounts.findById(uid).ifPresent(a->delivery.send(a.email,title,message));}
}
@Service
class MailDelivery {
    final JavaMailSender sender;final boolean enabled;final String from;
    MailDelivery(JavaMailSender sender,@Value("${app.mail-enabled}") boolean enabled,@Value("${app.mail-from}") String from){this.sender=sender;this.enabled=enabled;this.from=from;}
    @Async public void send(String to,String subject,String body){if(!enabled)return;try{SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(to);m.setSubject(subject);m.setText(body);sender.send(m);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Email delivery failed; in-app notification remains available");}}
}
