package com.parvnigam.careerbridge;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EmailVerification {
 final jakarta.persistence.EntityManager entityManager;final Repositories.Accounts accounts;final VerificationMail mail;final String frontend;final long hours;
 private final SecureRandom random=new SecureRandom();private final Map<String,Window> limits=new ConcurrentHashMap<>();record Window(int count,Instant expires) {}
 EmailVerification(jakarta.persistence.EntityManager entityManager,Repositories.Accounts accounts,VerificationMail mail,@Value("${app.frontend-url}") String frontend,@Value("${app.verification-hours:24}") long hours){this.entityManager=entityManager;this.accounts=accounts;this.mail=mail;this.frontend=frontend.replaceAll("/$","");this.hours=hours;}
 public Map<String,Object> testingSettings(){return Platform.map("enabled",mail.inbox!=null,"inboxUrl",mail.inbox==null?null:"http://127.0.0.1:"+mail.inbox.getAddress().getPort()+"/");}
 public synchronized void limit(String ip,String action){Instant now=Instant.now();limits.entrySet().removeIf(e->!e.getValue().expires().isAfter(now));String key=action+":"+ip;Window w=limits.get(key);int cap=action.equals("verify")?30:10;if(w!=null&&w.count()>=cap)throw Platform.fail(429,"Too many requests. Try again in 15 minutes.");if(limits.size()>=10000&&!limits.containsKey(key))throw Platform.fail(429,"Please try again later.");limits.put(key,new Window(w==null?1:w.count()+1,w==null?now.plusSeconds(900):w.expires()));}
 static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 void issue(Model.Account a){byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);a.verificationHash=hash(token);a.verificationExpiresAt=Instant.now().plusSeconds(hours*3600);a.verificationSentAt=Instant.now();accounts.saveAndFlush(a);mail.send(a.email,frontend+"/verify-email#token="+token,hours);}
 @Transactional public void resend(String email){Long found=accounts.emailAccountId(email.trim().toLowerCase(Locale.ROOT)).orElse(null);if(found==null)return;Model.Account a=accounts.lockAccount(found).orElseThrow();entityManager.refresh(a);if(!a.enabled||!Boolean.FALSE.equals(a.emailVerified))return;if(a.verificationSentAt!=null&&a.verificationSentAt.plusSeconds(60).isAfter(Instant.now()))return;issue(a);}
 @Transactional public void verify(String token){String hash=hash(token);Long found=accounts.verificationAccountId(hash).orElseThrow(()->Platform.fail(400,"This verification link is invalid, expired, or already used. Request a new link."));Model.Account a=accounts.lockAccount(found).orElseThrow();entityManager.refresh(a);if(!a.enabled||!Boolean.FALSE.equals(a.emailVerified)||!hash.equals(a.verificationHash)||a.verificationExpiresAt==null||!a.verificationExpiresAt.isAfter(Instant.now()))throw Platform.fail(400,"This verification link is invalid, expired, or already used. Request a new link.");a.emailVerified=true;a.emailVerifiedAt=Instant.now();a.verificationHash=null;a.verificationExpiresAt=null;a.authVersion++;accounts.save(a);}
}
