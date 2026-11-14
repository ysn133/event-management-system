package com.eventflow.evenement;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class JwtSecurityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired SecretKey key;
    String token(String issuer,Instant expiry) {
        var claims=JwtClaimsSet.builder().issuer(issuer).subject("1")
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiry).build();
        return new NimbusJwtEncoder(new ImmutableSecret<>(key)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    @Test void rejectsMissingToken() throws Exception {
        mvc.perform(get("/api/evenements")).andExpect(status().isUnauthorized());
    }
    @Test void rejectsInvalidSignature() throws Exception {
        String valid=token("eventflow",Instant.now().plusSeconds(300));
        String invalid=valid.substring(0,valid.lastIndexOf('.')+1)+"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        mvc.perform(get("/api/evenements").header("Authorization","Bearer "+invalid)).andExpect(status().isUnauthorized());
    }
    @Test void rejectsExpiredToken() throws Exception {
        mvc.perform(get("/api/evenements").header("Authorization","Bearer "+token("eventflow",Instant.now().minusSeconds(180))))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsOtherIssuer() throws Exception {
        mvc.perform(get("/api/evenements").header("Authorization","Bearer "+token("other",Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
    }
    @Test void acceptsValidToken() throws Exception {
        mvc.perform(get("/api/evenements").header("Authorization","Bearer "+token("eventflow",Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
    }
    @Test void allowsHealthWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
