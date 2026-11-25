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
- Done: one private console per player. `PlayerConsoles` (core) boots it when `/ws/screen` connects; pausing parks
  its state in `saves/{user}/{cartridge}.state` (see the lifecycle below). The game is picked once
  (`PUT /api/v1/me/game`, Redis hash `pibs:player:{userId}`); buttons go to `POST /api/v1/me/inputs`. Key bindings per user: `GET/PUT /api/v1/me/keys`, Redis hash `pibs:keys:{userId}` (kept across a reset).
  `/ws/screen` and `/ws/audio` carry only the caller's own console. /play shows `GamePicker` until a game is picked.
- Done: console lifecycle. The player's last /play tab closing only `leave`s (out of the lobby, buttons released,
  name tag off other screens): the console keeps running. `PlayerConsoles.sweep` (every 5 s) pauses one untouched for
  an hour (`inactivity`: no button and no browser activity ping, `"active"` on /ws/input at most once a minute; tab
  open or not; `idle`: paused, battery flushed, state parked, still in memory so Continue resumes at once) and shuts
  down one paused for a day (`grace`); linked consoles are left to LinkService. `onAway` (fired once a console
  is paused and parked) closes its screens with 4408 ("Paused while you were away" + Continue) and admin watchers
  with 4410. The next open boots from the parked state. Admin: People's Manage opens `/admin/players/{id}`: their
  Game Boy (`GET /api/v1/admin/sessions/{userId}`: RUNNING / IDLE / PAUSED-not-saved / CLOSED, last action, pause or
  shutdown time, .sav and .state info; `POST /{userId}/close`; `GET /{userId}/save` downloads the .sav), its screen live
  while it runs (admins may watch any running console on /ws/watch/{id}) or powered off, stats, Reset run.
