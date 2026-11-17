<!-- The picture for one activity line: the Pokémon's own party icon for catches (with a Poké Ball) and sightings
     (faded), the badge's sprite for badges, the rarity's ball for achievements, small drawn symbols otherwise. -->
<script lang="ts">
	import RarityBall from '#lib/components/RarityBall.svelte';
	import { game } from '#lib/game.svelte.ts';
	import { rarityOf, type ScoreEvent } from '#lib/play.ts';

	let { e }: { e: ScoreEvent } = $props();

	const badgeArt = import.meta.glob('../assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;

	/** National Pokédex number from the name, via the viewer's own game's species names. */
	const species = $derived.by(() => {
		if (e.type !== 'CAUGHT' && e.type !== 'SEEN') return 0;
		const names = game.summary?.collection?.names ?? [];
		const i = names.findIndex((n) => n?.toLowerCase() === e.detail.toLowerCase());
		return i + 1;
	});
	const badge = $derived.by(() => {
		if (e.type !== 'BADGE') return null;
		const i = (game.summary?.badges ?? []).findIndex((b) => b.name.toLowerCase() === e.detail.toLowerCase());
		return badgeArt[`../assets/badges/${e.detail.toLowerCase()}.png`] ?? (i >= 0 ? `/api/v1/game/badges/${i}.png` : null);
	});
	let broken = $state(false);
</script>

<span class="ico t-{e.type.toLowerCase()}" aria-hidden="true">
	{#if (e.type === 'CAUGHT' || e.type === 'SEEN') && species && !broken}
		<img class="mon" class:seen={e.type === 'SEEN'} src="/api/v1/game/icons/{species}.png" alt="" onerror={() => (broken = true)} />
		{#if e.type === 'CAUGHT'}<span class="corner"><RarityBall rarity="common" size={11} /></span>{/if}
	{:else if e.type === 'BADGE' && badge && !broken}
		<img class="badge" src={badge} alt="" onerror={() => (broken = true)} />
	{:else if e.type === 'ACHIEVEMENT'}
		<RarityBall rarity={rarityOf(e.points)} size={20} />
	{:else}
		<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
			{#if e.type === 'LEVEL'}
				<path d="M12 19V5M5 12l7-7 7 7" />
			{:else if e.type === 'HALL_OF_FAME'}
				<path d="M3 8l4 4 5-7 5 7 4-4-2 11H5z" />
			{:else if e.type === 'TITLE'}
				<circle cx="12" cy="9" r="5" /><path d="M9 13.5L7 21l5-3 5 3-2-7.5" />
			{:else if e.type === 'CAUGHT'}
				<circle cx="12" cy="12" r="8" /><path d="M4 12h16" /><circle cx="12" cy="12" r="2.5" />
			{:else if e.type === 'SEEN'}
				<path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z" /><circle cx="12" cy="12" r="2.5" />
			{:else if e.type === 'LINK_WIN' || e.type === 'LINK_LOSS'}
				<path d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2" /><path d="M14.5 6.5L18 3h3v3l-3.5 3.5M5 14l4 4M7 17l-3 3M3 19l2 2" />
			{:else if e.type === 'BADGE'}
				<path d="M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z" />
			{:else}
				<circle cx="12" cy="12" r="3" />
			{/if}
		</svg>
	{/if}
</span>

<style>
	.ico {
		position: relative;
		flex: none;
		width: 28px;
		height: 28px;
		border-radius: 8px;
		display: grid;
		place-items: center;
		background: var(--sunk, var(--border));
		border: 1px solid var(--border);
		color: var(--muted);
	}
	.mon,
	.badge {
		width: 24px;
		height: 24px;
		image-rendering: pixelated;
	}
	.badge {
		width: 20px;
		height: 20px;
	}
	.mon.seen {
		filter: grayscale(0.7);
		opacity: 0.6;
	}
	.corner {
		position: absolute;
		right: -3px;
		bottom: -3px;
		line-height: 0;
	}
	.t-level {
		color: var(--grass, #3fae4a);
	}
	.t-hall_of_fame,
	.t-link_win,
	.t-badge,
	.t-title {
		color: var(--accent-text);
	}
</style>
