# Validation record

Local verification on 2026-09-23:

| Check                                         | Result                                                                                                          |
| --------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Java unit/integration suite                   | 19 passing tests; real PostgreSQL 16 containers                                                                 |
| Input validation regression checks            | Passing, including null items and portions below database precision                                             |
| Frontend type check and ESLint                | Passing                                                                                                         |
| Frontend unit tests                           | 2 passing                                                                                                       |
| Playwright browser/API tests                  | 2 passing against the real local API and provider stub                                                          |
| Mobile and tablet                             | 390px overflow check and 768px account-menu check passed                                                        |
| Visual inspection                             | Reviewed 390px mobile and 1440px desktop screenshots                                                            |
| Vite production build                         | Passing                                                                                                         |
| OpenAPI 3.1 validation                        | Passing with openapi-spec-validator                                                                             |
| Database-backed photo storage                 | Migration, signed read, and persistence without a local disk passed                                            |
| Render Blueprint                              | `render.yaml` parses with one free web service and one free PostgreSQL database                                |
| Docker images                                 | Backend and frontend build; combined runtime built from cached equivalent Java/Nginx Alpine bases              |
| Container runtime                             | Java 21 health, session, PNG normalization, recognition, and discard smoke passed; packaged Nginx config passed |
| Render runtime path                           | PostgreSQL → API migration/startup → Nginx health proxy smoke passed within a 512 MB container limit           |
| Frontend production dependency audit          | No reported production dependency vulnerabilities                                                               |
| Local Compose parsing                         | Passing                                                                                                         |

Backend coverage includes atomic/idempotent scan confirmation under concurrent requests, consumed/remaining arithmetic, edit/delete recalculation, negative remaining values, effective-dated goals, cross-account isolation, hourly rate limits, nutrition caching, provider outage → manual confirmation, photo erasure (including orphan objects), signed URL forgery/expiry, JWT validation, CSRF and daylight-saving day boundaries.

The browser suite covers demo sign-in, a multipart upload, multi-item review, portion correction, explicit confirmation, meal editing/deletion, goal history and unauthenticated/CSRF rejection. Browser tests use stubbed external providers; they do not incur Gemini API usage.

The exact Java 21 backend Dockerfile built successfully after the Gemini migration. The application JAR, frontend bundle, entrypoint, Nginx configuration, migration, health proxy, and 512 MB runtime were also exercised in the combined Render image during the initial deployment validation.

Not verified with live credentials: Google sign-in, Gemini vision quality, USDA live matching, an actual Render Blueprint deployment, the public `onrender.com` route, or a database export/restore. Those require deployment-specific credentials and settings described in deployment.md. No cloud resources were created.
