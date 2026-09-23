package com.calsnap;

import static com.calsnap.Models.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.http.*;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.math.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;

@Singleton
public class FoodService {
  final Db db;
  final Photos photos;
  final Recognition recognition;
  final ObjectMapper json;
  final MeterRegistry metrics;
  final Config config;

  public FoodService(
      Db db,
      Photos photos,
      Recognition recognition,
      ObjectMapper json,
      MeterRegistry metrics,
      Config config) {
    this.db = db;
    this.photos = photos;
    this.recognition = recognition;
    this.json = json;
    this.metrics = metrics;
    this.config = config;
  }

  public void rateLimit(UUID user) {
    boolean allowed =
        db.tx(
            c -> {
              Db.lockUser(c, user);
              Db.execute(
                  c,
                  "DELETE FROM scan_usage WHERE user_id=? AND window_start<now()-interval '2 days'",
                  user);
              var row =
                  Db.one(
                      c,
                      "INSERT INTO scan_usage(user_id,window_start,attempts)"
                          + " VALUES(?,date_trunc('hour',now()),1) ON"
                          + " CONFLICT(user_id,window_start) DO UPDATE SET"
                          + " attempts=scan_usage.attempts+1 RETURNING attempts",
                      user);
              return ((Number) row.get("attempts")).intValue()
                  <= Integer.parseInt(config.get("SCANS_PER_HOUR", "10"));
            });
    if (!allowed)
      throw new HttpStatusException(
          HttpStatus.TOO_MANY_REQUESTS,
          "Hourly scan limit reached. You can still add food manually.");
  }

  public Map<String, Object> scan(UUID user, byte[] raw) throws Exception {
    rateLimit(user);
    byte[] image = ImageNormalizer.jpeg(raw);
    String[] uploaded = {null};
    try {
      return db.tx(
          c -> {
            Db.lockUser(c, user);
            uploaded[0] = photos.put(c, user, image);
            List<Candidate> items;
            boolean manual = false;
            try {
              items = recognition.recognize(c, user, image);
            } catch (Exception ex) {
              metrics.counter("calsnap.provider.failure", "provider", "vision").increment();
              items = List.of();
              manual = true;
            }
            if (items.isEmpty()) manual = true;
            UUID id = UUID.randomUUID();
            Db.execute(
                c,
                "INSERT INTO scans(id,user_id,image_url,recognized_items,status)"
                    + " VALUES(?,?,?,?::jsonb,'pending')",
                id,
                user,
                uploaded[0],
                json.writeValueAsString(items));
            metrics
                .counter("calsnap.scans", "result", manual ? "manual" : "recognized")
                .increment();
            return Map.of(
                "id",
                id,
                "status",
                "pending",
                "imageUrl",
                photos.url(uploaded[0]),
                "items",
                items,
                "manualEntry",
                manual,
                "demo",
                config.local() && config.get("VISION_MODE", "stub").equals("stub"));
          });
    } catch (Exception ex) {
      if (uploaded[0] != null)
        try {
          photos.delete(uploaded[0]);
        } catch (Exception cleanup) {
          ex.addSuppressed(cleanup);
        }
      throw ex;
    }
  }

  public List<Map<String, Object>> confirm(UUID user, UUID scan, Confirm input) {
    return db.tx(
        c -> {
          Db.lockUser(c, user);
          var row =
              Db.one(c, "SELECT status FROM scans WHERE id=? AND user_id=? FOR UPDATE", scan, user);
          if (row.get("status").equals("confirmed"))
            return Db.rows(
                c,
                "SELECT * FROM food_logs WHERE scan_id=? AND user_id=? ORDER BY item_index",
                scan,
                user);
          if (!row.get("status").equals("pending"))
            throw new HttpStatusException(HttpStatus.CONFLICT, "This scan was discarded");
          Instant when = checkedTime(input.loggedAt());
          int index = 0;
          for (var item : input.items()) insert(c, user, scan, index++, item, when);
          Db.execute(c, "UPDATE scans SET status='confirmed' WHERE id=? AND user_id=?", scan, user);
          return Db.rows(
              c,
              "SELECT * FROM food_logs WHERE scan_id=? AND user_id=? ORDER BY item_index",
              scan,
              user);
        });
  }

