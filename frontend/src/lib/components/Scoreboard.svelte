<!-- Standings for /play's Scoreboard tab: the top Pibs by Trainer Score with avatar, full name, status (linked / ready /
     playing / offline) · where they are, their party's icons, and score + grade; the signed-in player's row
     highlighted. Each row opens that Pib's profile. Then "Full scoreboard" and the "Latest" feed. Rows from board.svelte.ts. -->
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
	import { flip } from 'svelte/animate';
	import CountUp from '#lib/components/CountUp.svelte';
	import { arrive, glow, ms } from '#lib/motion.ts';

	// The top ten at most (fewer if the card is short: fitItems); the rest are on /scoreboard.
	let { limit = 10 }: { limit?: number } = $props();

	$effect(() => watchBoard());
	$effect(() => watchLink());
	const LATEST = 30;
	const shown = $derived(board.rows.slice(0, limit));
</script>

<div class="board">
	{#if shown.length}
		<div class="fit rows" use:fitItems>
		<ol aria-label="Scoreboard">
			{#each shown as r, i (r.userId)}
				{@const you = r.userId === session.me?.id}
				{@const st = playerStatus(r.userId, r.online, session.me?.id, link.state?.lobby ?? [], link.links)}
				{@const where = st.kind === 'linked' ? 'Trade Center' : place(r.location).city}
				<!-- Higher ranks paint on top, so a player climbing slides over the ones they pass. -->
				<li animate:flip={{ duration: ms(500) }} in:arrive style:position="relative" style:z-index={shown.length - i}>
					<a class="standing {i < 3 ? `podium p${i + 1} metal-${['gold', 'silver', 'bronze'][i]}` : ''}" class:you href="/pib/{encodeURIComponent(r.userId)}">
						{#key score(r)}<span class="flash" aria-hidden="true" in:glow></span>{/key}
						<span class="rank" aria-hidden="true"><RankNumber rank={i + 1} total={board.rows.length} medal /></span>
						<Avatar user={r.userId} size={32} />
						<span class="mid">
							<span class="line">
								<b class="name"><span class="sr-only">{i + 1}. </span><Name user={r.userId} colored /></b>
								{#if you}<span class="you-tag">You</span>{/if}
							</span>
							<span class="sub">
								<span class="status status-{st.kind}"><span class="status-dot"></span>{st.kind === 'linked' ? 'Linked' : st.label}{#if where}<span class="muted">&nbsp;· {where}</span>{/if}</span>
								{#if r.party?.length}<PartyIcons party={r.party} size={18} hp={false} />{/if}
							</span>
						</span>
						<span class="score">
							<span class="pts"><CountUp value={score(r)} /><span class="sr-only"> points, grade</span></span>
							<GradeChip grade={r.grade} rating={r.trainerScore} />
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
	.fit.rows {
		flex: 0 1 auto;
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
	/* The podium: the metal's sheen across the row and an edge in it, #1 the strongest (as /scoreboard's podium). */
	.standing.podium {
		background:
			linear-gradient(90deg, color-mix(in srgb, var(--m-hi) 16%, transparent), transparent 75%),
			var(--panel);
		box-shadow: inset 3px 0 0 var(--m-lo);
	}
	.standing.p1 {
		background:
			linear-gradient(90deg, color-mix(in srgb, var(--m-hi) 30%, transparent), color-mix(in srgb, var(--m-hi) 6%, transparent) 80%),
			var(--panel);
		box-shadow:
			inset 3px 0 0 var(--m-hi),
			inset 0 0 0 1px color-mix(in srgb, var(--m-lo) 60%, transparent),
			0 0 14px var(--m-glow);
	}
	.standing.p1 :global(.pib-name) {
		font-size: 1.06em;
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
	.sub {
		display: flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
	}
	.sub .status {
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
