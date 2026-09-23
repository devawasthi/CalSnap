package com.calsnap;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthTest {
  Config config =
      new Config() {
        public boolean local() {
          return true;
        }

        public String get(String key, String fallback) {
          return key.equals("JWT_SECRET")
              ? "test-signing-secret-with-at-least-32-bytes"
              : super.get(key, fallback);
        }
      };
  Auth auth = new Auth(config, null, new ObjectMapper());

  @Test
  void sessionRejectsWrongKindAndSignature() throws Exception {
    String token = auth.token(UUID.randomUUID());
    assertNotNull(auth.verify(token, "session"));
    assertThrows(RuntimeException.class, () -> auth.verify(token, "oauth"));
    assertThrows(
        RuntimeException.class,
        () -> auth.verify(token.substring(0, token.length() - 10) + "AAAAAAAAAA", "session"));
    String expired = auth.sign(Map.of("kind", "session"), -1);
    assertThrows(RuntimeException.class, () -> auth.verify(expired, "session"));
  }

  @Test
  void csrfAndOriginMustMatch() throws Exception {
    String token = auth.token(UUID.randomUUID());
    String csrf = auth.verify(token, "session").getStringClaim("csrf");
    var filter = new SessionFilter(auth);
    var req =
        HttpRequest.POST("/api/v1/goals", Map.of())
            .cookie(auth.cookie("calsnap_session", token, 3600));
    assertThrows(RuntimeException.class, () -> filter.authenticate(req));
    req.header("X-CSRF-Token", csrf).header("Origin", "https://attacker.example");
    assertThrows(RuntimeException.class, () -> filter.authenticate(req));
    req.getHeaders().set("Origin", config.origin());
    filter.authenticate(req);
    assertTrue(req.getAttribute("userId", String.class).isPresent());
  }
}
