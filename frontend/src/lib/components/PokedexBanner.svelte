<!-- A side banner with one part of the Pokédex (1-76 / 77-151 for Red; Kanto 1-151 / Johto 152-251 for
     Gold/Silver/Crystal), headed by its region and how much of it is caught. Unseen = a faint silhouette, seen = the
     sprite in soft colour, caught = full colour plus a Poké Ball. Hovering a number opens its Pokédex card: ??? while
     unseen, name, category and types once seen, height, weight and the entry text once caught (GET /game/dex/{n},
     which only answers what the player's own Pokédex knows). Sprites come from the ROM via /api/v1/game/icons/{n}.png. -->
<script lang="ts">
	import DexCard from '#lib/components/DexCard.svelte';
	import { DexHover } from '#lib/dexhover.svelte.ts';
	import { game, watchGame } from '#lib/game.svelte.ts';

	let { half }: { half: 1 | 2 } = $props();
	const GAP = 3;

	$effect(() => watchGame());

	const dex = $derived(game.summary?.collection);
	const seen = $derived(new Set(dex?.seen));
	const owned = $derived(new Set(dex?.owned));
	const gen2 = $derived((dex?.size ?? 0) > 151);
	// Gold/Silver/Crystal: Kanto's 151 on the left, Johto's 100 on the right. Red: 151 split down the middle.
	const split = $derived(gen2 ? 151 : Math.ceil((dex?.size ?? 0) / 2));
	const from = $derived(half === 1 ? 1 : split + 1);
	const to = $derived(half === 1 ? split : (dex?.size ?? 0));
	const numbers = $derived(Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i));
	const pad = (n: number) => String(n).padStart(3, '0');
	const region = $derived(gen2 ? (half === 1 ? 'Kanto' : 'Johto') : `No. ${pad(from)}–${pad(to)}`);
	const caughtHere = $derived(numbers.filter((n) => owned.has(n)).length);
	const seenHere = $derived(numbers.filter((n) => seen.has(n)).length);
	const stateOf = (n: number) => (owned.has(n) ? 'caught' : seen.has(n) ? 'seen' : 'unseen');

	let w = $state(0);
	let h = $state(0);
	// The column count with the biggest square cells; but the fewest columns within 5% of that size, so the grid runs
	// the banner's whole height instead of stopping short (one more column often wins by under a pixel).
	const columns = $derived.by(() => {
		const size = (c: number) => {
			const rows = Math.ceil(numbers.length / c);
			return Math.min((w - (c - 1) * GAP) / c, (h - (rows - 1) * GAP) / rows);
		};
		let biggest = 0;
		for (let c = 2; c <= 12; c++) biggest = Math.max(biggest, size(c));
		for (let c = 2; c <= 12; c++) if (size(c) >= biggest * 0.95) return c;
		return 4;
	});

	// The card: one at a time, beside the banner (to the right of the left one, to the left of the right one).
	const hover = new DexHover();
	const card = $derived(hover.card);
	const show = (n: number, el: HTMLElement) =>
		hover.show(n, el, { side: half === 1 ? 'right' : 'left', key: stateOf(n), state: stateOf(n), name: dex?.names[n - 1] ?? undefined });
	const hide = () => hover.hide();
</script>

