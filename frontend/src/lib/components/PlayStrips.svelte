<!-- Upright phones, the Play panel: the strips that go with the controller, the ones the player picked (Settings, up to
     three, in their order; controls.svelte.ts). Location (the sign, town map on tap), Party (tap one for its Pokédex
     card), Latest activity (one live line, › opens the Activity panel), Badges, Pokédex, Rank. When height runs out
     they give way before the screen does, the last one first, and one cut short hides instead of showing half. -->
<script lang="ts">
	import Activity from '#lib/components/Activity.svelte';
	import GradeChip from '#lib/components/GradeChip.svelte';
	import LocationMap from '#lib/components/LocationMap.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { controls, stripsThatFit } from '#lib/controls.svelte.ts';
	import { game, watchGame } from '#lib/game.svelte.ts';
	import { link } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { place, versionOf } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';

	let { onactivity }: { onactivity: () => void } = $props();

	$effect(() => watchBoard());
	$effect(() => watchGame());

	const me = $derived(session.me?.id ?? '');
	const rank = $derived(board.rows.findIndex((r) => r.userId === me));
	const row = $derived(rank < 0 ? null : board.rows[rank]);
	const where = $derived(place(row?.location));
	const ls = $derived(link.state?.session);
	const signLabel = $derived(ls ? (ls.kind === 'TRADE' ? 'Trade Center' : 'Colosseum') : where.city);
	const dexMax = $derived(row && versionOf(row.game, board.names) ? 251 : 151);

	const badgeArt = import.meta.glob('../assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;
	const art = (name: string, i: number) => badgeArt[`../assets/badges/${name.toLowerCase()}.png`] ?? `/api/v1/game/badges/${i}.png`;
	const named = $derived(game.summary?.badges ?? []);

	/** Cut short by the height left: hide rather than show half. */
	function giveWay(el: HTMLElement) {
		const watch = new ResizeObserver(() => {
			if (el.querySelector('.pop')) return; // the town map is open over it: that is no overflow
			el.classList.toggle('cramped', el.clientHeight + 1 < el.scrollHeight);
		});
		watch.observe(el);
		return { destroy: () => watch.disconnect() };
	}
</script>

{#snippet label(text: string)}<span class="label">{text}</span>{/snippet}

<div class="strips">
	{#each controls.strips.slice(0, stripsThatFit()) as id, i (id)}
		<!-- Later strips give way first: a bigger flex-shrink each. -->
		<div class="strip s-{id}" class:linked={id === 'location' && !!ls} style:flex-shrink={10 ** i} use:giveWay>
			{#if id === 'location'}
				{#if signLabel}
					<LocationMap user={me} label={signLabel}>
						<svg class="pin" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"
							><path d="M10 1a6 6 0 0 0-6 6c0 4.5 6 12 6 12s6-7.5 6-12a6 6 0 0 0-6-6zm0 8.5A2.5 2.5 0 1 1 10 4.5a2.5 2.5 0 0 1 0 5z" /></svg
						>
						<span class="sr-only">You are at </span>
						<span class="place pixel">{signLabel.toUpperCase()}</span>
						{#if ls || where.spot}<span class="sub">{ls ? `Cable Club · with ${nameOf(ls.peer)}` : where.spot}</span>{/if}
					</LocationMap>
				{:else}
					{@render label('Location')}<span class="muted">Somewhere unknown</span>
				{/if}
			{:else if id === 'party'}
				{@render label('Party')}
				<span class="grow end"><PartyIcons party={row?.party} size={30} dex /></span>
			{:else if id === 'latest'}
				{@render label('Latest')}
				<div class="grow feed"><Activity limit={1} label="Latest" /></div>
				<button type="button" class="more" aria-label="All activity" onclick={onactivity}>
					<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
						><path d="M9 6l6 6-6 6" /></svg
					>
				</button>
			{:else if id === 'badges'}
				{@render label('Badges')}
				{#if named.length}
					<ul class="grow badges" aria-label="Badges: {named.filter((b) => b.earned).length} of {named.length} earned">
						{#each named as b, j (b.name)}
							<li class:locked={!b.earned} title="{b.name} Badge: {b.earned ? 'earned' : 'not earned'}">
								<img src={art(b.name, j)} alt="{b.name} Badge{b.earned ? '' : ' (not earned)'}" class:rom={art(b.name, j).startsWith('/api/')} />
							</li>
						{/each}
					</ul>
				{:else}
					<span class="grow end"><b>{row?.badges ?? 0}</b> earned</span>
				{/if}
			{:else if id === 'dex'}
				{@render label('Pokédex')}
				<span class="bar grow" role="meter" aria-label="Pokédex caught" aria-valuemin="0" aria-valuemax={dexMax} aria-valuenow={row?.caught ?? 0}
					><b style:width="{((row?.caught ?? 0) * 100) / dexMax}%"></b></span
				>
				<span class="dex-line"><b>{row?.caught ?? 0}</b>/{dexMax}<span class="seen">&nbsp;· {row?.seen ?? 0} seen</span></span>
			{:else if id === 'rank'}
				{@render label('Rank')}
				{#if row}
					<span class="grow end rank"
						><b class="num">#{rank + 1}</b><span class="muted">of {board.rows.length}</span><b>{row.trainerScore}</b><span class="title">{row.title}</span><GradeChip
							grade={row.grade}
						/></span
					>
				{:else}
					<span class="grow end muted">Not ranked yet</span>
				{/if}
			{/if}
		</div>
	{/each}
</div>

<style>
	.strips {
		display: flex;
		flex-direction: column;
		gap: 8px;
		min-height: 0;
	}
	/* One height for every strip (STRIP_SPACE in controls.svelte.ts is this plus the gap), so how many fit is exact. */
	.strip {
		flex: 0 1 48px;
		height: 48px;
		box-sizing: border-box;
		min-height: 0;
		overflow: hidden;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 0 12px;
		border: 1px solid var(--border);
		border-radius: 12px;
		background: var(--panel);
		font-size: 13px;
	}
	.strip:global(.cramped) {
		visibility: hidden;
	}
	.label {
		flex: none;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.grow {
		flex: 1 1 auto;
		min-width: 0;
	}
	.end {
		display: flex;
		justify-content: flex-end;
		align-items: center;
	}
	.muted {
		color: var(--muted);
	}
	/* Location: the sign, as desktop shows it over the screen. */
	.s-location {
		padding: 0;
		border: 2px solid var(--border-strong);
		background: var(--sunk);
	}
	/* The town map (tap the sign) opens over everything: the strip must not clip it. */
	.s-location {
		overflow: visible;
	}
	/* The town map opens in the middle of the screen, wherever the strip sits (above or below the controller). */
	.s-location :global(.pop) {
		position: fixed;
		top: 50%;
		left: 50%;
		translate: -50% -50%;
		max-width: calc(100vw - 24px);
		z-index: 60;
	}
	.s-location.linked {
		border-color: var(--link);
	}
	.s-location :global(.where) {
		flex: 1;
		min-width: 0;
		padding: 0 10px;
		align-self: stretch;
		gap: 10px;
	}
	.s-location > .label {
		margin-left: 12px;
	}
	.pin {
		flex: none;
		width: 18px;
		height: 18px;
		color: var(--accent-text);
	}
	.linked .pin {
		color: var(--link);
	}
	.place {
		flex: 1;
		min-width: 0;
		font-size: 11px;
		line-height: 1.5;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
	}
	/* Latest: one line, ellipsised. */
	.s-latest {
		padding-right: 4px;
	}
	.feed :global(li) {
		align-items: center;
	}
	.feed :global(li > span) {
		min-width: 0;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.more {
		flex: none;
		width: 36px;
		height: 36px;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
	}
	.more:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	/* Badges: every badge in one row (16 for Gold/Silver/Crystal), greyed out until earned. */
	.badges {
		display: flex;
		flex-wrap: wrap;
		justify-content: flex-end;
		gap: 2px;
		margin: 0;
		padding: 0;
		list-style: none;
	}
	.badges li {
		width: 15px;
		height: 15px;
	}
	.badges img {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}
	.badges img.rom {
		image-rendering: pixelated;
	}
	.badges .locked img {
		filter: grayscale(1) brightness(0.55);
		opacity: 0.7;
	}
	/* Pokédex: a bar and the count. */
	.dex-line {
		flex: none;
		color: var(--muted);
		font-variant-numeric: tabular-nums;
	}
	.dex-line b {
		color: var(--text);
	}
	.bar {
		height: 6px;
		border-radius: 999px;
		background: var(--sunk);
		overflow: hidden;
	}
	.bar b {
		display: block;
		height: 100%;
		background: var(--accent);
	}
	/* Rank: place, score, title, grade. */
	.rank {
		gap: 8px;
		white-space: nowrap;
	}
	.rank .num {
		color: var(--accent-text);
	}
	.rank .title {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		color: var(--muted);
	}
</style>
