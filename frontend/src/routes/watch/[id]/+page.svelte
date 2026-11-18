<!-- /watch/{userId}: spectate a running link, read only: the screens come from /ws/watch/{id} and this page sends no
     buttons. A dark stage: the match bar (each trainer with their game, rank, badges and link record; the head-to-head
     score between them, GET /link/versus), then both Game Boys with each party beside its screen (from their profile,
     GET /players/{id}/profile, every few seconds while live; sprites from that player's own ROM, icons?of=) and the
     latest results between them down the middle. Narrow windows stack it. When the cable comes out it says so. -->
<script lang="ts">
	import { page } from '$app/state';
	import { api } from '#lib/api.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { on } from '#lib/events.ts';
	import { clock, link, since, watchLink } from '#lib/link.svelte.ts';
	import { ago, badgeMasks, bitCount, hpColor, TYPE_COLORS, versionOf, winRate, type ProfileMon, type ScoreEvent } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	type Versus = { a: string; b: string; aWins: number; bWins: number; recent: ScoreEvent[] };
	type Mon = { species: number; label: string; species_: string | null; level: number; hp: number | null; status: string | null; types: string[]; egg: boolean };

	$effect(() => watchLink());
	$effect(() => watchBoard());

	const id = $derived(decodeURIComponent(page.params.id ?? '').toLowerCase());
	const pair = $derived(link.state?.links.find((p) => p.a === id || p.b === id));
	// Remember who was at the other end, so the page stays up (screens saying "Not linked") once it ends.
	let last = $state<{ a: string; b: string; kind: string } | null>(null);
	$effect(() => {
		if (pair) last = { a: pair.a, b: pair.b, kind: pair.kind };
	});
	const shown = $derived(pair ?? last);
	const live = $derived(!!pair);
	const rowOf = (user: string) => board.rows.find((r) => r.userId === user);
	const rankOf = (user: string) => board.rows.findIndex((r) => r.userId === user) + 1;

	let versus = $state<Versus | null>(null);
	let parties = $state<Record<string, ProfileMon[]>>({});
	let now = $state(Date.now());
	$effect(() => {
		const a = shown?.a,
			b = shown?.b;
		if (!a || !b) return;
		const loadVersus = () =>
			api<Versus>('GET', `/link/versus?a=${encodeURIComponent(a)}&b=${encodeURIComponent(b)}`)
				.then((v) => (versus = v))
				.catch(() => {});
		const loadParties = () =>
			[a, b].forEach((u) =>
				api<{ party: ProfileMon[] }>('GET', `/players/${encodeURIComponent(u)}/profile`)
					.then((p) => (parties[u] = p.party ?? []))
					.catch(() => {})
			);
		loadVersus();
		loadParties();
		const off = on('PROGRESS', (e: ScoreEvent) => {
			if (e.type === 'LINK_WIN' && [a, b].includes(e.user)) loadVersus();
		});
		const poll = setInterval(loadParties, 5000); // HP and fainted Pokémon as the battle goes
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => {
			off();
			clearInterval(poll);
			clearInterval(tick);
		};
	});
	const winsOf = (user: string) => (!versus ? 0 : user === versus.a ? versus.aWins : versus.bWins);
	// Who won, as the game calls them (two Pibs can share a name; their trainers rarely do).
	const trainer = (user: string) => rowOf(user)?.trainerName || nameOf(user);

	/** The party: the profile's (names, HP, types) when there, else the scoreboard row's (species and level). */
	function party(user: string): Mon[] {
		const full = parties[user];
		if (full?.length) {
			return full.map((m) => ({
				species: m.species,
				label: m.egg ? 'Egg' : (m.nickname ?? m.name),
				species_: m.nickname && !m.egg ? m.name : null,
				level: m.level,
				hp: m.maxHp ? Math.round(((m.hp ?? 0) * 100) / m.maxHp) : null,
				status: m.status,
				types: m.types ?? [],
				egg: m.egg
			}));
		}
		return (rowOf(user)?.party ?? []).map((m) => ({
			species: m.species,
			label: m.egg ? 'Egg' : `#${m.species}`,
			species_: null,
			level: m.level,
			hp: m.hpPercent ?? null,
			status: m.status ?? null,
			types: [],
			egg: !!m.egg
		}));
	}
	const VERSION_COLOR: Record<string, string> = { Gold: '#e0b13a', Silver: '#b8c0c8', Crystal: '#62c4ea' };
</script>

<svelte:head><title>Watch {nameOf(id)} · PPPP</title></svelte:head>

{#snippet trainerCard(user: string, side: 'a' | 'b')}
	{@const r = rowOf(user)}
	{@const v = r ? versionOf(r.game, board.names) : null}
	{@const m = r ? badgeMasks(r) : null}
	{@const rate = winRate(r?.link)}
	<div class="trainer {side}" style:--ver={v ? VERSION_COLOR[v] : '#8a8f96'}>
		<span class="ring"><Avatar {user} size={56} /></span>
		<div class="tinfo">
			<span class="pib">{nameOf(user)}</span>
			<span class="tname pixel">{r?.trainerName ?? '???'}{#if v}<span class="ver">{v}</span>{/if}</span>
			<span class="facts">
				{#if rankOf(user)}#{rankOf(user)}{' · '}{/if}{m ? bitCount(m.johto) + bitCount(m.kanto) : 0} badges · link
				<b class="w">{r?.link?.wins ?? 0}</b>–<b class="l">{r?.link?.losses ?? 0}</b>{#if rate !== null}{' '}({rate}%){/if}
			</span>
		</div>
	</div>
{/snippet}

{#snippet partyColumn(user: string, side: 'a' | 'b')}
	{@const mons = party(user)}
	<ul class="party {side}" aria-label="{nameOf(user)}'s party">
		{#each mons as mon, i (i)}
			<li class:fnt={mon.status === 'FNT' || mon.hp === 0}>
				<img src="/api/v1/game/icons/{mon.species}.png?of={encodeURIComponent(user)}" alt="" width="40" height="40" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />
				<div class="mon">
					<span class="mname"><b>{mon.label}</b><span class="lv">Lv {mon.level}</span></span>
					{#if mon.species_}<span class="species">{mon.species_}</span>{/if}
					{#if mon.hp !== null && !mon.egg}
						<span class="hp" role="img" aria-label="{mon.hp}% HP"><i style:width="{mon.hp}%" style:background={hpColor(mon.hp)}></i></span>
					{/if}
					<span class="tags">
						{#each mon.types as t (t)}<span class="type" style:background={TYPE_COLORS[t] ?? '#777'}>{t}</span>{/each}
						{#if mon.status}<span class="status">{mon.status}</span>{/if}
					</span>
				</div>
			</li>
		{:else}
			<li class="empty">No party seen yet</li>
		{/each}
	</ul>
{/snippet}

<SubPage>
	<div class="page">
		{#if shown}
			<section class="stage" aria-label="{shown.kind === 'TRADE' ? 'Trade' : 'Link battle'}: {nameOf(shown.a)} vs {nameOf(shown.b)}">
				<div class="topline">
					<span class="badge" class:on={live}><span class="dot" aria-hidden="true"></span>{live ? 'LIVE' : 'ENDED'}</span>
					<span class="kind">{shown.kind === 'TRADE' ? 'Link trade' : 'Link battle'}{#if pair} · {clock(since(pair.since))}{/if}</span>
				</div>

				<div class="match">
					{@render trainerCard(shown.a, 'a')}
					<div class="vs" aria-label="Head to head {winsOf(shown.a)} to {winsOf(shown.b)}">
						<span class="pixel big">VS</span>
						<span class="score pixel"
							><span class:lead={winsOf(shown.a) > winsOf(shown.b)}>{winsOf(shown.a)}</span><span class="dash">–</span><span
								class:lead={winsOf(shown.b) > winsOf(shown.a)}>{winsOf(shown.b)}</span
							></span
						>
						<span class="sub">{versus ? `${versus.aWins + versus.bWins} ${versus.aWins + versus.bWins === 1 ? 'battle' : 'battles'} between them` : ' '}</span>
					</div>
					{@render trainerCard(shown.b, 'b')}
				</div>

				<div class="arena">
					{@render partyColumn(shown.a, 'a')}
					<figure><GameScreen watch={shown.a} /></figure>
					<aside class="results" aria-label="Latest results between them">
						<span class="label">Latest</span>
						{#if versus?.recent.length}
							<ol>
								{#each versus.recent as e (e.at + e.user)}
									<li class={e.user === shown.a ? 'to-a' : 'to-b'}>
										<b>{trainer(e.user)}</b>
										<span>won · {ago(now - e.at)}</span>
									</li>
								{/each}
							</ol>
						{:else if versus}
							<span class="first">Their first battle</span>
						{/if}
					</aside>
					<figure><GameScreen watch={shown.b} /></figure>
					{@render partyColumn(shown.b, 'b')}
				</div>
			</section>
		{:else if link.state}
			<p class="muted">{nameOf(id)} is not linked with anyone right now.</p>
		{/if}
		<p><a href="/play">← Back to your game</a></p>
	</div>
</SubPage>

<style>
	.page {
		width: 100%;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.muted {
		color: var(--muted);
	}

	/* The stage is its own dark surface in both themes, like the Game Boy bezel. */
	.stage {
		--ink: #eef1f4;
		--ink-dim: #9aa3ad;
		--line: rgba(255, 255, 255, 0.1);
		color: var(--ink);
		background:
			radial-gradient(ellipse at 50% 0%, rgba(98, 196, 234, 0.12), transparent 60%),
			linear-gradient(180deg, #1b1f26, #12151a);
		border-radius: 18px;
		padding: 14px 18px 18px;
		display: flex;
		flex-direction: column;
		gap: 12px;
		box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
	}
	.topline {
		display: flex;
		align-items: center;
		gap: 12px;
		font-size: 12px;
		letter-spacing: 0.06em;
		color: var(--ink-dim);
	}
	.badge {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 3px 8px;
		border-radius: 999px;
		background: rgba(255, 255, 255, 0.08);
		font-weight: 700;
	}
	.badge.on {
		background: #e3350d;
		color: #fff;
	}
	.dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: currentColor;
	}
	.badge.on .dot {
		animation: pulse 1.2s ease-in-out infinite;
	}
	@keyframes pulse {
		50% {
			opacity: 0.3;
		}
	}
	.kind {
		text-transform: uppercase;
		font-weight: 600;
	}

	.match {
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		align-items: center;
		gap: 16px;
		padding-bottom: 12px;
		border-bottom: 1px solid var(--line);
	}
	.trainer {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
	}
	.trainer.b {
		flex-direction: row-reverse;
		text-align: right;
	}
	.ring {
		display: inline-flex;
		border-radius: 50%;
		padding: 3px;
		background: var(--ver);
		box-shadow: 0 0 18px color-mix(in srgb, var(--ver) 45%, transparent);
		flex: none;
	}
	.tinfo {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.pib {
		font-size: 18px;
		font-weight: 700;
	}
	.tname {
		font-size: 13px;
		color: var(--ver);
		display: inline-flex;
		align-items: center;
		gap: 8px;
	}
	.trainer.b .tname {
		justify-content: flex-end;
	}
	.ver {
		font-family: var(--body-font);
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.04em;
		padding: 1px 7px;
		border-radius: 999px;
		color: #12151a;
		background: var(--ver);
	}
	.facts {
		font-size: 13px;
		color: var(--ink-dim);
	}
	.w {
		color: #7ed37f;
	}
	.l {
		color: #ff7b6b;
	}
	.vs {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		min-width: 170px;
	}
	.big {
		font-size: 14px;
		color: #e0b13a;
		letter-spacing: 0.2em;
	}
	.score {
		font-size: 40px;
		line-height: 1.1;
		color: var(--ink-dim);
	}
	.score .lead {
		color: var(--ink);
	}
	.dash {
		padding: 0 6px;
		opacity: 0.5;
	}
	.sub {
		font-size: 12px;
		color: var(--ink-dim);
	}

	.arena {
		display: grid;
		grid-template-columns: minmax(170px, 210px) minmax(0, 1fr) 150px minmax(0, 1fr) minmax(170px, 210px);
		gap: 14px;
		align-items: start;
	}
	figure {
		margin: 0;
		width: 100%;
		/* As big as fits under the header, the match bar and the page's margins. */
		max-width: calc((100vh - 330px) * 160 / 144 + 32px);
		justify-self: center;
	}
	.party {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.party li {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 6px 8px;
		border-radius: 10px;
		background: rgba(255, 255, 255, 0.05);
		border: 1px solid var(--line);
	}
	.party.b li {
		flex-direction: row-reverse;
		text-align: right;
	}
	.party li.fnt {
		opacity: 0.45;
	}
	.party li.empty {
		color: var(--ink-dim);
		font-size: 13px;
		justify-content: center;
	}
	.party img {
		width: 40px;
		height: 40px;
		image-rendering: pixelated;
		flex: none;
	}
	.mon {
		display: flex;
		flex-direction: column;
		gap: 3px;
		min-width: 0;
		flex: 1;
	}
	.mname {
		display: flex;
		align-items: baseline;
		gap: 6px;
		font-size: 13px;
	}
	.party.b .mname {
		flex-direction: row-reverse;
	}
	.mname b {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.lv {
		color: var(--ink-dim);
		font-size: 12px;
		white-space: nowrap;
	}
	.species {
		font-size: 11px;
		color: var(--ink-dim);
	}
	.hp {
		display: block;
		height: 4px;
		border-radius: 2px;
		background: rgba(255, 255, 255, 0.12);
		overflow: hidden;
	}
	.party.b .hp {
		transform: scaleX(-1); /* drains toward the screen, like the other side */
	}
	.hp i {
		display: block;
		height: 100%;
		transition: width 400ms ease;
	}
	.tags {
		display: flex;
		gap: 4px;
		flex-wrap: wrap;
	}
	.party.b .tags {
		justify-content: flex-end;
	}
	.type,
	.status {
		font-size: 10px;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		padding: 1px 5px;
		border-radius: 4px;
		color: #fff;
	}
	.status {
		background: #5b6068;
	}

	.results {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		padding-top: 6px;
		text-align: center;
	}
	.label {
		font-size: 11px;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--ink-dim);
	}
	.results ol {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
		width: 100%;
	}
	.results li {
		display: flex;
		flex-direction: column;
		padding: 5px 8px;
		border-radius: 8px;
		background: rgba(255, 255, 255, 0.05);
		font-size: 12px;
	}
	.results li span {
		color: var(--ink-dim);
	}
	.results li.to-a {
		border-left: 3px solid #e0b13a;
		text-align: left;
	}
	.results li.to-b {
		border-right: 3px solid #e0b13a;
		text-align: right;
	}
	.first {
		font-size: 12px;
		color: var(--ink-dim);
	}

	@media (prefers-reduced-motion: reduce) {
		.badge.on .dot {
			animation: none;
		}
		.hp i {
			transition: none;
		}
	}

	/* Narrow: the match bar on top, then each Game Boy with its party under it. */
	@media (max-width: 1100px) {
		.arena {
			grid-template-columns: 1fr 1fr;
		}
		.arena figure:nth-of-type(1) {
			grid-column: 1;
			grid-row: 1;
		}
		.arena figure:nth-of-type(2) {
			grid-column: 2;
			grid-row: 1;
		}
		.party.a {
			grid-column: 1;
			grid-row: 2;
		}
		.party.b {
			grid-column: 2;
			grid-row: 2;
		}
		.results {
			grid-column: 1 / -1;
			grid-row: 3;
		}
		figure {
			max-width: none;
		}
	}
	@media (max-width: 640px) {
		.match {
			grid-template-columns: 1fr;
		}
		.trainer.b {
			flex-direction: row;
			text-align: left;
		}
		.trainer.b .tname {
			justify-content: flex-start;
		}
		.arena {
			grid-template-columns: 1fr;
		}
		.arena > * {
			grid-column: 1 !important;
			grid-row: auto !important;
		}
	}
</style>
