# Route Scheduler web app

Angular 22 front end for `route-planner-api`: a landing page plus the working app for depots, vehicles, shifts, customers, items, orders, team management and route planning.

## Run it

You need Node 22.22+, 24.15+ or 26+.

```bash
# Terminal 1: database, then the API on :8088
cd ../route-planner-api
docker compose up -d db
./mvnw spring-boot:run

# Terminal 2: the web app on :4200
npm install
npm start
```

Open http://localhost:4200. Demo logins are in `../route-planner-api/scripts/db/seed-data.sql`. Sofia Rossi owns two companies, and users 4 to 10 are drivers.

| Command | What it does |
| --- | --- |
| `npm start` | Dev server with live reload and the API proxy |
| `npm test` | Unit tests (Vitest) |
| `npm run build` | Production build in `dist/` |

## How requests reach the API

`proxy.conf.json` makes the dev server forward `/api/*` to `localhost:8088`. The browser only ever talks to one origin, so CORS never comes up. The proxy doesn't exist in a production build: serve the app and API from the same origin behind a reverse proxy, or add CORS to `SecurityConfig`.

## Signing up as an owner or a driver

The API has no account-level role: `company_members` makes someone an owner or driver per company. So the role picked at sign-up isn't sent to the API. It only chooses the first screen, and is remembered in `localStorage` (`core/signup-role.ts`).

- **Owner:** signs up, creates a company, and becomes its owner.
- **Driver:** signs up (`/register?role=driver` preselects this), gives their email to the owner, and is added from the Team page. Drivers can't add themselves to a company.

## Project layout

```
src/app/
  core/       API client, DTO types, session, auth interceptor and guards, time zones, errors
  shared/     Reusable UI: dialog, form field, address picker, maps, badges, pager
  features/   One folder per page, mirroring the API's feature/ packages
    planning/plan-view.ts   Joins the API's plan response with loaded orders and vehicles
src/styles.css              Design tokens and shared component classes
```

## Coming from Spring

| Spring | Angular here | Where |
| --- | --- | --- |
| `@Service` singleton bean | `@Injectable({ providedIn: 'root' })` | `core/api.ts`, `core/session.ts` |
| Constructor injection | `inject(Api)` | every component |
| Servlet filter | HTTP interceptor (runs on the way out of the client) | `core/auth.interceptor.ts` |
| `SecurityConfig` matchers | Route guards. They only decide what to show; the API still enforces access | `core/auth.guard.ts` |
| `@GetMapping("/{id}")` + `@PathVariable` | Route config + `input()` | `app.routes.ts`, `features/shell/shell.ts` |
| DTO records | TypeScript interfaces | `core/models.ts` |
| Bean Validation | Reactive form validators, with server errors mapped back onto fields | `shared/field.ts`, `core/errors.ts` |
| `@ControllerAdvice` | `errorMessage()` turns a ProblemDetail into a sentence | `core/errors.ts` |

Screen state lives in signals. Angular 22 components are `OnPush` by default, so a plain field changing doesn't redraw anything, while a signal read in a template does. `rxResource` is the standard way to load data: it re-fetches when its `params` signals change and exposes loading, error and value states.

## Things the UI works around in the API

- **Plan times have no timezone.** `PlanningService` builds them with `ZoneId.systemDefault()`, so they're in the server's zone. `serverLocalToInstant()` in `core/time.ts` reads them in the browser's zone, which is only right while both run on the same machine. Returning `Instant` from the API fixes this, and that function can then go.
- **Plans aren't stored.** There's no endpoint to read a plan back and solving doesn't change order status, so the last plan per company and date is kept in `localStorage`.
- **No road geometry.** Map lines connect stops in visiting order as straight segments.
- **No "my role" endpoint.** The shell calls the owner-only members list and treats a 403 as "not the owner".
