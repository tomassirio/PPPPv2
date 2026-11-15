# Pibs&PibettesPlayPokemonV2 (PPPPv2)

The sequel to PPPP: every Pib gets a private Game Boy (Pokémon Gold, Silver or Crystal, picked once), a shared
scoreboard fed by RAM-read progress events, and a link cable between any two players.
Started as a copy of PPPP v1 (commit 420d8c2). Java package `dev.pppp`, the Maven modules and the `PPPP_*` env vars
keep the v1 names on purpose; the product, Docker images, Helm releases (`ppppv2-backend`, `ppppv2-frontend`) and the
data volume (`ppppv2-data-pvc`) are named for v2, so both can run on the same cluster.

Design doc: https://claude.ai/artifact/W6kDXEHSaUafTTw8GUa4Fs (read with the Claude Docs tools, not web fetch).
Screen mock-ups: https://claude.ai/artifact/BJrZcj9MsEe7sN9m6odiMa.

## Status
- Done: link cable in `EmulatorHost` (`link`, `unlink`, `isLinked`); `LinkCableTest` swaps bytes between two consoles.
- Done: one private console per player. `PlayerConsoles` (core) boots it when `/ws/screen` connects, pauses it and
  parks its state in `saves/{user}/{cartridge}.state` when the player's last socket closes. The game is picked once
  (`PUT /api/v1/me/game`, Redis hash `pibs:player:{userId}`); buttons go to `POST /api/v1/me/inputs`.
  `/ws/screen` and `/ws/audio` carry only the caller's own console. /play shows `GamePicker` until a game is picked.
- Done: lobby and link sessions. `LinkService` (core): ready to TRADE/BATTLE in Redis hash `pibs:lobby`, challenges
  (60 s) and sessions (15 min cap) in memory, `PlayerConsoles.link/unlink`. REST under `/api/v1/link`; events
  `LOBBY_CHANGED`, `LINK_CHALLENGE`, `LINK_SESSION` (to everyone, with user ids). Panel: `LinkPanel.svelte`.
- Done: progress + scoreboard. `ProgressTracker` (core, `service/game`) samples every running console once a second
  and scores badges, Pokédex, level milestones. Redis: `pibs:events` stream, `pibs:scoreboard` sorted set (also the
  list of players), `pibs:progress:{userId}` hash. `GET /api/v1/scoreboard`, `GET /api/v1/activity`; events `PROGRESS`,
  `SCOREBOARD_CHANGED`. Profiles: `PokemonRed`, `PokemonGen2` (Gold/Silver/Crystal, addresses from the pret symbol
  files, never run against a real ROM). Panel: `Scoreboard.svelte`.
- Done: v1's shared console is gone (crowd input, modes, votes, admin console/save states/launch/reset/autosave,
  rate limiter, `GET /state`, the public screenshot). The game-status endpoints serve the caller's own console:
  `GET /api/v1/game/state` (their `GameSummary`, 204 without a game or a profile) and `/api/v1/game/icons/{id}.png`
  come from `ProgressTracker.summary/icon(userId)`, the same confirmed reading it scores; a change goes out as
  `GAME_STATE {user, state}` to everyone and the browser keeps its own (`game.svelte.ts`). Panels: `GameStatus.svelte`,
  `PokedexBanner.svelte`; `GameToasts.svelte` shows the player's own `PROGRESS` events. Only `PokemonRed` has a party
  box; `PokemonGen2`'s summary is counters only. `/api/v1/public/teaser` is `{online, players}`. Admin has a
  read-only Players table (from the scoreboard).
- Next: admin actions on player consoles, a party box for Gold/Silver/Crystal, a real trade between two Pokémon
  ROMs, the byte-level serial endpoint if linked pairs are too slow on the Pi, chart renames for deploy, README
  (still describes v1).
- No deploy workflow yet. The charts are renamed; the cluster still needs the `ppppv2-data-pvc` volume and a workflow.

