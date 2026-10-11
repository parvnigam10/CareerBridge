package com.parvnigam.careerbridge;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
    @Bean JwtEncoder encoder(@Value("${app.jwt-secret}") String secret) {
        if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        return new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
    }
    @Bean JwtDecoder decoder(@Value("${app.jwt-secret}") String secret) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("careerbridge"));
        return decoder;
    }
    @Bean SecurityFilterChain chain(HttpSecurity http,Repositories.Accounts accounts) throws Exception {
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Model.Account account=accounts.findById(Long.valueOf(jwt.getSubject())).orElse(null);
            Number session=jwt.getClaim("sessionVersion");
            if(account==null || !account.enabled || Boolean.FALSE.equals(account.emailVerified) || session==null || session.intValue()!=account.authVersion) return List.of();
            return List.of(new SimpleGrantedAuthority("ROLE_"+account.role));
        });
        return http.csrf(csrf->csrf.disable()).cors(cors->{})
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/auth/register","/api/auth/verify-email","/api/auth/resend-verification","/api/auth/forgot-password","/api/auth/reset-password","/api/public/**","/actuator/health","/error").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/placement/**").hasAnyRole("PLACEMENT_OFFICER","ADMIN")
                .requestMatchers("/api/recruiter/**").hasRole("RECRUITER")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .requestMatchers("/api/**","/actuator/**").authenticated().anyRequest().permitAll())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)))
            .build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.frontend-url}") String origin) {
        CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(List.of(origin));
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type"));
        UrlBasedCorsConfigurationSource s=new UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/api/**",c);return s;
    }
    public static String token(JwtEncoder encoder,Model.Account user) {
        Instant now=Instant.now();
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("careerbridge").subject(user.id.toString()).issuedAt(now)
            .expiresAt(now.plusSeconds(14400)).claim("role",user.role.name()).claim("sessionVersion",user.authVersion).id(UUID.randomUUID().toString()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
}
