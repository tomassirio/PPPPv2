<p align="center">
  <img src="frontend/src/lib/assets/banner-v2.png" alt="Pibs&amp;PibettesPlayPokemon V2" width="720">
</p>

<p align="center">
  <img src="frontend/src/lib/assets/ppppv2-logo.png" alt="PPPP V2" width="160">
</p>

A Game Boy for every Pib. Each player picks Pokémon Gold, Silver or Crystal once and plays their own private game in
the browser. Progress is read from the game, scored and shown on a shared scoreboard, and any two players can plug in
a link cable to trade or battle.

The sequel to Pibs&PibettesPlayPokemon, where everyone shared one Game Boy.

## What it does

- **Your own Game Boy.** The server runs one emulator ([Coffee GB](https://github.com/trekawek/coffee-gb)) per player
  and streams its screen and sound only to that player. It pauses and saves when you leave and resumes when you come back.
- **Pick a game, once.** Gold, Silver or Crystal, from the cartridges the admin uploaded.
- **Scoreboard.** The server reads each game's memory and scores badges, Pokédex entries and level milestones. Everyone
  sees the ranking and an activity feed live.
- **Link cable.** Mark yourself ready to trade or battle, challenge someone who is ready, and both consoles are linked
  on the server until one of you unplugs. Walk into the Cable Club as on real hardware.
- **Chat, presence, themes, controller colours, phone layouts.** Kept from the first version.
- **Who gets in.** Google sign-in for emails on the allowlist; anyone else becomes an access request for an admin.
- **Admin.** Upload cartridges, manage people and access requests, see every player's game and points, and reset a
  player's run (their pick, save, points and activity).

## How it's built

```
browser ──HTTP/WS───▶ nginx ──▶ Spring Boot ──▶ Redis   (players, lobby, scoreboard, activity, chat, sessions)
   ▲                              │
   └──── screen + audio (WS) ─────┘ one Coffee GB per player; ROMs, battery saves and parked states on disk
```

| Part | Stack |
| --- | --- |
| `backend/` | Java 21, Spring Boot 3.5, Maven modules: `pppp-emulator` (Coffee GB wrapper and link cable, no Spring), `pppp-core` (services, Redis repositories, scoring), `pppp-app` (controllers, security, WebSockets) |
| `frontend/` | SvelteKit + Svelte 5 runes, TypeScript, Vite, static SPA served by nginx |
| Auth | Google sign-in (OIDC), only for emails on the allowlist. Sessions in Redis |
| Live updates | WebSockets: `/ws/events` (one per tab) for scoreboard, chat, who is online, lobby, link sessions and progress; `/ws/screen`, `/ws/audio` and `/ws/input` for each player's own console |

Not included: ROMs. Bring your own legally dumped cartridges and upload them from the admin page (or drop them in
`data/roms/`).

## Running it

You need Java 21, Maven, Node 22+, Docker (for Redis and the integration tests), and a Google OAuth client with
`http://localhost:5173/login/oauth2/code/google` as a redirect URI.

```sh
cp .env.example .env            # fill in GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, PPPP_ADMINS (your email)
docker compose up -d redis

# backend on :8080
cd backend
mvn -q clean package -DskipTests
set -a; . ../.env; set +a
java -jar pppp-app/target/pppp-app-*.jar

# frontend on :5173 (proxies /api, /ws, /oauth2 to :8080)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and sign in. The emails in `PPPP_ADMINS` become the first admins; add everyone else from
`/admin`. For local testing without Google, start the backend with `PPPP_DEV_LOGIN=true` and open
`/dev/login?email=someone@example.com` (never on a reachable host).

## Deploying

Not wired up yet. The Helm charts in `backend/chart` and `frontend/chart` are named `ppppv2-backend` and
`ppppv2-frontend`, push images to `ghcr.io/<owner>/ppppv2-{backend,frontend}` and expect a `ppppv2-data-pvc` volume,
so they can run next to the first version on the same cluster.

## Tests

```sh
cd backend && mvn -q test                                  # unit + integration (Testcontainers Redis)
cd frontend && npm run check && npm test && npm run build
```

## Configuration

| Variable | Default | |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | | Google OAuth client |
| `PPPP_ADMINS` | | Comma-separated emails seeded as admins when the allowlist is empty |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `PPPP_DATA_DIR` | `./data` | ROMs, battery saves, parked console states |
| `PPPP_DEV_LOGIN` | `false` | Local only: password-less sign-in at `/dev/login` |

Button hold and release timing is changed at runtime from the admin page.

## Not affiliated

Fan project for friends. Not affiliated with Nintendo, Game Freak or The Pokémon Company. Pokémon is their trademark.
