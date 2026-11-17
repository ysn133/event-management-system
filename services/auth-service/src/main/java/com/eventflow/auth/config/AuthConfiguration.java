package com.eventflow.auth.config;
import javax.crypto.SecretKey;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.eventflow.auth.domain.AppUser;
import com.eventflow.auth.repository.AppUserRepository;
@Configuration
public class AuthConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean ApplicationRunner initialUser(AppUserRepository users,PasswordEncoder passwords,
            @Value("${BOOTSTRAP_USER_EMAIL:}") String email,
            @Value("${BOOTSTRAP_USER_PASSWORD:}") String password) {
        return args -> {
            if(email.isBlank() || password.isBlank()) return;
            if(password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
                throw new IllegalArgumentException("Bootstrap password must be 12–72 bytes");
            String normalized=email.strip().toLowerCase(Locale.ROOT);
            if(users.findByEmail(normalized).isEmpty())
                users.save(new AppUser(normalized,"Organisateur",passwords.encode(password)));
        };
    }
}
