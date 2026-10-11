package com.parvnigam.careerbridge;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordRecovery {
    private final Repositories.Accounts accounts;
    private final EntityManager entityManager;
    private final VerificationMail mail;
    private final PasswordEncoder passwords;
    private final NotificationService notices;
    private final TransactionTemplate transactions;
    private final SecureRandom random=new SecureRandom();
    private final String frontend;
    private final ThreadPoolExecutor requests=new ThreadPoolExecutor(1,2,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100),r->{Thread t=new Thread(r,"password-reset-mail");t.setDaemon(true);return t;});

    PasswordRecovery(Repositories.Accounts accounts,EntityManager entityManager,VerificationMail mail,
        PasswordEncoder passwords,NotificationService notices,PlatformTransactionManager manager,
        @Value("${app.frontend-url}") String frontend){
        this.accounts=accounts;this.entityManager=entityManager;this.mail=mail;this.passwords=passwords;
        this.notices=notices;this.transactions=new TransactionTemplate(manager);this.frontend=frontend.replaceAll("/$","");
    }

    /** Queue before account lookup so account existence and SMTP latency do not affect the HTTP response. */
    public void request(String email){
        mail.requireDelivery();
        String normalized=email.trim().toLowerCase(Locale.ROOT);
        try{requests.execute(()->{
            try{transactions.executeWithoutResult(status->issue(normalized));}
            catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Password-reset email could not be prepared or sent; the request can be retried.");}
        });}catch(RejectedExecutionException e){throw Platform.fail(429,"Password recovery is busy. Please try again shortly.");}
    }

    private void issue(String email){
        Long id=accounts.emailAccountId(email).orElse(null);if(id==null)return;
        Model.Account a=accounts.lockAccount(id).orElseThrow();entityManager.refresh(a);
        if(!a.enabled)return;
        Instant now=Instant.now();
        if(a.passwordResetSentAt!=null&&a.passwordResetSentAt.plusSeconds(60).isAfter(now))return;
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        a.passwordResetHash=EmailVerification.hash(token);a.passwordResetExpiresAt=now.plusSeconds(1800);a.passwordResetSentAt=now;
        accounts.saveAndFlush(a);
        mail.sendReset(a.email,frontend+"/reset-password#token="+token);
    }

    @Transactional public void reset(Inputs.ResetPassword input){
        String pw=input.newPassword();
        if(!pw.equals(input.confirmPassword()))throw Platform.fail(400,"The passwords do not match.");
        if(!pw.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{10,72}")||pw.getBytes(StandardCharsets.UTF_8).length>72)
            throw Platform.fail(400,"Use 10–72 characters including uppercase, lowercase and a number (at most 72 UTF-8 bytes).");
        String hash=EmailVerification.hash(input.token());
        Long id=accounts.passwordResetAccountId(hash).orElseThrow(PasswordRecovery::invalidLink);
        Model.Account a=accounts.lockAccount(id).orElseThrow(PasswordRecovery::invalidLink);entityManager.refresh(a);
        if(!a.enabled||!hash.equals(a.passwordResetHash)||a.passwordResetExpiresAt==null||!a.passwordResetExpiresAt.isAfter(Instant.now()))throw invalidLink();
        if(passwords.matches(pw,a.passwordHash))throw Platform.fail(400,"Choose a new password different from your current password.");
        a.passwordHash=passwords.encode(pw);a.authVersion++;clear(a);accounts.saveAndFlush(a);
        notices.send(a.id,"Password changed","Your CareerBridge password was reset and previous sessions were ended. If this was not you, contact your platform administrator.","/settings");
    }

    static org.springframework.web.server.ResponseStatusException invalidLink(){return Platform.fail(400,"This password-reset link is invalid, expired, or already used. Request a new link.");}
    static void clear(Model.Account a){a.passwordResetHash=null;a.passwordResetExpiresAt=null;}
    @PreDestroy void close(){requests.shutdown();}
}
