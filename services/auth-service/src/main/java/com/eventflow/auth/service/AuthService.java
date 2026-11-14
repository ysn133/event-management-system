package com.eventflow.auth.service;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.eventflow.auth.repository.AppUserRepository;
import com.eventflow.auth.dto.*;
@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final String issuer;
    private final long ttl;
    private final String dummyHash;
    public AuthService(AppUserRepository users,PasswordEncoder passwords,JwtEncoder tokens,
            @Value("${auth.jwt.issuer}") String issuer,@Value("${auth.jwt.ttl-seconds}") long ttl) {
        this.users=users; this.passwords=passwords; this.tokens=tokens; this.issuer=issuer; this.ttl=ttl;
        this.dummyHash=passwords.encode("unused-password-for-timing");
    }
    public LoginResponse login(LoginRequest request) {
        var user=users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches=passwords.matches(request.password(),user==null?dummyHash:user.getPasswordHash());
        if(user==null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email ou mot de passe incorrect");
        var now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(ttl)).build();
        String token=tokens.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return new LoginResponse(token,"Bearer",ttl,AuthUserResponse.from(user));
    }
    public AuthUserResponse current(String subject) {
        try { return users.findById(Long.valueOf(subject)).map(AuthUserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
        catch(NumberFormatException error) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    }
}
