<!-- A side banner with one part of the Pokédex (1-76 / 77-151 for Red; Gen 1 1-151 / Gen 2 152-251 for Gold/Silver/Crystal):
     unseen = silhouette with "?", seen = sprite, caught = sprite plus a Poké Ball. Sprites come from the ROM via
     /api/v1/game/icons/{n}.png. Fills its container, with as many columns as make the cells biggest. -->
<script lang="ts">
	import { game, watchGame } from '#lib/game.svelte.ts';

	let { half }: { half: 1 | 2 } = $props();
	const GAP = 4;

	$effect(() => watchGame());

	const dex = $derived(game.summary?.collection);
	const seen = $derived(new Set(dex?.seen));
	const owned = $derived(new Set(dex?.owned));
	// Gold/Silver/Crystal: Kanto's 151 on the left, Johto's 100 on the right. Red: 151 split down the middle.
	const split = $derived((dex?.size ?? 0) > 151 ? 151 : Math.ceil((dex?.size ?? 0) / 2));
	const from = $derived(half === 1 ? 1 : split + 1);
	const to = $derived(half === 1 ? split : (dex?.size ?? 0));
	const numbers = $derived(Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i));
	const pad = (n: number) => String(n).padStart(3, '0');

	let w = $state(0);
	let h = $state(0);
	const columns = $derived.by(() => {
		let best = 4;
		let cell = 0;
		for (let c = 2; c <= 12; c++) {
			const rows = Math.ceil(numbers.length / c);
			const size = Math.min((w - (c - 1) * GAP) / c, (h - (rows - 1) * GAP) / rows);
			if (size > cell) [best, cell] = [c, size];
		}
		return best;
	});
</script>

{#if dex?.size}
	<div class="fit" bind:clientWidth={w} bind:clientHeight={h}>
	<ol class="banner" aria-label="Pokédex {from} to {to}" style:--rows={Math.ceil(numbers.length / columns)} style:--cols={columns}>
		{#each numbers as n (n)}
			{@const name = dex.names[n - 1]}
			<li class:seen={seen.has(n)} class:owned={owned.has(n)} title="#{pad(n)} {name ?? '???'}">
				{#if seen.has(n)}
					<img src="/api/v1/game/icons/{n}.png" alt="{name}{owned.has(n) ? ', caught' : ', seen'}" loading="lazy" />
				{:else}
					<span class="shape" role="img" aria-label="#{n}, not seen yet" style:--src="url(/api/v1/game/icons/{n}.png)"></span>
				{/if}
				{#if !seen.has(n)}<span class="unknown" aria-hidden="true">?</span>{/if}
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
{/if}

<style>
	/* .fit is the size container: square cells, as big as the space allows; no tiles. */
	.fit {
		container-type: size;
		height: 100%;
	}

	.banner {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		--gap: 4px; /* GAP in the script */
		--cell: min(
			calc((100cqw - (var(--cols) - 1) * var(--gap)) / var(--cols)),
			calc((100cqh - (var(--rows) - 1) * var(--gap)) / var(--rows))
		);
		grid-template-columns: repeat(var(--cols), var(--cell));
		grid-auto-rows: var(--cell);
		gap: var(--gap);
		justify-content: center;
		align-content: center;
		height: 100%;
	}
	li {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	img,
	.shape {
		width: 100%;
		height: 100%;
	}
	img {
		object-fit: contain;
		image-rendering: pixelated;
	}
	/* Unseen: the sprite's shape filled with the text colour, faint, so it works on light and dark themes. */
	.shape {
		background: var(--text);
		opacity: 0.16;
		-webkit-mask: var(--src) center / contain no-repeat;
		mask: var(--src) center / contain no-repeat;
	}
	.unknown {
		position: absolute;
		font: 700 calc(var(--cell) * 0.34) / 1 var(--pixel-font);
		color: var(--muted);
	}
	.ball {
		position: absolute;
		right: 0;
		bottom: 0;
		width: 30%;
		height: 30%;
	}
</style>
