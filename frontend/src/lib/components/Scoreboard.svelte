<!-- Standings for /play's Scoreboard tab: the top Pibs by Trainer Score with avatar, full name, status (linked / ready /
     playing / offline) · where they are, their party's icons, and score + grade; the signed-in player's row
     highlighted. Each row opens that Pib's profile. Then "Full scoreboard" and the "Latest" feed. Rows from board.svelte.ts. -->
<script lang="ts">
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
				<li>
					<a class="standing" class:you href="/pib/{encodeURIComponent(r.userId)}">
						<span class="rank pixel" aria-hidden="true">{i + 1}</span>
						<Avatar user={r.userId} size={36} />
						<span class="mid">
							<span class="line">
								<b class="name"><span class="sr-only">{i + 1}. </span>{nameOf(r.userId)}</b>
								{#if you}<span class="you-tag">You</span>{/if}
							</span>
							<span class="status status-{st.kind}">{st.kind === 'linked' ? 'Linked' : st.label}{#if where}<span class="muted"> · {where}</span>{/if}</span>
							{#if r.party?.length}<PartyIcons party={r.party} size={22} hp={false} />{/if}
						</span>
						<span class="score">
							<span class="pts">{score(r)}<span class="sr-only"> points, grade</span></span>
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
		grid-template-columns: 18px 36px minmax(0, 1fr) auto;
		align-items: center;
		gap: 6px 10px;
		padding: 8px 10px;
		margin: 0 -10px;
		border-radius: 8px;
		color: inherit;
		text-decoration: none;
	}
	.standing:hover {
		background: var(--sunk);
		color: inherit;
	}
	.standing.you {
		background: var(--accent-tint);
		box-shadow: inset 3px 0 0 var(--accent);
	}
	.standing:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.rank {
		font-size: 10px;
		color: var(--muted);
	}
	.mid {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
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
