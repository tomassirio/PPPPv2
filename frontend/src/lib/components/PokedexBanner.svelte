<!-- A side banner with one part of the Pokédex (1-76 / 77-151 for Red; Kanto 1-151 / Johto 152-251 for
     Gold/Silver/Crystal), headed by its region and how much of it is caught. Unseen = a faint silhouette, seen = the
     sprite in soft colour, caught = full colour plus a Poké Ball. Hovering a number opens its Pokédex card: ??? while
     unseen, name, category and types once seen, height, weight and the entry text once caught (GET /game/dex/{n},
     which only answers what the player's own Pokédex knows). Sprites come from the ROM via /api/v1/game/icons/{n}.png. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { game, watchGame } from '#lib/game.svelte.ts';
	import { TYPE_COLORS } from '#lib/play.ts';

	type Entry = {
		number: number;
		state: 'unseen' | 'seen' | 'caught';
		name: string | null;
		category: string | null;
		types: string[] | null;
		heightFeet: number | null;
		heightInches: number | null;
		weightPounds: number | null;
		text: string | null;
	};

	let { half }: { half: 1 | 2 } = $props();
	const GAP = 4;
	const CARD_HALF = 130; // about half the card's height, plus a margin

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

	// The card: one at a time, beside the banner (to the right of the left one, to the left of the right one).
	const entries = new Map<string, Promise<Entry | null>>(); // by number and state: a new catch asks again
	let card = $state<{ n: number; x: number; y: number; entry: Entry | null } | null>(null);
	let zoom = $state(1); // the page's CSS zoom: the card on <body> is zoomed alike, so its left/top are divided by it
	let hideTimer: ReturnType<typeof setTimeout> | undefined;

	function load(n: number): Promise<Entry | null> {
		const key = `${n}:${stateOf(n)}`;
		let p = entries.get(key);
		if (!p) {
			p = api<Entry>('GET', `/game/dex/${n}`).catch(() => null);
			entries.set(key, p);
		}
		return p;
	}

	function show(n: number, el: HTMLElement) {
		clearTimeout(hideTimer);
		const r = el.getBoundingClientRect();
		zoom = (el as HTMLElement & { currentCSSZoom?: number }).currentCSSZoom ?? 1; // the zoom it inherits
		const x = half === 1 ? r.right + 10 : r.left - 10;
		const half_ = CARD_HALF * zoom;
		const y = Math.min(Math.max(half_, r.top + r.height / 2), innerHeight - half_); // card stays on screen
		card = { n, x, y, entry: card?.n === n ? card.entry : null };
		load(n).then((entry) => {
			if (card?.n === n) card = { ...card, entry };
		});
	}

	function hide() {
		hideTimer = setTimeout(() => (card = null), 80);
	}

	/**
	 * The card lives on <body>: /play may be CSS-zoomed (see uiZoom), and inside a zoomed page a fixed element's
	 * left/top are not screen pixels. It takes the page's zoom so it matches the panels.
	 */
	function portal(node: HTMLElement) {
		node.style.zoom = String(zoom);
		document.body.appendChild(node);
		return { destroy: () => node.remove() };
	}

	const feet = (e: Entry) => `${e.heightFeet}′${String(e.heightInches).padStart(2, '0')}″`;
	const metres = (e: Entry) => (((e.heightFeet ?? 0) * 12 + (e.heightInches ?? 0)) * 0.0254).toFixed(1);
	const kilos = (e: Entry) => ((e.weightPounds ?? 0) * 0.45359237).toFixed(1);
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

{#if card}
	{@const e = card.entry}
	{@const st = e?.state ?? stateOf(card.n)}
	<div class="card {st}" class:left={half === 2} style:left="{card.x / zoom}px" style:top="{card.y / zoom}px" role="tooltip" use:portal>
		<div class="card-head">
			<span class="no pixel">No.{pad(card.n)}</span>
			<span class="name">{st === 'unseen' ? '???' : (e?.name ?? dex?.names[card.n - 1] ?? '…')}</span>
		</div>
		<div class="card-body">
			<div class="pic">
				{#if st === 'unseen'}
					<span class="shape" style:--src="url(/api/v1/game/icons/{card.n}.png)"></span>
				{:else}
					<img src="/api/v1/game/icons/{card.n}.png" alt="" />
				{/if}
			</div>
			<dl class="facts">
				<dt>Category</dt>
				<dd>{st === 'unseen' ? '???' : e?.category ? `${e.category} Pokémon` : '…'}</dd>
				<dt>Type</dt>
				<dd class="types">
					{#if st !== 'unseen' && e?.types}
						{#each e.types as t (t)}<span class="type" style:--t={TYPE_COLORS[t] ?? 'var(--border)'}>{t}</span>{/each}
					{:else}???{/if}
				</dd>
				<dt>Height</dt>
				<dd>{#if st === 'caught' && e?.heightFeet != null}{feet(e)} <span class="muted">· {metres(e)} m</span>{:else}?′??″{/if}</dd>
				<dt>Weight</dt>
				<dd>{#if st === 'caught' && e?.weightPounds != null}{e.weightPounds.toFixed(1)} lb <span class="muted">· {kilos(e)} kg</span>{:else}???.? lb{/if}</dd>
			</dl>
		</div>
		<p class="entry">
			{#if st === 'caught'}{e?.text ?? '…'}{:else if st === 'seen'}Catch it to read its Pokédex entry.{:else}Not seen yet.{/if}
		</p>
	</div>
{/if}

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
		--gap: 4px; /* GAP in the script */
		--cell: min(
			calc((100cqw - (var(--cols) - 1) * var(--gap)) / var(--cols)),
			calc((100cqh - (var(--rows) - 1) * var(--gap)) / var(--rows))
		);
		grid-template-columns: repeat(var(--cols), var(--cell));
		grid-auto-rows: var(--cell);
		gap: var(--gap);
		justify-content: center;
		align-content: start;
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

	/* The Pokédex card, drawn like the game's: a red frame around a screen. */
	.card {
		position: fixed;
		z-index: 50;
		width: 300px;
		transform: translateY(-50%);
		background: var(--panel);
		border: 3px solid #c8281e;
		border-radius: 12px;
		box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.55);
		color: var(--text);
		pointer-events: none;
		overflow: hidden;
		animation: pop 120ms ease-out;
	}
	.card.left {
		transform: translate(-100%, -50%);
	}
	@keyframes pop {
		from {
			opacity: 0;
			scale: 0.96;
		}
	}
	.card-head {
		display: flex;
		align-items: baseline;
		gap: 10px;
		padding: 8px 12px;
		background: #c8281e;
		color: #fff;
	}
	.no {
		font-size: 10px;
	}
	.name {
		font-weight: 700;
		font-size: 15px;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.card-body {
		display: grid;
		grid-template-columns: 88px 1fr;
		gap: 12px;
		padding: 12px 12px 4px;
		align-items: center;
	}
	.pic {
		width: 88px;
		height: 88px;
		display: grid;
		place-items: center;
		border-radius: 8px;
		background: color-mix(in srgb, var(--text) 5%, transparent);
	}
	.pic img,
	.pic .shape {
		width: 80px;
		height: 80px;
	}
	.pic .shape {
		opacity: 0.18;
	}
	.facts {
		margin: 0;
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 3px 8px;
		font-size: 12px;
	}
	dt {
		color: var(--muted);
	}
	dd {
		margin: 0;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
		min-width: 0;
	}
	.muted {
		color: var(--muted);
		font-weight: 500;
	}
	.types {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}
	.type {
		padding: 1px 7px;
		border-radius: 999px;
		background: var(--t);
		color: #1d1f1c;
		font-size: 11px;
		font-weight: 700;
		text-transform: capitalize;
	}
	.entry {
		margin: 0;
		padding: 8px 12px 12px;
		font-size: 13px;
		line-height: 1.45;
		color: var(--text);
	}
	.card:not(.caught) .entry {
		color: var(--muted);
		font-style: italic;
	}
	@media (prefers-reduced-motion: reduce) {
		li,
		.card {
			transition: none;
			animation: none;
		}
		li.active {
			transform: none;
		}
	}
</style>
