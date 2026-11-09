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

## Publishing a plan to drivers

1. The owner solves on **Plan routes**. The API saves the plan as a draft, and the page shows a **Draft** bar.
2. **Publish to drivers** sends it (`POST /plans/{id}/publish`). Every route needs a driver, which the route copies from its shift when it's solved. So a driver added to a shift afterwards needs a fresh solve. Publishing also turns the plan's orders from `pending` to `planned`.
3. Each driver opens **My routes** (`features/my-routes/`, from `GET /drivers/me/routes`): their routes by day, a map with the road path, and the stops in order. `?route=12` in the URL picks one.
4. The driver accepts or turns down each route (`POST /drivers/me/routes/{id}/accept` or `/reject`, with an optional reason). They can change their answer until the route starts. After it starts or finishes, or once the plan is completed, the API answers 409 and the route's `answerOpen` is false.
5. An accepted route has **Start route** (`/start`), which moves its orders from `planned` to `out_for_delivery`. Then **Finish route** (`/finish`) asks for confirmation first. The planner loads both `planned` and `out_for_delivery` orders, so a published plan still shows customer names after its routes start. The panel for all of this is `shared/route-actions.ts`.
6. Drivers can also do all of this without logging in. Once a plan is published, each route card on **Plan routes** has **Copy driver link**, which copies `/route/{shareToken}` (`features/shared-route/`, calling `/api/v1/shared/routes/{token}` and its `/accept`, `/reject`, `/start` and `/finish`). The auth interceptor skips `/shared/` calls, because Spring answers 401 to an expired bearer token even on a `permitAll` path.

## Project layout

```
src/app/
  core/       API client, DTO types, session, auth interceptor and guards, time zones, errors
  shared/     Reusable UI: dialog, form field, address picker, maps, badges, pager
  features/   One folder per page, mirroring the API's feature/ packages
    planning/plan-view.ts   Joins the API's plan response with loaded orders and vehicles
    my-routes/my-routes-view.ts   Same for a driver, who can't read orders: pins come from the company's addresses
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

- **Plan times have no offset.** They're `LocalDateTime` on the company's clock, so `companyLocalToInstant()` in `core/time.ts` reads them in the company's timezone.
- **Plans can't be read back.** The API saves them but has no `GET` for a plan, so the planner keeps the last plan per company and date in `localStorage`. Copies saved before plans had an id are ignored.
- **A driver's route has ids only.** `GET /drivers/me/routes` has no company, date, vehicle name, customer or coordinates, and drivers can't read orders. The page keeps routes whose vehicle belongs to the current company, takes the date from the first stop, and finds each stop's pin by matching its address text against the company's addresses. A stop with no match is listed without a pin.
- **No "my role" endpoint.** The shell calls the owner-only members list and treats a 403 as "not the owner".
