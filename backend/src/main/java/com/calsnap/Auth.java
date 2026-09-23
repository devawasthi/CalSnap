package com.calsnap;

import static com.calsnap.Models.bad;

import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import io.micronaut.context.annotation.Context;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.cookie.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Context
public class Auth {
  final Config config;
  final Db db;
  final ObjectMapper json;
  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private volatile JWKSet googleKeys;
  private volatile Instant keysExpire = Instant.EPOCH;

  public Auth(Config config, Db db, ObjectMapper json) {
    this.config = config;
    this.db = db;
    this.json = json;
    config.secret();
    if (!config.local()) {
      if (config.get("DATABASE_URL", "").isBlank()) config.required("DATABASE_HOST");
      config.required("DATABASE_USER");
      config.required("DATABASE_PASSWORD");
    }
    if (!config.local() && !config.origin().startsWith("https://"))
      throw new IllegalStateException("Production requires HTTPS PUBLIC_ORIGIN");
  }

  String sign(Map<String, Object> claims, int seconds) throws Exception {
    var b =
        new JWTClaimsSet.Builder()
            .issuer("calsnap")
            .audience("calsnap")
            .expirationTime(Date.from(Instant.now().plusSeconds(seconds)))
            .issueTime(new Date());
    claims.forEach(b::claim);
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), b.build());
    jwt.sign(new MACSigner(config.secret()));
    return jwt.serialize();
  }

  JWTClaimsSet verify(String raw, String kind) {
    try {
      var jwt = SignedJWT.parse(raw);
      if (!jwt.getHeader().getAlgorithm().equals(JWSAlgorithm.HS256)
          || !jwt.verify(new MACVerifier(config.secret()))) throw new Exception();
      var c = jwt.getJWTClaimsSet();
      if (!"calsnap".equals(c.getIssuer())
          || !c.getAudience().contains("calsnap")
          || c.getExpirationTime() == null
          || c.getExpirationTime().before(new Date())
          || !kind.equals(c.getStringClaim("kind"))) throw new Exception();
      return c;
    } catch (Exception e) {
      throw new io.micronaut.http.exceptions.HttpStatusException(
          HttpStatus.UNAUTHORIZED, "Please sign in again");
    }
  }

  Cookie cookie(String name, String value, int maxAge) {
    return Cookie.of(name, value)
        .httpOnly(true)
        .secure(!config.local())
        .sameSite(SameSite.Lax)
        .path("/")
        .maxAge(maxAge);
  }

  UUID user(HttpRequest<?> req) {
    return UUID.fromString(req.getAttribute("userId", String.class).orElseThrow());
  }

  static String random() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String enc(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  synchronized JWKSet keys() throws Exception {
    if (googleKeys == null || Instant.now().isAfter(keysExpire)) {
      var response =
          http.send(
              java.net.http.HttpRequest.newBuilder(
                      URI.create("https://www.googleapis.com/oauth2/v3/certs"))
                  .timeout(Duration.ofSeconds(8))
                  .GET()
                  .build(),
              BodyHandlers.ofString());
      if (response.statusCode() != 200) throw new Exception("OIDC keys unavailable");
      googleKeys = JWKSet.parse(response.body());
      keysExpire = Instant.now().plusSeconds(3600);
    }
    return googleKeys;
  }

  String token(UUID id) throws Exception {
    return sign(Map.of("sub", id.toString(), "kind", "session", "csrf", random()), 3600);
  }

  @Controller("/auth")
  @ExecuteOn(TaskExecutors.BLOCKING)
  public static class Routes {
    final Auth auth;

    public Routes(Auth auth) {
      this.auth = auth;
    }

    @Get("/google")
    public HttpResponse<?> login() throws Exception {
      String state = random(), nonce = random(), verifier = random();
      String challenge =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  MessageDigest.getInstance("SHA-256")
                      .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
      String flow =
          auth.sign(
              Map.of("kind", "oauth", "state", state, "nonce", nonce, "verifier", verifier), 600);
      String url =
          "https://accounts.google.com/o/oauth2/v2/auth?client_id="
              + enc(auth.config.required("GOOGLE_CLIENT_ID"))
              + "&redirect_uri="
              + enc(auth.config.origin() + "/auth/callback")
              + "&response_type=code&scope=openid%20email%20profile&state="
              + state
              + "&nonce="
              + nonce
              + "&code_challenge="
              + challenge
              + "&code_challenge_method=S256";
      return HttpResponse.redirect(URI.create(url)).cookie(auth.cookie("oauth_flow", flow, 600));
    }

    @Get("/callback{?code,state}")
    public HttpResponse<?> callback(String code, String state, HttpRequest<?> request)
        throws Exception {
      var flow =
          auth.verify(
              request.getCookies().findCookie("oauth_flow").map(Cookie::getValue).orElse(""),
              "oauth");
      if (!MessageDigest.isEqual(
          state.getBytes(StandardCharsets.UTF_8),
          flow.getStringClaim("state").getBytes(StandardCharsets.UTF_8)))
        throw bad("Invalid login state");
      String body =
          "code="
              + enc(code)
              + "&client_id="
              + enc(auth.config.required("GOOGLE_CLIENT_ID"))
              + "&client_secret="
              + enc(auth.config.required("GOOGLE_CLIENT_SECRET"))
              + "&redirect_uri="
              + enc(auth.config.origin() + "/auth/callback")
              + "&grant_type=authorization_code&code_verifier="
              + enc(flow.getStringClaim("verifier"));
      var response =
          auth.http.send(
              java.net.http.HttpRequest.newBuilder(
                      URI.create("https://oauth2.googleapis.com/token"))
                  .timeout(Duration.ofSeconds(10))
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              BodyHandlers.ofString());
      if (response.statusCode() != 200) throw bad("Google sign-in failed. Try again.");
      var jwt = SignedJWT.parse(auth.json.readTree(response.body()).path("id_token").asText());
      var key = auth.keys().getKeyByKeyId(jwt.getHeader().getKeyID());
      if (!(key instanceof RSAKey rsa)
          || !JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
          || !jwt.verify(new RSASSAVerifier(rsa))) throw bad("Invalid identity signature");
      var claims = jwt.getJWTClaimsSet();
      if (!List.of("https://accounts.google.com", "accounts.google.com")
              .contains(claims.getIssuer())
          || !claims.getAudience().contains(auth.config.required("GOOGLE_CLIENT_ID"))
          || claims.getExpirationTime() == null
          || claims.getExpirationTime().before(new Date())
          || !flow.getStringClaim("nonce").equals(claims.getStringClaim("nonce"))
          || !Boolean.TRUE.equals(claims.getBooleanClaim("email_verified"))
          || claims.getSubject() == null) throw bad("Invalid Google identity");
      UUID id =
          auth.db.tx(
              c -> {
                var rows =
                    Db.rows(
                        c,
                        "INSERT INTO users(id,email,name,oauth_provider,oauth_subject)"
                            + " VALUES(?,?,?,'google',?) ON CONFLICT(oauth_provider,oauth_subject)"
                            + " DO UPDATE SET email=excluded.email,name=excluded.name RETURNING id",
                        UUID.randomUUID(),
                        claims.getStringClaim("email"),
                        Optional.ofNullable(claims.getStringClaim("name")).orElse("CalSnap user"),
                        claims.getSubject());
                return (UUID) rows.getFirst().get("id");
              });
      return HttpResponse.redirect(URI.create("/"))
          .cookie(auth.cookie("calsnap_session", auth.token(id), 3600))
          .cookie(auth.cookie("oauth_flow", "", 0));
    }

    @Post("/demo")
    public HttpResponse<?> demo(HttpRequest<?> request) throws Exception {
      if (!auth.config.local()) return HttpResponse.notFound();
      if (!auth.config.origin().equals(request.getHeaders().get("Origin")))
        throw bad("Invalid origin");
      UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
      auth.db.tx(
          c -> {
            Db.one(c, "SELECT id FROM users WHERE id=?", id);
            return null;
          });
      return HttpResponse.ok(Map.of("ok", true))
          .cookie(auth.cookie("calsnap_session", auth.token(id), 3600));
    }
  }
}
