# CalSnap

A mobile-first food journal: photograph a meal → review estimated foods, portions and USDA nutrition matches → explicitly confirm → track daily calories and protein. Java 21 + Micronaut + PostgreSQL, React PWA, and a Render Blueprint for a temporary free beta.

[Desktop screenshot](docs/screenshots/desktop.png) · [Mobile screenshot](docs/screenshots/mobile.png)

## Start locally

Prerequisites: Docker with Compose. No API keys are needed for the local demo.

```sh
cp .env.example .env
docker compose up --build -d
# Wait until http://localhost:8080/health reports UP, then:
./scripts/seed.sh
```

Open **http://localhost:5173** and choose **Explore the local demo**. Photo recognition is explicitly labelled as a deterministic sample in this mode. Upload a JPEG/PNG under 5 MB, adjust portions or nutrition candidates, and confirm. The seeded journal has three meals. Local services bind to loopback only.

For faster development:

```sh
docker compose up -d postgres stub
mvn -s backend/maven-settings.xml -f backend/pom.xml package -DskipTests
./scripts/dev-api.sh
# In a second terminal:
cd frontend
npm ci
VITE_DEMO=true npm run dev
# Once the API has migrated the schema, seed in another terminal:
./scripts/seed.sh
```

Use JDK **21+** (the bytecode target is 21), Maven 3.9+, Node 22+. If your machine overrides Java in ~/.mavenrc, use MAVEN_SKIP_RC=true. PostgreSQL is exposed at localhost:5438. Each service’s Dockerfile provides the supported build runtime.

## Live recognition and Google login

Set `VISION_MODE=live`, `OPENAI_API_KEY`, and `USDA_API_KEY`, then restart the API. **`OPENAI_MODEL=gpt-5-nano` is the default**. It supports image input and structured output; the model is configurable without changing storage or API contracts. No automatic model upgrades occur. Set OpenAI project spending limits separately. Images are normalized to 1024px and use low-detail recognition, output is capped, and users are limited to 10 scans/hour across replicas. USDA results are cached per user in PostgreSQL for 30 days. Model and nutrition provider timeouts/circuit breakers degrade to manual entry.

Create a Google OAuth web client. Register `http://localhost:5173/auth/callback` for local work and `https://YOUR_ORIGIN/auth/callback` in production. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. The backend exchanges authorization codes with PKCE, verifies Google's signature, audience, issuer, expiry, nonce and verified email, then creates a one-hour app JWT in an HttpOnly cookie. Mutation requests require a cookie-bound CSRF token. Sign in again when the session expires.

New Google users start with an empty journal and no goal; choose **Your goals** to begin. Change the journal timezone under the account menu. Goal revisions preserve effective dates, including deliberate backdating. All date queries use the account timezone; changing it also changes how past timestamps group into days.

## Tests

```sh
mvn -s backend/maven-settings.xml -f backend/pom.xml verify
cd frontend
npm ci
npm run lint
npm test
npm run build
# With the local stack and seed running:
npx playwright install chromium
npm run test:e2e
```

Backend integration tests require a working Docker daemon and use real PostgreSQL through Testcontainers. On Colima, set `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`. Browser tests modify the local demo account, including appending a goal revision. Run them against a development database. Backend tests are intentionally not silently skipped when Docker is unavailable.

## Layout and contracts

- `backend/`: API, Google OIDC/session authentication, provider adapters, storage, migrations, unit/integration tests.
- `frontend/`: React/TypeScript application, install manifest, offline fallback, browser tests. API access is isolated in `src/api.ts` for a future Capacitor shell.
- `render.yaml`: one free Render web service and one free PostgreSQL database.
- `render/`: the combined 512 MB deployment image containing the frontend, Nginx and API.
- `docs/openapi.json`: OpenAPI 3.1 request/response contracts, status codes, pagination and CSRF details.
- `docs/deployment.md`: Render Blueprint setup, Google OAuth configuration and the required pre-expiry export.
- `scripts/`: deterministic provider stub and local seed helpers.

Per-user predicates are present on all data reads/writes; composite foreign keys prevent cross-account scan/log associations. Confirmation locks the user and scan in one transaction. A confirmed scan cannot create a second set of entries even under concurrent retries or after its entries are deleted. Totals are sums of logs, never increment/decrement counters. Decimal rounding occurs at portion scaling / database boundaries. Over-goal remaining amounts can be negative.

Account deletion removes photos and then cascades all rows. On Render, normalized photos are stored in PostgreSQL because free web-service filesystems are ephemeral. Confirmed photos remain attached to the account even if the individual log is removed; deleting the account removes them.

See [the validation record](docs/validation.md) for checks performed and the remaining live-service checks.

## Deployment and operational limits

See [deployment guide](docs/deployment.md). The Render free service has 512 MB RAM, sleeps after 15 idle minutes and can cold-start for about a minute. Free Render PostgreSQL is limited to 1 GB, expires after 30 days and has no managed backups. Export or migrate it before expiry. Live Google, OpenAI, USDA and Render checks require your credentials before launch.

JWTs are signed with a generated deployment secret. Never enable APP_ENV=local in a deployed environment. PWA caches contain only a generic offline page, never account data or photos. Prometheus metrics are internal and JSON logs omit meal images, credentials and provider response bodies.

A nano model reduces inference cost; it does not make portion estimates precise. Users always review results, and incomplete nutrition matches require manual values. Evaluate recognition on your actual foods before enabling live uploads broadly.

References: [GPT-5 nano](https://developers.openai.com/api/docs/models/gpt-5-nano), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs), [FoodData Central API](https://fdc.nal.usda.gov/api-guide), [Render free services](https://render.com/docs/free).