{#if dex?.size}
	<div class="wrap">
		<header class="head">
			<span class="region pixel">{region}</span>
			<span class="count" title="{caughtHere} caught, {seenHere} seen"><b>{caughtHere}</b>/{numbers.length}</span>
			<span class="bar" title="{caughtHere} caught, {seenHere} seen" aria-label="{caughtHere} caught, {seenHere} seen of {numbers.length}" role="img">
				<i style:width="{(seenHere / Math.max(1, numbers.length)) * 100}%"></i>
				<b style:width="{(caughtHere / Math.max(1, numbers.length)) * 100}%"></b>
			</span>
		</header>
		<div class="fit" bind:clientWidth={w} bind:clientHeight={h}>
			<ol class="banner" aria-label="Pokédex {from} to {to}" style:--rows={Math.ceil(numbers.length / columns)} style:--cols={columns}>
				{#each numbers as n (n)}
					{@const name = dex.names[n - 1]}
					<li
						class={stateOf(n)}
						class:active={card?.n === n}
						onpointerenter={(e) => show(n, e.currentTarget)}
						onpointerleave={hide}
					>
						{#if seen.has(n)}
							<img src="/api/v1/game/icons/{n}.png" alt="#{pad(n)} {name}{owned.has(n) ? ', caught' : ', seen'}" loading="lazy" />
						{:else}
							<span class="shape" role="img" aria-label="#{pad(n)}, not seen yet" style:--src="url(/api/v1/game/icons/{n}.png)"></span>
						{/if}
						{#if owned.has(n)}
							<svg class="ball" viewBox="0 0 16 16" aria-hidden="true">
								<circle cx="8" cy="8" r="7" fill="#fff" stroke="#111" stroke-width="1.5" />
								<path d="M1 8a7 7 0 0 1 14 0z" fill="#e3350d" stroke="#111" stroke-width="1.5" />
								<line x1="1" y1="8" x2="15" y2="8" stroke="#111" stroke-width="1.5" />
								<circle cx="8" cy="8" r="2.2" fill="#fff" stroke="#111" stroke-width="1.5" />
							</svg>
						{/if}
					</li>
				{/each}
			</ol>
		</div>
	</div>
{/if}

{#if card}<DexCard {...card} />{/if}

<style>
	.wrap {
		display: flex;
		flex-direction: column;
		gap: 10px;
		height: 100%;
	}
	.head {
		display: grid;
		grid-template-columns: 1fr auto;
		align-items: baseline;
		gap: 6px 8px;
		padding: 0 2px;
	}
	.region {
		font-size: 10px;
		letter-spacing: 0.04em;
		color: var(--text);
	}
	.count {
		white-space: nowrap;
		font-size: 12px;
		color: var(--muted);
		font-variant-numeric: tabular-nums;
	}
	.count b {
		color: var(--text);
	}
	.bar {
		grid-column: 1 / -1;
		position: relative;
		height: 4px;
		border-radius: 2px;
		background: color-mix(in srgb, var(--text) 10%, transparent);
		overflow: hidden;
	}
	.bar i,
	.bar b {
		position: absolute;
		inset: 0 auto 0 0;
		border-radius: 2px;
	}
	.bar i {
		background: color-mix(in srgb, var(--text) 25%, transparent);
	}
	.bar b {
		background: #e3350d;
	}

	/* .fit is the size container: square cells, as big as the space allows. */
	.fit {
		container-type: size;
		flex: 1;
		min-height: 0;
	}
	.banner {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		--gap: 3px; /* GAP in the script */
		--cell: min(
			calc((100cqw - (var(--cols) - 1) * var(--gap)) / var(--cols)),
			calc((100cqh - (var(--rows) - 1) * var(--gap)) / var(--rows))
		);
		grid-template-columns: repeat(var(--cols), var(--cell));
		grid-auto-rows: var(--cell);
		gap: var(--gap);
		justify-content: center;
		/* When the banner's width caps the cells, the rows spread over the height left instead of leaving it empty. */
		align-content: space-between;
		height: 100%;
	}
	li {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 6px;
		background: color-mix(in srgb, var(--text) 3%, transparent);
		transition:
			transform 120ms ease,
			background 120ms ease;
	}
	li.caught {
		background: color-mix(in srgb, #e3350d 9%, transparent);
	}
	li.active {
		transform: scale(1.18);
		z-index: 1;
		background: color-mix(in srgb, var(--text) 12%, transparent);
	}
	img,
	.shape {
		width: 92%;
		height: 92%;
	}
	img {
		object-fit: contain;
		image-rendering: pixelated;
	}
	li.seen img {
		opacity: 0.7;
		filter: saturate(0.45);
	}
	/* Unseen: the sprite's shape filled with the text colour, faint, so it works on light and dark themes. */
	.shape {
		display: block;
		background: var(--text);
		opacity: 0.1;
		-webkit-mask: var(--src) center / contain no-repeat;
		mask: var(--src) center / contain no-repeat;
	}
	.ball {
		position: absolute;
		right: 1px;
		bottom: 1px;
		width: 30%;
		height: 30%;
	}

	@media (prefers-reduced-motion: reduce) {
		li {
			transition: none;
			animation: none;
		}
		li.active {
			transform: none;
		}
	}
</style>