- Done: god messages. `POST /api/v1/admin/players/{userId}/god-message` {text} (1-200 chars; 409 when they have no
  tab open; not stored; admin chat logs it) sends `GOD_MESSAGE` {text, at} to that player's /ws/events sockets only
  (`EventPublisher.publishTo`), with a copy to admins ({user}) so the admin player page shows it on that player's
  screen. `GodMessage.svelte user=` writes it across that screen in glowing purple text, fading in and out, one at a
  time. The admin player page shows everything the player sees: /ws/watch carries the same name tags + notes text
  message as /ws/screen, plus their notification cards (`GameToasts user=`) and the chat overlay.
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
  After accepting, arrow keys are locked (`PlayerConsoles.pin`) until the player is inside the right link room or the
  session ends, and the sweep presses A once `PokecenterDig.atReceptionist` (dig done, facing her).
  Spectators: `/ws/watch/{userId}` (`WatchWebSocketHandler`) streams a linked player's screen, read only, closing 4404
  when they are not linked; `/watch/{id}` shows both ends, linked from the Link cable card's "Live now".
  Reset button on the bezel (`ResetControl`, `POST /api/v1/me/reset`, `PlayerConsoles.reset`): ends a link session,
  boots the cartridge again from its battery save. A console left in a link room without a cable for 60 s is reset by
  the link sweep (a chat line says so).
  `PokecenterDig.patientReceptionist` (a frame hook per console) stretches the receptionist's "Please wait" from 8.5 s
  to about a minute while linked (`LinkWaitTest`).
  Trainer colour: Default (the sprite's own) or red/blue/green/brown/pink/silver (`PlayerSprites.COLORS`, Redis
  `spriteColor` in pibs:player): Crystal's SpawnPlayer palette byte, Gold/Silver's PlayerObjectTemplate palette
  nibble (bank 2); previews `?color=`; Ghosts draw each player in the palette their game uses (OBJECT_PALETTE).
  Trainer picture (`PlayerPic`): a picked trainer that has a trainer class shows its class pic and palette from the front:
  Crystal's ChrisPic/KrisPic (intro, Hall of Fame), ChrisCardPic/KrisCardPic (card) and PlayerPalette; Gold/Silver's CAL entries (intro, HoF) and
  the 5x7 card pic. Link battles draw the opponent as CAL: `PlayerPic.linkOpponent` points it at the peer's class while
  linked (restored on unlink). No back sprites exist for trainer classes, so battle backs stay Chris/Kris.
  The friend in TradeCenter/Colosseum walks as the peer's chosen sprite: `PlayerConsoles.link` patches both friend
  objects' sprite bytes in each running ROM (`PlayerSprites.cableFriend`, `EmulatorHost.patchRom`); unlink puts Chris back.
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
  `ProgressRepository.delete` (player reset) removes it. Battery save: `PlayerConsoles.batterySave` flushes the
  emulator's cartridge RAM to `saves/{user}/{cartridge}.sav` (it is otherwise written only on power off/reset, so the file
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
- Done: phones. Upright, the header's burger (`AppHeader`, a `<dialog>` drawer) replaces the nav and, on /play, the
  tabs. One list with icons on every page: the /play panels under the screen (Play, Link cable, Your run, Scoreboard,
  Activity, Chat; in place on /play, `/play?panel=<id>` elsewhere; Scoreboard's panel links the full page), then a
  divider and Live; on top the logo and the player's trainer card (game, rank, Trainer Score, link status; opens
  their profile), Sign out at its bottom. /scoreboard on phones: the game as a full-width
  segmented control, the sorts as chips scrolling sideways; each table row a small card (no sideways scroll). Profiles on phones: the header a centred stack, the tabs
  share the width ("Stats"). Sign-in on phones: the handheld with the full banner on its bezel (tapping its screen is START),
  the numbers on a pill and the Google button, sized to fit one screen. `GET /api/v1/me` is open: 204 when signed out.
  Tab titles: "<page> · PPPPv2". `FrameEncoder` writes palette PNGs by hand (BEST_SPEED,
  RGB past 256 colours): ~15x cheaper than ImageIO, which at 120 frames/s took the CPU a 4x console needed on the
  Pi (it measured 0.9x; `GET /api/v1/me/speed`). GameScreen coalesces /ws/screen: messages only store the
  newest picture and name tags, one draw per animation frame (handling each of 120/s at 4x queued up on phones). Game picker on phones: the three cartridges in one row. A sideways drag on /play's panel moves it with the next/previous one beside it (menu order, looping; a third
  of the width or a flick switches, else it springs back; not
  from the screen, the pad's buttons, text fields or sideways scrollers). The Link cable card fills its panel on
  phones (record as stat tiles, Trade/Battle tiles with a hint); "Nobody is waiting" is an empty state on every size.
  Chat: only admins see system lines and emotes (the lone swaps: ok, gg, <3, :youlost: ...) in the list (and the
  System / Emotes switches); formatted lines show with their markers taken out (`plainText`). The "Aa" button by the
  message box opens the formats and emotes (`FORMATS`, `EMOTES` in chat/bubble.ts; :youlost: kept a secret), click to insert.
  New chat from others while Chat is closed: a small bubble with the count in the controller's bottom-left corner
  (Gamepad `unread`, shell-coloured; opens Chat; own messages, system lines and emotes don't count).
  `FindButton` is the one "Find someone online" button (Link cable card, /watch, profiles).
  On phones Link cable and Your run are exactly the height left under the screen (size containers) and drop parts by
  height container queries instead of scrolling; Scoreboard, Activity and Chat scroll their lists.
  The Play panel on phones (`play-panel`, one swipe unit): the controller and the strips the player picked (`PlayStrips`;
  Settings on phones, as many as the measured room fits (48px each, at most 3; `controls.room`, `stripsThatFit`) of
  Location, Party, Latest activity, Badges, Pokédex, Rank, dragged into order;
  `controls.svelte.ts`, per browser). Strips give way before the screen when height runs out, the last first, and hide
  when cut short; the triangle in the controller's corner puts them under the screen and the controller at the bottom. A sideways drag on /play's panel moves it with the next/previous one beside it (menu order, looping; a third
  of the width or a flick switches, else it springs back; not
  from the screen, the pad's buttons, text fields or sideways scrollers). The Link cable card fills its panel on
  phones: the record as stat tiles, Trade/Battle tiles with a hint, "Nobody is waiting" as an empty state. Held
  sideways the icon rail stays. Settings (key bindings) is hidden on touch screens. A player's picked speed survives
  their console rebooting (`PlayerConsoles.speeds`, memory only; plugging a cable clears it).
- Done: the end credits name only "TOMÁS SIRIO" (`Credits.patch` at insert: every CreditsStringsPointers entry before
  .End, 70 Crystal / 50 Gold-Silver, points at one string over SATOSHI TAJIRI's bytes; the font's unused Ä tile is
  redrawn as Á). Controller colours: the picker is a popover (never clipped); a Solid / Atomic switch picks the
  see-through plastic for colours that have one (`Shell.atomic`, `variant`, localStorage `pppp.shellAtomic`); atomic
  shells show the board inside (Gamepad `::before`, membranes via `--guts`).
- Done: Mystery Gifts. Admins send one of `MysteryGifts.CATALOG` (19 fair items, each with a cap: Rare Candy 3,
  stones 1, Max Repel 10, ...; names resolved per ROM by `PokemonGen2.itemId`) to one player (admin player page) or
  everyone who picked a game (admin page, confirm; a chat line says so): `GET/POST /api/v1/admin/gifts`
  {gift, quantity, to}. Queued in Redis list `pibs:gifts:{userId}` ("id:qty"); every 10 s a running, free (PokecenterDig.busy)
  console gets the oldest into its PC item list in WRAM (`PokemonGen2.pcItems`, stacks to 99, 50 entries), then
  `MYSTERY_GIFT {user, name, quantity}` shows a "Mystery Gift!" card (GameToasts). The player saves to keep it.
  Event Pokémon too (`MysteryGifts.EVENTS`, `GiftMon`): GS Ball Celebi Lv30, Crystal's Odd Egg (odds and moves from
  data/events/odd_eggs.asm, shinies included), event Mew Lv5, Surfing Pikachu Lv5; built as a party_struct (stats,
  exp by growth rate, PP, OT AAAA ID 1 so they obey by badges) into the party once it has room, Pokédex marked.
  Admin panel: chips (Items / Event Pokémon) + a quantity stepper. No decorations yet.
- Done: key bindings include Fast forward (`KeyBindings.fast`, Redis field `fast`, "" = none; Space by default, and
  for older saved layouts unless a button has Space): Settings lists it as a 9th row and on the keyboard drawing;
  `SpeedControl` toggles on it (`isFastKey`). Notification cards (`GameToasts`) swipe away (sideways or up, finger or
  mouse; × for keyboards), are see-through, and catches/sightings take the Pokémon's tier (`GET /api/v1/game/tiers`,
  PowerRating letters by dex number: S/M legendary, A epic, B rare, C uncommon, D-F common).
