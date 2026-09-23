package com.calsnap;

import static com.calsnap.Models.*;

import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import java.time.*;
import java.util.*;

@Controller("/api/v1")
@ExecuteOn(TaskExecutors.BLOCKING)
public class Api {
  final FoodService food;
  final Auth auth;

  public Api(FoodService food, Auth auth) {
    this.food = food;
    this.auth = auth;
  }

  @Get("/session")
  public Map<String, Object> session(HttpRequest<?> req) {
    return auth.db.tx(
        c -> {
          var user =
              Db.one(c, "SELECT id,email,name,timezone FROM users WHERE id=?", auth.user(req));
          return Map.of(
              "user",
              user,
              "csrf",
              req.getAttribute("csrf", String.class).orElseThrow(),
              "demo",
              auth.config.local());
        });
  }

  @Post("/logout")
  public HttpResponse<?> logout() {
    return HttpResponse.noContent().cookie(auth.cookie("calsnap_session", "", 0));
  }

  @Put("/timezone")
  public Map<String, Object> timezone(HttpRequest<?> req, @Body Map<String, String> input) {
    String zone = input.get("timezone");
    try {
      ZoneId.of(zone);
    } catch (Exception ex) {
      throw bad("Choose a valid timezone");
    }
    return auth.db.tx(
        c -> {
          Db.lockUser(c, auth.user(req));
          Db.execute(c, "UPDATE users SET timezone=? WHERE id=?", zone, auth.user(req));
          return Map.of("timezone", zone);
        });
  }

  @Post(value = "/scans", consumes = MediaType.MULTIPART_FORM_DATA)
  public Map<String, Object> scan(HttpRequest<?> req, @Part("image") CompletedFileUpload image)
      throws Exception {
    return food.scan(auth.user(req), image.getBytes());
  }

  @Patch("/scans/{id}/confirm")
  public List<Map<String, Object>> confirm(HttpRequest<?> req, UUID id, @Body Confirm input) {
    return food.confirm(auth.user(req), id, input);
  }

  @Delete("/scans/{id}")
  public HttpResponse<?> discard(HttpRequest<?> req, UUID id) {
    food.discard(auth.user(req), id);
    return HttpResponse.noContent();
  }

  @Get("/goals")
  public List<Map<String, Object>> goals(HttpRequest<?> req) {
    return food.goals(auth.user(req));
  }

  @Put("/goals")
  public Map<String, Object> goal(HttpRequest<?> req, @Body GoalInput input) {
    return food.goal(auth.user(req), input);
  }

  @Get("/daily-summary{?date}")
  public Map<String, Object> summary(HttpRequest<?> req, Optional<LocalDate> date) {
    return food.summary(auth.user(req), date.orElse(null));
  }

  @Get("/logs{?from,to,page}")
  public Map<String, Object> history(
      HttpRequest<?> req, LocalDate from, LocalDate to, @QueryValue(defaultValue = "0") int page) {
    return food.history(auth.user(req), from, to, page);
  }

  public record Manual(Selection item, Instant loggedAt) {
    public Manual {
      if (item == null) throw bad("A food item is required");
    }
  }

  @Post("/logs")
  public Map<String, Object> manual(HttpRequest<?> req, @Body Manual input) {
    return food.manual(auth.user(req), input.item(), input.loggedAt());
  }

  @Patch("/logs/{id}")
  public Map<String, Object> edit(HttpRequest<?> req, UUID id, @Body Selection input) {
    return food.edit(auth.user(req), id, input);
  }

  @Delete("/logs/{id}")
  public HttpResponse<?> delete(HttpRequest<?> req, UUID id) {
    food.deleteLog(auth.user(req), id);
    return HttpResponse.noContent();
  }

  @Delete("/account")
  public HttpResponse<?> account(HttpRequest<?> req) {
    food.deleteAccount(auth.user(req));
    return HttpResponse.noContent().cookie(auth.cookie("calsnap_session", "", 0));
  }
}
