<!-- A party as six slots: each member's 16x16 party icon (drawn from the viewer's own ROM, same species numbers) with
     a thin HP bar (hp={false} drops it, as on the scoreboards), greyed out when fainted, a star when shiny; empty slots dashed. levels: "Lv 38" under each.
     dex: hovering a member shows its Pokédex card, as far as the viewer's own Pokédex knows it. -->
<script module lang="ts">
	import { game } from '#lib/game.svelte.ts';
	import type { PartyMon } from '#lib/play.ts';

	/** The species' name from the viewer's own game, or "#25". */
	export const speciesName = (n: number) => game.summary?.collection?.names?.[n - 1] ?? `#${n}`;

	/** "Gyarados · Lv 32 · PAR · shiny", for tooltips and screen readers. */
	export const monLabel = (m: PartyMon) =>
		m.egg ? 'Egg' : [speciesName(m.species), `Lv ${m.level}`, m.status, m.shiny && 'shiny'].filter(Boolean).join(' · ');

	/** "Lv 32 · PAR · shiny": the line under the name on a party member's Pokédex card. */
	export const monNote = (m: PartyMon) => [`Lv ${m.level}`, m.status, m.shiny && 'shiny'].filter(Boolean).join(' · ');
</script>

<script lang="ts">
	import { hpColor } from '#lib/play.ts';
	import { watchGame } from '#lib/game.svelte.ts';
	import DexCard from '#lib/components/DexCard.svelte';
	import { DexHover, keepTap } from '#lib/dexhover.svelte.ts';

	let {
		party = [],
		size = 24,
		levels = false,
		hp = true,
		dex = false
	}: { party?: PartyMon[]; size?: number; levels?: boolean; hp?: boolean; dex?: boolean } = $props();
	const hover = new DexHover();
	$effect(() => watchGame()); // species names for the labels
	const slots = $derived([...party.slice(0, 6), ...Array(Math.max(0, 6 - party.length)).fill(null)] as (PartyMon | null)[]);
</script>

<span class="party" style:--s="{size}px" role="img" aria-label="Party: {party.length ? party.map(monLabel).join(', ') : 'empty'}">
	{#each slots as m, i (i)}
		<span class:slot={levels}>
			{#if m}
				<span
					role="presentation"
					class="mon"
					class:fnt={m.status === 'FNT'}
					title={dex ? undefined : monLabel(m)}
					onpointerenter={dex && !m.egg ? (e) => hover.show(m.species, e.currentTarget, { note: monNote(m) }) : undefined}
					onpointerleave={dex ? () => hover.hide() : undefined}
					onclick={dex && !m.egg ? keepTap : undefined}
				>
					{#if m.egg}
						<span class="egg"></span>
					{:else}
						<img src="/api/v1/game/icons/{m.species}.png" alt="" loading="lazy" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />
						{#if hp && m.hpPercent != null}<span class="hp"><b style:width="{m.hpPercent}%" style:background={hpColor(m.hpPercent)}></b></span>{/if}
						{#if m.shiny}<span class="shiny">✦</span>{/if}
					{/if}
				</span>
			{:else}
				<span class="mon empty"></span>
			{/if}
			{#if levels}<small>{m && !m.egg ? `Lv ${m.level}` : ' '}</small>{/if}
		</span>
	{/each}
</span>
{#if hover.card}<DexCard {...hover.card} />{/if}

<style>
	.party {
		display: flex;
		gap: 4px;
		flex: none;
	}
	.slot {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 3px;
	}
	small {
		font-size: 10px;
		color: var(--muted);
		font-variant-numeric: tabular-nums;
		white-space: pre;
	}
	.mon {
		position: relative;
		display: grid; /* the sprite centred in its slot */
		place-items: center;
		width: var(--s);
		height: var(--s);
		border-radius: 5px;
		background: var(--sunk);
	}
	.mon.empty {
		box-sizing: border-box;
		background: transparent;
		border: 1px dashed var(--border-strong);
	}
	.mon.fnt img {
		filter: grayscale(1);
		opacity: 0.45;
	}
	img {
		display: block;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
	}
	.hp {
		position: absolute;
		left: 3px;
		right: 3px;
		bottom: 1px;
		height: 2px;
		border-radius: 1px;
		background: rgb(0 0 0 / 0.22);
		overflow: hidden;
	}
	.hp b {
		display: block;
		height: 100%;
	}
	.shiny {
		position: absolute;
		top: -5px;
		right: -4px;
		font: 700 11px var(--body-font);
		color: var(--accent-text);
		text-shadow: 0 0 2px var(--panel);
	}
	.egg {
		position: absolute;
		inset: 20% 26%;
		border-radius: 50% 50% 46% 46% / 60% 60% 40% 40%;
		background: #f4f1e8;
		box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.25);
	}
</style>