- Done: the release number at the foot of the account menu (`UserMenu`, desktop and phones): `__APP_VERSION__`, baked in
  by Vite from `PPPP_VERSION` (deploy.yml passes it as a build-arg to the frontend image; "dev" locally).
- Done: notes on the ground (Dark Souls style). Holding A ~0.7 s while free in the overworld (Gamepad, nobody nearby)
  opens `NoteComposer`: a phrase + one word (from the ROM: the 251 Pokémon, every item, the 251 moves; or `NoteService.THINGS`;
  `GET /api/v1/notes/words`), left on the tile the player stands still on (`GET /api/v1/notes/spot` 409 otherwise,
  `POST /api/v1/notes`). Keyed by pret map constant + tile, so all three games share them; 10 newest per player, one
  per tile. Redis hash `pibs:notes` (id -> JSON), raters in set `pibs:notes:good:{id}`. `/ws/screen`'s text message
  carries `notes` in view (`NoteService.visible`, via `Ghosts.where`): orange glows on the screen; standing on one shows
  it with Good (`POST /{id}/good`) or Remove (`DELETE /{id}`, author or admin).
- Done: first-time help on upright phones (`lib/tour.svelte.ts`, `Coach.svelte` coach marks, seen ids in localStorage
  `pppp.tour`): the /play tour (screen, burger, swipe, strips, triangle, Fast/Reset) once, then single tips the first
  time the Link cable / Your run panel opens, someone is nearby, a progress card or @mention arrives, or the console
  was paused away. Settings (phones) has "Show the tour again".
- Done: AAAA's Mail. `AaaaMail` (core, every 10 s) puts a Portrait Mail (Articuno; "Thank you, Pib! / Mt.Silver awaits") from AAAA (v1's player, who
  waits on Mt. Silver as Red: `V1Red`) into each Gen 2 player's PC mailbox and its backup, once (`aaaaMail` in
  `pibs:player:{userId}`), after their first save, while they are free in the overworld (`PokecenterDig.busy`),
  opening and closing save RAM as the game does. `AaaaMailTest` runs it on the real ROMs and parked games in data/.
