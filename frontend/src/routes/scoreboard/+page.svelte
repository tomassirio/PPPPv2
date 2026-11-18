<!-- /scoreboard (frontend/design/PlayV2.mock.html, #scoreboard): the top three on a podium, then every Pib in a table
     (rank, avatar, name, status · where · game, party, badges, Pokédex, Trainer Score), filtered by version and sorted
     by a column, a page at a time (?page=, from 1; ?size= rows a page, 25 by default). Rows open the Pib's profile.
     Live from board.svelte.ts; paged here, since every row is already on the page for the live updates. -->
<script lang="ts">
	import Crown from '#lib/components/Crown.svelte';
	import RankNumber from '#lib/components/RankNumber.svelte';
	import Name from '#lib/components/Name.svelte';
	import GradeChip from '#lib/components/GradeChip.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import PartyIcons, { monLabel } from '#lib/components/PartyIcons.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import { goto } from '$app/navigation';
	import { flip } from 'svelte/animate';
	import CountUp from '#lib/components/CountUp.svelte';
	import { arrive, ms } from '#lib/motion.ts';
	import { page } from '$app/state';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { link, watchLink } from '#lib/link.svelte.ts';
	import { boardRows, place, playerStatus, versionOf, winRate, type BoardSort, type Version, score } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	$effect(() => watchBoard());
	$effect(() => watchLink());

	let version = $state<Version | 'All'>('All');
	let sort = $state<BoardSort>('points');
	const VERSIONS = ['All', 'Gold', 'Silver', 'Crystal'] as const;
	const SORTS: [BoardSort, string][] = [
		['points', 'Points'],
		['link', 'Link wins'],
		['winrate', 'Win %'],
		['badges', 'Badges'],
		['dex', 'Pokédex'],
		['level', 'Top level']
	];
	const rows = $derived(boardRows(board.rows, version, sort, board.names));

	// Paging: the page and its size live in the URL, so a page can be linked and Back goes to the previous one.
	const size = $derived(Math.min(100, Math.max(1, Number(page.url.searchParams.get('size')) || 25)));
	const pages = $derived(Math.max(1, Math.ceil(rows.length / size)));
	const current = $derived(Math.min(pages, Math.max(1, Number(page.url.searchParams.get('page')) || 1)));
	const first = $derived((current - 1) * size); // rank offset of this page
	const pageRows = $derived(rows.slice(first, first + size));
	function go(to: number) {
		const url = new URL(page.url.href);
		if (to === 1) url.searchParams.delete('page');
		else url.searchParams.set('page', String(to));
		goto(url);
	}
	// A new filter or sort starts again from the first page.
	function pick(apply: () => void) {
		apply();
		if (current !== 1) go(1);
	}
	const href = (user: string) => `/pib/${encodeURIComponent(user)}`;
	const status = (user: string, online: boolean) => playerStatus(user, online, session.me?.id, link.state?.lobby ?? [], link.links);
</script>

<svelte:head><title>Scoreboard · Pibs&amp;PibettesPlayPokemonV2</title></svelte:head>

<SubPage>
	<div class="head">
		<div class="titles">
			<h1 class="pixel">Scoreboard</h1>
			<span class="muted small">{board.rows.length} {board.rows.length === 1 ? 'Pib has' : 'Pibs have'} started · updates live</span>
		</div>
		<div class="filters">
			<div class="seg" role="group" aria-label="Game">
				{#each VERSIONS as v (v)}
					<button type="button" aria-pressed={version === v} onclick={() => pick(() => (version = v))}>{v}</button>
				{/each}
			</div>
			<div class="seg" role="group" aria-label="Sort by">
				{#each SORTS as [k, label] (k)}
					<button type="button" aria-pressed={sort === k} onclick={() => pick(() => (sort = k))}>{label}</button>
				{/each}
			</div>
		</div>
	</div>

	{#if rows.length}
		{#if current === 1}
		<!-- In the DOM 1, 2, 3 (read in order); on a wide screen placed 2 · 1 · 3, #1 standing tallest. -->
		<ol class="podium" aria-label="Top three">
			{#each rows.slice(0, 3) as r, i (r.userId)}
				{@const v = versionOf(r.game, board.names)}
				{@const metal = ['gold', 'silver', 'bronze'][i]}
				<li class="spot p{i + 1} metal-{metal}">
					<a class="pod metal-frame" href={href(r.userId)}>
						<span class="hang" aria-hidden="true">
							{#if i === 0}<Crown />{/if}
							<RankNumber rank={i + 1} total={board.rows.length} medal size={i === 0 ? 52 : 44} />
						</span>
						<span class="head">
							<span class="ring"><Avatar user={r.userId} size={i === 0 ? 68 : 56} /></span>
							<span class="info">
								<b class="name"><Name user={r.userId} colored label={r.userId === session.me?.id ? 'You' : undefined} /></b>
								<span class="row xs meta">
									{#if v}<span class="chip g-{v}"><span class="cart-dot"></span>{v}</span>{/if}
									<span class="muted">{r.badges} badges · {r.caught} caught</span>
								</span>
								<span class="pts-line"><span class="pts big">{score(r).toLocaleString('en')}</span> <GradeChip grade={r.grade} rating={r.trainerScore} /></span>
							</span>
						</span>
						<!-- The party on a shelf: six tiles, each member big in hard pixels, hopping now and then. -->
						<span class="shelf" role="img" aria-label="Party: {r.party?.length ? r.party.map(monLabel).join(', ') : 'empty'}">
							{#each Array.from({ length: 6 }, (_, k) => r.party?.[k] ?? null) as m, k (k)}
								<span class="tile" class:empty={!m} class:fnt={m?.status === 'FNT'} style:--d="{k * 0.35}s" title={m ? monLabel(m) : undefined}>
									{#if m}
										{#if m.egg}<span class="egg"></span>{:else}<img src="/api/v1/game/icons/{m.species}.png" alt="" loading="lazy" />{/if}
										{#if m.shiny}<span class="spark">✦</span>{/if}
										{#if !m.egg}<small class="lv">Lv {m.level}</small>{/if}
									{/if}
								</span>
							{/each}
						</span>
					</a>
				</li>
			{/each}
		</ol>
		{/if}

		<div class="tablewrap">
			<div class="tbl">
				<div class="tr th" aria-hidden="true">
					<span>#</span><span>Pib</span><span>Party</span><span>Link</span><span class="num">Badges</span><span>Pokédex</span><span class="num">Points</span>
				</div>
				{#each pageRows as r, j (r.userId)}
					{@const i = first + j}
					{@const st = status(r.userId, r.online)}
					{@const v = versionOf(r.game, board.names)}
					{@const where = st.kind === 'linked' ? 'Trade Center' : place(r.location).city}
					<a class="tr" class:you={r.userId === session.me?.id} href={href(r.userId)} animate:flip={{ duration: ms(500) }} in:arrive style:position="relative" style:z-index={pageRows.length - j}>
						<span class="rank"><RankNumber rank={i + 1} total={board.rows.length} medal /></span>
						<span class="row">
							<Avatar user={r.userId} />
							<span class="grow">
								<b class="name"><Name user={r.userId} colored /></b>
								<span class="row xs status-{st.kind}"><span class="status-dot"></span>{st.label}{#if where}<span class="muted ell">· {where}</span>{/if}{#if v}<span class="muted">·&nbsp;<span class="ver-{v}">{v}</span></span>{/if}</span>
							</span>
						</span>
						<span><PartyIcons party={r.party} size={30} hp={false} /></span>
						<span class="linkcol">
							<span class="rec pixel"><span class="w">{r.link?.wins ?? 0}</span>–<span class="l">{r.link?.losses ?? 0}</span><span class="sr-only"> link battles won and lost</span></span>
							<span class="muted xs">{#if winRate(r.link) != null}{winRate(r.link)}% won · {/if}{r.link?.trades ?? 0} {(r.link?.trades ?? 0) === 1 ? 'trade' : 'trades'}</span>
						</span>
						<span class="num"><b>{r.badges}</b><span class="muted"> / 16</span><span class="sr-only"> badges</span></span>
						<span class="dex">
							<span class="small"><b>{r.caught}</b> <span class="muted">caught · {r.seen} seen</span></span>
							<span class="bar"><b style:width="{(r.caught * 100) / 251}%"></b></span>
						</span>
						<span class="num"><span class="pts"><CountUp value={score(r)} /><span class="sr-only"> points, grade</span></span> <GradeChip grade={r.grade} rating={r.trainerScore} /></span>
					</a>
				{/each}
			</div>
		</div>
		{#if pages > 1}
			<nav class="pager" aria-label="Scoreboard pages">
				<button type="button" disabled={current === 1} onclick={() => go(current - 1)}>← Previous</button>
				<span class="small">
					Page <b>{current}</b> of {pages}<span class="muted">&nbsp;· {first + 1}–{first + pageRows.length} of {rows.length}</span>
				</span>
				<button type="button" disabled={current === pages} onclick={() => go(current + 1)}>Next →</button>
			</nav>
		{/if}
		<p class="muted xs">Points add up every catch, sighting, badge and achievement, with no cap; the grade is the separate 0–1000 Trainer rating. Link: battles won–lost, the share won, and Pokémon received in trades.</p>
	{:else if board.rows.length}
		<p class="muted">Nobody plays {version} yet.</p>
	{:else}
		<p class="muted">Nobody has picked a game yet.</p>
	{/if}
</SubPage>

<style>
	h1 {
		margin: 0;
		font-size: 16px;
		line-height: 1.6;
	}
	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-end;
		justify-content: space-between;
		gap: 16px;
	}
	.titles {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.filters {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
	}
	.pager {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		margin-top: 12px;
	}
	.pager button {
		padding: 8px 14px;
		border-radius: 8px;
		border: 1px solid var(--border);
		background: var(--panel);
		color: var(--text);
		font: inherit;
		font-weight: 600;
		cursor: pointer;
	}
	.pager button:disabled {
		opacity: 0.4;
		cursor: default;
	}
	.seg {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
		padding: 4px;
		border-radius: 10px;
		background: var(--panel);
		border: 1px solid var(--border);
	}
	.seg button {
		height: 28px;
		padding: 0 10px;
		border-radius: 7px;
		border: none;
		background: transparent;
		color: var(--muted);
		font-size: 12px;
		font-weight: 600;
	}
	.seg button[aria-pressed='true'] {
		background: var(--accent);
		color: var(--ink);
	}
	button:focus-visible,
	a:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.muted {
		color: var(--muted);
	}
	.small {
		font-size: 13px;
	}
	.xs {
		font-size: 12px;
	}
	p {
		margin: 0;
	}
	.row {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}
	.row.xs {
		gap: 6px;
		margin-top: 3px;
	}
	.between {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.grow {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.name,
	.ell {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.pts {
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.pts.big {
		font-size: 20px;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 2px 8px;
		border-radius: 999px;
		background: var(--sunk);
		border: 1px solid var(--border);
		font-weight: 600;
		white-space: nowrap;
	}
	.cart-dot {
		width: 8px;
		height: 8px;
		border-radius: 2px;
	}
	.g-Gold {
		border-color: var(--cart-gold);
	}
	.g-Gold .cart-dot {
		background: var(--cart-gold);
	}
	.g-Silver {
		border-color: var(--cart-silver);
	}
	.g-Silver .cart-dot {
		background: var(--cart-silver);
	}
	.g-Crystal {
		border-color: var(--cart-crystal);
	}
	.g-Crystal .cart-dot {
		background: var(--cart-crystal);
	}

	.podium {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		align-items: end;
		gap: 14px;
		padding-top: 18px; /* room for #1's crown */
	}
	.spot {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.spot.p1 {
		order: 2;
	}
	.spot.p2 {
		order: 1;
	}
	.spot.p3 {
		order: 3;
	}
	.pod {
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: stretch;
		gap: 14px;
		padding: 40px 16px 16px;
		margin-top: 34px; /* the medal hangs above the card */
		text-align: center;
		color: inherit;
		text-decoration: none;
		border-radius: 16px;
	}
	.p1 .pod {
		padding: 52px 18px 20px;
		margin-top: 52px;
	}
	.p2 .pod {
		margin-bottom: 0;
	}
	.pod:hover {
		color: inherit;
	}
	.pod:hover .ring {
		transform: translateY(-2px);
	}
	.hang {
		position: absolute;
		top: 0;
		left: 50%;
		translate: -50% -58%;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.ring {
		display: inline-flex;
		padding: 3px;
		border-radius: 50%;
		background: linear-gradient(135deg, var(--m-hi), var(--m-lo));
		box-shadow: 0 0 18px var(--m-glow);
		transition: transform 0.2s;
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 14px;
		min-width: 0;
	}
	.info {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 4px;
		min-width: 0;
		text-align: left;
	}
	.meta {
		flex-wrap: wrap;
	}
	.shelf {
		display: grid;
		grid-template-columns: repeat(6, minmax(0, 1fr));
		gap: 6px;
		padding: 8px;
		border-radius: 12px;
		background: color-mix(in srgb, var(--m-lo) 10%, var(--sunk));
		box-shadow: inset 0 2px 6px rgb(0 0 0 / 0.18);
	}
	.tile {
		position: relative;
		aspect-ratio: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 10px;
		background: radial-gradient(circle at 50% 62%, color-mix(in srgb, var(--m-hi) 22%, transparent), transparent 70%), var(--panel);
		border: 1px solid color-mix(in srgb, var(--m-hi) 35%, var(--border));
	}
	.tile.empty {
		background: none;
		border: 1px dashed var(--border-strong);
		opacity: 0.6;
	}
	.tile img {
		width: 72%;
		height: 72%;
		image-rendering: pixelated;
		filter: drop-shadow(0 3px 0 rgb(0 0 0 / 0.25));
		animation: hop 3.2s ease-in-out var(--d) infinite;
	}
	.tile.fnt img {
		filter: grayscale(1);
		opacity: 0.5;
		animation: none;
	}
	.tile .egg {
		width: 40%;
		height: 50%;
		border-radius: 50% 50% 46% 46% / 58% 58% 42% 42%;
		background: #f3ead2;
		border: 2px solid #2b2b2b;
	}
	@keyframes hop {
		0%,
		70%,
		100% {
			transform: none;
		}
		78% {
			transform: translateY(-14%);
		}
		86% {
			transform: none;
		}
		92% {
			transform: translateY(-5%);
		}
	}
	.spark {
		position: absolute;
		top: 2px;
		right: 4px;
		font-size: 11px;
		color: #ffd84a;
		text-shadow: 0 0 6px rgb(255 216 74 / 0.8);
	}
	.lv {
		position: absolute;
		bottom: -6px;
		left: 50%;
		translate: -50% 0;
		padding: 0 5px;
		border-radius: 999px;
		font-size: 9.5px;
		font-weight: 700;
		line-height: 14px;
		white-space: nowrap;
		color: var(--text);
		background: var(--panel);
		border: 1px solid color-mix(in srgb, var(--m-hi) 45%, var(--border));
		font-variant-numeric: tabular-nums;
	}
	@media (prefers-reduced-motion: reduce) {
		.tile img {
			animation: none;
		}
	}
	.pts-line {
		display: inline-flex;
		align-items: center;
		gap: 6px;
	}
	.pod .name {
		font-size: 16px;
	}
	.p1 .pod .name {
		font-size: 19px;
	}

	.tablewrap {
		overflow-x: auto;
		border: 1px solid var(--border);
		border-radius: 12px;
		background: var(--panel);
	}
	.linkcol {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.linkcol .rec {
		font-size: 12px;
		color: var(--muted);
	}
	.linkcol .w {
		color: var(--grass, #3fae4a);
	}
	.linkcol .l {
		color: var(--danger);
	}
	.tbl {
		min-width: 970px;
	}
	.tr {
		display: grid;
		grid-template-columns: 44px minmax(180px, 1.3fr) 214px 96px 92px 150px 110px;
		align-items: center;
		gap: 14px;
		padding: 10px 18px;
		border-top: 1px solid var(--border);
		color: inherit;
		text-decoration: none;
	}
	.tr.th {
		border-top: none;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
		padding-block: 12px;
	}
	a.tr {
		background: var(--panel); /* solid, so a row sliding past another doesn't show through it */
	}
	a.tr:hover {
		background: var(--sunk);
		color: inherit;
	}
	a.tr.you {
		background: var(--accent-tint);
		box-shadow: inset 3px 0 0 var(--accent);
	}
	.rank {
		font-size: 11px;
	}
	.num {
		font-variant-numeric: tabular-nums;
		text-align: right;
	}
	.dex {
		display: flex;
		flex-direction: column;
		gap: 5px;
	}
	.bar {
		display: block;
		height: 6px;
		border-radius: 3px;
		background: var(--border);
		overflow: hidden;
	}
	.bar b {
		display: block;
		height: 100%;
		background: var(--accent);
	}
	@media (max-width: 820px) {
		.podium {
			grid-template-columns: minmax(0, 1fr);
		}
		.spot.p1,
		.spot.p2,
		.spot.p3 {
			order: 0;
		}
	}

	@media (prefers-reduced-motion: no-preference) {
		a.tr,
		.pod {
			transition: background 0.15s, border-color 0.15s;
		}
	}
</style>
