package com.calsnap;

import static com.calsnap.Models.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class FlowTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  static Db db;
  static FoodService food;
  UUID user, other, scan;
  static final Macros macros =
      new Macros(
          new BigDecimal("250"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("8"));
  static final Selection item = new Selection("Rice bowl", new BigDecimal("200"), macros);

  @BeforeAll
  static void setup() {
    var ds = new PGSimpleDataSource();
    ds.setURL(postgres.getJdbcUrl());
    ds.setUser(postgres.getUsername());
    ds.setPassword(postgres.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    db = new Db(ds);
    food =
        new FoodService(
            db,
            new Photos(
                new Config() {
                  public boolean local() {
                    return true;
                  }
                },
                db),
            null,
            new ObjectMapper(),
            new SimpleMeterRegistry(),
            new Config());
  }

  @BeforeEach
  void seed() {
    user = UUID.randomUUID();
    other = UUID.randomUUID();
    scan = UUID.randomUUID();
    db.tx(
        c -> {
          for (UUID id : List.of(user, other))
            Db.execute(
                c,
                "INSERT INTO users(id,email,name,oauth_provider,oauth_subject,timezone)"
                    + " VALUES(?,'test@example.com','Test','test',?,'Asia/Kolkata')",
                id,
                id.toString());
          Db.execute(
              c,
              "INSERT INTO scans(id,user_id,image_url,status) VALUES(?,?,'','pending')",
              scan,
              user);
          return null;
        });
    food.goal(
        user,
        new GoalInput(new BigDecimal("2000"), new BigDecimal("120"), LocalDate.of(2026, 1, 1)));
  }

  @Test
  void confirmDeductEditDeleteAndReplay() {
    var when = Instant.parse("2026-09-20T20:00:00Z");
    var result = food.confirm(user, scan, new Confirm(List.of(item), when));
    UUID log = (UUID) result.getFirst().get("id");
    assertEquals(
        log, food.confirm(user, scan, new Confirm(List.of(item), when)).getFirst().get("id"));
    var summary = food.summary(user, LocalDate.of(2026, 9, 21));
    assertEquals(new BigDecimal("1750.00"), ((Map<?, ?>) summary.get("remaining")).get("calories"));
    assertEquals(new BigDecimal("100.00"), ((Map<?, ?>) summary.get("remaining")).get("protein"));
    food.edit(
        user,
        log,
        new Selection("Half bowl", new BigDecimal("100"), macros.scale(new BigDecimal("0.5"))));
    assertEquals(
        new BigDecimal("1875.00"),
        ((Map<?, ?>) food.summary(user, LocalDate.of(2026, 9, 21)).get("remaining"))
            .get("calories"));
    food.deleteLog(user, log);
    assertEquals(
        new BigDecimal("2000.00"),
        ((Map<?, ?>) food.summary(user, LocalDate.of(2026, 9, 21)).get("remaining"))
            .get("calories"));
    assertTrue(food.confirm(user, scan, new Confirm(List.of(item), when)).isEmpty());
  }

  @Test
  void isolatesUsers() {
    assertThrows(
        RuntimeException.class, () -> food.confirm(other, scan, new Confirm(List.of(item), null)));
    var row = food.manual(user, item, Instant.now());
    UUID id = (UUID) row.get("id");
    assertThrows(RuntimeException.class, () -> food.edit(other, id, item));
    assertThrows(RuntimeException.class, () -> food.deleteLog(other, id));
    assertTrue(((List<?>) food.summary(other, null).get("entries")).isEmpty());
  }

  @Test
  void effectiveDatedGoalsDoNotChangePast() {
    food.goal(
        user,
        new GoalInput(new BigDecimal("1800"), new BigDecimal("110"), LocalDate.of(2026, 9, 22)));
    assertEquals(
        new BigDecimal("2000.00"),
        ((Map<?, ?>) food.summary(user, LocalDate.of(2026, 9, 21)).get("goal"))
            .get("daily_calories"));
    assertEquals(
        new BigDecimal("1800.00"),
        ((Map<?, ?>) food.summary(user, LocalDate.of(2026, 9, 22)).get("goal"))
            .get("daily_calories"));
    assertNull(food.summary(user, LocalDate.of(2025, 1, 1)).get("goal"));
    assertEquals(2, food.goals(user).size());
  }

  @Test
  void concurrentConfirmationCreatesOneSet() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Object> call =
          () ->
              food.confirm(
                  user,
                  scan,
                  new Confirm(List.of(item, item), Instant.parse("2026-09-21T06:00:00Z")));
      for (var f : pool.invokeAll(List.of(call, call))) f.get();
    }
    assertEquals(
        2, ((List<?>) food.summary(user, LocalDate.of(2026, 9, 21)).get("entries")).size());
  }

  @Test
  void overGoalRemainsNegative() {
    food.goal(
        user,
        new GoalInput(new BigDecimal("100"), new BigDecimal("10"), LocalDate.of(2026, 9, 21)));
    food.manual(user, item, Instant.parse("2026-09-21T06:00:00Z"));
    assertEquals(
        new BigDecimal("-150.00"),
        ((Map<?, ?>) food.summary(user, LocalDate.of(2026, 9, 21)).get("remaining"))
            .get("calories"));
  }

  @Test
  void deletionCascadesAndInvalidatesUser() {
    food.manual(user, item, Instant.now());
    db.tx(
        c -> {
          Db.execute(c, "DELETE FROM scans WHERE user_id=?", user);
          return null;
        });
    food.deleteAccount(user);
    assertThrows(RuntimeException.class, () -> food.summary(user, null));
    assertEquals(0, db.tx(c -> Db.rows(c, "SELECT * FROM food_logs WHERE user_id=?", user)).size());
  }

  @Test
  void hourlyLimitSharedAcrossInstances() {
    for (int i = 0; i < 10; i++) food.rateLimit(user);
    assertThrows(RuntimeException.class, () -> food.rateLimit(user));
    assertThrows(
        RuntimeException.class,
        () -> new FoodService(db, null, null, null, null, new Config()).rateLimit(user));
    food.rateLimit(other);
  }

  @Test
  void discardedScanCannotBeConfirmed() {
    db.tx(
        c -> {
          Db.execute(c, "UPDATE scans SET status='discarded' WHERE id=?", scan);
          return null;
        });
    assertThrows(
        RuntimeException.class, () -> food.confirm(user, scan, new Confirm(List.of(item), null)));
  }
}
