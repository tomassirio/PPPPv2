<!-- Behind the sign-in page: the real Johto, as Pokémon Crystal draws it (GET /public/maps/{MAP}.png: the map, its
     connected neighbours and its border, rendered from the ROM for the time of day), and what stands on it
     (GET /public/maps/{MAP}.json): the town's own people walking their rounds, tall grass where that route's wild
     Pokémon peek out, and the players. A camera visits each player in turn; to a player on a connected map it pans
     along the road. Fresh catches, badges and achievements pop up over the player and the camera goes to them.
     Hovering a name shows that player's card. A Pokégear clock in the corner keeps the game's time of day. One
     whole-number pixel scale for everything; the camera keeps the player in the open part of the window (the right,
     beside the device and the card; the top on phones). -->
<script lang="ts">
	import { untrack } from 'svelte';
	import { api } from '#lib/api.js';
	import type { MapInfo, Teaser } from '#lib/teaser.js';
	import hoOh from '#lib/assets/legends/ho-oh.webp';
	import lugia from '#lib/assets/legends/lugia.webp';
	import suicune from '#lib/assets/legends/suicune.webp';

	/** legend: the cartridge on the device's screen; its legendary's shadow passes over the map once per turn. */
	let { teaser, legend = null }: { teaser: Teaser | null; legend?: 'gold' | 'silver' | 'crystal' | null } = $props();
	// Silhouettes cut from each game's title screen; all face left, so they cross right to left.
	const LEGENDS = { gold: hoOh, silver: lugia, crystal: suicune };

	const MARGIN = 20; // border blocks round each map image (PublicController.MAP_MARGIN)
	const BLOCK = 32; // px per block; a step is half a block
	const STAY_MS = 8000;
	const COLORS: Record<string, string> = { POKEMON_GLD: '#e6b422', POKEMON_SLV: '#b8c2ce', PM_CRYSTAL: '#4fc3f0' };
	const VERSION_NAMES: Record<string, string> = { POKEMON_GLD: 'Gold', POKEMON_SLV: 'Silver', PM_CRYSTAL: 'Crystal' };
	const HOME = { map: 'NEW_BARK_TOWN', x: 13, y: 6, facing: 'down' as const }; // where everyone starts
	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	// The game's clock: morning 4:00-9:59, day 10:00-17:59, night otherwise. Ticks so the map changes on the hour.
	let now = $state(new Date());
	$effect(() => {
		const t = setInterval(() => (now = new Date()), 15000);
		return () => clearInterval(t);
	});
	const time = $derived.by(() => {
		const hr = now.getHours();
		return hr >= 4 && hr < 10 ? 'morn' : hr >= 10 && hr < 18 ? 'day' : 'nite';
	});
	const DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
	const clock = $derived(`${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}`);

	let w = $state(1280);
	let h = $state(800);
	const scale = $derived(w >= 2200 ? 4 : w >= 900 ? 3 : 2);
	const wide = $derived(w >= 1100);
	// Where on the window the camera puts the player: the open right part, or the top band on phones.
	const focus = $derived(wide ? { x: w * 0.74, y: h * 0.55 } : { x: w / 2, y: h * 0.22 });

	type Facing = 'down' | 'up' | 'left' | 'right';
	type Spot = { map: string; x: number; y: number; facing: Facing };
	const placed = $derived(
		(teaser?.trainers ?? []).map((t, i) => ({ ...t, i, at: (t.spot ?? HOME) as Spot, color: COLORS[t.version] ?? '#f2c14e' }))
	);

	// Map info, fetched once per map and time of day.
	const infos = $state<Record<string, MapInfo | null>>({});
	function load(map: string) {
		const key = `${map}:${time}`;
		if (key in infos) return;
		infos[key] = null;
		api<MapInfo>('GET', `/public/maps/${map}.json?time=${time}&m=${MARGIN}`).then(
			(v) => {
				infos[key] = v;
			},
			() => {}
		);
	}
	const infoOf = (map: string) => infos[`${map}:${time}`] ?? null;

	// The camera's tour: every player (online ones first), or New Bark Town alone. A fresh moment cuts in.
	const tour = $derived([...placed].sort((a, b) => Number(b.online) - Number(a.online)));
	let turn = $state(0);
	let spotlight = $state<number | null>(null); // a trainer index while their moment shows
	$effect(() => {
		if (still) return;
		const t = setInterval(() => spotlight === null && turn++, STAY_MS);
		return () => clearInterval(t);
	});
	const current = $derived(
		spotlight !== null ? (placed[spotlight] ?? null) : tour.length ? tour[turn % tour.length] : null
	);
	const target = $derived<Spot>(current?.at ?? HOME);

	// What the picture shows (shown) and where the camera points, in steps from that map's top-left. To a player
	// on a map that is in the picture (a neighbour), the camera pans there first and then the picture becomes that
	// map, drawn so that nothing moves; anywhere else it fades.
	let shown = $state(HOME.map);
	let cam = $state({ x: HOME.x, y: HOME.y });
	let panning = $state(false);
	let instant = $state(false);
	let panMs = $state(1200);
	$effect(() => {
		const t = target; // the only thing this follows; the rest is read as it stands
		return untrack(() => {
			load(t.map);
			load(shown);
			if (t.map === shown) {
				cam = { x: t.x, y: t.y };
				return;
			}
			const n = infoOf(shown)?.neighbours.find((m) => m.map === t.map);
			if (!n || still) {
				shown = t.map;
				cam = { x: t.x, y: t.y };
				return;
			}
			const to = { x: n.x * 2 + t.x, y: n.y * 2 + t.y };
			panMs = Math.min(3200, 900 + Math.hypot(to.x - cam.x, to.y - cam.y) * 40);
			panning = true;
			cam = to;
			const swap = setTimeout(() => {
				instant = true;
				shown = t.map;
				cam = { x: t.x, y: t.y };
				panning = false;
				requestAnimationFrame(() => requestAnimationFrame(() => (instant = false)));
			}, panMs + 50);
			return () => clearTimeout(swap);
		});
	});
	const info = $derived(infoOf(shown));

	// Pixel position of a step's centre in the map image.
	const px = (x: number) => (MARGIN * BLOCK + x * 16 + 8) * scale;
	const shift = $derived({ x: focus.x - px(cam.x), y: focus.y - px(cam.y) });

	// Players on the shown map and on the neighbours in its picture, in the shown map's steps. Several on one step
	// (two in the same building stand at its door) line up beside the first, left, right, further left...; names
	// on odd steps from the first sit higher, so side-by-side tags don't overlap.
	const SIDE = [0, -1, 1, -2, 2, -3, 3];
	const here = $derived.by(() => {
		const off: Record<string, { x: number; y: number }> = { [shown]: { x: 0, y: 0 } };
		for (const n of info?.neighbours ?? []) off[n.map] = { x: n.x * 2, y: n.y * 2 };
		const taken: Record<string, number> = {};
		return placed
			.filter((p) => p.at.map in off)
			.map((p) => {
				const x = p.at.x + off[p.at.map].x, y = p.at.y + off[p.at.map].y;
				const k = (taken[`${x},${y}`] = (taken[`${x},${y}`] ?? -1) + 1);
				const dx = SIDE[k] ?? k;
				return { ...p, sx: x + dx, sy: y, raised: Math.abs(dx) % 2 === 1 };
			});
	});

	// The party trails behind: opposite to where the trainer faces.
	const BEHIND: Record<string, [number, number]> = { down: [0, -1], up: [0, 1], left: [1, 0], right: [-1, 0] };
	const ROW: Record<string, number> = { down: 0, up: 1, left: 2, right: 2 };
	// m: the margin, so a cached picture drawn with another one is never reused.
	const url = (map: string) => `/api/v1/public/maps/${map}.png?time=${time}&m=${MARGIN}`;

	// The town's people: where they stand now, which way they look, whether they are mid-step.
	type Walker = { sprite: number; palette: number; frames: number; x: number; y: number; facing: Facing; step: boolean };
	let walkers = $state<Walker[]>([]);
	$effect(() => {
		const npcs = info?.npcs ?? [];
		walkers = npcs.map((n) => ({ sprite: n.sprite, palette: n.palette, frames: n.frames, x: n.x, y: n.y, facing: n.facing, step: false }));
		if (still || !npcs.length) return;
		const DIRS: [Facing, number, number][] = [['down', 0, 1], ['up', 0, -1], ['left', -1, 0], ['right', 1, 0]];
		const t = setInterval(() => {
			walkers.forEach((wk, i) => {
				const n = npcs[i];
				if (n.move === 'still' || Math.random() < 0.6) return;
				if (n.move === 'spin') {
					wk.facing = DIRS[Math.floor(Math.random() * 4)][0];
					return;
				}
				const options = DIRS.filter(([, dx, dy]) =>
					n.move === 'updown' ? dx === 0 : n.move === 'leftright' ? dy === 0 : true
				);
				const [facing, dx, dy] = options[Math.floor(Math.random() * options.length)];
				wk.facing = facing;
				const rx = Math.max(n.rx, 1), ry = Math.max(n.ry, 1);
				const x = wk.x + dx, y = wk.y + dy;
				if (Math.abs(x - n.x) > rx || Math.abs(y - n.y) > ry) return;
				wk.x = x;
				wk.y = y;
				wk.step = true;
				setTimeout(() => (wk.step = false), 280);
			});
		}, 1100);
		return () => clearInterval(t);
	});
	// Sheet frames: down, up, left (right mirrors left), then the stepping ones for walking sprites.
	const frameOf = (wk: Walker) => {
		const row = wk.frames === 1 ? 0 : ROW[wk.facing];
		return wk.step && wk.frames === 6 ? row + 3 : row;
	};

	// Wild Pokémon: now and then a tuft of tall grass in view shakes and one of the route's Pokémon peeks out.
	const spriteCart = $derived(placed[0]?.cartridge ?? null);
	let peek = $state<{ x: number; y: number; mon: number; key: number } | null>(null);
	$effect(() => {
		if (still) return;
		let key = 0;
		const t = setInterval(() => {
			const i = info;
			if (!i || !i.wild.length || !spriteCart) return;
			const inView = i.grass.filter(([x, y]) => {
				const sx = shift.x + px(x), sy = shift.y + px(y);
				return sx > 40 && sx < w - 40 && sy > 80 && sy < h - 40 && (!wide || sx > w * 0.58);
			});
			if (!inView.length) return;
			const [x, y] = inView[Math.floor(Math.random() * inView.length)];
			const k = ++key;
			peek = { x, y, mon: i.wild[Math.floor(Math.random() * i.wild.length)], key: k };
			setTimeout(() => peek?.key === k && (peek = null), 2600);
		}, 5200);
		return () => clearInterval(t);
	});

	// Moments: anything newer than what this page has seen pops up over the player; the camera goes to them.
	// On arrival, the newest moment of the last ten minutes counts as new, so the page opens on something.
	let seenAt: number | null = null;
	let bubble = $state<{ trainer: number; text: string; key: number } | null>(null);
	let bubbleKey = 0;
	type Moment = NonNullable<Teaser['moments']>[number];
	const say = (m: Moment) =>
		m.type === 'CAUGHT' ? `Caught ${m.detail}!` : m.type === 'BADGE' ? `${m.detail} Badge!`
			: m.type === 'HALL_OF_FAME' ? 'Hall of Fame!' : `★ ${m.detail}`;
	$effect(() => {
		if (!teaser) return;
		const moments = teaser.moments ?? [];
		const since = seenAt ?? Date.now() - 10 * 60_000;
		const fresh = moments.filter((m) => m.at > since && m.trainer >= 0).reverse().slice(-3); // oldest first
		seenAt = Math.max(since, ...moments.map((m) => m.at));
		if (!fresh.length) return;
		// Not cleared on the next poll: the last timer always hands the camera back to the tour.
		fresh.forEach((m, k) =>
			setTimeout(() => {
				bubble = { trainer: m.trainer, text: say(m), key: ++bubbleKey };
				spotlight = m.trainer;
			}, k * 5000)
		);
		const last = bubbleKey + fresh.length;
		setTimeout(() => bubbleKey === last && ((bubble = null), (spotlight = null)), fresh.length * 5000);
	});

	// The card over a name: on hover or keyboard focus.
	let hovered = $state<number | null>(null);
