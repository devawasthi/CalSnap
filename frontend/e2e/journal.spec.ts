import { test, expect } from "@playwright/test";
import path from "node:path";
test("mobile scan, confirmation, edit, deletion and effective goal history", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: "Explore the local demo" }).click();
  await expect(
    page.getByRole("heading", { name: /A fresh perspective/ }),
  ).toBeVisible();
  const before = await page.request
    .get("/api/v1/daily-summary")
    .then((r) => r.json());
  const baseline = before.entries.length;
  const oldIds = new Set(before.entries.map((e: { id: string }) => e.id));
  const oldGoals = await page.request
    .get("/api/v1/goals")
    .then((r) => r.json());
  await page
    .getByLabel("Upload food photo")
    .setInputFiles(path.resolve("public/icon-192.png"));
  await expect(page.getByText("Demo mode · Sample recognition")).toBeVisible();
  await expect(page.getByRole("dialog").getByLabel("Food name")).toHaveCount(2);
  await page
    .getByRole("dialog")
    .getByLabel("Food name")
    .first()
    .fill("Browser test lunch");
  await page.getByRole("dialog").getByLabel("Portion (g)").first().fill("100");
  await page.getByRole("button", { name: "Log this meal" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".meal")).toHaveCount(baseline + 2);
  const after = await page.request
    .get("/api/v1/daily-summary")
    .then((r) => r.json());
  expect(Number(after.consumed.calories)).toBeGreaterThan(
    Number(before.consumed.calories),
  );
  await page
    .getByRole("button", { name: "Edit Browser test lunch", exact: true })
    .click();
  await page.getByLabel("Portion (g)").fill("50");
  await page.getByRole("button", { name: "Save meal" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Edit Browser test lunch", exact: true })
    .click();
  await page.getByRole("button", { name: "Delete entry" }).click();
  await expect(page.locator(".meal")).toHaveCount(baseline + 1);
  await page.getByRole("button", { name: "Your goals", exact: true }).click();
  await page.getByLabel("Daily calories (kcal)").fill("2100");
  await page.getByRole("button", { name: "Save goals", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("button", { name: "Your goals", exact: true }).click();
  await expect(page.locator(".goal-history p")).toHaveCount(
    oldGoals.length + 1,
  );
  await page.getByRole("button", { name: "Close dialog" }).click();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "test-results/calsnap-mobile.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 768, height: 1024 });
  await expect(
    page.getByRole("button", { name: "Your account", exact: true }),
  ).toBeVisible();
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({
    path: "test-results/calsnap-desktop.png",
    fullPage: true,
  });
  // Restore the shared demo fixture so the test is repeatable.
  const sess = await page.request.get("/api/v1/session").then((r) => r.json());
  const logs = await page.request
    .get("/api/v1/daily-summary")
    .then((r) => r.json());
  for (const entry of logs.entries.filter(
    (e: { id: string }) => !oldIds.has(e.id),
  )) {
    await page.request.delete("/api/v1/logs/" + entry.id, {
      headers: { "X-CSRF-Token": sess.csrf },
    });
  }
});
test("authentication and CSRF protect writes", async ({ request }) => {
  expect((await request.get("/api/v1/daily-summary")).status()).toBe(401);
  await request.post("/auth/demo", {
    headers: { Origin: "http://localhost:5173" },
  });
  expect(
    (
      await request.put("/api/v1/goals", {
        data: {
          dailyCalories: 2000,
          dailyProteinG: 100,
          effectiveFrom: "2026-09-21",
        },
      })
    ).status(),
  ).toBe(403);
});
