package com.parvnigam.careerbridge;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
/** Opt-in development inbox; no route in the application's public API. */
@Service
public class VerificationMail {
 record Message(String to,String link,long hours) {}
 final List<Message> messages=new ArrayList<>();final JavaMailSender sender;final boolean local,enabled;final String from;HttpServer inbox;
 VerificationMail(JavaMailSender sender,@Value("${app.local-email-testing:false}") boolean local,@Value("${app.mail-enabled}") boolean enabled,@Value("${app.mail-from}") String from,@Value("${app.local-inbox-port:8025}") int port)throws java.io.IOException {
 this.sender=sender;this.local=local;this.enabled=enabled;this.from=from;
 if(local){inbox=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),0);inbox.createContext("/",exchange->{String host=exchange.getRequestHeaders().getFirst("Host");boolean allowed=host!=null&&(host.equals("localhost:"+inbox.getAddress().getPort())||host.equals("127.0.0.1:"+inbox.getAddress().getPort()));if(!allowed||!exchange.getRequestMethod().equals("GET")){exchange.sendResponseHeaders(403,-1);exchange.close();return;}
 StringBuilder page=new StringBuilder("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>CareerBridge local inbox</title><style>body{font:16px system-ui;max-width:760px;margin:40px auto;padding:20px;background:#f6f7f4}article{background:white;padding:24px;margin:16px 0;border:1px solid #ddd;border-radius:12px}a{color:#176752}</style><h1>CareerBridge local inbox</h1><p>Development only. Messages stay on this computer and clear when the backend stops. Refresh to see new messages.</p>");
 synchronized(messages){if(messages.isEmpty())page.append("<p>No messages yet. Register a new account in CareerBridge.</p>");for(int i=messages.size()-1;i>=0;i--){Message m=messages.get(i);page.append("<article><p>To: ").append(escape(m.to())).append("</p><h2>Verify your CareerBridge email</h2><p>Confirm your email address. The link expires in ").append(m.hours()).append(" hours and works once.</p><a href=\"").append(escape(m.link())).append("\" rel='noreferrer'>Verify email</a></article>");}}
 byte[] body=page.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'");exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});inbox.start();org.slf4j.LoggerFactory.getLogger(getClass()).info("Development inbox enabled on http://127.0.0.1:{} (no real email delivery)",inbox.getAddress().getPort());}}
 static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
 void send(String to,String link,long hours){if(local){synchronized(messages){if(messages.size()>=200)messages.remove(0);messages.add(new Message(to,link,hours));}return;}if(!enabled)throw Platform.fail(503,"Email delivery is not configured. Start local email testing or configure SMTP before registering.");try{SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(to);m.setSubject("Verify your CareerBridge email");m.setText("Confirm your email address using the link below. It expires in "+hours+" hours and works once.\n\n"+link+"\n\nIf you did not register, ignore this email.");sender.send(m);}catch(Exception e){throw Platform.fail(503,"Verification email could not be sent. Please try again later.");}}
 @PreDestroy void close(){if(inbox!=null)inbox.stop(0);}
}