## Local run (next to a running PPPP v1 dev stack)
- Backend, from `backend/` after `mvn clean package -DskipTests`: `SERVER_PORT=8082 PPPP_DATA_DIR=<abs>/PPPPv2/data
  PPPP_DEV_LOGIN=true SPRING_DATA_REDIS_DATABASE=1 java -jar pppp-app/target/pppp-app-*.jar` (Redis db 1 keeps its
  keys apart from v1's db 0 on the same local Redis).
- Frontend: `PPPP_BACKEND=http://127.0.0.1:8082 npm run dev -- --host 127.0.0.1 --port 5174`. Open it as
  `http://127.0.0.1:5174`, not `localhost`: cookies are per host, and `localhost` belongs to v1's session.
- Sign in without Google: `http://127.0.0.1:5174/dev/login?email=someone@example.com` (only with `PPPP_DEV_LOGIN=true`;
  an e-mail listed in `PPPP_ADMINS` gets the admin role). Use two browsers or profiles for two players.

## Backend (`backend/`)
- Spring Boot 3.5, Java 21, Maven multi-module, MVC layers. Package root `dev.pppp`.
  - `pppp-emulator`: `dev.pppp.emulator` — Coffee GB on its own thread. Plain Java, no Spring.
  - `pppp-core`: `model`, `exception`, `service`, `repository`, `job`, `config` — business logic. No web dependencies; Redis access only in `repository`.
  - `pppp-app`: `controller` (+ `ApiExceptionHandler`), `security`, `realtime` (SSE, screen WebSocket), `config`, `PpppApplication`.
  - Dependencies point one way: app -> core -> emulator.
- Build/test: `cd backend && mvn -q test`. Run: `mvn -q package -DskipTests && java -jar pppp-app/target/pppp-app-*.jar` with the repo-root `.env` exported (`set -a; . ../.env; set +a`). Integration tests live in pppp-app: `@SpringBootTest @DirtiesContext @AutoConfigureMockMvc @Import(TestRedis.class)` (Testcontainers Redis; Docker required). `@DirtiesContext` gives each class its own context and Redis: shared contexts leak settings and consoles between classes.
- Auth in tests: `.with(user("x@y.z").roles("PLAYER"))` or `oidcLogin()`; mutating calls need `.with(csrf())`.
- Emulator: Coffee GB, `eu.rekawek.coffeegb:core` from Maven Central.

## Conventions
- User id = lower-cased email: `CurrentUser.id(authentication)`.
- Errors: services throw domain exceptions, `ApiExceptionHandler` alone maps them to ProblemDetail: IllegalArgumentException 400, ForbiddenException 403, NotFoundException 404, IllegalStateException 409, RateLimitedException 429 (chat).
- Live events to browsers: services publish `new LiveEvent(type, payload)` via `ApplicationEventPublisher`; `LiveEventRelay` forwards to SSE (`/api/v1/events`).
- Settings: `SettingsService` (Redis JSON, publishes the new record as an ApplicationEvent on save). Admin GET/PUT under `/api/v1/admin/settings/input` (holdFrames, releaseFrames), the only setting left.
- Admin actions announce themselves via `ChatService.postSystem(text)`.
- Redis keys follow the design doc's key layout table exactly.
- Keep it small: no interfaces with one implementation, no speculative config.

## Frontend (`frontend/`)
- SvelteKit 3 + Svelte 5 runes, TypeScript, Vite 8, adapter-static (SPA, `ssr = false`), served by nginx. Config lives in `vite.config.ts` (no svelte.config.js).
- Commands: `cd frontend && npm run check && npm test && npm run build`. Dev: `npm run dev` proxies /api, /ws, /oauth2, /login/oauth2 to localhost:8080.
- Screen designs to match for /play: `frontend/design/Play*.dc.html`. The sign-in page (`/`) is the handheld boot-up. Tokens in `src/app.css` have dark and light values (theme: `src/lib/theme.svelte.ts`, Auto/Light/Dark); use tokens, not hex, for anything that sits on the page background.
- Backend calls only through `src/lib/api.ts` (`api(method, path, body)`, adds X-XSRF-TOKEN, throws `ApiError{status, detail}`).
- Live events only through `src/lib/events.ts` (`on(type, handler)` returns unsubscribe; one shared EventSource).
- Imports via `#lib/*` need the emitted extension: `#lib/session.svelte.js`, `#lib/api.js`.
- Session: `src/lib/session.svelte.ts` (`session.me`, `loadMe()`, `isAdmin()`, `logout()`).
- Backend payload shapes: read the controllers/records in `backend/src/main/java/dev/pppp` rather than guessing.
- Accessibility: real buttons/labels, visible focus, `prefers-reduced-motion` respected, chat rendered as text never HTML.
