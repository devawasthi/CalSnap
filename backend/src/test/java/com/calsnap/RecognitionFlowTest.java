package com.calsnap;

import static com.calsnap.Models.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class RecognitionFlowTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  static Db db;
  static HttpServer server;
  static Path folder;
  static Config config;
  static ObjectMapper json = new ObjectMapper();
  static AtomicInteger failures = new AtomicInteger(), lookups = new AtomicInteger();
  FoodService food;
  Photos photos;
  UUID user;
  byte[] jpeg;

  @BeforeAll
  static void setup() throws Exception {
    folder = Files.createTempDirectory("calsnap-photos-test-");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          if (failures.get() > 0) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
          }
          String data =
              exchange.getRequestURI().getPath().equals("/responses")
                  ? "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"items\\\":[{\\\"foodName\\\":\\\"banana\\\",\\\"portionG\\\":120,\\\"confidence\\\":0.6}]}\"}]}]}"
                  : "{\"foods\":[{\"fdcId\":1,\"description\":\"Banana\",\"foodNutrients\":[{\"nutrientId\":1008,\"value\":89},{\"nutrientId\":1003,\"value\":1.09},{\"nutrientId\":1004,\"value\":0.33},{\"nutrientId\":1005,\"value\":22.84}]}]}";
          if (exchange.getRequestURI().getPath().equals("/foods/search")) lookups.incrementAndGet();
          byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    config =
        new Config() {
          public boolean local() {
            return true;
          }

          public String get(String key, String fallback) {
            return switch (key) {
              case "STUB_URL" -> "http://localhost:" + server.getAddress().getPort();
              case "PHOTO_DIR" -> folder.toString();
              case "JWT_SECRET" -> "test-signing-secret-with-more-than-32-characters";
              default -> super.get(key, fallback);
            };
          }
        };
    var ds = new PGSimpleDataSource();
    ds.setURL(postgres.getJdbcUrl());
    ds.setUser(postgres.getUsername());
    ds.setPassword(postgres.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    db = new Db(ds);
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  @BeforeEach
  void seed() throws Exception {
    failures.set(0);
    lookups.set(0);
    user = UUID.randomUUID();
    db.tx(
        c -> {
          Db.execute(
              c,
              "INSERT INTO users(id,email,name,oauth_provider,oauth_subject)"
                  + " VALUES(?,'test@example.com','Test','test',?)",
              user,
              user.toString());
          return null;
        });
    var metrics = new SimpleMeterRegistry();
    photos = new Photos(config, db);
    food =
        new FoodService(db, photos, new Recognition(config, json, metrics), json, metrics, config);
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpg", out);
    jpeg = out.toByteArray();
  }

  @Test
  void scanReviewConfirmAndAccountErasure() throws Exception {
    var scan = food.scan(user, jpeg);
    assertEquals(false, scan.get("manualEntry"));
    assertEquals("pending", scan.get("status"));
    assertTrue(((List<?>) food.summary(user, null).get("entries")).isEmpty());
    var items = (List<?>) scan.get("items");
    Candidate candidate = (Candidate) items.getFirst();
    var item =
        new Selection(
            candidate.foodName(),
            candidate.portionG(),
            candidate
                .candidates()
                .getFirst()
                .per100g()
                .scale(candidate.portionG().movePointLeft(2)));
    food.confirm(user, (UUID) scan.get("id"), new Confirm(List.of(item), null));
    assertEquals(
        new java.math.BigDecimal("106.80"),
        ((Map<?, ?>) food.summary(user, null).get("consumed")).get("calories"));
    // Even an object orphaned by a crashed upload must be erased with the account.
    photos.put(user, jpeg);
    food.deleteAccount(user);
    assertFalse(Files.exists(folder.resolve(user.toString())));
    assertThrows(RuntimeException.class, () -> food.summary(user, null));
  }

  @Test
  void cachesNutritionAndDiscardsPhotos() throws Exception {
    var first = food.scan(user, jpeg);
    var second = food.scan(user, jpeg);
    assertEquals(1, lookups.get());
    food.discard(user, (UUID) first.get("id"));
    food.discard(user, (UUID) second.get("id"));
    try (var files = Files.list(folder.resolve(user.toString()))) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void providerOutageStillAllowsManualConfirmation() throws Exception {
    failures.set(1);
    var scan = food.scan(user, jpeg);
    assertEquals(true, scan.get("manualEntry"));
    var manual =
        new Selection(
            "Labelled snack",
            new java.math.BigDecimal("50"),
            new Macros(
                new java.math.BigDecimal("100"),
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.TEN,
                java.math.BigDecimal.ONE));
    food.confirm(user, (UUID) scan.get("id"), new Confirm(List.of(manual), null));
    assertEquals(
        new java.math.BigDecimal("100.00"),
        ((Map<?, ?>) food.summary(user, null).get("consumed")).get("calories"));
  }

  @Test
  void imageValidationRejectsNonImages() {
    assertThrows(RuntimeException.class, () -> food.scan(user, "not an image".getBytes()));
  }

  @Test
  void extractsStructuredGeminiRecognition() throws Exception {
    var recognition = new Recognition(config, json, new SimpleMeterRegistry());
    var response =
        json.readTree(
            "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"items\\\":[]}\"}]}}]}");
    assertEquals("{\"items\":[]}", recognition.recognitionOutput(response, false));
  }

  @Test
  void rejectsBlockedGeminiRecognition() throws Exception {
    var recognition = new Recognition(config, json, new SimpleMeterRegistry());
    var response =
        json.readTree(
            "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"content\":{\"parts\":[]}}]}");
    assertThrows(
        IllegalStateException.class, () -> recognition.recognitionOutput(response, false));
  }

  @Test
  void photoLinksExpireAndCannotBeForged() throws Exception {
    String key = photos.put(user, jpeg);
    UUID image = UUID.fromString(key.split("/")[1].replace(".jpg", ""));
    long expired = java.time.Instant.now().minusSeconds(5).getEpochSecond();
    assertThrows(
        RuntimeException.class,
        () -> photos.readLocal(user, image, expired, photos.signature(key, expired)));
    assertThrows(
        RuntimeException.class,
        () ->
            photos.readLocal(
                user, image, java.time.Instant.now().plusSeconds(100).getEpochSecond(), "forged"));
  }

  @Test
  void databasePhotosSurviveWithoutLocalDisk() throws Exception {
    Config databaseConfig =
        new Config() {
          public String get(String key, String fallback) {
            return switch (key) {
              case "PHOTO_STORAGE" -> "database";
              case "JWT_SECRET" -> "test-signing-secret-with-more-than-32-characters";
              default -> super.get(key, fallback);
            };
          }
        };
    Photos databasePhotos = new Photos(databaseConfig, db);
    String key = databasePhotos.put(user, jpeg);
    UUID image = UUID.fromString(key.split("/")[1].replace(".jpg", ""));
    long expiry = java.time.Instant.now().plusSeconds(60).getEpochSecond();
    assertArrayEquals(
        jpeg, databasePhotos.read(user, image, expiry, databasePhotos.signature(key, expiry)));
    databasePhotos.delete(key);
    assertThrows(
        RuntimeException.class,
        () -> databasePhotos.read(user, image, expiry, databasePhotos.signature(key, expiry)));
  }
}
