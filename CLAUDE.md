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
  (`PUT /api/v1/me/game`, Redis hash `pibs:player:{userId}`); buttons go to `POST /api/v1/me/inputs`. Key bindings per user: `GET/PUT /api/v1/me/keys`, Redis hash `pibs:keys:{userId}` (kept across a reset).
  `/ws/screen` and `/ws/audio` carry only the caller's own console. /play shows `GamePicker` until a game is picked.
- Done: lobby and link sessions. `LinkService` (core): ready to TRADE/BATTLE in Redis hash `pibs:lobby`, challenges
  (60 s) and sessions (no time cap; a player with no tab open for 60 s leaves, via `LinkService.presence` = EventPublisher.isOnline) in memory, `PlayerConsoles.link/unlink`. REST under `/api/v1/link`; events
  `LOBBY_CHANGED`, `LINK_CHALLENGE`, `LINK_SESSION` (to everyone, with user ids). Panel: `LinkPanel.svelte`.
- Done: accepting a link Digs both players up to the Cable Club, onto the tile below the receptionist for the
  kind (trade left, battle middle), facing her (`PokecenterDig`, core `service/game`). `accept` refuses (409,
  challenge kept) while either Gold/Silver/Crystal player is in a battle, a menu, a dialog or event, mid-step, in the
  Bug-Catching Contest, or indoors where neither Fly nor Dig works (a Pokémon Center is fine). `PokecenterDig.patch`
  writes two scripts into the free tail of ROM bank 3 at insert (from the Dig script's bytes + loadmem of map/tile +
  newloadmap MAPSETUP_TELEPORT); `queue` arms one through wMapReentryScriptQueueFlag and sets wBackupWarp* so the 2F
  stairs lead into that Center (the current one, or wLastSpawnMap's). The link sweep reads each linked player's room;
  a link room that does not fit the kind sends LINK_SESSION status ROOM and `Session.wrongRoom/peerWrongRoom`.
  A linked player's console keeps running when they leave /play (ScreenWebSocketHandler). `PokecenterDigTest` runs
  the real ROMs and parked states in `data/` (skipped without them).
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
  box; `PokemonGen2`'s summary has counters, the 16 badges and the 251-entry Pokédex (no party). `/api/v1/public/teaser` is `{online, players}`. Admin has a
  read-only Players table (from the scoreboard).
- Done: achievements and Trainer Score (design: https://claude.ai/artifact/GTd7t2WnQNqZzdwLdxngtJ). `PokemonGen2`
  reads a `Gen2Save` (party DVs/OT/types, bag + PC items, HMs, event flags, coins, Unown Dex, phone list, play time;
  every address/flag cited from pret in comments). `ProgressTracker.record` unlocks `Achievements` (55 in 8 categories,
  see below; Hall of Fame / Red ones only on the observed moment) into
  `pibs:achievements:{userId}` (id -> unlockedAt, never re-awarded) and rates the save with `TrainerScoring`
  (0-1000, title, grade; live, can drop) into `pibs:trainer:{userId}`. `PowerRating` is the design's 251-entry table.
  `pibs:scoreboard` now ranks by trainerScore x 10,000 + achievement points; `ScoreRow.points` is the older per-step
  tally, computed from `pibs:progress`. Feed types ACHIEVEMENT and TITLE; live `ACHIEVEMENT_UNLOCKED`
  {user, id, name, points, at}. `GET /api/v1/achievements`, `GET /api/v1/players/{userId}/achievements`.
  `EmulatorHost.readBytes` returns null for D000-DFFF while a CGB game has another WRAM bank in. No UI yet.
- Done: the rest of the achievements. Rarity from points (1-3 common, 5 uncommon, 10 rare, 25 epic, 50 legendary);
  a locked legendary is served `hidden: true` with name, description and note null (`Achievement.seenBy`) in
  `/achievements`, `/players/{id}/achievements` and the profile (whose entries carry category, rarity, hidden).
  Removed as untellable: Johto Complete (the New Pokédex lists all 251, so it would be 250 Club), Bug Champion, No
  Shopping. Moments between two confirmed readings: Breeder (egg -> hatched in the same slot), Odd One (that, shiny,
  knowing Dizzy Punch), Untouchable (Will's room to the Hall of Fame with no party member at 0 HP), trades (a new
  OT+DVs in the party in TRADE_CENTER / TIME_CAPSULE). Kept in `pibs:history:{userId}` (JSON `TrainerLog`: phone
  contacts ever seen, eggs hatched, link partners, trades, cross-version, the Elite Four run, link battle record);
  `ProgressRepository.delete` (player reset) removes it. Battery save: `PlayerConsoles.batterySave` flushes Coffee
  GB's cartridge RAM to `saves/{user}/{cartridge}.sav` (it is otherwise written only on power off/reset, so the file
  is stale while the console runs) and reads it back, once a minute (every 5 s in the Battle Tower); a read counts
  when two in a row agree. `PokemonGen2.sram` reads the PC boxes (sBox is the box wCurBox names; its own sBoxN is
  stale; Gen 2 has no box in WRAM), sLinkBattleWins/Losses and Crystal's sBattleTowerChallengeState: Living History,
  Link Champion, Tower Climber. Link achievements (category "Link"): Plugged In, First Trade, Trade Evolution,
  Cross-Version, Link Champion, Social Butterfly; partners come from `PlayerConsoles.linkedWith` at each reading.
  Profile `stats` adds {trades, linkWins, linkLosses}. Location: `ScoreRow.location` is "Landmark · Room" inside
  (room from pret's map constants in `pokegold-maps.txt` / `pokecrystal-maps.txt`, keyed by game, group, number;
  the map header's environment byte tells inside from out), the landmark outdoors, the room alone in the link rooms
  and Pokémon Center 2F. `ScoreRow.trainerName` / `TrainerCard.trainerName` = wPlayerName.
- Done: Gen 2 art from the player's ROM (`Gen2Pic`: pret's LZ, front pics padded to 56x56 as PadFrontpic, normal
  palette; Unown as A). `/api/v1/game/icons/{1-251}.png` and `/api/v1/game/badges/{index}.png` (the 8 Johto badges
  from the trainer card's BadgeGFX + PREDEFPAL_CGB_BADGE; the ROM has no Kanto badge art, so those use v1's
  `assets/badges/*.png`). Every ROM address in `PokemonGen2.Layout`, cited from pret's .sym/.map files. "Your run"
  shows badge sprites (greyed out until earned); `PokedexBanner half={1|2}` splits the dex in two (1-126 / 127-251)
  and picks its column count to fit; the banners show on every desktop size (the zoom gives >= 1728px of room).
- Done: party detail, location, play time (Gen 2). `PokemonGen2` reads every party member (eggs too) into
  `PartyMon` (nickname, HP, status, held item, moves, shiny, types; item/move/species names from the ROM's
  ItemNames/MoveNames/PokemonNames), the landmark via wMapGroup/wMapNumber -> MapGroupPointers map header -> Landmarks
  (Crystal's list has BATTLE_TOWER, so ids differ from Gold's), and "H:MM" play time. Kept as JSON field `card`
  (`TrainerCard`) of `pibs:trainer:{userId}` (replaces the old `party` field). `ScoreRow` has party (`Member`:
  species, level, hpPercent, status, shiny, egg), location, playTime, johtoBadges, kantoBadges; SCOREBOARD_CHANGED goes
  out when a confirmed reading changes them (play time alone is stored, not pushed). `GET /api/v1/players/{userId}/profile`
  = `PlayerProfile` {row, party, achievements (progress/goal only for the caught/Kanto-badge counts), stats {caught,
  seen, playTime}, recent (their 10 newest feed entries)}. No trade or link battle counters exist.
- Next: achievements UI, admin actions on player consoles, a party box for Gold/Silver/Crystal, a real trade between two Pokémon
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
  - `pppp-app`: `controller` (+ `ApiExceptionHandler`), `security`, `realtime` (WebSockets: events, screen, audio, input), `config`, `PpppApplication`.
  - Dependencies point one way: app -> core -> emulator.
- Build/test: `cd backend && mvn -q test`. Run: `mvn -q package -DskipTests && java -jar pppp-app/target/pppp-app-*.jar` with the repo-root `.env` exported (`set -a; . ../.env; set +a`). Integration tests live in pppp-app: `@SpringBootTest @DirtiesContext @AutoConfigureMockMvc @Import(TestRedis.class)` (Testcontainers Redis; Docker required). `@DirtiesContext` gives each class its own context and Redis: shared contexts leak consoles and Redis state between classes.
- Auth in tests: `.with(user("x@y.z").roles("PLAYER"))` or `oidcLogin()`; mutating calls need `.with(csrf())`.
- Emulator: Coffee GB, `eu.rekawek.coffeegb:core` from Maven Central.

## Conventions
- User id = lower-cased email: `CurrentUser.id(authentication)`.
- Errors: services throw domain exceptions, `ApiExceptionHandler` alone maps them to ProblemDetail: IllegalArgumentException 400, ForbiddenException 403, NotFoundException 404, IllegalStateException 409, RateLimitedException 429 (chat).
- Live events to browsers: services publish `new LiveEvent(type, payload)` via `ApplicationEventPublisher`; `LiveEventRelay` forwards to every `/ws/events` WebSocket (`EventPublisher`, JSON `{type, data}`).
- Input: keys and pad buttons go down and up over `/ws/input` ("down UP" / "up UP", `InputWebSocketHandler` -> `PlayerConsoles.hold` -> `EmulatorHost.hold`), so a held key stays held on the Game Boy; closing the socket or leaving lets go of everything. `POST /api/v1/me/inputs` is only the fallback single tap. No admin input settings.
- Admin actions announce themselves via `ChatService.postSystem(text)`.
- Redis keys follow the design doc's key layout table exactly.
- Keep it small: no interfaces with one implementation, no speculative config.

## Frontend (`frontend/`)
- SvelteKit 3 + Svelte 5 runes, TypeScript, Vite 8, adapter-static (SPA, `ssr = false`), served by nginx. Config lives in `vite.config.ts` (no svelte.config.js).
- Commands: `cd frontend && npm run check && npm test && npm run build`. Dev: `npm run dev` proxies /api, /ws, /oauth2, /login/oauth2 to localhost:8080.
- Screen designs to match: `frontend/design/PlayV2.mock.html` (canvas v2.1, https://claude.ai/artifact/BJrZcj9MsEe7sN9m6odiMa; views via the hash: #pick, #play, #linked, #scoreboard, #profile-jordan, #phone). Older v1 references: `Play*.dc.html`. The sign-in page (`/`) is the handheld boot-up. Tokens in `src/app.css` have dark and light values (theme: `src/lib/theme.svelte.ts`, Auto/Light/Dark); use tokens, not hex, for anything that sits on the page background.
- Backend calls only through `src/lib/api.ts` (`api(method, path, body)`, adds X-XSRF-TOKEN, throws `ApiError{status, detail}`).
- Live events only through `src/lib/events.ts` (`on(type, handler)` returns unsubscribe; `onOpen` re-fetches snapshots on reconnect; one shared WebSocket).
- Imports via `#lib/*` need the emitted extension: `#lib/session.svelte.js`, `#lib/api.js`.
- Session: `src/lib/session.svelte.ts` (`session.me`, `loadMe()`, `isAdmin()`, `logout()`).
- Backend payload shapes: read the controllers/records in `backend/src/main/java/dev/pppp` rather than guessing.
- Accessibility: real buttons/labels, visible focus, `prefers-reduced-motion` respected, chat rendered as text never HTML.
