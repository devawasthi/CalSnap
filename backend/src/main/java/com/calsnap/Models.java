package com.calsnap;

import java.math.*;
import java.time.*;
import java.util.*;

public final class Models {
  private Models() {}

  public record Macros(BigDecimal calories, BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
    public Macros {
      check(calories, 50000);
      check(protein, 10000);
      check(carbs, 10000);
      check(fat, 10000);
    }

    private static void check(BigDecimal n, int max) {
      if (n == null || n.signum() < 0 || n.compareTo(BigDecimal.valueOf(max)) > 0)
        throw bad("Macros are outside the supported range");
    }

    public Macros scale(BigDecimal factor) {
      return new Macros(
          round(calories.multiply(factor)),
          round(protein.multiply(factor)),
          round(carbs.multiply(factor)),
          round(fat.multiply(factor)));
    }

    public Macros plus(Macros b) {
      return new Macros(
          calories.add(b.calories), protein.add(b.protein), carbs.add(b.carbs), fat.add(b.fat));
    }

    public static Macros zero() {
      return new Macros(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
  }

  public record Candidate(
      String foodName,
      BigDecimal portionG,
      double confidence,
      List<Nutrition> candidates,
      Macros estimatedMacros) {}

  public record Nutrition(String fdcId, String description, Macros per100g) {}

  public record Selection(String foodName, BigDecimal portionG, Macros macros) {
    public Selection {
      if (foodName == null || foodName.isBlank() || foodName.length() > 200)
        throw bad("Enter a food name up to 200 characters");
      if (portionG == null
          || portionG.compareTo(new BigDecimal("0.01")) < 0
          || portionG.compareTo(new BigDecimal("10000")) > 0)
        throw bad("Portion must be between 0.01 and 10000 grams");
      if (macros == null) throw bad("Enter all macros");
    }
  }

  public record Confirm(List<Selection> items, Instant loggedAt) {
    public Confirm {
      if (items == null
          || items.isEmpty()
          || items.size() > 20
          || items.stream().anyMatch(Objects::isNull)) throw bad("Select 1 to 20 food items");
    }
  }

  public record GoalInput(
      BigDecimal dailyCalories, BigDecimal dailyProteinG, LocalDate effectiveFrom) {
    public GoalInput {
      if (dailyCalories == null
          || dailyCalories.compareTo(BigDecimal.ONE) < 0
          || dailyCalories.compareTo(new BigDecimal("20000")) > 0
          || dailyProteinG == null
          || dailyProteinG.compareTo(BigDecimal.ONE) < 0
          || dailyProteinG.compareTo(new BigDecimal("1000")) > 0
          || effectiveFrom == null) throw bad("Enter valid goals and effective date");
    }
  }

  public static BigDecimal round(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  public static io.micronaut.http.exceptions.HttpStatusException bad(String message) {
    return new io.micronaut.http.exceptions.HttpStatusException(
        io.micronaut.http.HttpStatus.BAD_REQUEST, message);
  }

  public static Instant[] bounds(LocalDate day, ZoneId zone) {
    return new Instant[] {
      day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()
    };
  }
}
