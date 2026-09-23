import { describe, it, expect } from "vitest";
import {
  scale,
  dayInZone,
  normalizeScan,
  nutritionForCandidate,
  type Scan,
} from "./api";
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
  it("normalizes a manual fallback response that omits empty items", () => {
    const response = {
      id: "scan-id",
      imageUrl: "/photo",
      manualEntry: true,
      demo: false,
    } as Scan;
    expect(normalizeScan(response).items).toEqual([]);
  });
  it("normalizes a recognized item that omits empty nutrition candidates", () => {
    const response = {
      id: "scan-id",
      imageUrl: "/photo",
      manualEntry: false,
      demo: false,
      items: [
        {
          foodName: "mixed vegetables",
          portionG: 180,
          confidence: 0.84,
        },
      ],
    } as Scan;
    expect(normalizeScan(response).items[0]).toEqual({
      foodName: "mixed vegetables",
      portionG: 180,
      confidence: 0.84,
      candidates: [],
    });
  });
  it("uses the AI nutrition estimate when USDA has no match", () => {
    expect(
      nutritionForCandidate({
        foodName: "mixed vegetables cooked",
        portionG: 120,
        confidence: 0.8,
        candidates: [],
        estimatedMacros: {
          calories: 145,
          protein: 4.2,
          carbs: 19,
          fat: 6.5,
        },
      }),
    ).toEqual({ calories: 145, protein: 4.2, carbs: 19, fat: 6.5 });
  });
});