</script>

<svelte:window bind:innerWidth={w} bind:innerHeight={h} />

<div class="scene {time}" style:--s={scale}>
	{#key shown}
		<div
			class="world"
			class:fade={!instant}
			class:instant
			style:translate="{shift.x}px {shift.y}px"
			style:--pan="{panning ? panMs : 1200}ms"
			aria-hidden="true"
		>
			<img class="map" src={url(shown)} alt="" />
			{#if peek}
				{#key peek.key}
					<div class="peek" style:left="{px(peek.x)}px" style:top="{px(peek.y)}px">
						<img src="/api/v1/public/sprites/{spriteCart}/{peek.mon}.png" alt="" />
						<span class="tuft"></span>
					</div>
				{/key}
			{/if}
			{#each walkers as n, i (i)}
				<span
					class="npc"
					class:mirror={n.facing === 'right' && n.frames > 1}
					style:left="{px(n.x)}px"
					style:top="{px(n.y)}px"
					style:background-image="url(/api/v1/public/npcs/{n.sprite}/{n.palette}.png?time={time})"
					style:background-size="{16 * scale}px {16 * scale * n.frames}px"
					style:background-position="0 {-16 * scale * frameOf(n)}px"
				></span>
			{/each}
		</div>
	{/key}
	<!-- Players sit on a layer of their own so their name tags take the pointer; it moves with the world. -->
	<div class="players" class:instant style:translate="{shift.x}px {shift.y}px" style:--pan="{panning ? panMs : 1200}ms">
		{#each here as t (t.i)}
			{@const back = BEHIND[t.at.facing] ?? BEHIND.down}
			<div
				class="trainer"
				class:offline={!t.online}
				class:me={current !== null && t.i === current.i}
				class:raised={t.raised}
				style:left="{px(t.sx)}px"
				style:top="{px(t.sy)}px"
				style:--c={t.color}
			>
				{#each t.party as mon, k (k)}
					<img
						class="mon"
						src="/api/v1/public/sprites/{t.cartridge}/{mon}.png"
						alt=""
						style:translate="{back[0] * (k + 1) * 16 * scale}px {back[1] * (k + 1) * 16 * scale}px"
						style:--k={k}
					/>
				{/each}
				<span
					class="player"
					class:mirror={t.at.facing === 'right'}
					style:background-image="url(/api/v1/public/sprites/{t.cartridge}/player.png{t.sprite ? `?sprite=${encodeURIComponent(t.sprite)}` : ''})"
					style:--row={ROW[t.at.facing] ?? 0}
				></span>
				<button
					type="button"
					class="name"
					aria-label="{t.name}, {VERSION_NAMES[t.version] ?? 'Pokémon'}{t.online ? ', online' : ''}"
					aria-expanded={hovered === t.i}
					onmouseenter={() => (hovered = t.i)}
					onmouseleave={() => (hovered = null)}
					onfocus={() => (hovered = t.i)}
					onblur={() => (hovered = null)}
				><i class:on={t.online}></i>{t.name}</button>
				{#if bubble && bubble.trainer === t.i}
					{#key bubble.key}
						<p class="bubble" role="status">{bubble.text}</p>
					{/key}
				{/if}
				{#if hovered === t.i}
					<div class="card" role="tooltip">
						<p class="who"><b>{t.name}</b><span>{VERSION_NAMES[t.version] ?? ''}</span></p>
						{#if t.title}<p class="title">{t.title}</p>{/if}
						<dl>
							<dt>BADGES</dt><dd>{t.badges ?? 0}</dd>
							<dt>CAUGHT</dt><dd>{t.caught ?? 0}</dd>
							{#if t.playTime}<dt>TIME</dt><dd>{t.playTime}</dd>{/if}
						</dl>
						{#if t.location}<p class="where">{t.location}</p>{/if}
						{#if t.party.length}
							<p class="party">
								{#each t.party as mon, k (k)}<img src="/api/v1/public/sprites/{t.cartridge}/{mon}.png" alt="" />{/each}
							</p>
						{/if}
					</div>
				{/if}
			</div>
		{/each}
	</div>
	{#if legend && !still}
		{#key legend}
			<img class="legend {legend}" src={LEGENDS[legend]} alt="" aria-hidden="true" />
		{/key}
	{/if}
	<!-- The Pokégear's clock: the day and time that set the map's morning, day or night. -->
	<p class="gear" aria-label="Game time: {time === 'morn' ? 'morning' : time === 'day' ? 'day' : 'night'}">
		<span class="day">{DAYS[now.getDay()]}</span>
		<span class="clock">{clock}</span>
		<span class="tod {time}">{time === 'morn' ? 'MORN' : time === 'day' ? 'DAY' : 'NITE'}</span>
	</p>
</div>

<style>
	.scene {
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
		background: #202830;
	}
	.world,
	.players {
		position: absolute;
		left: 0;
		top: 0;
		transition: translate var(--pan) cubic-bezier(0.4, 0, 0.2, 1);
	}
	/* The dark theme dims the world a little so the card stays the brightest thing. */
	.world {
		filter: var(--scenery-filter);
	}
	.world.fade {
		animation: arrive 0.7s ease-out;
	}
	.instant {
		transition: none;
	}
	@keyframes arrive {
		from {
			opacity: 0;
		}
	}
	.map {
		display: block;
		max-width: none;
		transform: scale(var(--s));
		transform-origin: 0 0;
		image-rendering: pixelated;
	}
	/* A trainer stands on their step: 16x16 at the map's scale, centred on it. */
	.trainer {
		position: absolute;
		width: calc(16px * var(--s));
		height: calc(16px * var(--s));
		translate: -50% -50%;
	}
	.trainer .player,
	.trainer .mon {
		filter: var(--scenery-filter) drop-shadow(0 calc(1px * var(--s)) 0 rgb(0 0 0 / 0.25));
	}
	.offline .player,
	.offline .mon {
		opacity: 0.7;
	}
	.player,
	.mon {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
	}
	.mon {
		object-fit: contain;
	}
	/* The player's sheet (16x96: down, up, left, then each one's walking frame); right mirrors left. */
	.player {
		background-size: 100% 600%; /* six frames; frame r sits at r x 20% */
		background-position: 0 calc(var(--row) * 20%);
	}
	/* The town's people: their sheet sized and shifted to one frame (inline); a step glides over a quarter second. */
	.npc {
		position: absolute;
		width: calc(16px * var(--s));
		height: calc(16px * var(--s));
		translate: -50% -50%;
		image-rendering: pixelated;
		background-repeat: no-repeat;
		filter: drop-shadow(0 calc(1px * var(--s)) 0 rgb(0 0 0 / 0.25));
		transition:
			left 0.28s linear,
			top 0.28s linear;
	}
	.mirror {
		scale: -1 1;
	}
	@media (prefers-reduced-motion: no-preference) {
		/* Online: walking on the spot, the party hopping along. */
		.trainer:not(.offline) .player {
			animation: step 0.6s steps(1) infinite;
		}
		.trainer:not(.offline) .mon {
			animation: hop 0.6s steps(1) calc(var(--k) * -0.3s) infinite;
		}
	}
	@keyframes step {
		50% {
			background-position: 0 calc((var(--row) + 3) * 20%);
		}
	}
	@keyframes hop {
		50% {
			margin-top: calc(-1px * var(--s));
		}
	}
	/* Wild Pokémon: rises out of the tuft, looks round, ducks back. */
	.peek {
		position: absolute;
		width: calc(16px * var(--s));
		height: calc(16px * var(--s));
		translate: -50% -50%;
	}
	.peek img {
		position: absolute;
		left: 50%;
		bottom: 30%;
		width: calc(22px * var(--s));
		image-rendering: pixelated;
		animation: peek 2.6s ease-in-out both;
	}
	@keyframes peek {
		0%,
		100% {
			translate: -50% 70%;
			opacity: 0;
		}
		18%,
		82% {
			translate: -50% 0;
			opacity: 1;
		}
		45% {
			rotate: -6deg;
		}
		60% {
			rotate: 6deg;
		}
	}
	.tuft {
		position: absolute;
		inset: 0;
		border-radius: 30%;
		box-shadow: inset 0 0 0 calc(1px * var(--s)) rgb(255 255 255 / 0.5);
		animation: shake 0.18s steps(2) 5;
	}
	@keyframes shake {
		50% {
			translate: calc(1px * var(--s)) 0;
		}
	}
	.name {
		position: absolute;
		left: 50%;
		bottom: calc(100% + 6px);
		translate: -50% 0;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 4px 8px 5px;
		border-radius: 6px;
		background: rgb(16 18 24 / 0.85);
		border: 2px solid var(--c);
		color: #fff;
		font: 10px/1 var(--pixel-font);
		white-space: nowrap;
		pointer-events: auto;
		cursor: default;
	}
	.name:focus-visible {
		outline: 2px solid #fff;
		outline-offset: 2px;
	}
	/* A second player on the same spot: their tag (and bubble, card) one tag higher. */
	.raised .name {
		bottom: calc(100% + 32px);
	}
	.raised .bubble {
		bottom: calc(100% + 66px);
	}
	.raised .card {
		bottom: calc(100% + 64px);
	}
	.me .name {
		font-size: 12px;
		padding: 5px 10px 6px;
	}
	.name i {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: #8a8f98;
	}
	.name i.on {
		background: #3fb950;
		box-shadow: 0 0 5px #3fb950;
	}
	/* A moment: the game's speech box over the name. */
	.bubble {
		position: absolute;
		left: 50%;
		bottom: calc(100% + 40px);
		translate: -50% 0;
		margin: 0;
		padding: 8px 12px;
		background: #fff;
		color: #202020;
		border: 3px solid #202020;
		border-radius: 6px;
		box-shadow:
			inset 0 0 0 2px #fff,
			inset 0 0 0 3px #202020;
		font: 11px/1.4 var(--pixel-font);
		white-space: nowrap;
		animation: pop 0.35s cubic-bezier(0.3, 1.6, 0.5, 1) both;
	}
	.bubble::after {
		content: '';
		position: absolute;
		top: 100%;
		left: calc(50% - 6px);
		border: 6px solid transparent;
		border-top-color: #202020;
	}
	@keyframes pop {
		from {
			scale: 0;
		}
	}
	/* The card over a name: a small Trainer Card. */
	.card {
		position: absolute;
		left: 50%;
		bottom: calc(100% + 38px);
		translate: -50% 0;
		z-index: 2;
		min-width: 170px;
		padding: 10px 12px;
		border-radius: 8px;
		background: rgb(16 18 24 / 0.94);
		border: 2px solid var(--c);
		color: #f2f2f2;
		font-size: 12px;
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.35);
	}
	.card p {
		margin: 0;
	}
	.who {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 12px;
	}
	.who b {
		font: 11px/1.3 var(--pixel-font);
	}
	.who span {
		font: 8px/1 var(--pixel-font);
		color: var(--c);
	}
	.card .title,
	.card .where {
		margin-top: 4px;
		color: #c9ccd2;
	}
	.card dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 3px 10px;
		margin: 8px 0 0;
	}
	.card dt {
		font: 8px/1.6 var(--pixel-font);
		color: #9aa0a8;
	}
	.card dd {
		margin: 0;
		font-weight: 600;
	}
	.card .party {
		display: flex;
		gap: 2px;
		margin-top: 8px;
	}
	.party img {
		width: 28px;
		height: 28px;
		image-rendering: pixelated;
	}
	.legend {
		position: absolute;
		left: 0;
		top: 0;
		image-rendering: pixelated;
		filter: brightness(0) blur(3px);
		opacity: 0.6;
	}
	.legend.gold {
		width: 360px;
		animation: soar 6.5s cubic-bezier(0.35, 0, 0.6, 1) both;
	}
	.legend.silver {
		width: 560px;
		animation: glide 7s linear both;
	}
	.legend.crystal {
		width: 220px;
		top: 64vh;
		filter: brightness(0) blur(2px);
		animation: dash 3.4s cubic-bezier(0.2, 0, 0.8, 1) both;
	}
	@keyframes soar {
		from {
			translate: 100vw 70vh;
		}
		to {
			translate: -60vw -10vh;
		}
	}
	@keyframes glide {
		from {
			translate: 100vw 18vh;
		}
		to {
			translate: -80vw 26vh;
		}
	}
	@keyframes dash {
		from {
			translate: 100vw 0;
		}
		to {
			translate: -60vw 0;
		}
	}
	/* Pokégear clock: a dark handset strip in the corner, the time of day in its own colour. */
	.gear {
		position: absolute;
		right: max(16px, env(safe-area-inset-right, 0px));
		bottom: max(16px, env(safe-area-inset-bottom, 0px));
		margin: 0;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 8px 12px;
		border-radius: 10px;
		background: rgb(20 22 30 / 0.88);
		border: 2px solid rgb(255 255 255 / 0.18);
		color: #f2f2f2;
		font: 11px/1 var(--pixel-font);
		box-shadow: 0 6px 18px rgb(0 0 0 / 0.3);
	}
	.gear .day {
		color: #9aa0a8;
	}
	.gear .clock {
		font-size: 14px;
	}
	.tod {
		padding: 3px 6px;
		border-radius: 4px;
		font-size: 9px;
		color: #202020;
	}
	.tod.morn {
		background: #ffd48a;
	}
	.tod.day {
		background: #8fd3ff;
	}
	.tod.nite {
		background: #6f6ad8;
		color: #fff;
	}
	/* Phones: the device covers the corners; the map's colours still tell the time. */
	@media (max-width: 700px) {
		.gear {
			display: none;
		}
	}
</style>
