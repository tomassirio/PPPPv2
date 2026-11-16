<!-- Behind the sign-in page: the real Johto, as Pokémon Crystal draws it (GET /public/maps/{MAP}.png: the map, its
     connected neighbours and its border, rendered from the ROM for the time of day). A camera visits each player in
     turn, every few seconds: their map, them standing on their tile facing the way they face, their party behind,
     their first name above; anyone else on the same map stands there too. One whole-number pixel scale for
     everything. The camera keeps the player in the open part of the window (the right, beside the device and the
     card; the top on phones). -->
<script lang="ts">
	import type { Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	const MARGIN = 14; // border blocks round each map image (PublicController.MAP_MARGIN)
	const BLOCK = 32; // px per block; a step is half a block
	const STAY_MS = 8000;
	const COLORS: Record<string, string> = { POKEMON_GLD: '#e6b422', POKEMON_SLV: '#b8c2ce', PM_CRYSTAL: '#4fc3f0' };
	const HOME = { map: 'NEW_BARK_TOWN', x: 13, y: 6, facing: 'down' }; // where everyone starts
	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	// The game's clock: morning 4:00-9:59, day 10:00-17:59, night otherwise.
	const hour = new Date().getHours();
	const time = hour >= 4 && hour < 10 ? 'morn' : hour >= 10 && hour < 18 ? 'day' : 'nite';

	let w = $state(1280);
	let h = $state(800);
	const scale = $derived(w >= 2200 ? 4 : w >= 900 ? 3 : 2);
	const wide = $derived(w >= 1100);
	// Where on the window the camera puts the player: the open right part, or the top band on phones.
	const focus = $derived(wide ? { x: w * 0.74, y: h * 0.55 } : { x: w / 2, y: h * 0.22 });

	type Trainer = NonNullable<Teaser['trainers']>[number];
	const placed = $derived(
		(teaser?.trainers ?? []).map((t) => ({ ...t, at: t.spot ?? HOME, color: COLORS[t.version] ?? '#f2c14e' }))
	);
	// The camera's tour: every player (online ones first), or New Bark Town alone.
	const tour = $derived([...placed].sort((a, b) => Number(b.online) - Number(a.online)));
	let turn = $state(0);
	$effect(() => {
		if (still) return;
		const t = setInterval(() => turn++, STAY_MS);
		return () => clearInterval(t);
	});
	const current = $derived(tour.length ? tour[turn % tour.length] : null);
	const spot = $derived(current?.at ?? HOME);
	const here = $derived(placed.filter((p) => p.at.map === spot.map));

	// Pixel position of a step's centre in the map image.
	const px = (x: number) => (MARGIN * BLOCK + x * 16 + 8) * scale;
	const shift = $derived({ x: focus.x - px(spot.x), y: focus.y - px(spot.y) });

	// The party trails behind: opposite to where the trainer faces.
	const BEHIND: Record<string, [number, number]> = { down: [0, -1], up: [0, 1], left: [1, 0], right: [-1, 0] };
	const ROW: Record<string, number> = { down: 0, up: 1, left: 2, right: 2 };
	const url = (map: string) => `/api/v1/public/maps/${map}.png?time=${time}`;
	const isMe = (t: Trainer & { at: { map: string } }) => current !== null && t === current;
</script>

<svelte:window bind:innerWidth={w} bind:innerHeight={h} />

<div class="scene {time}" aria-hidden="true" style:--s={scale}>
	{#key spot.map}
		<div class="world" style:translate="{shift.x}px {shift.y}px">
			<img class="map" src={url(spot.map)} alt="" />
			{#each here as t, i (i)}
				{@const back = BEHIND[t.at.facing] ?? BEHIND.down}
				<div
					class="trainer"
					class:offline={!t.online}
					class:me={isMe(t)}
					style:left="{px(t.at.x)}px"
					style:top="{px(t.at.y)}px"
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
						style:background-image="url(/api/v1/public/sprites/{t.cartridge}/player.png)"
						style:--row={ROW[t.at.facing] ?? 0}
					></span>
					<span class="name"><i class:on={t.online}></i>{t.name}</span>
				</div>
			{/each}
		</div>
	{/key}
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
	/* The dark theme dims the world a little so the card stays the brightest thing. */
	.world {
		position: absolute;
		left: 0;
		top: 0;
		filter: var(--scenery-filter);
		transition: translate 1.2s cubic-bezier(0.4, 0, 0.2, 1);
		animation: arrive 0.7s ease-out;
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
	.offline {
		opacity: 0.7;
		filter: saturate(0.5);
	}
	.player,
	.mon {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
		filter: drop-shadow(0 calc(1px * var(--s)) 0 rgb(0 0 0 / 0.25));
	}
	.mon {
		object-fit: contain;
	}
	/* The player's sheet (16x96: down, up, left, then each one's walking frame); right mirrors left. */
	.player {
		background-size: 100% 600%; /* six frames; frame r sits at r x 20% */
		background-position: 0 calc(var(--row) * 20%);
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
</style>
