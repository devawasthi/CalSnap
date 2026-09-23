import { describe, it, expect } from "vitest";
import { scale, dayInZone } from "./api";
describe("nutrition and calendar calculations", () => {
  it("scales all four macros for a changed portion", () => {
    expect(
      scale({ calories: 89, protein: 1.09, carbs: 22.84, fat: 0.33 }, 1.2),
    ).toEqual({ calories: 106.8, protein: 1.31, carbs: 27.41, fat: 0.4 });
  });
  it("uses the account timezone rather than UTC", () => {
    expect(dayInZone("Asia/Kolkata", new Date("2026-09-20T20:00:00Z"))).toBe(
      "2026-09-21",
    );
  });
});
