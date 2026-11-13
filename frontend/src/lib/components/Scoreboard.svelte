<!-- Scoreboard: every player's points and progress, plus the latest activity.
     GET /api/v1/scoreboard and /activity, kept current by SCOREBOARD_CHANGED (the new rows) and PROGRESS (one event). -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { on, onOpen } from '#lib/events.ts';
	import { activityText, ago, shortUser, type ScoreEvent, type ScoreRow } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';

	const LATEST = 5;
	let rows = $state<ScoreRow[]>([]);
	let latest = $state<ScoreEvent[]>([]);
	let now = $state(Date.now());

	function load() {
		api<ScoreRow[]>('GET', '/scoreboard')
			.then((r) => (rows = r))
			.catch(() => {});
		api<ScoreEvent[]>('GET', `/activity?limit=${LATEST}`)
			.then((a) => (latest = a))
			.catch(() => {});
	}

	$effect(() => {
		load();
		const tick = setInterval(() => (now = Date.now()), 30_000);
		const offs = [
			on('SCOREBOARD_CHANGED', (r: ScoreRow[]) => (rows = r)),
			on('PROGRESS', (e: ScoreEvent) => (latest = [e, ...latest].slice(0, LATEST))),
			onOpen(load), // events sent while the stream was down are gone: fetch again
			() => clearInterval(tick)
		];
		return () => offs.forEach((off) => off());
	});
</script>

<section class="panel card" aria-labelledby="score-h">
	<h2 id="score-h">Scoreboard</h2>
	{#if rows.length}
		<table>
			<thead class="sr-only">
				<tr><th scope="col">Rank</th><th scope="col">Player</th><th scope="col">Badges</th><th scope="col">Caught</th><th scope="col">Points</th></tr>
			</thead>
			<tbody>
				{#each rows as r, i (r.userId)}
					{@const me = r.userId === session.me?.id}
					<tr class:me>
						<td class="rank">{i + 1}</td>
						<th scope="row">
							<span class="dot" class:on={r.online} aria-hidden="true"></span>
							<span class="name">{shortUser(r.userId)}</span>
							{#if me}<span class="you">you</span>{/if}
							<span class="sr-only">{r.online ? 'online' : 'away'}</span>
						</th>
						<td class="pips" title="{r.badges} badges">
							<span aria-hidden="true">
								{#each Array(8) as _, b (b)}<i class:earned={b < r.badges}></i>{/each}
							</span>
							{#if r.badges > 8}<span aria-hidden="true">+{r.badges - 8}</span>{/if}
							<span class="sr-only">{r.badges} badges</span>
						</td>
						<td class="caught">{r.caught} caught</td>
						<td class="points">{r.points}<span class="sr-only"> points</span></td>
					</tr>
				{/each}
			</tbody>
		</table>
	{:else}
		<p class="muted">Nobody has picked a game yet.</p>
	{/if}
	{#if latest.length}
		<hr />
		<h2 id="latest-h">Latest</h2>
		<ul aria-labelledby="latest-h">
			{#each latest as e (e)}
				<li>
					<span class="what">{activityText(e)}</span>
					<span class="muted">{' · '}{ago(now - e.at)}{' · '}+{e.points}</span>
				</li>
			{/each}
		</ul>
	{/if}
</section>

<style>
	.card {
		padding: 16px 20px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	h2 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	p,
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}
	.muted {
		font-size: 13px;
		color: var(--muted);
	}
	hr {
		width: 100%;
		margin: 0;
		border: none;
		border-top: 1px solid var(--border);
	}
	table {
		width: 100%;
		border-collapse: collapse;
		font-size: 13px;
	}
	td,
	tbody th {
		padding: 6px 4px;
		font-weight: normal;
		text-align: left;
		white-space: nowrap;
	}
	tbody th {
		width: 100%; /* the name takes the spare width and is the one to shrink */
		max-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.rank {
		color: var(--muted);
		font-variant-numeric: tabular-nums;
	}
	.me td,
	.me th {
		background: var(--bg);
		font-weight: 600;
	}
	.me td:first-child {
		border-radius: 8px 0 0 8px;
	}
	.me td:last-child {
		border-radius: 0 8px 8px 0;
	}
	.you {
		margin-left: 4px;
		padding: 0 5px;
		border: 1px solid var(--border-strong);
		border-radius: 999px;
		font-size: 10px;
		font-weight: normal;
		color: var(--muted);
	}
	/* Online: a filled dot; away: a hollow ring (shape, not only colour). */
	.dot {
		display: inline-block;
		width: 6px;
		height: 6px;
		margin-right: 4px;
		border: 1px solid var(--muted);
		border-radius: 50%;
	}
	.dot.on {
		border-color: var(--grass);
		background: var(--grass);
	}
	.pips i {
		display: inline-block;
		width: 5px;
		height: 5px;
		margin-right: 2px;
		border: 1px solid var(--border-strong);
		border-radius: 50%;
	}
	.pips i.earned {
		border-color: var(--accent-text);
		background: var(--accent-text);
	}
	.caught {
		color: var(--muted);
	}
	.points {
		text-align: right;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
	}
	li {
		font-size: 13px;
		line-height: 1.5;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
		white-space: nowrap;
	}
</style>
