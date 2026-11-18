<!-- A Pokédex card, drawn like the game's (a red frame around a screen), floating at x/y on <body>: ??? while
     unseen, name, category and types once seen, height, weight and the entry text once caught. side: "right" of the
     point (vertically centred), "left" of it, or "above" it (horizontally centred). note: an extra line under the
     name ("Lv 100 · shiny"). The hovering itself: DexHover (dexhover.svelte.ts). -->
<script lang="ts">
	import { TIER_NAMES, TYPE_COLORS } from '#lib/play.ts';
	import type { DexEntry } from '#lib/dexhover.svelte.ts';

	let {
		n,
		entry,
		state,
		x,
		y,
		side = 'right',
		zoom = 1,
		name,
		note
	}: {
		n: number;
		entry: DexEntry | null;
		state: 'unseen' | 'seen' | 'caught';
		x: number;
		y: number;
		side?: 'right' | 'left' | 'above';
		zoom?: number;
		name?: string;
		note?: string;
	} = $props();

	const tier = $derived(state !== 'unseen' ? (entry?.tier ?? null) : null);
	const pad = (k: number) => String(k).padStart(3, '0');
	const feet = (e: DexEntry) => `${e.heightFeet}′${String(e.heightInches).padStart(2, '0')}″`;
	const metres = (e: DexEntry) => (((e.heightFeet ?? 0) * 12 + (e.heightInches ?? 0)) * 0.0254).toFixed(1);
	const kilos = (e: DexEntry) => ((e.weightPounds ?? 0) * 0.45359237).toFixed(1);

	/**
	 * The card lives on <body>: /play may be CSS-zoomed (see uiZoom), and inside a zoomed page a fixed element's
	 * left/top are not screen pixels. It takes the page's zoom so it matches the panels.
	 */
	function portal(node: HTMLElement) {
		node.style.zoom = String(zoom);
		document.body.appendChild(node);
		return { destroy: () => node.remove() };
	}
</script>

<div class="card {state} {tier && tier !== 'F' ? `t-${tier}` : ''}" class:left={side === 'left'} class:above={side === 'above'} style:left="{x / zoom}px" style:top="{y / zoom}px" role="tooltip" use:portal>
	<div class="card-head">
		<span class="no pixel">No.{pad(n)}</span>
		<span class="name">{state === 'unseen' ? '???' : (entry?.name ?? name ?? '…')}</span>
	</div>
	{#if note}<div class="note">{note}</div>{/if}
	<div class="card-body">
		<div class="pic">
			{#if tier}<span class="tier-tag tier-{tier}">{tier}</span>{/if}
			{#if state === 'unseen'}
				<span class="shape" style:--src="url(/api/v1/game/icons/{n}.png)"></span>
			{:else}
				<img src="/api/v1/game/icons/{n}.png" alt="" />
			{/if}
		</div>
		<dl class="facts">
			<dt>Category</dt>
			<dd>{state === 'unseen' ? '???' : entry?.category ? `${entry.category} Pokémon` : '…'}</dd>
			<dt>Type</dt>
			<dd class="types">
				{#if state !== 'unseen' && entry?.types}
					{#each entry.types as t (t)}<span class="type" style:--t={TYPE_COLORS[t] ?? 'var(--border)'}>{t}</span>{/each}
				{:else}???{/if}
			</dd>
			{#if state !== 'unseen' && entry?.tier}
				<dt>Tier</dt>
				<dd><span class="tier-name t-{entry.tier}">{entry.tier} · {TIER_NAMES[entry.tier]}</span></dd>
			{/if}
			<dt>Height</dt>
			<dd>{#if state === 'caught' && entry?.heightFeet != null}{feet(entry)} <span class="muted">· {metres(entry)} m</span>{:else}?′??″{/if}</dd>
			<dt>Weight</dt>
			<dd>{#if state === 'caught' && entry?.weightPounds != null}{entry.weightPounds.toFixed(1)} lb <span class="muted">· {kilos(entry)} kg</span>{:else}???.? lb{/if}</dd>
		</dl>
	</div>
	<p class="entry">
		{#if state === 'caught'}{entry?.text ?? '…'}{:else if state === 'seen'}Catch it to read its Pokédex entry.{:else}Not seen yet.{/if}
	</p>
</div>

<style>
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
	.card.above {
		transform: translate(-50%, -100%);
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
	/* The card's frame and header in its tier's colours (F, and anything not seen yet: the Pokédex red): E green, D blue, C violet;
	   B silver, A gold, S gold with a glow, M a turning holographic rainbow, as the party cards (.tier-card). */
	.card.t-E {
		--edge: #4caf50;
	}
	.card.t-D {
		--edge: #3d8bfd;
	}
	.card.t-C {
		--edge: #9b5de5;
	}
	.card.t-B {
		--edge: linear-gradient(135deg, #8a959e, #eef2f5 45%, #a7b1b9 60%, #e3e8ec);
		--head-ink: #1d1f1c;
	}
	.card.t-A {
		--edge: linear-gradient(135deg, #b8860b, #f7d774 45%, #d9a520 60%, #fff0b3);
		--head-ink: #1d1f1c;
	}
	.card.t-S {
		--edge: linear-gradient(135deg, #b8860b, #f7d774 30%, #fff6c9 50%, #f7d774 70%, #b8860b);
		--head-ink: #1d1f1c;
		box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.55), 0 0 22px -2px rgb(247 200 80 / 0.7);
	}
	.card.t-M {
		--edge: conic-gradient(from var(--tier-turn), #ff6fb5, #ffd36f, #7dffb0, #6fd8ff, #b58cff, #ff6fb5);
		--head-ink: #1d1f1c;
		box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.55), 0 0 22px -2px rgb(181 140 255 / 0.7);
		animation: pop 120ms ease-out, tier-turn 6s linear infinite;
	}
	.card[class*=' t-'] {
		border-color: transparent;
		background:
			linear-gradient(var(--panel), var(--panel)) padding-box,
			var(--edge) border-box;
	}
	.card[class*=' t-'] .card-head {
		background: var(--edge);
		color: var(--head-ink, #fff);
	}
	.pic .tier-tag {
		top: 4px;
		right: 4px;
	}
	.tier-name {
		font-weight: 700;
	}
	.tier-name.t-S,
	.tier-name.t-A {
		color: #c9930f;
	}
	.tier-name.t-M {
		background: linear-gradient(90deg, #ff6fb5, #e0a923, #2fbf71, #3aa7e0, #9b6ad6);
		-webkit-background-clip: text;
		background-clip: text;
		color: transparent;
	}
	.tier-name.t-B {
		color: #8a959e;
	}
	.tier-name.t-C {
		color: #9b5de5;
	}
	.tier-name.t-D {
		color: #3d8bfd;
	}
	.tier-name.t-E {
		color: #4caf50;
	}
	.note {
		padding: 6px 12px 0;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	img {
		object-fit: contain;
		image-rendering: pixelated;
	}
	.shape {
		display: block;
		background: var(--text);
		-webkit-mask: var(--src) center / contain no-repeat;
		mask: var(--src) center / contain no-repeat;
	}
	@media (prefers-reduced-motion: reduce) {
		.card {
			animation: none;
		}
	}
</style>
