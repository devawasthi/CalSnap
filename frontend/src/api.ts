export type Macros = {
  calories: number;
  protein: number;
  carbs: number;
  fat: number;
};
export type Entry = {
  id: string;
  food_name: string;
  portion_g: number;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  logged_at: string;
  imageUrl: string;
};
export type Goal = {
  daily_calories: number;
  daily_protein_g: number;
  effective_from: string;
  created_at: string;
};
export type Summary = {
  date: string;
  timezone: string;
  consumed: Macros;
  remaining: { calories: number; protein: number } | null;
  goal: Goal | null;
  entries: Entry[];
};
export type Nutrition = { fdcId: string; description: string; per100g: Macros };
export type Candidate = {
  foodName: string;
  portionG: number;
  confidence: number;
  candidates: Nutrition[];
};
export type Scan = {
  id: string;
  imageUrl: string;
  items: Candidate[];
  manualEntry: boolean;
  demo: boolean;
};
export function normalizeScan(scan: Scan): Scan {
  return {
    ...scan,
    items: Array.isArray(scan.items)
      ? scan.items.map((item) => ({
          ...item,
          candidates: Array.isArray(item.candidates) ? item.candidates : [],
        }))
      : [],
  };
}
export type Selection = { foodName: string; portionG: number; macros: Macros };
export type Session = {
  user: { name: string; email: string; timezone: string };
  csrf: string;
  demo: boolean;
};
let csrf = "";
export function setCsrf(value: string) {
  csrf = value;
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const multipart = body instanceof FormData;
  const response = await fetch("/api/v1" + path, {
    method,
    credentials: "same-origin",
    headers: {
      ...(method === "GET" ? {} : { "X-CSRF-Token": csrf }),
      ...(body && !multipart ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? (multipart ? body : JSON.stringify(body)) : undefined,
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(
      response.status === 401
        ? "Your session has ended. Please sign in again."
        : data.message ||
          data._embedded?.errors?.[0]?.message ||
          "Something went wrong. Please try again.",
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
export function scale(macros: Macros, factor: number): Macros {
  return Object.fromEntries(
    Object.entries(macros).map(([key, value]) => [
      key,
      Math.round(value * factor * 100) / 100,
    ]),
  ) as Macros;
}
export function dayInZone(zone: string, date = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}
