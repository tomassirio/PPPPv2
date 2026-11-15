<!-- "Your run": the player's points and rank (scoreboard), badges as pips, Pokédex progress, and the top of their
     party and play time where the game profile reads them. The game summary is read from the console's RAM
     (GET /api/v1/game/state, kept current by GAME_STATE); Gold/Silver/Crystal give counters only (16 badges: Johto
     8, then Kanto 8). -->
<script lang="ts">
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { game, watchGame } from '#lib/game.svelte.ts';
	import { ordinal } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';

	$effect(() => watchGame());
	$effect(() => watchBoard());

	const summary = $derived(game.summary);
	const rank = $derived(board.rows.findIndex((r) => r.userId === session.me?.id));
	const mine = $derived(rank >= 0 ? board.rows[rank] : null);
	const counter = (label: string) => summary?.counters.find((c) => c.label === label);
	const dex = $derived(counter('Pokédex'));
	const seen = $derived(counter('Seen'));
	const badgeCount = $derived(counter('Badges'));
	const named = $derived(summary?.badges ?? []);
	const earned = $derived(named.length ? named.filter((b) => b.earned).length : (badgeCount?.value ?? 0));
	const max = $derived(named.length || badgeCount?.max || 0);
	// Rows of 8: one for an 8-badge game, Johto then Kanto for Gold/Silver/Crystal.
	const rows = $derived(
		max > 8
			? [
					{ label: 'Johto badges', got: Math.min(earned, 8) },
					{ label: 'Kanto badges', got: Math.max(0, earned - 8) }
				]
			: max
				? [{ label: 'Badges', got: earned }]
				: []
	);
	const top = $derived(summary?.party[0]);
</script>

<section class="panel card" aria-labelledby="run-h">
	<div class="head">
		<h2 id="run-h">Your run</h2>
		{#if mine}
			<span><span class="pts">{mine.points.toLocaleString()}</span> <span class="muted">pts · {ordinal(rank + 1)}</span></span>
		{/if}
	</div>
	{#if summary}
		{#each rows as row, r (row.label)}
			<div class="line wrap" class:later={r > 0 && !row.got}>
				<span>{row.label}</span>
				<span class="pips" role="img" aria-label="{row.got} of 8 {row.label}">
					{#each Array(8) as _, i (i)}
						{@const b = named[r * 8 + i]}
						<i class:got={i < row.got} title={b ? `${b.name} Badge (${b.place})` : undefined}></i>
					{/each}
				</span>
			</div>
		{/each}
		{#if dex}
			<div class="dex">
				<div class="line">
					<span>Pokédex</span>
					<span class="muted">{dex.value} of {dex.max} caught{seen ? ` · ${seen.value} seen` : ''}</span>
				</div>
				<span class="bar" role="meter" aria-label="Pokédex caught" aria-valuemin="0" aria-valuemax={dex.max} aria-valuenow={dex.value}
					><b style:width="{dex.max ? (dex.value * 100) / dex.max : 0}%"></b></span
				>
			</div>
		{/if}
		{#if top}
			<div class="line"><span>Top of party</span><span class="muted">{top.name} · Lv {top.level}</span></div>
		{/if}
		{#if summary.playTime}
			<div class="line"><span>Time played</span><span class="muted">{summary.playTime}</span></div>
		{/if}
	{:else}
		<p class="muted">Badges and your Pokédex show up here once your game is past the title screen.</p>
	{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		font-size: 14px;
	}
	.head,
	.line {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-width: 0;
	}
	.wrap {
		flex-wrap: wrap;
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
		line-height: 19px;
	}
	.pts {
		font-size: 22px;
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.pips {
		display: flex;
		gap: 5px;
	}
	.pips i {
		width: 22px;
		height: 22px;
		box-sizing: border-box;
		border-radius: 50%;
		border: 2px solid var(--border-strong);
	}
	.pips i.got {
		background: var(--accent);
		border-color: var(--accent);
	}
	.dex {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.bar {
		display: block;
		height: 8px;
		border-radius: 4px;
		background: var(--border);
		overflow: hidden;
	}
	.bar b {
		display: block;
		height: 100%;
		background: var(--accent);
		transition: width 0.4s ease;
	}
	@media (prefers-reduced-motion: reduce) {
		.bar b {
			transition: none;
		}
	}
	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		.card {
			padding: 16px;
			gap: 12px;
		}
		.pts {
			font-size: 20px;
		}
		/* Compact on the Scores tab: Kanto's row only once it starts. */
		.later {
			display: none;
		}
		.pips i {
			width: 18px;
			height: 18px;
		}
	}
</style>
