<!-- Scoreboard: every player ranked by Trainer Score, with their full name, a status line (linked / ready / playing /
     offline, from the link cable and the row's online flag) and Pokédex count, then their party's icons and badge pips;
     the signed-in player's row highlighted. Party icons are drawn from the viewer's own ROM (same species numbers).
     Rows from board.svelte.ts; the "Latest" feed under it. -->
<script lang="ts">
	import Activity from '#lib/components/Activity.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { link, watchLink } from '#lib/link.svelte.ts';
	import { playerStatus } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { game } from '#lib/game.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	/** "Cyndaquil, Lv 12" when the viewer's own game knows the species name. */
	const monLabel = (m: { species: number; level: number }) =>
		`${game.summary?.collection?.names?.[m.species - 1] ?? `#${m.species}`}, Lv ${m.level}`;

	$effect(() => watchBoard());
	$effect(() => watchLink());
	const LATEST = 4;
</script>

<div class="board">
	{#if board.rows.length}
		<ol aria-label="Scoreboard">
			{#each board.rows as r, i (r.userId)}
				{@const you = r.userId === session.me?.id}
				{@const st = playerStatus(r.userId, r.online, session.me?.id, link.state?.lobby ?? [], link.links)}
				{@const pips = r.badges > 8 ? 16 : 8}
				<li class:you>
					<span class="rank pixel" aria-hidden="true">{i + 1}</span>
					<Avatar user={r.userId} />
					<div class="mid">
						<div class="line">
							<b class="name" title={nameOf(r.userId)}><span class="sr">{i + 1}. </span>{nameOf(r.userId)}</b>
							{#if you}<span class="you-tag">You</span>{/if}
						</div>
						<div class="status {st.kind}"><span class="dot" aria-hidden="true"></span>{st.label}<span class="muted">· {r.caught} caught</span></div>
						<div class="line extra">
							{#if r.party?.length}
								<ul class="party" aria-label="Party">
									{#each r.party as m, k (k)}
										<li><img src="/api/v1/game/icons/{m.species}.png" alt={monLabel(m)} title={monLabel(m)} width="56" height="56" loading="lazy" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} /></li>
									{/each}
								</ul>
							{/if}
							<span class="pips" title="{r.badges} badges" aria-hidden="true">
								{#each Array(pips) as _, b (b)}<i class:got={b < r.badges}></i>{/each}
							</span>
							<span class="sr">, {r.badges} badges</span>
						</div>
					</div>
					<span class="score" title="{r.title} · {r.achievementsUnlocked} achievements ({r.achievementPoints} pts)">
						<span class="pts">{r.trainerScore}<span class="sr"> Trainer Score, grade</span></span>
						<span class="grade pixel">{r.grade}</span>
					</span>
				</li>
			{/each}
		</ol>
	{:else}
		<p class="muted">Nobody has picked a game yet.</p>
	{/if}
	<hr />
	<h2 id="latest-h">Latest</h2>
	<Activity limit={LATEST} label="Latest" />
</div>

<style>
	.board {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	ol {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	li {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
	}
	/* Reaches the edges of the card around it (--bleed: that card's side padding). */
	li.you {
		margin: -8px calc(-1 * var(--bleed, 20px));
		padding: 8px var(--bleed, 20px);
		background: var(--accent-tint);
		box-shadow: inset 3px 0 0 var(--accent);
	}
	.rank {
		flex: none;
		width: 20px;
		font-size: 10px;
		color: var(--muted);
	}
	.mid {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.line {
		display: flex;
		align-items: center;
		gap: 8px;
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
		padding: 1px 6px;
		border-radius: 999px;
		background: var(--accent-tint);
		color: var(--accent-text);
		font-size: 11px;
		font-weight: 700;
	}
	.extra {
		justify-content: space-between;
		flex-wrap: wrap;
		gap: 4px 8px;
		margin-top: 2px;
	}
	.party {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		gap: 2px;
	}
	.party img {
		display: block;
		width: 26px;
		height: 26px;
		image-rendering: pixelated;
	}
	/* 8 pips a row: Johto, then Kanto on a second row once it starts. */
	.pips {
		flex: none;
		display: grid;
		grid-template-columns: repeat(8, 9px);
		gap: 3px 4px;
	}
	.pips i {
		width: 9px;
		height: 9px;
		box-sizing: border-box;
		border-radius: 50%;
		border: 1.5px solid var(--border-strong);
	}
	.pips i.got {
		background: var(--accent);
		border-color: var(--accent);
	}
	.status {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		min-width: 0;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		color: var(--muted);
	}
	.dot {
		flex: none;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--muted);
	}
	.status.playing {
		color: var(--grass);
	}
	.status.trade {
		color: var(--accent-text);
	}
	.status.battle {
		color: var(--danger);
	}
	.status.linked {
		color: var(--link);
	}
	.playing .dot {
		background: var(--grass);
	}
	.trade .dot {
		background: var(--accent);
	}
	.battle .dot {
		background: var(--danger);
	}
	.linked .dot {
		background: var(--link);
	}
	/* Offline: a hollow ring, so it differs by shape and not only colour. */
	.offline .dot {
		background: none;
		box-shadow: inset 0 0 0 1.5px var(--muted);
	}
	.score {
		flex: none;
		display: flex;
		align-items: baseline;
		gap: 6px;
	}
	.pts {
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.grade {
		min-width: 2.4em;
		padding: 3px 5px;
		border-radius: 4px;
		background: var(--accent-tint);
		color: var(--accent-text);
		font-size: 9px;
		text-align: center;
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
		font-size: 13px;
		color: var(--muted);
	}
	.status .muted {
		font-size: 12px;
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
		white-space: nowrap;
	}
</style>
