import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Camera,
  Leaf,
  ArrowUpRight,
  Plus,
  ChevronLeft,
  ChevronRight,
  X,
  Check,
  Utensils,
  History,
  SlidersHorizontal,
  LogOut,
  Trash2,
  Pencil,
  Flame,
  ScanLine,
  Sun,
  ArrowRight,
  LoaderCircle,
  AlertCircle,
} from "lucide-react";
import {
  api,
  setCsrf,
  scale,
  dayInZone,
  type Entry,
  type Goal,
  type Summary,
  type Scan,
  type Selection,
  type Session,
} from "./api";
import "./styles.css";
const fmt = (n: number) => Math.round(Number(n)).toLocaleString();
const empty: Selection = {
  foodName: "",
  portionG: 100,
  macros: { calories: 0, protein: 0, carbs: 0, fat: 0 },
};
type Dialog = "scan" | "manual" | "goals" | "account" | null;
function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current!;
    d.showModal();
    return () => d.close();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="modal-head">
        <h2>{title}</h2>
        <button className="icon" aria-label="Close dialog" onClick={onClose}>
          <X size={21} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
function FoodFields({
  value,
  onChange,
}: {
  value: Selection;
  onChange: (v: Selection) => void;
}) {
  return (
    <div className="food-fields">
      <label>
        Food name
        <input
          required
          maxLength={200}
          value={value.foodName}
          onChange={(e) => onChange({ ...value, foodName: e.target.value })}
        />
      </label>
      <label>
        Portion (g)
        <input
          required
          type="number"
          min="0.01"
          max="10000"
          step="0.01"
          value={value.portionG}
          onChange={(e) => {
            const grams = Number(e.target.value);
            onChange({
              ...value,
              portionG: grams,
              macros: scale(
                value.macros,
                value.portionG > 0 ? grams / value.portionG : 1,
              ),
            });
          }}
        />
      </label>
      <div className="macro-inputs">
        {(["calories", "protein", "carbs", "fat"] as const).map((key) => (
          <label key={key}>
            {key === "calories" ? "Calories (kcal)" : `${key} (g)`}
            <input
              required
              type="number"
              min="0"
              max={key === "calories" ? 50000 : 10000}
              step="0.01"
              value={value.macros[key]}
              onChange={(e) =>
                onChange({
                  ...value,
                  macros: { ...value.macros, [key]: Number(e.target.value) },
                })
              }
            />
          </label>
        ))}
      </div>
    </div>
  );
}
function App() {
  const [session, setSession] = useState<Session | null>(null),
    [loading, setLoading] = useState(true),
    [summary, setSummary] = useState<Summary | null>(null),
    [date, setDate] = useState(""),
    [tab, setTab] = useState("today"),
    [dialog, setDialog] = useState<Dialog>(null),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [scan, setScan] = useState<Scan | null>(null),
    [selections, setSelections] = useState<Selection[]>([]),
    [selected, setSelected] = useState<boolean[]>([]),
    [manual, setManual] = useState<Selection>(empty),
    [editing, setEditing] = useState<Entry | null>(null),
    [goals, setGoals] = useState<Goal[]>([]),
    [history, setHistory] = useState<Entry[]>([]),
    [page, setPage] = useState(0),
    [hasMore, setHasMore] = useState(false),
    [online, setOnline] = useState(navigator.onLine),
    [deleteText, setDeleteText] = useState("");
  const [goalCalories, setGoalCalories] = useState(2000),
    [goalProtein, setGoalProtein] = useState(120),
    [goalDate, setGoalDate] = useState("");
  const upload = useRef<HTMLInputElement>(null);
  useEffect(() => {
    api<Session>("/session")
      .then((s) => {
        setCsrf(s.csrf);
        setSession(s);
        setDate(dayInZone(s.user.timezone));
      })
      .catch(() => {})
      .finally(() => setLoading(false));
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  useEffect(() => {
    if (!session || !date) return;
    let active = true;
    api<Summary>("/daily-summary?date=" + date)
      .then((s) => {
        if (active) setSummary(s);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [session, date]);
  useEffect(() => {
    if (tab !== "history" || !session) return;
    let active = true;
    api<{ entries: Entry[]; hasMore: boolean }>(
      `/logs?from=${Number(today.slice(0, 4)) - 4}-01-01&to=${dayInZone(session.user.timezone)}&page=${page}`,
    )
      .then((data) => {
        if (active) {
          setHistory((old) =>
            page ? [...old, ...data.entries] : data.entries,
          );
          setHasMore(data.hasMore);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [tab, page, session]);
  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(""), 4500);
    return () => clearTimeout(t);
  }, [notice]);
  async function refresh(day = date) {
    if (session && day)
      setSummary(await api<Summary>("/daily-summary?date=" + day));
    if (tab === "history") {
      const data = await api<{ entries: Entry[]; hasMore: boolean }>(
        `/logs?from=${Number(today.slice(0, 4)) - 4}-01-01&to=${dayInZone(session!.user.timezone)}&page=0`,
      );
      setHistory(data.entries);
      setHasMore(data.hasMore);
      setPage(0);
    }
  }
  async function run(action: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function demo() {
    await run(async () => {
      const response = await fetch("/auth/demo", { method: "POST" });
      if (!response.ok)
        throw new Error(
          "Demo sign-in is unavailable. Run the local seed script first.",
        );
      const s = await api<Session>("/session");
      setCsrf(s.csrf);
      setSession(s);
      setDate(dayInZone(s.user.timezone));
    });
  }
  function startManual(entry?: Entry) {
    setEditing(entry || null);
    setManual(
      entry
        ? {
            foodName: entry.food_name,
            portionG: Number(entry.portion_g),
            macros: {
              calories: Number(entry.calories),
              protein: Number(entry.protein_g),
              carbs: Number(entry.carbs_g),
              fat: Number(entry.fat_g),
            },
          }
        : structuredClone(empty),
    );
    setDialog("manual");
  }
  async function scanPhoto(file?: File) {
    if (!file) return;
    await run(async () => {
      setDialog("scan");
      setScan(null);
      const body = new FormData();
      body.append("image", file);
      const result = await api<Scan>("/scans", "POST", body);
      setScan(result);
      const items = result.items.length
        ? result.items.map((item) => ({
            foodName: item.foodName,
            portionG: item.portionG,
            macros: item.candidates.length
              ? scale(item.candidates[0].per100g, item.portionG / 100)
              : { ...empty.macros },
          }))
        : [structuredClone(empty)];
      setSelections(items);
      setSelected(items.map(() => true));
    });
    if (upload.current) upload.current.value = "";
  }
  async function closeScan() {
    if (busy) return;
    if (scan)
      await run(async () => {
        await api("/scans/" + scan.id, "DELETE");
        setDialog(null);
        setScan(null);
      });
    else setDialog(null);
  }
  async function openGoals() {
    if (busy) return;
    setGoalCalories(Number(summary?.goal?.daily_calories || 2000));
    setGoalProtein(Number(summary?.goal?.daily_protein_g || 120));
    setGoalDate(dayInZone(session!.user.timezone));
    setDialog("goals");
    await run(async () => setGoals(await api<Goal[]>("/goals")));
  }
  const today = session ? dayInZone(session.user.timezone) : "";
  function changeDay(delta: number) {
    const d = new Date(date + "T12:00:00Z");
    d.setUTCDate(d.getUTCDate() + delta);
    setDate(d.toISOString().slice(0, 10));
  }
  const consumed = summary?.consumed || empty.macros,
    goal = summary?.goal;
  const entries = tab === "history" ? history : summary?.entries || [];
  function row(entry: Entry) {
    return (
      <article className="meal" key={entry.id}>
        <div className="meal-photo">
          {entry.imageUrl ? (
            <img alt={entry.food_name} src={entry.imageUrl} />
          ) : (
            <Utensils size={23} />
          )}
        </div>
        <div className="meal-info">
          <h3>{entry.food_name}</h3>
          <p>
            {fmt(entry.portion_g)} g <span>·</span>{" "}
            {new Date(entry.logged_at).toLocaleTimeString([], {
              timeZone: session!.user.timezone,
              hour: "numeric",
              minute: "2-digit",
            })}
            {tab === "history"
              ? " · " +
                dayInZone(session!.user.timezone, new Date(entry.logged_at))
              : ""}
          </p>
          <small>
            {fmt(entry.protein_g)}g protein <span>·</span> {fmt(entry.carbs_g)}g
            carbs <span>·</span> {fmt(entry.fat_g)}g fat
          </small>
        </div>
        <strong className="meal-kcal">
          {fmt(entry.calories)}
          <small>kcal</small>
        </strong>
        <button
          className="icon"
          aria-label={"Edit " + entry.food_name}
          disabled={busy}
          onClick={() => startManual(entry)}
        >
          <Pencil size={17} />
        </button>
      </article>
    );
  }
  if (loading)
    return (
      <div className="loading">
        <LoaderCircle className="spin" /> Opening your journal…
      </div>
    );
  return (
    <>
      <div className="shell">
        <aside className="sidebar">
          <a href="/" className="brand">
            <span>
              <ScanLine />
            </span>
            CalSnap<span className="brand-dot">.</span>
          </a>
          <div className="sidebar-inner">
            <p className="eyebrow">A LITTLE EVERY DAY</p>
            <nav>
              {[
                { id: "today", icon: Sun, label: "Daily overview" },
                { id: "history", icon: History, label: "Food journal" },
              ].map((item) => (
                <button
                  key={item.id}
                  className={tab === item.id ? "active" : ""}
                  onClick={() => {
                    setTab(item.id);
                    setPage(0);
                  }}
                >
                  <item.icon size={20} />
                  {item.label}
                  {tab === item.id && <span className="nav-dot" />}
                </button>
              ))}
              <button disabled={busy} onClick={() => session && openGoals()}>
                <SlidersHorizontal size={20} />
                Your goals
              </button>
            </nav>
            <div className="sidebar-note">
              <Leaf size={27} />
              <h3>
                Small steps.
                <br />
                Lasting habits.
              </h3>
              <p>One meal at a time is a great place to start.</p>
              <span className="note-leaf">◒</span>
            </div>
          </div>
          <button
            className="profile"
            aria-label="Your account"
            onClick={() => session && setDialog("account")}
          >
            <span className="avatar">
              {session?.user.name.slice(0, 1) || "C"}
            </span>
            <span>
              <strong>{session?.user.name || "Your daily companion"}</strong>
              <small>{session ? "Your account" : "Eat well. Feel good."}</small>
            </span>
            {session && <SlidersHorizontal size={17} />}
          </button>
        </aside>
        {!session ? (
          <main className="welcome">
            <span className="pill">
              <Leaf size={14} /> A little clarity in every meal
            </span>
            <h1>
              Good food.
              <br />A clearer picture.
            </h1>
            <p>
              Snap your meal, make it yours, and build a food journal that fits
              your day.
            </p>
            <div className="welcome-art">
              <div className="plate">
                <Leaf size={68} />
                <span>know your nourishment</span>
              </div>
            </div>
            <a className="primary" href="/auth/google">
              Continue with Google <ArrowRight size={19} />
            </a>
            {import.meta.env.VITE_DEMO === "true" && (
              <button className="secondary" disabled={busy} onClick={demo}>
                Explore the local demo
              </button>
            )}
            <small>
              Photo estimates are a starting point. You’re always in control.
            </small>
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
          </main>
        ) : (
          <main>
            <header className="topbar">
              <span className="breadcrumb">
                My wellbeing <ChevronRight size={14} />{" "}
                <strong>
                  {tab === "history" ? "Food journal" : "Daily overview"}
                </strong>
              </span>
              <span className="live-dot">
                {session.demo ? "LOCAL DEMO" : "YOUR PERSONAL JOURNAL"}
              </span>
            </header>
            <section className="page-heading">
              <div>
                <p className="eyebrow">MAKE ROOM FOR FEELING GOOD</p>
                <h1>
                  {tab === "history"
                    ? "Your food journal."
                    : `A fresh perspective, ${session.user.name.split(" ")[0]}.`}
                </h1>
                <p>
                  {tab === "history"
                    ? "The meals that make up your days."
                    : "A little awareness. A little balance. All at your pace."}
                </p>
              </div>
              <button
                className="primary"
                disabled={busy || !online}
                onClick={() => upload.current?.click()}
              >
                <Camera size={19} /> Scan food
              </button>
            </section>
            <input
              ref={upload}
              className="sr-only"
              type="file"
              accept="image/jpeg,image/png"
              capture="environment"
              aria-label="Upload food photo"
              onChange={(e) => scanPhoto(e.target.files?.[0])}
            />
            {!online && (
              <p className="error" role="status">
                You’re offline. Reconnect to save changes.
              </p>
            )}
            {error && !dialog && (
              <p className="error" role="alert">
                <AlertCircle size={17} />
                {error}
                <button
                  className="icon"
                  onClick={() => setError("")}
                  aria-label="Dismiss error"
                >
                  <X size={16} />
                </button>
              </p>
            )}
            {tab === "today" && (
              <>
                <div className="date-row">
                  <span>
                    <Sun size={18} />
                    {date === today
                      ? "Today"
                      : new Date(date + "T12:00:00").toLocaleDateString([], {
                          weekday: "long",
                        })}
                    <i />{" "}
                    {new Date(date + "T12:00:00").toLocaleDateString([], {
                      month: "long",
                      day: "numeric",
                      year: "numeric",
                    })}
                  </span>
                  <div>
                    <button
                      className="icon"
                      aria-label="Previous day"
                      onClick={() => changeDay(-1)}
                    >
                      <ChevronLeft size={17} />
                    </button>
                    <input
                      aria-label="Journal date"
                      type="date"
                      value={date}
                      max={today}
                      onChange={(e) =>
                        e.target.value && setDate(e.target.value)
                      }
                    />
                    <button
                      className="icon"
                      disabled={date >= today}
                      aria-label="Next day"
                      onClick={() => changeDay(1)}
                    >
                      <ChevronRight size={17} />
                    </button>
                  </div>
                </div>
                <section className="stats">
                  <article className="calorie-card">
                    <div className="card-label">
                      <span>
                        <Flame size={18} /> Daily energy
                      </span>
                      <span className="tiny-pill">CALORIES</span>
                    </div>
                    <div className="energy-content">
                      <div>
                        <h2>
                          {goal
                            ? fmt(
                                Math.abs(Number(summary?.remaining?.calories)),
                              )
                            : fmt(consumed.calories)}
                          <span>kcal</span>
                        </h2>
                        <p>
                          {goal
                            ? Number(summary?.remaining?.calories) < 0
                              ? "above your daily goal"
                              : "remaining for your day"
                            : "logged today"}
                        </p>
                      </div>
                      <div
                        className="ring"
                        style={
                          {
                            "--progress": `${Math.min(100, (Number(consumed.calories) / Number(goal?.daily_calories || 1)) * 100)}%`,
                          } as React.CSSProperties
                        }
                      >
                        <div>
                          <Leaf size={24} />
                          <strong>
                            {goal
                              ? Math.round(
                                  (Number(consumed.calories) /
                                    Number(goal.daily_calories)) *
                                    100,
                                ) + "%"
                              : "—"}
                          </strong>
                          <small>of daily goal</small>
                        </div>
                      </div>
                    </div>
                    <div className="energy-bottom">
                      <span>
                        <i />
                        {fmt(consumed.calories)} consumed
                      </span>
                      <span>
                        {goal ? (
                          fmt(goal.daily_calories) + " daily goal"
                        ) : (
                          <button onClick={openGoals}>Set your goals →</button>
                        )}
                      </span>
                    </div>
                  </article>
                  <article className="protein-card">
                    <div className="card-label">
                      <span>
                        <Utensils size={18} /> Protein
                      </span>
                      <span className="tiny-pill">DAILY GOAL</span>
                    </div>
                    <h2>
                      {fmt(consumed.protein)}
                      <span> / {goal ? fmt(goal.daily_protein_g) : "—"} g</span>
                    </h2>
                    <div className="bar">
                      <span
                        style={{
                          width: `${Math.min(100, (Number(consumed.protein) / Number(goal?.daily_protein_g || 1)) * 100)}%`,
                        }}
                      />
                    </div>
                    <p>
                      {goal
                        ? Number(summary?.remaining?.protein) > 0
                          ? `${fmt(Number(summary?.remaining?.protein))} g to your protein goal`
                          : "Protein goal reached"
                        : "Set a goal to track your progress"}
                    </p>
                    <div className="macro-pair">
                      <span>
                        <i className="amber" /> Carbs
                        <strong>
                          {fmt(consumed.carbs)} <small>g</small>
                        </strong>
                      </span>
                      <span>
                        <i className="pink" /> Fat
                        <strong>
                          {fmt(consumed.fat)} <small>g</small>
                        </strong>
                      </span>
                    </div>
                  </article>
                </section>
                <section className="scan-banner">
                  <div className="scan-symbol">
                    <Camera size={28} />
                    <span>✦</span>
                  </div>
                  <div>
                    <h3>A photo is a lovely place to start.</h3>
                    <p>
                      Snap your plate. Review your estimate. Get on with your
                      day.
                    </p>
                  </div>
                  <button
                    onClick={() => upload.current?.click()}
                    disabled={busy || !online}
                  >
                    Let’s scan <ArrowUpRight size={18} />
                  </button>
                </section>
              </>
            )}
            <section className="journal">
              <div className="section-heading">
                <div>
                  <h2>
                    {tab === "history" ? "All your meals" : "On your plate"}
                  </h2>
                  <span>
                    {entries.length}{" "}
                    {entries.length === 1 ? "entry" : "entries"}
                    {tab === "today" ? " for this day" : ""}
                  </span>
                </div>
                <button
                  className="text-button"
                  disabled={busy || !online}
                  onClick={() => startManual()}
                >
                  <Plus size={17} /> Add manually
                </button>
              </div>
              {entries.length ? (
                <div className="meal-list">{entries.map(row)}</div>
              ) : (
                <div className="empty">
                  <Utensils size={30} />
                  <h3>Your next meal starts here.</h3>
                  <p>Scan a photo or add a meal to begin your journal.</p>
                  <button className="text-button" onClick={() => startManual()}>
                    <Plus size={16} /> Log your first meal
                  </button>
                </div>
              )}
              {tab === "history" && hasMore && (
                <button
                  className="secondary load-more"
                  onClick={() => setPage((p) => p + 1)}
                >
                  Load more meals
                </button>
              )}
            </section>
            <footer>
              <Leaf size={15} />
              <span>Progress is a practice, one meal at a time.</span>
              <span className="footer-right">Made for your everyday.</span>
            </footer>
          </main>
        )}
      </div>
      {notice && (
        <div role="status" className="toast">
          <Check size={18} />
          {notice}
        </div>
      )}
      {dialog === "scan" && (
        <Modal
          title="A closer look at your meal"
          onClose={() => void closeScan()}
        >
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          {!scan ? (
            <div className="scan-loading">
              <ScanLine size={46} className={busy ? "pulse" : ""} />
              <h3>
                {busy
                  ? "Getting to know your plate…"
                  : "We couldn’t scan this photo."}
              </h3>
              <p>
                {busy
                  ? "Identifying food and checking nutrition."
                  : "Try a JPEG or PNG, or add your meal manually."}
              </p>
              {!busy && (
                <button className="secondary" onClick={() => startManual()}>
                  Enter manually
                </button>
              )}
            </div>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void run(async () => {
                  const items = selections.filter((_, i) => selected[i]);
                  await api("/scans/" + scan.id + "/confirm", "PATCH", {
                    items,
                  });
                  setDialog(null);
                  setScan(null);
                  setDate(today);
                  await refresh(today);
                  setNotice("Meal added to your journal");
                });
              }}
            >
              <img
                className="scan-preview"
                src={scan.imageUrl}
                alt="Your uploaded meal"
              />
              {scan.demo && (
                <p className="demo-label">
                  Demo mode · Sample recognition, not an analysis of this photo
                </p>
              )}
              <p className="hint">
                {scan.manualEntry
                  ? "Recognition is unavailable. Enter your food and nutrition below."
                  : "Photo estimates are approximate. Check each food, serving size, and nutrition before saving."}
              </p>
              {selections.map((value, i) => (
                <div className="review-item" key={i}>
                  <div className="review-top">
                    <label className="checkbox">
                      <input
                        type="checkbox"
                        checked={selected[i]}
                        onChange={(e) =>
                          setSelected((v) =>
                            v.map((x, j) => (j === i ? e.target.checked : x)),
                          )
                        }
                      />{" "}
                      Include this food
                    </label>
                    {scan.items[i] && (
                      <span
                        className={
                          scan.items[i].confidence < 0.75
                            ? "confidence low"
                            : "confidence"
                        }
                      >
                        {Math.round(scan.items[i].confidence * 100)}% confidence
                      </span>
                    )}
                  </div>
                  {scan.items[i]?.candidates.length > 0 ? (
                    <label>
                      Nutrition match
                      <select
                        defaultValue="0"
                        onChange={(e) => {
                          const candidate =
                            scan.items[i].candidates[Number(e.target.value)];
                          setSelections((old) =>
                            old.map((item, j) =>
                              i === j
                                ? {
                                    ...item,
                                    foodName: candidate.description,
                                    macros: scale(
                                      candidate.per100g,
                                      item.portionG / 100,
                                    ),
                                  }
                                : item,
                            ),
                          );
                        }}
                      >
                        {scan.items[i].candidates.map((candidate, j) => (
                          <option key={candidate.fdcId} value={j}>
                            {candidate.description} · USDA {candidate.fdcId}
                          </option>
                        ))}
                      </select>
                    </label>
                  ) : (
                    <p className="hint">
                      No verified nutrition match. Enter macros from a label or
                      trusted source.
                    </p>
                  )}
                  <FoodFields
                    value={value}
                    onChange={(v) =>
                      setSelections((old) =>
                        old.map((item, j) => (i === j ? v : item)),
                      )
                    }
                  />
                  <small className="hint">
                    Changing a name doesn’t update nutrition. Check the macros
                    if you choose a different food.
                  </small>
                </div>
              ))}
              <div className="modal-actions">
                <button
                  className="secondary"
                  type="button"
                  disabled={busy}
                  onClick={() => void closeScan()}
                >
                  Discard
                </button>
                <button
                  className="primary"
                  disabled={busy || !selected.some(Boolean)}
                >
                  {busy ? (
                    <LoaderCircle className="spin" size={18} />
                  ) : (
                    <Check size={18} />
                  )}{" "}
                  Log this meal
                </button>
              </div>
            </form>
          )}
        </Modal>
      )}
      {dialog === "manual" && (
        <Modal
          title={editing ? "Edit your meal" : "Add a meal"}
          onClose={() => !busy && setDialog(null)}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                if (editing) await api("/logs/" + editing.id, "PATCH", manual);
                else
                  await api("/logs", "POST", {
                    item: manual,
                    loggedAt:
                      date === today
                        ? null
                        : new Date(
                            date +
                              "T12:00:00" +
                              offsetFor(date, session!.user.timezone),
                          ).toISOString(),
                  });
                setDialog(null);
                await refresh();
                setNotice(editing ? "Meal updated" : "Meal added");
              });
            }}
          >
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <p className="hint">
              {editing
                ? "Update the serving or nutrition below."
                : "Enter nutrition for your full portion. Portion changes scale the macros."}
            </p>
            <FoodFields value={manual} onChange={setManual} />
            <div className="modal-actions">
              {editing && (
                <button
                  type="button"
                  className="danger-text"
                  disabled={busy}
                  onClick={() =>
                    void run(async () => {
                      await api("/logs/" + editing.id, "DELETE");
                      setDialog(null);
                      await refresh();
                      setNotice("Entry deleted. Daily totals updated.");
                    })
                  }
                >
                  <Trash2 size={17} /> Delete entry
                </button>
              )}
              <button className="primary" disabled={busy || !online}>
                {busy ? "Saving…" : "Save meal"}
                <Check size={17} />
              </button>
            </div>
          </form>
        </Modal>
      )}
      {dialog === "goals" && (
        <Modal
          title="Goals that grow with you"
          onClose={() => !busy && setDialog(null)}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                await api("/goals", "PUT", {
                  dailyCalories: goalCalories,
                  dailyProteinG: goalProtein,
                  effectiveFrom: goalDate,
                });
                setDialog(null);
                await refresh();
                setNotice("Goals saved with their effective date");
              });
            }}
          >
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <p className="hint">
              Choose what works for you. Earlier goals stay in your history.
            </p>
            <label>
              Daily calories (kcal)
              <input
                required
                type="number"
                min="1"
                max="20000"
                value={goalCalories}
                onChange={(e) => setGoalCalories(Number(e.target.value))}
              />
            </label>
            <label>
              Daily protein (g)
              <input
                required
                type="number"
                min="1"
                max="1000"
                value={goalProtein}
                onChange={(e) => setGoalProtein(Number(e.target.value))}
              />
            </label>
            <label>
              Effective from
              <input
                required
                type="date"
                value={goalDate}
                onChange={(e) => setGoalDate(e.target.value)}
              />
            </label>
            {goalDate < today && (
              <p className="hint">
                This updates the goal used for past days from this date.
                Existing food logs stay the same.
              </p>
            )}
            <button className="primary full" disabled={busy}>
              Save goals <Check size={17} />
            </button>
          </form>
          {goals.length > 0 && (
            <div className="goal-history">
              <h3>Goal history</h3>
              {goals.map((g, i) => (
                <p key={i}>
                  <span>{g.effective_from}</span>
                  <strong>
                    {fmt(g.daily_calories)} kcal · {fmt(g.daily_protein_g)} g
                    protein
                  </strong>
                </p>
              ))}
            </div>
          )}
        </Modal>
      )}
      {dialog === "account" && (
        <Modal title="Your space" onClose={() => !busy && setDialog(null)}>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <p>{session?.user.email}</p>
          <label>
            Journal timezone
            <select
              value={session!.user.timezone}
              disabled={busy}
              onChange={(e) =>
                void run(async () => {
                  await api("/timezone", "PUT", { timezone: e.target.value });
                  const s = await api<Session>("/session");
                  setSession(s);
                  setDate(dayInZone(s.user.timezone));
                  setNotice("Timezone updated");
                })
              }
            >
              {Array.from(
                new Set([
                  session!.user.timezone,
                  Intl.DateTimeFormat().resolvedOptions().timeZone,
                  "UTC",
                  "Asia/Kolkata",
                  "America/New_York",
                  "America/Los_Angeles",
                  "Europe/London",
                  "Australia/Sydney",
                ]),
              ).map((z) => (
                <option key={z}>{z}</option>
              ))}
            </select>
          </label>
          <button
            className="secondary full"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await api("/logout", "POST");
                setSession(null);
                setSummary(null);
                setHistory([]);
                setCsrf("");
                setDialog(null);
              })
            }
          >
            <LogOut size={17} /> Sign out
          </button>
          <div className="delete-account">
            <h3>Delete account</h3>
            <p>
              This permanently removes your meals, goal history, scans, and
              photos. Type DELETE to confirm.
            </p>
            <input
              aria-label="Type DELETE to confirm account deletion"
              value={deleteText}
              onChange={(e) => setDeleteText(e.target.value)}
            />
            <button
              className="danger full"
              disabled={busy || deleteText !== "DELETE"}
              onClick={() =>
                void run(async () => {
                  await api("/account", "DELETE");
                  setSession(null);
                  setSummary(null);
                  setHistory([]);
                  setDialog(null);
                  setCsrf("");
                  setDeleteText("");
                  setNotice("Your account and data have been deleted");
                })
              }
            >
              Delete my account and data
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
// Convert the chosen local noon to an offset without assuming the device timezone.
function offsetFor(date: string, zone: string) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: zone,
    timeZoneName: "longOffset",
  }).formatToParts(new Date(date + "T12:00:00Z"));
  const value = parts.find((p) => p.type === "timeZoneName")?.value || "GMT";
  return value === "GMT" ? "+00:00" : value.replace("GMT", "");
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
if ("serviceWorker" in navigator && import.meta.env.PROD)
  navigator.serviceWorker.register("/sw.js").catch(() => {});
