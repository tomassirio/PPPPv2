<!-- Behind the sign-in device: Kanto and Johto redrawn as an overworld, one tile per cell of the Pokégear town map
     (lib/townmap.ts): sea, grass with flowers and tall grass, forested mountains, sandy routes, a Pokémon Center in
     every town, caves at the landmarks. Every player stands where they last were in their own game as their trainer
     (from their cartridge), their party following like v1's, their first name above. Online ones pace up and down
     their spot; offline ones stand faded. New players start in New Bark Town. -->
<script lang="ts">
	import { TERRAIN, cellOf } from '#lib/townmap.ts';
	import type { Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	const COLS = TERRAIN[0].length;
	const ROWS = TERRAIN.length;
	const COLORS: Record<string, string> = { POKEMON_GLD: '#e6b422', POKEMON_SLV: '#b8c2ce', PM_CRYSTAL: '#4fc3f0' };
	const NEW_BARK = { region: 'johto' as const, x: 140, y: 100 };
	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	let w = $state(1280);
	let h = $state(800);
	// Cover the window: the bigger of the two fits, so no edge shows.
	// Wide windows: the whole map across, the sea going on above and below (New Bark Town, where everyone starts,
	// is in the far right column). Narrow ones and phones: cover, cropping the sides. Whole pixels, so tile edges
	// never fall between two and show a seam.
	const cell = $derived(Math.ceil(w / h >= 1.2 ? w / COLS : Math.max(w / COLS, h / ROWS)));

	const hash = (x: number, y: number) => (((x * 73856093) ^ (y * 19349663)) >>> 0) % 997;
	const at = (c: number, r: number) => TERRAIN[r]?.[c] ?? 'w';
	const walkable = (c: number, r: number) => 'pgsCP'.includes(at(c, r));

	// size: 1 for a tile, 2 for a 2x2 building or cave drawn from its top-left cell (the rest of it draws nothing).
	type Tile = { x: number; y: number; kind: string; deco: string | null; wave: boolean; size: number };
	const tiles: Tile[] = [];
	for (let r = 0; r < ROWS; r++)
		for (let c = 0; c < COLS; c++) {
			const t = at(c, r), n = hash(c, r);
			let kind = 'grass', deco: string | null = null, size = 1;
			if (t === 'w') kind = 'water';
			else if (t === 'h') kind = 'shallow';
			else if (t === 's') kind = 'sand';
			else if (t === 'p') kind = 'path';
			else if (t === 'e') kind = 'ledge';
			else if (t === 'm') kind = n % 5 === 0 ? 'tree' : n % 7 === 0 ? 'rock' : 'ground';
			else if (t === 'C' || t === 'P') {
				if (at(c - 1, r) === t || at(c, r - 1) === t) kind = 'none';
				else {
					kind = t === 'P' ? 'cave' : n % 3 === 0 ? 'house' : 'pc';
					size = 2;
				}
			} else if (n < 70) kind = 'tree';
			else if (n < 190) kind = 'tall';
			else if (n < 290) deco = n % 2 ? 'flower red' : 'flower';
			tiles.push({ x: c, y: r, kind, deco, wave: t === 'w' && n % 4 === 0, size });
		}

	// Each trainer paces up to two cells either way along their row, as far as it can walk; with no room, stands.
	function patrol(col: number, row: number) {
		const span = (dc: number) => {
			let k = 0;
			while (k < 3 && walkable(col + dc * (k + 1), row)) k++;
			return k;
		};
		return { from: -span(-1), to: span(1) };
	}

	const trainers = $derived.by(() => {
		const crowd = new Map<string, number>();
		return (teaser?.trainers ?? []).map((t) => {
			const spot = t.region ? { region: t.region, x: t.x, y: t.y } : NEW_BARK;
			const { col, row } = cellOf(spot.region, spot.x, spot.y);
			const key = col + ',' + row;
			const k = crowd.get(key) ?? 0; // several on one spot stand a cell apart
			crowd.set(key, k + 1);
			const p = patrol(col, row);
			const offset = k === 0 ? 0 : (k % 2 ? 1 : -1) * Math.ceil(k / 2);
			const steps = p.to - p.from;
			return {
				...t,
				col,
				row: row + offset,
				...p,
				steps,
				paces: steps > 0,
				color: COLORS[t.version] ?? '#f2c14e',
				party: t.party.slice(0, 6)
			};
		});
	});
</script>

<svelte:window bind:innerWidth={w} bind:innerHeight={h} />

<div class="backdrop" aria-hidden="true">
	<div class="map" style:width="{COLS * cell}px" style:height="{ROWS * cell}px" style:--cell="{cell}px">
		<svg width="100%" height="100%" viewBox="0 0 {COLS * 16} {ROWS * 16}" shape-rendering="crispEdges">
			<defs>
			<symbol id="t-grass" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--g)" />
				<path d="M3 4h1v1H3zM5 4h1v1H5zM4 5h1v1H4zM11 10h1v1h-1zM13 10h1v1h-1zM12 11h1v1h-1z" fill="var(--m)" />
			</symbol>
			<symbol id="t-tall" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--m)" />
				<path d="M0 8 2 2 4 8 6 2 8 8 10 2 12 8 14 2 16 8ZM0 16 2 10 4 16 6 10 8 16 10 10 12 16 14 10 16 16Z" fill="var(--d)" />
				<path d="M2 2h1v2H2zM6 2h1v2H6zM10 2h1v2h-1zM14 2h1v2h-1zM2 10h1v2H2zM6 10h1v2H6zM10 10h1v2h-1zM14 10h1v2h-1z" fill="var(--l)" />
			</symbol>
			<!-- Swaying frames: the blade tips lean right, then left. Only the tips move; the clump stays put. -->
			<symbol id="t-tall-r" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--m)" />
				<path d="M0 8 3 2 4 8 7 2 8 8 11 2 12 8 15 2 16 8ZM0 16 3 11 4 16 7 11 8 16 11 11 12 16 15 11 16 16Z" fill="var(--d)" />
				<path d="M3 2h1v2H3zM7 2h1v2H7zM11 2h1v2h-1zM15 2h1v2h-1zM3 11h1v2H3zM7 11h1v2H7zM11 11h1v2h-1zM15 11h1v2h-1z" fill="var(--l)" />
			</symbol>
			<symbol id="t-tall-l" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--m)" />
				<path d="M0 8 1 2 4 8 5 2 8 8 9 2 12 8 13 2 16 8ZM0 16 1 11 4 16 5 11 8 16 9 11 12 16 13 11 16 16Z" fill="var(--d)" />
				<path d="M1 2h1v2H1zM5 2h1v2H5zM9 2h1v2H9zM13 2h1v2h-1zM1 11h1v2H1zM5 11h1v2H5zM9 11h1v2H9zM13 11h1v2h-1z" fill="var(--l)" />
			</symbol>
			<symbol id="t-tree" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--g)" />
				<path d="M4 15h8v1H4z" fill="var(--m)" />
				<path d="M6 11h4v4H6z" fill="var(--bark)" />
				<path d="M4 1h8v1H4zM2 2h12v1H2zM1 3h14v7H1zM2 10h12v1H2zM4 11h8v1H4z" fill="var(--d)" />
				<path d="M4 3h4v2H4zM3 5h2v2H3z" fill="var(--m)" />
				<path d="M10 8h4v2h-4zM8 10h4v1H8z" fill="var(--k)" />
			</symbol>
			<symbol id="t-path" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--p)" />
				<path d="M3 4h1v1H3zM11 2h1v1h-1zM7 9h1v1H7zM13 12h1v1h-1zM2 13h1v1H2z" fill="var(--p2)" />
			</symbol>
			<symbol id="t-water" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--w)" />
			</symbol>
			<symbol id="t-sign" viewBox="0 0 16 16">
				<use href="#t-grass" width="16" height="16" />
				<path d="M7 9h2v6H7z" fill="var(--bark)" />
				<path d="M2 2h12v8H2z" fill="var(--k)" />
				<path d="M3 3h10v6H3z" fill="#c89858" />
				<path d="M4 4h6v1H4zM4 6h8v1H4z" fill="var(--bark)" />
			</symbol>
			<!-- An item ball on the grass: red top, white bottom, a band and a button. -->
			<symbol id="t-ball" viewBox="0 0 16 16">
				<use href="#t-grass" width="16" height="16" />
				<path d="M4 13h8v1H4z" fill="var(--m)" />
				<path d="M6 3h4v1H6zM4 4h8v2H4zM3 6h10v1H3z" fill="#e04848" />
				<path d="M3 8h10v1H3zM4 9h8v2H4zM6 11h4v1H6z" fill="#f8f8f8" />
				<path d="M3 7h10v1H3z" fill="#202020" />
				<path d="M7 6h2v3H7z" fill="#202020" />
				<path d="M7.5 7h1v1h-1z" fill="#f8f8f8" />
				<path d="M5 4h2v1H5z" fill="#ff9a9a" />
			</symbol>
			<!-- A Pokémon Center, 3x3 tiles: red roof, the Poké Ball sign, two windows, the sliding door. -->
			<symbol id="t-pc" viewBox="0 0 48 48">
				<rect width="48" height="48" fill="var(--g)" />
				<path d="M3 46h42v2H3z" fill="var(--m)" />
				<path d="M4 3h40v1H4zM2 4h44v12H2z" fill="var(--k)" />
				<path d="M3 4h42v10H3z" fill="#e04848" />
				<path d="M3 12h42v2H3z" fill="#a02828" />
				<path d="M3 5h42v1H3zM3 8h42v1H3z" fill="#f07070" />
				<path d="M4 16h40v30H4z" fill="var(--k)" />
				<path d="M5 16h38v29H5z" fill="#f4ecd8" />
				<path d="M5 16h38v2H5z" fill="#d8ccb0" />
				<path d="M20 18h8v8h-8z" fill="var(--k)" />
				<path d="M21 19h6v3h-6z" fill="#e04848" />
				<path d="M21 22h6v3h-6z" fill="#f8f8f8" />
				<path d="M23 21h2v2h-2z" fill="var(--k)" />
				<path d="M8 24h9v8H8zM31 24h9v8h-9z" fill="var(--k)" />
				<path d="M9 25h7v6H9zM32 25h7v6h-7z" fill="#78b8e8" />
				<path d="M9 25h3v2H9zM32 25h3v2h-3z" fill="#c8e8ff" />
				<path d="M19 33h10v12H19z" fill="var(--k)" />
				<path d="M20 34h4v11h-4zM24 34h4v11h-4z" fill="#58a0d8" />
				<path d="M20 34h1v11h-1zM24 34h1v11h-1z" fill="#c8e8ff" />
				<path d="M8 36h9v1H8zM31 36h9v1h-9z" fill="#d8ccb0" />
			</symbol>
			<!-- Two frames each, swapped by CSS. -->
			<symbol id="t-flower-1" viewBox="0 0 16 16">
				<path d="M7 5h2v2H7zM5 7h2v2H5zM9 7h2v2H9zM7 9h2v2H7z" fill="var(--petal)" />
				<path d="M7 7h2v2H7z" fill="#f0c030" />
			</symbol>
			<symbol id="t-flower-2" viewBox="0 0 16 16">
				<path d="M5 5h2v2H5zM9 5h2v2H9zM5 9h2v2H5zM9 9h2v2H9z" fill="var(--petal)" />
				<path d="M7 7h2v2H7z" fill="#f0c030" />
			</symbol>
			<symbol id="t-wave-1" viewBox="0 0 16 16">
				<path d="M2 4h4v1H2zM10 10h4v1h-4z" fill="var(--w2)" />
			</symbol>
			<symbol id="t-wave-2" viewBox="0 0 16 16">
				<path d="M4 4h4v1H4zM8 10h4v1H8z" fill="var(--w2)" />
			</symbol>
			<!-- Mountains: a rocky ledge with a boulder. -->
			<symbol id="t-rock" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--r)" />
				<path d="M0 12h16v1H0z" fill="var(--r2)" />
				<path d="M0 13h16v3H0z" fill="var(--r3)" />
				<path d="M3 3h6v1H3zM2 4h8v4H2zM3 8h6v1H3z" fill="var(--r3)" />
				<path d="M3 4h4v1H3zM3 5h2v1H3z" fill="var(--r4)" />
				<path d="M11 6h1v1h-1zM13 2h1v1h-1zM6 10h1v1H6z" fill="var(--r2)" />
			</symbol>
			<!-- A landmark: a cave mouth in the rock. -->
			<symbol id="t-cave" viewBox="0 0 16 16">
				<use href="#t-rock" width="16" height="16" />
				<path d="M4 6h8v1H4zM3 7h10v6H3z" fill="var(--r3)" />
				<path d="M5 7h6v1H5zM4 8h8v5H4z" fill="#141018" />
			</symbol>
			<!-- The shallows along the coast, a sandy beach, and a mountain's south face. -->
			<symbol id="t-shallow" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--w)" />
				<path d="M0 0h16v16H0z" fill="var(--w2)" opacity="0.35" />
				<path d="M2 3h3v1H2zM9 9h4v1H9z" fill="#fff" opacity="0.6" />
			</symbol>
			<symbol id="t-sand" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--sand)" />
				<path d="M3 3h1v1H3zM10 6h1v1h-1zM6 11h1v1H6zM13 13h1v1h-1z" fill="var(--sand2)" />
			</symbol>
			<!-- Mountains mostly bare rock; a boulder now and then (t-rock). -->
			<symbol id="t-ground" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--r)" />
				<path d="M3 4h2v1H3zM11 2h1v1h-1zM8 9h2v1H8zM2 12h1v1H2zM13 11h1v1h-1z" fill="var(--r2)" />
				<path d="M4 4h1v1H4zM9 9h1v1H9z" fill="var(--r4)" />
			</symbol>
			<symbol id="t-ledge" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--r)" />
				<path d="M0 9h16v2H0z" fill="var(--r4)" />
				<path d="M0 11h16v5H0z" fill="var(--r3)" />
				<path d="M2 12h2v3H2zM7 12h1v3H7zM11 12h2v3h-2z" fill="var(--r2)" />
				<path d="M4 3h1v1H4zM10 5h1v1h-1z" fill="var(--r2)" />
			</symbol>
			<!-- A house for towns that are not a Pokémon Center's tile. -->
			<symbol id="t-house" viewBox="0 0 16 16">
				<rect width="16" height="16" fill="var(--g)" />
				<path d="M2 2h12v1H2zM1 3h14v5H1z" fill="var(--k)" />
				<path d="M2 3h12v4H2z" fill="#4878c8" />
				<path d="M2 5h12v1H2z" fill="#2858a8" />
				<path d="M2 8h12v7H2z" fill="var(--k)" />
				<path d="M3 8h10v6H3z" fill="#f4ecd8" />
				<path d="M7 10h3v4H7z" fill="#7a5230" />
				<path d="M4 10h2v2H4z" fill="#78b8e8" />
			</symbol>
			</defs>
			{#each tiles as t (t.x + ',' + t.y)}
				{@const x = t.x * 16}
				{@const y = t.y * 16}
				{#if t.kind === 'none'}
					<!-- under a 2x2 building or cave -->
				{:else if t.size === 2}
					<use href="#t-grass" {x} {y} width="32" height="32" />
					<use href="#t-{t.kind}" {x} {y} width="32" height="32" />
				{:else if t.kind === 'water'}
					<use href="#t-water" {x} {y} width="16" height="16" />
					{#if t.wave}
						<use class="f1" href="#t-wave-1" {x} {y} width="16" height="16" />
						<use class="f2" href="#t-wave-2" {x} {y} width="16" height="16" />
					{/if}
				{:else}
					<use href="#t-{t.kind}" {x} {y} width="16" height="16" />
					{#if t.deco}
						<g class:red={t.deco.includes('red')}>
							<use class="f1" href="#t-flower-1" {x} {y} width="16" height="16" />
							<use class="f2" href="#t-flower-2" {x} {y} width="16" height="16" />
						</g>
					{/if}
				{/if}
			{/each}
		</svg>

		{#each trainers as t, i (i)}
			<div
				class="trainer"
				class:walking={t.online && t.paces && !still}
				class:offline={!t.online}
				style:left="{(t.col - 0.5) * cell}px"
				style:top="{(t.row - 1) * cell}px"
				style:--from={t.from}
				style:--to={t.to}
				style:--steps={Math.max(1, t.steps)}
				style:--c={t.color}
			>
				<span class="name"><i class:on={t.online}></i>{t.name}</span>
				<!-- The line faces left with the party trailing to its right; mirrored on the way right. -->
				<div class="line">
					{#each t.party.slice().reverse() as mon, k (k)}
						<img class="walker mon" src="/api/v1/public/sprites/{t.cartridge}/{mon}.png" alt="" style:--i={t.party.length - k} />
					{/each}
					<span class="walker player" style:background-image="url(/api/v1/public/sprites/{t.cartridge}/player.png)"></span>
				</div>
			</div>
		{/each}
	</div>
</div>

<style>
	.backdrop {
		--l: #d8f0a8;
		--g: #a8d878;
		--m: #70b058;
		--d: #387848;
		--k: #1c4030;
		--bark: #7a5230;
		--p: #e8cf98;
		--p2: #c8a868;
		--sand: #f4ecc4;
		--sand2: #e0d098;
		--w: #5898d8;
		--w2: #b0d8f8;
		--r: #8f7a62;
		--r2: #6e5c48;
		--r3: #4a3c2e;
		--r4: #b09a7e;
		--petal: #fff;
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
		background: var(--w);
		filter: var(--scenery-filter);
	}
	.map {
		position: absolute;
		left: 50%;
		top: 50%;
		translate: -50% -50%;
	}
	svg {
		display: block;
	}
	.red {
		--petal: #f06878;
	}
	.f2 {
		visibility: hidden;
	}
	@media (prefers-reduced-motion: no-preference) {
		.f1,
		.f2 {
			animation: frame 1.6s steps(1) infinite;
		}
		.f2 {
			animation-delay: -0.8s;
		}
	}
	@keyframes frame {
		0% {
			visibility: visible;
		}
		50% {
			visibility: hidden;
		}
	}

	/* A trainer: the line (party behind the player) paces from --from to --to cells and back, three cells a second. */
	/* Two cells tall (16px sprites at an eighth of the cells' 4px), the feet on the trainer's cell. */
	.trainer {
		--sprite: calc(var(--cell) * 2);
		position: absolute;
		width: var(--sprite);
		height: var(--sprite);
		--t: calc(var(--steps) * 0.2s);
	}
	.walking {
		animation: pace calc(var(--t) * 2) linear infinite;
	}
	@keyframes pace {
		0%,
		100% {
			translate: calc(var(--from) * var(--cell)) 0;
		}
		50% {
			translate: calc(var(--to) * var(--cell)) 0;
		}
	}
	.line {
		position: absolute;
		inset: 0;
	}
	.walking .line {
		animation: face calc(var(--t) * 2) steps(1) infinite;
	}
	/* Facing right (mirrored) on the way out, left on the way back. */
	@keyframes face {
		0% {
			scale: -1 1;
		}
		50% {
			scale: 1 1;
		}
	}
	.offline {
		opacity: 0.65;
		filter: saturate(0.4);
	}
	.walker {
		position: absolute;
		inset: 0;
		image-rendering: pixelated;
		filter: drop-shadow(0 3px 0 rgb(0 0 0 / 0.2));
	}
	/* The party trails behind, a step each. */
	.mon {
		width: 100%;
		height: 100%;
		object-fit: contain;
		translate: calc(var(--i) * var(--sprite) * 0.7) 0;
	}
	.walking .mon {
		animation: hop 0.34s steps(1) calc(var(--i) * -0.17s) infinite;
	}
	@keyframes hop {
		50% {
			margin-top: -3px;
		}
	}
	/* The player's sheet (16x96: down, up, left, then each one's walking frame): standing still faces down. */
	.player {
		background-size: var(--sprite) calc(var(--sprite) * 6);
		background-position: 0 0;
	}
	.walking .player {
		background-position: 0 calc(var(--sprite) * -2);
		animation: step 0.34s steps(1) infinite;
	}
	@keyframes step {
		50% {
			background-position: 0 calc(var(--sprite) * -5);
		}
	}
	.name {
		position: absolute;
		left: 50%;
		bottom: calc(100% + 4px);
		translate: -50% 0;
		display: inline-flex;
		align-items: center;
		gap: 5px;
		padding: 3px 7px 4px;
		border-radius: 6px;
		background: rgb(16 18 24 / 0.85);
		border: 2px solid var(--c);
		color: #fff;
		font: 9px/1 var(--pixel-font);
		white-space: nowrap;
		z-index: 1;
	}
	.name i {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: #8a8f98;
	}
	.name i.on {
		background: #3fb950;
		box-shadow: 0 0 5px #3fb950;
	}
</style>
