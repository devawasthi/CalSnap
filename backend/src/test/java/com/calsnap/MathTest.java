package com.calsnap;

import static com.calsnap.Models.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MathTest {
  @Test
  void scalesExactDecimals() {
    var m =
        new Macros(
            new BigDecimal("89"),
            new BigDecimal("1.09"),
            new BigDecimal("22.84"),
            new BigDecimal("0.33"));
    var scaled = m.scale(new BigDecimal("1.2"));
    assertEquals(new BigDecimal("106.80"), scaled.calories());
    assertEquals(new BigDecimal("1.31"), scaled.protein());
  }

  @Test
  void daylightSavingUsesCalendarDays() {
    var spring = bounds(LocalDate.of(2026, 3, 8), ZoneId.of("America/New_York"));
    assertEquals(23, Duration.between(spring[0], spring[1]).toHours());
    var fall = bounds(LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York"));
    assertEquals(25, Duration.between(fall[0], fall[1]).toHours());
  }

  @Test
  void rejectsInvalidInputs() {
    assertThrows(
        RuntimeException.class,
        () -> new Macros(new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    assertThrows(RuntimeException.class, () -> new Selection("", BigDecimal.ONE, Macros.zero()));
    assertThrows(RuntimeException.class, () -> new Confirm(java.util.List.of(), null));
    assertThrows(
        RuntimeException.class, () -> new Confirm(java.util.Arrays.asList((Selection) null), null));
    assertThrows(
        RuntimeException.class,
        () -> new Selection("Tiny serving", new BigDecimal("0.001"), Macros.zero()));
    assertThrows(
        RuntimeException.class,
        () -> new GoalInput(new BigDecimal("0.5"), BigDecimal.TEN, LocalDate.now()));
    assertThrows(RuntimeException.class, () -> new Api.Manual(null, null));
  }
}
