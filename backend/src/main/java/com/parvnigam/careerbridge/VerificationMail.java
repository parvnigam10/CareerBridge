package com.parvnigam.careerbridge;

import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Opt-in development inbox; no messages or tokens are exposed through the application API. */
@Service
public class VerificationMail {
    enum Kind { VERIFICATION, PASSWORD_RESET }
    record Message(String to,String link,long minutes,Instant receivedAt,Kind kind) {}
    final List<Message> messages=new ArrayList<>();
    final JavaMailSender sender;
    final boolean local,enabled;
    final String from,frontend;
    HttpServer inbox;

    VerificationMail(JavaMailSender sender,@Value("${app.local-email-testing:false}") boolean local,
        @Value("${app.mail-enabled}") boolean enabled,@Value("${app.mail-from}") String from,
        @Value("${app.local-inbox-port:8025}") int port,@Value("${app.frontend-url}") String frontend)throws java.io.IOException {
        this.sender=sender;this.local=local;this.enabled=enabled;this.from=from;this.frontend=frontend.replaceAll("/$","");
        if(local){
            inbox=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),0);
            inbox.createContext("/",exchange->{
                String host=exchange.getRequestHeaders().getFirst("Host");
                boolean allowed=host!=null&&(host.equals("localhost:"+inbox.getAddress().getPort())||host.equals("127.0.0.1:"+inbox.getAddress().getPort()));
                if(!allowed||!exchange.getRequestMethod().equals("GET")){exchange.sendResponseHeaders(403,-1);exchange.close();return;}
                if(!exchange.getRequestURI().getPath().equals("/")){exchange.sendResponseHeaders(404,-1);exchange.close();return;}
                String filter="",flow="verification";
                try{String query=exchange.getRequestURI().getRawQuery();if(query!=null)for(String part:query.split("&")){String[] pair=part.split("=",2);if(pair.length==2){if(pair[0].equals("email"))filter=URLDecoder.decode(pair[1],StandardCharsets.UTF_8).trim();if(pair[0].equals("flow")&&pair[1].equals("reset"))flow="reset";}}}
                catch(IllegalArgumentException e){exchange.sendResponseHeaders(400,-1);exchange.close();return;}
                byte[] body=renderInbox(filter,flow).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control","no-store");
                exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");
                exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
                exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
            });
            inbox.start();
            org.slf4j.LoggerFactory.getLogger(getClass()).info("Development inbox enabled on http://127.0.0.1:{} (no real email delivery)",inbox.getAddress().getPort());
        }
    }

    String renderInbox(String filter){return renderInbox(filter,"verification");}
    String renderInbox(String filter,String flow){
        boolean resetting=flow.equals("reset");
        String returnUrl=frontend+(resetting?"/forgot-password":"/verify-email")+(filter.isEmpty()?"":"?email="+URLEncoder.encode(filter,StandardCharsets.UTF_8));
        StringBuilder page=new StringBuilder("""
            <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Test inbox · CareerBridge</title><style>
            *{box-sizing:border-box}body{margin:0;font:15px/1.6 system-ui,sans-serif;color:#203432;background:#f6f6f1}
            a{color:#173c34;text-decoration:none}a:focus-visible{outline:3px solid #ec8356;outline-offset:4px}
            .inbox-shell{max-width:900px;margin:0 auto;padding:32px 24px 40px}
            .inbox-header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:44px}
            .brand{display:inline-flex;align-items:center;gap:10px;color:#223331;font-size:23px;font-weight:700;letter-spacing:-1.1px;line-height:1;flex-shrink:0}.brand-symbol{width:41px;height:41px;display:block;flex-shrink:0;border-radius:11px}.brand small{display:block;color:inherit;font-size:6.2px;font-weight:500;letter-spacing:1.35px;margin-top:7px}
            .mode{padding:5px 9px;border-radius:6px;background:#e7ede5;color:#355246;font-size:10px;font-weight:700;letter-spacing:1px;white-space:nowrap}
            .inbox-title{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-bottom:24px}
            h1{font-size:32px;line-height:1.25;letter-spacing:-1px;margin:0 0 10px}h2{font-size:20px;line-height:1.4;margin:16px 0 8px}
            p{margin:0 0 16px}.intro{max-width:540px;color:#65736b;font-size:14px}.actions{display:grid;gap:10px;flex:0 0 180px}
            .button{min-height:44px;padding:11px 17px;border:1px solid #173c34;border-radius:9px;display:inline-flex;align-items:center;justify-content:center;background:#173c34;color:#fff;font-size:13px;font-weight:650;text-align:center;line-height:1.45}
            .button.secondary{color:#203432;background:white;border-color:#dce2d8}.button:hover{background:#265247}.button.secondary:hover{background:#eef2e9}
            .filter-note{font-size:13px;color:#65736b;margin:0 0 18px;overflow-wrap:anywhere}.filter-note a{font-weight:650;margin-left:8px;text-decoration:underline}
            .message{padding:26px;background:#fff;border:1px solid #e3e6dd;border-radius:14px;margin-bottom:16px;box-shadow:0 3px 12px #173c3404}
            .message-meta{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:11px;color:#74807b}.message-meta span:first-child{color:#355246;background:#edf3eb;border-radius:5px;padding:4px 7px;font-weight:650}
            .recipient{font-size:13px;overflow-wrap:anywhere;margin:16px 0 0;color:#58665d}.recipient strong{color:#203432}
            .message-copy{font-size:14px;color:#65736b;max-width:600px}.message-footer{display:flex;align-items:center;justify-content:space-between;gap:20px;border-top:1px solid #edf0e8;padding-top:20px;margin-top:22px}
            .message-footer small{font-size:12px;color:#74807b}.empty{background:#fff;border:1px solid #e3e6dd;border-radius:14px;padding:40px 26px;text-align:center}.empty h2{margin-top:0}.empty p{color:#65736b;font-size:14px}
            .inbox-footer{font-size:12px;color:#74807b;text-align:center;margin:26px 0 0}.message.earlier .message-meta span:first-child{background:#f2f2ef;color:#74807b}
            @media(max-width:600px){.inbox-shell{padding:24px 16px}.inbox-header{margin-bottom:30px}.brand{font-size:20px;gap:8px}.brand-symbol{width:34px;height:34px}.brand small{font-size:5.4px;letter-spacing:1px}.inbox-title{align-items:flex-start;flex-direction:column;gap:14px}h1{font-size:28px}.actions{width:100%;grid-template-columns:1fr 1fr;flex:none}.message{padding:22px 20px}.message-footer{align-items:stretch;flex-direction:column;gap:12px}.message-footer .button{width:100%}.message-meta{align-items:flex-start}.message-meta span:last-child{text-align:right}.empty{padding:30px 20px}}
            @media(max-width:380px){.inbox-header{gap:12px}.brand{font-size:18px;gap:7px}.brand-symbol{width:30px;height:30px}.brand small{font-size:4.8px;letter-spacing:.9px}.mode{font-size:9px;letter-spacing:.6px;padding:5px 7px}}
            </style></head><body><main class="inbox-shell"><header class="inbox-header">
            """);
        page.append("<a class=\"brand\" aria-label=\"CareerBridge home\" href=\"");
        // Embed the app's vector icon so the inbox header also works without the frontend server.
        page.append(escape(frontend)).append("\" target=\"_self\">").append("""
            <svg class="brand-symbol" width="41" height="41" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96"><rect width="96" height="96" rx="25" fill="#ed895e"/><g transform="translate(9 9) scale(1.21875)" fill="#173c34"><path d="M51 16C40 5 20 8 12 25C8 34 9 42 13 48C18 36 29 26 41 24C46 23 49 21 51 16Z"/><path d="M15 50C24 31 45 31 54 47H40C35 36 27 39 26 53Z"/></g></svg>
            <span class="brand-wordmark">careerbridge<small>PREPARE. CONNECT. GROW.</small></span></a><span class="mode">LOCAL TESTING</span></header>
            """);
        page.append("<section class=\"inbox-title\"><div><h1>Your test inbox</h1><p class=\"intro\">CareerBridge test messages arrive here. Nothing is sent to a real email account.</p></div><nav class=\"actions\" aria-label=\"Inbox actions\"><a class=\"button secondary\" href=\"\" target=\"_self\">Refresh inbox</a><a class=\"button secondary\" href=\"").append(escape(returnUrl)).append("\" target=\"_self\">").append(resetting?"Back to password recovery":"Back to verification").append("</a></nav></section>");
        if(!filter.isEmpty())page.append("<p class=\"filter-note\">Messages for <strong>").append(escape(filter)).append("</strong><a href=\"").append(resetting?"/?flow=reset":"/").append("\" target=\"_self\">Show all</a></p>");
        List<Message> visible;
        synchronized(messages){visible=new ArrayList<>(messages);}
        if(!filter.isEmpty())visible.removeIf(m->!m.to().equalsIgnoreCase(filter));
        if(resetting)visible.removeIf(m->m.kind()!=Kind.PASSWORD_RESET);
        Collections.reverse(visible);
        if(visible.isEmpty())page.append("<section class=\"empty\"><h2>No messages yet</h2><p>Register a new account or request another email, then refresh this inbox.</p><a class=\"button\" target=\"_self\" href=\"").append(escape(returnUrl)).append("\">Back to CareerBridge</a></section>");
        Set<String> seen=new HashSet<>();
        for(Message m:visible){
            boolean latest=seen.add(m.to().toLowerCase(Locale.ROOT)+":"+m.kind());
            long minutes=Math.max(0,Duration.between(m.receivedAt(),Instant.now()).toMinutes());
            String received=minutes==0?"Just now":minutes<60?minutes+" min ago":minutes<1440?(minutes/60)+" hr ago":(minutes/1440)+" days ago";
            boolean reset=m.kind()==Kind.PASSWORD_RESET;
            String duration=m.minutes()%60==0?(m.minutes()/60)+" hours":m.minutes()+" minutes";
            page.append("<article class=\"message ").append(latest?"latest":"earlier").append("\"><div class=\"message-meta\"><span>").append(latest?"Latest email":"Earlier email").append("</span><span>").append(received).append("</span></div><p class=\"recipient\">To: <strong>").append(escape(m.to())).append("</strong></p><h2>").append(reset?"Reset your CareerBridge password":"Verify your CareerBridge email").append("</h2><p class=\"message-copy\">").append(!latest?"A newer email of this type has been sent. This earlier link may no longer work.":reset?"Choose a new password. If you did not request this, ignore this message.":"Confirm your email address to finish setting up your account.").append("</p><div class=\"message-footer\"><small>Single-use link · Expires after ").append(duration).append("</small><a class=\"button\" href=\"").append(escape(m.link())).append("\" target=\"_self\" rel=\"noreferrer\">").append(reset?"Reset password →":"Verify email →").append("</a></div></article>");
        }
        return page.append("<p class=\"inbox-footer\">Messages stay on this computer and clear when the backend stops.<br>Continue in the same tab.</p></main></body></html>").toString();
    }

    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    void requireDelivery(){if(!local&&!enabled)throw Platform.fail(503,"Email delivery is not configured. Start local email testing or configure SMTP first.");}
    void send(String to,String link,long hours){sendLink(to,link,hours*60,Kind.VERIFICATION);}
    void sendReset(String to,String link){sendLink(to,link,30,Kind.PASSWORD_RESET);}
    private void sendLink(String to,String link,long minutes,Kind kind){
        requireDelivery();
        if(local){synchronized(messages){if(messages.size()>=200)messages.remove(0);messages.add(new Message(to,link,minutes,Instant.now(),kind));}return;}
        boolean reset=kind==Kind.PASSWORD_RESET;
        String duration=minutes%60==0?(minutes/60)+" hours":minutes+" minutes";
        try{SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(to);m.setSubject(reset?"Reset your CareerBridge password":"Verify your CareerBridge email");m.setText((reset?"Choose a new CareerBridge password":"Confirm your email address")+" using the link below. It expires in "+duration+" and works once.\n\n"+link+"\n\nIf you did not request this, ignore this email.");sender.send(m);}
        catch(Exception e){throw Platform.fail(503,"The email could not be sent. Please try again later.");}
    }
    @PreDestroy void close(){if(inbox!=null)inbox.stop(0);}
}