  static Instant checkedTime(Instant time) {
    Instant when = time == null ? Instant.now() : time;
    if (when.isAfter(Instant.now().plusSeconds(300))
        || when.isBefore(Instant.parse("2000-01-01T00:00:00Z"))) throw bad("Invalid log time");
    return when;
  }

  private UUID insert(
      Connection c, UUID user, UUID scan, Integer index, Selection item, Instant when)
      throws Exception {
    UUID id = UUID.randomUUID();
    var m = item.macros();
    Db.execute(
        c,
        "INSERT INTO"
            + " food_logs(id,user_id,scan_id,item_index,food_name,calories,protein_g,carbs_g,fat_g,quantity_multiplier,portion_g,logged_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,1,?,?)",
        id,
        user,
        scan,
        index,
        item.foodName().strip(),
        m.calories(),
        m.protein(),
        m.carbs(),
        m.fat(),
        item.portionG(),
        when);
    return id;
  }

  public Map<String, Object> manual(UUID user, Selection item, Instant when) {
    return db.tx(
        c -> {
          Db.lockUser(c, user);
          UUID id = insert(c, user, null, null, item, checkedTime(when));
          return Db.one(c, "SELECT * FROM food_logs WHERE id=? AND user_id=?", id, user);
        });
  }

  public Map<String, Object> edit(UUID user, UUID id, Selection item) {
    return db.tx(
        c -> {
          Db.lockUser(c, user);
          Db.one(c, "SELECT id FROM food_logs WHERE id=? AND user_id=?", id, user);
          var m = item.macros();
          Db.execute(
              c,
              "UPDATE food_logs SET"
                  + " food_name=?,portion_g=?,calories=?,protein_g=?,carbs_g=?,fat_g=? WHERE id=?"
                  + " AND user_id=?",
              item.foodName(),
              item.portionG(),
              m.calories(),
              m.protein(),
              m.carbs(),
              m.fat(),
              id,
              user);
          return Db.one(c, "SELECT * FROM food_logs WHERE id=? AND user_id=?", id, user);
        });
  }

  public void deleteLog(UUID user, UUID id) {
    db.tx(
        c -> {
          Db.lockUser(c, user);
          if (Db.execute(c, "DELETE FROM food_logs WHERE id=? AND user_id=?", id, user) == 0)
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Entry not found");
          return null;
        });
  }

  public void discard(UUID user, UUID id) {
    db.tx(
        c -> {
          Db.lockUser(c, user);
          var row = Db.one(c, "SELECT * FROM scans WHERE id=? AND user_id=? FOR UPDATE", id, user);
          if (row.get("status").equals("confirmed"))
            throw new HttpStatusException(HttpStatus.CONFLICT, "Delete the logged entries instead");
          photos.delete(c, row.get("image_url").toString());
          Db.execute(
              c,
              "UPDATE scans SET status='discarded',image_url='',recognized_items='[]' WHERE id=?"
                  + " AND user_id=?",
              id,
              user);
          return null;
        });
  }

  public List<Map<String, Object>> goals(UUID user) {
    return db.tx(
        c -> {
          Db.one(c, "SELECT id FROM users WHERE id=?", user);
          return Db.rows(
              c,
              "SELECT * FROM goals WHERE user_id=? ORDER BY effective_from DESC,created_at DESC",
              user);
        });
  }

