<!-- /watch/{userId}: spectate a running link, both Game Boys side by side (stacked on a phone), read only: the screens
     come from /ws/watch/{id} and this page sends no buttons. Above each screen, who is playing (from their scoreboard
     row: game, trainer name, badges, rank, link record, party with levels); between them their head-to-head
     (GET /link/versus, fetched again when a link battle between them is scored). When the cable comes out it says so. -->
<script lang="ts">
	import { page } from '$app/state';
	import { api } from '#lib/api.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { on } from '#lib/events.ts';
	import { clock, link, since, watchLink } from '#lib/link.svelte.ts';
	import { ago, badgeMasks, bitCount, versionOf, winRate, type ScoreEvent } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	type Versus = { a: string; b: string; aWins: number; bWins: number; recent: ScoreEvent[] };

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
	const rowOf = (user: string) => board.rows.find((r) => r.userId === user);
	const rankOf = (user: string) => board.rows.findIndex((r) => r.userId === user) + 1;

	let versus = $state<Versus | null>(null);
	let now = $state(Date.now());
	$effect(() => {
		const a = shown?.a,
			b = shown?.b;
		if (!a || !b) return;
		const load = () =>
			api<Versus>('GET', `/link/versus?a=${encodeURIComponent(a)}&b=${encodeURIComponent(b)}`)
				.then((v) => (versus = v))
				.catch(() => {});
		load();
		const off = on('PROGRESS', (e: ScoreEvent) => {
			if (e.type === 'LINK_WIN' && [a, b].includes(e.user)) load();
		});
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => {
			off();
			clearInterval(tick);
		};
	});
	// Who won, as the game calls them (two Pibs can share a name; their trainers rarely do).
	const trainer = (user: string) => rowOf(user)?.trainerName || nameOf(user);
	const winsOf = (user: string) => (!versus ? 0 : user === versus.a ? versus.aWins : versus.bWins);
</script>

<svelte:head><title>Watch {nameOf(id)} · PPPP</title></svelte:head>

{#snippet player(user: string)}
	{@const r = rowOf(user)}
	{@const v = r ? versionOf(r.game, board.names) : null}
	{@const m = r ? badgeMasks(r) : null}
	{@const rate = winRate(r?.link)}
	<div class="player">
		<Avatar {user} size={44} />
		<div class="info">
			<b class="name">{nameOf(user)}</b>
			<span class="muted line">
				{[v, r?.trainerName, m ? `${bitCount(m.johto) + bitCount(m.kanto)} badges` : null, rankOf(user) ? `#${rankOf(user)}` : null]
					.filter(Boolean)
					.join(' · ')}
			</span>
			<span class="muted line"
				>Link record <b class="win">{r?.link?.wins ?? 0}</b>–<b class="loss">{r?.link?.losses ?? 0}</b>{#if rate !== null}{' '}({rate}%){/if}</span
			>
		</div>
		{#if r?.party?.length}<div class="party"><PartyIcons party={r.party.slice(0, 6)} size={28} levels /></div>{/if}
	</div>
{/snippet}

<SubPage>
	<section class="watch">
		{#if shown}
			<header>
				<h1>{shown.kind === 'TRADE' ? 'Trade' : 'Link battle'}: {nameOf(shown.a)} vs {nameOf(shown.b)}</h1>
				<p class="muted">
					{#if pair}<span class="live">●</span> Live · linked {clock(since(pair.since))} · you're watching, nothing you press reaches their games{:else}This link has ended.{/if}
				</p>
			</header>
			<div class="arena">
				<figure>
					{@render player(shown.a)}
					<div class="screen"><GameScreen watch={shown.a} /></div>
				</figure>
				<aside class="h2h" aria-label="Head to head">
					<span class="label">Head to head</span>
					<span class="score pixel"
						><span class:lead={winsOf(shown.a) > winsOf(shown.b)}>{winsOf(shown.a)}</span>–<span class:lead={winsOf(shown.b) > winsOf(shown.a)}
							>{winsOf(shown.b)}</span
						></span
					>
					<span class="muted xs">{versus ? `${versus.aWins + versus.bWins} ${versus.aWins + versus.bWins === 1 ? 'battle' : 'battles'}` : ''}</span>
					{#if versus?.recent.length}
						<ol class="recent">
							{#each versus.recent as e (e.at + e.user)}
								<li>{#if e.user === shown.a}◀ {/if}<b>{trainer(e.user)}</b> won{#if e.user === shown.b}{' '}▶{/if} <span class="muted">· {ago(now - e.at)}</span></li>
							{/each}
						</ol>
					{:else if versus}
						<span class="muted xs">First battle between them</span>
					{/if}
				</aside>
				<figure>
					{@render player(shown.b)}
					<div class="screen"><GameScreen watch={shown.b} /></div>
				</figure>
			</div>
		{:else if link.state}
			<p class="muted">{nameOf(id)} is not linked with anyone right now.</p>
		{/if}
		<p><a href="/play">← Back to your game</a></p>
	</section>
</SubPage>

<style>
	.watch {
		width: 100%;
		max-width: 1400px;
		margin: 0 auto;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	h1 {
		margin: 0;
		font-size: 22px;
	}
	header p {
		margin: 4px 0 0;
	}
	.live {
		color: var(--danger);
	}
	.arena {
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		gap: 16px;
		align-items: start;
	}
	figure {
		/* As wide as fits the window's height under the header, title and player card. */
		width: 100%;
		max-width: calc((100vh - 500px) * 160 / 144 + 32px);
		min-width: 0;
		justify-self: center;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.player {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 12px;
		border: 1px solid var(--border);
		border-radius: 12px;
		flex-wrap: wrap;
	}
	.info {
		display: flex;
		flex-direction: column;
		min-width: 0;
		flex: 1;
	}
	.name {
		font-size: 16px;
	}
	.line {
		font-size: 13px;
	}
	.party {
		flex-basis: 100%; /* its own row under the name, so nothing is cut short */
	}
	.win {
		color: var(--grass, #3fae4a);
	}
	.loss {
		color: var(--danger);
	}
	.h2h {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
		padding-top: 18px;
		min-width: 140px;
		text-align: center;
	}
	.label {
		font-size: 12px;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.score {
		font-size: 34px;
		color: var(--muted);
	}
	.score .lead {
		color: var(--text);
	}
	.recent {
		list-style: none;
		margin: 6px 0 0;
		padding: 0;
		font-size: 13px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.xs {
		font-size: 12px;
	}
	.muted {
		color: var(--muted);
	}
	@media (max-width: 760px) {
		.arena {
			grid-template-columns: 1fr;
		}
		.h2h {
			order: -1;
			padding-top: 0;
		}
		figure {
			max-width: none;
		}
	}
</style>
