package com.calsnap;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.exceptions.HttpStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@ServerFilter("/api/**")
public class SessionFilter {
  private final Auth auth;

  public SessionFilter(Auth auth) {
    this.auth = auth;
  }

  @RequestFilter
  public void authenticate(HttpRequest<?> request) throws Exception {
    var claims =
        auth.verify(
            request.getCookies().findCookie("calsnap_session").map(Cookie::getValue).orElse(""),
            "session");
    if (!request.getMethod().equals(HttpMethod.GET)
        && !request.getMethod().equals(HttpMethod.HEAD)) {
      String csrf = request.getHeaders().get("X-CSRF-Token"),
          origin = request.getHeaders().get("Origin");
      if (csrf == null
          || !MessageDigest.isEqual(
              csrf.getBytes(StandardCharsets.UTF_8),
              claims.getStringClaim("csrf").getBytes(StandardCharsets.UTF_8))
          || (origin != null && !origin.equals(auth.config.origin())))
        throw new HttpStatusException(HttpStatus.FORBIDDEN, "Invalid request token");
    }
    request.setAttribute("userId", claims.getSubject());
    request.setAttribute("csrf", claims.getStringClaim("csrf"));
  }

  @ResponseFilter
  public void headers(MutableHttpResponse<?> response) {
    response.header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff");
  }
}