- Speed: one Fast toggle (`SpeedControl`; on = the server's 4x: as fast as it manages, ~3x on the Pi since SameBoy; `GET /api/v1/me/speed` still reports the measured rate); a Pi 4
  core ran a Gen 2 console at about 1.2x on Coffee GB (`GET /api/v1/me/speed` timing: all of it in the emulator);
  hence SameBoy (2.1.0): ~2.7x cheaper per frame on a Mac, Pi numbers still to measure.
- Done: metrics. `/actuator/prometheus` (cluster-only: the frontend's nginx never proxies /actuator; permitted without
  login) adds `GameMetrics` (pppp-app config) to the JVM/HTTP ones: gauges read per scrape, aggregates only (no user ids):
  pppp_consoles{state}, pppp_consoles_by_game{game}, pppp_consoles_fast, pppp_console_speed_avg/min, pppp_consoles_lagging
  (1x below 0.9), pppp_emulator_{cpu,listeners,hooks}_cores (EmulatorHost.Timing summed), pppp_players_online,
  pppp_scoreboard_players, pppp_websockets{endpoint}, pppp_link_sessions, pppp_lobby_ready, pppp_notes; counter
  pppp_events_total{type, detail} from LiveEvents (PROGRESS, ACHIEVEMENT_UNLOCKED, MYSTERY_GIFT, CHAT_MESSAGE+kind,
  LINK_CHALLENGE+status, LINK_SESSION+status, PLAYER_RESET). Scraped by turing-pi-server's Prometheus (job
  `ppppv2-backend-prod`, labels project=ppppv2 ...); Grafana dashboard `services/grafana/dashboards/ppppv2.json` there.
- Next: achievements UI, admin actions on player consoles, a party box for Gold/Silver/Crystal, a real trade between two Pokémon
  ROMs, the byte-level serial endpoint if linked pairs are too slow on the Pi, README
  (still describes v1).
- Deploy: `.github/workflows/deploy.yml`. Every push to main is a release: the next version (latest `v*` tag, patch
  bump; `#minor`/`#major` in the commit message; first 2.0.0), arm64 images `ghcr.io/tomassirio/ppppv2-{backend,
  frontend}:{version,sha,latest}`, both charts as OCI packages `oci://ghcr.io/tomassirio/charts/ppppv2-*` (image tag
  = version), a GitHub Release `v{version}` (commits since the last one, chart .tgz attached), then Helm releases `ppppv2-backend` / `ppppv2-frontend` in namespace `ppppv2`; GitHub environment `prod`:
  secrets TWINGATE_SERVICE_KEY, KUBECONFIG_CONTENT, GOOGLE_CLIENT_SECRET; vars GOOGLE_CLIENT_ID, PPPP_ADMINS).
  Namespace and `ppppv2-data-pvc` (NFS 192.168.2.150:/volume1/PPPPv2) come from turing-pi-server's storage chart; v1
  stays in `pppp` with its own /volume1/PPPP. KUBECONFIG_CONTENT = `kubectl config view --raw --minify --flatten
  --context turing | base64` (the k3s admin certificate: renew yearly, also in turing-pi-server). Public hostname
  https://pppp-v2.tomassir.io: a Cloudflare Tunnel route to `ppppv2-frontend.ppppv2:80`, set in the Cloudflare
  dashboard. The ROMs were copied to the volume's data/roms by hand (2026-11-23). v1's data was archived on 2026-11-23 to v1's
  `data/v1-archive-20261123/` (local, gitignored).

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
  - `pppp-emulator`: `dev.pppp.emulator` — SameBoy (C) via JNI, one thread per console. No Spring.
    `Bus` is our memory interface (getByte/setByte) for code that pokes a running game.
  - `pppp-core`: `model`, `exception`, `service`, `repository`, `job`, `config` — business logic. No web dependencies; Redis access only in `repository`.
  - `pppp-app`: `controller` (+ `ApiExceptionHandler`), `security`, `realtime` (WebSockets: events, screen, audio, input), `config`, `PpppApplication`.
  - Dependencies point one way: app -> core -> emulator.
- Build/test: `cd backend && mvn -q test`. Run: `mvn -q package -DskipTests && java -jar pppp-app/target/pppp-app-*.jar` with the repo-root `.env` exported (`set -a; . ../.env; set +a`). Integration tests live in pppp-app: `@SpringBootTest @DirtiesContext @AutoConfigureMockMvc @Import(TestRedis.class)` (Testcontainers Redis; Docker required). `@DirtiesContext` gives each class its own context and Redis: shared contexts leak consoles and Redis state between classes.
- Auth in tests: `.with(user("x@y.z").roles("PLAYER"))` or `oidcLogin()`; mutating calls need `.with(csrf())`.
- Emulator: SameBoy v1.0.5 (MIT) through `pppp-emulator/native/sameboy_jni.c` and `SameBoy.java`. Build it before
  tests or a local run: `pppp-emulator/native/build.sh` (git, make, cc, a JDK, RGBDS for the fast CGB boot ROM;
  `BOOT_ROM=<file>` skips RGBDS) writes `src/main/resources/native/<os>-<arch>/` + `cgb_boot.bin` (gitignored). The
  Dockerfile builds the arm64 copy in its own stage; CI builds the runner's before `mvn test`. SameBoy loads Coffee GB's
  parked BESS states. A fresh console runs the boot ROM first (~1 s): tests wait it out.

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
