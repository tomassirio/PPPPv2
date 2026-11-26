<!-- Standings for /play's Scoreboard tab: the top Pibs by Trainer Score with avatar, full name, status (linked / ready /
     playing / offline) · where they are ("Near you" when it is the player's own town or route), their party's icons
     (only the Pokémon there are), score + grade, and ▲/▼ for places moved since today began (GET /scoreboard/baseline).
     Offline Pibs are dimmed. The signed-in player's row is highlighted, says how many points take the next place, and
     always stays in sight (appended when outside the top ten; fitItems keeps it when the card is short). Each row opens
     that Pib's profile. Then "Full scoreboard" and the "Latest" feed. Rows from board.svelte.ts. -->
<script lang="ts">
	import RankNumber from '#lib/components/RankNumber.svelte';
	import Name from '#lib/components/Name.svelte';
	import GradeChip from '#lib/components/GradeChip.svelte';
	import Activity from '#lib/components/Activity.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { link, watchLink } from '#lib/link.svelte.ts';
	import { place, playerStatus, score } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';
	import { fitItems } from '#lib/fit.ts';
	import { api } from '#lib/api.ts';
	import { flip } from 'svelte/animate';
	import CountUp from '#lib/components/CountUp.svelte';
	import { arrive, glow, ms } from '#lib/motion.ts';

	// The top ten at most (fewer if the card is short: fitItems); the rest are on /scoreboard.
	let { limit = 10 }: { limit?: number } = $props();

	$effect(() => watchBoard());
	$effect(() => watchLink());
	const LATEST = 30;
	const me = $derived(session.me?.id);
	const myIndex = $derived(board.rows.findIndex((r) => r.userId === me));
	// The top ten, and the player's own row after them when they are further down.
	const shown = $derived(myIndex >= limit ? [...board.rows.slice(0, limit), board.rows[myIndex]] : board.rows.slice(0, limit));
	const rankOf = (userId: string) => board.rows.findIndex((r) => r.userId === userId) + 1;
	// Where everyone stood when today began (UTC): ▲ / ▼ for places moved since.
	let baseline = $state<Record<string, number>>({});
	$effect(() => {
		const load = () => api<Record<string, number>>('GET', '/scoreboard/baseline').then((b) => (baseline = b), () => {});
		load();
		const t = setInterval(load, 10 * 60_000);
		return () => clearInterval(t);
	});
	const moved = (userId: string) => (baseline[userId] ? baseline[userId] - rankOf(userId) : 0);
	const myCity = $derived(myIndex >= 0 ? place(board.rows[myIndex].location).city : '');
	// Points the player needs to take the place above them.
	const toNext = $derived(myIndex > 0 ? score(board.rows[myIndex - 1]) - score(board.rows[myIndex]) + 1 : null);
</script>

<div class="board">
	{#if shown.length}
		<div class="fit rows" use:fitItems={{ keep: '.mine' }}>
		<ol aria-label="Scoreboard">
			{#each shown as r, i (r.userId)}
				{@const you = r.userId === me}
				{@const rank = rankOf(r.userId)}
				{@const up = moved(r.userId)}
				{@const st = playerStatus(r.userId, r.online, session.me?.id, link.state?.lobby ?? [], link.links)}
				{@const where = st.kind === 'linked' ? 'Trade Center' : place(r.location).city}
				<!-- Higher ranks paint on top, so a player climbing slides over the ones they pass. -->
				{@const near = !you && !!myCity && st.kind !== 'linked' && place(r.location).city === myCity}
				<li class:mine={you} animate:flip={{ duration: ms(500) }} in:arrive style:position="relative" style:z-index={shown.length - i}>
					<a
						class="standing {rank <= 3 ? `podium p${rank} metal-frame metal-${['gold', 'silver', 'bronze'][rank - 1]}` : ''}"
						class:you
						class:offline={st.kind === 'offline' && !you}
						href="/pib/{encodeURIComponent(r.userId)}"
					>
						{#key score(r)}<span class="flash" aria-hidden="true" in:glow></span>{/key}
						<span class="rank" aria-hidden="true"
							><RankNumber rank={rank} total={board.rows.length} medal size={rank === 1 ? 24 : 21} />{#if up}<span
									class="move {up > 0 ? 'up' : 'down'}"
									title="{up > 0 ? 'Up' : 'Down'} {Math.abs(up)} since today began">{up > 0 ? '▲' : '▼'}{Math.abs(up)}</span
								>{/if}</span
						>
						<Avatar user={r.userId} size={32} />
						<span class="mid">
							<span class="line">
								<b class="name"><span class="sr-only">{rank}. </span><Name user={r.userId} colored /></b>
								{#if you}<span class="you-tag">You</span>{/if}
							</span>
							<span class="sub">
								<span class="status status-{st.kind}"
									><span class="status-dot"></span>{st.kind === 'linked' ? 'Linked' : st.label}{#if where}<span class="muted">&nbsp;· {where}</span>{/if}{#if near}<span
											class="near">Near you</span
										>{/if}</span
								>
								{#if r.party?.length}<PartyIcons party={r.party} size={18} hp={false} dex empty={false} />{/if}
							</span>
						</span>
						<span class="score">
							<span class="pts"><CountUp value={score(r)} /><span class="sr-only"> points, grade</span></span>
							<GradeChip grade={r.grade} rating={r.trainerScore} />
							{#if you && toNext != null}<span class="next">{toNext} to #{rank - 1}</span>{/if}
						</span>
					</a>
				</li>
			{/each}
		</ol>
		</div>
		<a class="full" href="/scoreboard">Full scoreboard · all {board.rows.length} Pibs</a>
	{:else}
		<p class="muted">Nobody has picked a game yet.</p>
	{/if}
	<hr />
	<h2>Latest</h2>
	<div class="fit latest" use:fitItems><Activity limit={LATEST} label="Latest" /></div>
</div>

<style>
	/* The card is split: standings up to their natural height, Latest takes the rest; each shows what fits. */
	.board {
		flex: 1 1 auto;
		min-height: 0;
		display: flex;
		flex-direction: column;
	}
	.fit {
		min-height: 0;
		overflow: hidden;
	}
	/* Room for the rows' -10px sides (and the podium frames) inside the clipping box. */
	.fit.rows {
		flex: 0 1 auto;
		margin: -4px -10px 0;
		padding: 4px 10px 0;
	}
	.fit.latest {
		flex: 1 1 0;
		min-height: 60px;
	}
	.board {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	ol {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.standing {
		display: grid;
		grid-template-columns: 18px 32px minmax(0, 1fr) auto;
		align-items: center;
		gap: 6px 10px;
		padding: 6px 10px;
		margin: 0 -10px;
		border-radius: 8px;
		color: inherit;
		text-decoration: none;
	}
	/* Lights up when the row's points change (the {#key} re-mounts it), then fades. */
	.standing {
		position: relative;
		background: var(--panel); /* solid, so a row sliding past another doesn't show through it */
	}
	.flash {
		position: absolute;
		inset: 0;
		border-radius: 8px;
		background: color-mix(in oklab, var(--accent) 22%, transparent);
		opacity: 0;
		pointer-events: none;
	}
	.standing:hover {
		background: var(--sunk);
		color: inherit;
	}
	.standing.you {
		background: var(--accent-tint);
		box-shadow: inset 3px 0 0 var(--accent);
	}
	/* The podium: framed in its metal (.metal-frame, as everywhere the top three show). */
	.standing.p1 :global(.pib-name) {
		font-size: 1.06em;
	}
	/* In a list the frame stays still apart from its gleam: no glow, no breathing (it would pulse over the row below). */
	.standing.metal-frame,
	.standing.metal-frame.metal-gold {
		box-shadow: none;
		animation: frame-gleam 5s ease-in-out infinite;
	}
	.standing.metal-frame.you {
		box-shadow: inset 3px 0 0 var(--accent);
	}
	@media (prefers-reduced-motion: reduce) {
		.standing.metal-frame,
		.standing.metal-frame.metal-gold {
			animation: none;
		}
	}
	.standing.podium:hover {
		filter: brightness(1.06);
	}
	.standing:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.rank {
		font-size: 11px;
	}
	/* Two lines a row, so ten fit: the name, then status and the party side by side. */
	.mid {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 3px;
	}
	/* Status and city on a line of their own (the city never hides behind the party), the party under it. */
	.sub {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 3px;
		min-width: 0;
	}
	/* Not playing right now: faded, so who is around stands out. */
	.standing.offline {
		opacity: 0.55;
	}
	.standing.offline:hover,
	.standing.offline:focus-visible {
		opacity: 1;
	}
	.rank {
		position: relative;
	}
	.move {
		position: absolute;
		left: 50%;
		bottom: -12px;
		translate: -50% 0;
		font-size: 9px;
		font-weight: 800;
		line-height: 1;
		white-space: nowrap;
	}
	.move.up {
		color: var(--hp-ok);
	}
	.move.down {
		color: var(--danger);
	}
	.near {
		margin-left: 6px;
		padding: 0 6px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--link) 20%, transparent);
		color: var(--link);
		font-size: 10px;
		font-weight: 700;
	}
	.next {
		font-size: 10px;
		font-weight: 700;
		color: var(--accent-text);
		white-space: nowrap;
	}
	.sub .status {
		max-width: 100%;
		min-width: 0;
	}
	.sub :global(.party) {
		flex: none;
	}
	.line {
		display: flex;
		align-items: center;
		gap: 6px;
		min-width: 0;
	}
	.name {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.you-tag {
		flex: none;
		padding: 3px 6px;
		border-radius: 4px;
		background: var(--accent-tint);
		color: var(--accent-text);
		font-size: 11px;
		font-weight: 600;
	}
	.status {
		font-size: 12px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.status .status-dot {
		width: 7px;
		height: 7px;
		margin-right: 5px;
		vertical-align: 1px;
	}
	.score {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 4px;
	}
	.pts {
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.full {
		align-self: stretch;
		height: 30px;
		display: flex;
		align-items: center;
		justify-content: center;
		border: 1px solid var(--border-strong);
		border-radius: 8px;
		color: var(--text);
		font-size: 13px;
		font-weight: 600;
		text-decoration: none;
	}
	.full:hover {
		background: var(--sunk);
		color: var(--text);
	}
	.full:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	hr {
		width: 100%;
		margin: 0;
		border: none;
		border-top: 1px solid var(--border);
	}
	h2 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	p {
		margin: 0;
	}
	.muted {
		color: var(--muted);
	}
	p.muted {
		font-size: 13px;
	}
	@media (prefers-reduced-motion: no-preference) {
		.standing {
			transition: background 0.15s;
		}
	}
</style>