  public Map<String, Object> goal(UUID user, GoalInput input) {
    return db.tx(
        c -> {
          Db.lockUser(c, user);
          UUID id = UUID.randomUUID();
          Db.execute(
              c,
              "INSERT INTO goals(id,user_id,daily_calories,daily_protein_g,effective_from)"
                  + " VALUES(?,?,?,?,?)",
              id,
              user,
              input.dailyCalories(),
              input.dailyProteinG(),
              input.effectiveFrom());
          return Db.one(c, "SELECT * FROM goals WHERE id=? AND user_id=?", id, user);
        });
  }

  public Map<String, Object> summary(UUID user, LocalDate date) {
    return db.tx(
        c -> {
          c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
          var account = Db.one(c, "SELECT timezone FROM users WHERE id=?", user);
          ZoneId zone = ZoneId.of(account.get("timezone").toString());
          LocalDate day = date == null ? LocalDate.now(zone) : date;
          var range = bounds(day, zone);
          var totals =
              Db.one(
                  c,
                  "SELECT coalesce(sum(calories),0) AS calories,coalesce(sum(protein_g),0) AS"
                      + " protein,coalesce(sum(carbs_g),0) AS carbs,coalesce(sum(fat_g),0) AS fat"
                      + " FROM food_logs WHERE user_id=? AND logged_at>=? AND logged_at<?",
                  user,
                  range[0],
                  range[1]);
          var goals =
              Db.rows(
                  c,
                  "SELECT * FROM goals WHERE user_id=? AND effective_from<=? ORDER BY"
                      + " effective_from DESC,created_at DESC LIMIT 1",
                  user,
                  day);
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("date", day.toString());
          out.put("timezone", zone.toString());
          out.put("consumed", totals);
          out.put("goal", goals.isEmpty() ? null : goals.getFirst());
          out.put(
              "remaining",
              goals.isEmpty()
                  ? null
                  : Map.of(
                      "calories",
                      ((BigDecimal) goals.getFirst().get("daily_calories"))
                          .subtract((BigDecimal) totals.get("calories")),
                      "protein",
                      ((BigDecimal) goals.getFirst().get("daily_protein_g"))
                          .subtract((BigDecimal) totals.get("protein"))));
          out.put("entries", entries(c, user, range[0], range[1], null));
          return out;
        });
  }

  List<Map<String, Object>> entries(Connection c, UUID user, Instant from, Instant to, Integer page)
      throws Exception {
    var rows =
        Db.rows(
            c,
            "SELECT l.*,s.image_url AS photo_key FROM food_logs l LEFT JOIN scans s ON"
                + " l.scan_id=s.id AND l.user_id=s.user_id WHERE l.user_id=? AND l.logged_at>=? AND"
                + " l.logged_at<? ORDER BY l.logged_at DESC,l.id"
                + (page == null ? "" : " LIMIT 21 OFFSET " + page * 20),
            user,
            from,
            to);
    for (var row : rows) {
      Object key = row.remove("photo_key");
      row.put(
          "imageUrl", key == null || key.toString().isBlank() ? "" : photos.url(key.toString()));
    }
    return rows;
  }

  public Map<String, Object> history(UUID user, LocalDate from, LocalDate to, int page) {
    if (page < 0 || page > 10000 || to.isBefore(from) || from.plusYears(5).isBefore(to))
      throw bad("Invalid date range or page");
    return db.tx(
        c -> {
          ZoneId zone =
              ZoneId.of(
                  Db.one(c, "SELECT timezone FROM users WHERE id=?", user)
                      .get("timezone")
                      .toString());
          var rows =
              entries(
                  c,
                  user,
                  from.atStartOfDay(zone).toInstant(),
                  to.plusDays(1).atStartOfDay(zone).toInstant(),
                  page);
          return Map.of(
              "entries",
              rows.subList(0, Math.min(20, rows.size())),
              "hasMore",
              rows.size() > 20,
              "page",
              page);
        });
  }

  public void deleteAccount(UUID user) {
    db.tx(
        c -> {
          Db.lockUser(c, user);
          photos.deleteOwner(c, user);
          Db.execute(c, "DELETE FROM users WHERE id=?", user);
          return null;
        });
  }
}
