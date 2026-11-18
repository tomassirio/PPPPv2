<!-- A Pib's two ratings, kept apart: uncapped points with the rank they give (top row), and the capped 0–1000
     Trainer rating behind grade and title (bottom row, with a bar). Your run and the profile hero use it. -->
<script lang="ts">
	import Crown from '#lib/components/Crown.svelte';
	import RankNumber from '#lib/components/RankNumber.svelte';
	import GradeChip from '#lib/components/GradeChip.svelte';
	import { score, type ScoreRow } from '#lib/play.ts';

	let { row, rank, total }: { row: ScoreRow; rank: number; total: number } = $props();
	// The top three get the podium's look: the card in their metal, the medal (and #1's crown) for the number.
	const metal = $derived(rank < 3 ? (['gold', 'silver', 'bronze'] as const)[rank] : null);
	const TITLES = ['Top of the ladder', 'Runner-up', 'Third place'];
</script>

<div class="summary {metal ? `metal-frame metal-${metal}` : ''}" class:podium={!!metal}>
	<div class="top">
		<div class="figure">
			<span class="big">{score(row).toLocaleString('en')}</span>
			<span class="unit">points</span>
		</div>
		<div class="place" class:first={rank === 0}>
			{#if metal}
				<span class="podium-text">
					<b class="podium-title">{TITLES[rank]}</b>
					<span class="unit">#{rank + 1} of {total} Pibs</span>
				</span>
				<span class="podium-place">
					{#if rank === 0}<span class="crown-at"><Crown size={24} /></span>{/if}
					<RankNumber rank={rank + 1} {total} medal size={rank === 0 ? 42 : 36} animate />
				</span>
			{:else}
				<span class="pos"><RankNumber rank={rank + 1} {total} prefix="#" animate /></span>
				<span class="unit">of {total} {total === 1 ? 'Pib' : 'Pibs'}</span>
			{/if}
		</div>
	</div>
	<div class="rating">
		<div class="grade-line">
			<GradeChip grade={row.grade} rating={row.trainerScore} size={12} />
			<b class="title">{row.title}</b>
			<span class="unit">{row.trainerScore} / 1000</span>
		</div>
		<span class="meter" role="meter" aria-label="Trainer rating" aria-valuemin="0" aria-valuemax="1000" aria-valuenow={row.trainerScore}
			><b style:width="{Math.max(1, Math.min(100, row.trainerScore / 10))}%"></b></span
		>
	</div>
</div>

<style>
	.summary {
		border-radius: 12px;
		background: var(--sunk);
		border: 1px solid var(--border);
		overflow: hidden;
	}
	.summary.podium {
		--frame-bg: var(--sunk);
		overflow: visible;
	}
	.podium-place {
		position: relative;
		display: inline-flex;
	}
	.crown-at {
		position: absolute;
		top: 2px;
		left: 50%;
		translate: -50% -60%;
	}
	.place:has(.podium-place) {
		flex-direction: row;
		align-items: center;
		gap: 10px;
	}
	.podium-text {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 2px;
	}
	.podium-title {
		font-size: 13px;
		color: color-mix(in srgb, var(--m-lo) 35%, var(--text));
	}
	.top {
		display: flex;
		align-items: stretch;
		justify-content: space-between;
		gap: 12px;
		padding: 12px 14px;
	}
	.figure,
	.place {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.place {
		align-items: flex-end;
		justify-content: center;
	}
	.big {
		font-size: 30px;
		font-weight: 800;
		line-height: 1;
		letter-spacing: -0.01em;
		font-variant-numeric: tabular-nums;
	}
	.pos {
		font-size: 18px;
		line-height: 1.2;
		color: var(--text);
	}
	.unit {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
	}
	.rating {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 10px 14px 12px;
		border-top: 1px solid var(--border);
	}
	.grade-line {
		display: flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
	}
	.title {
		flex: 1;
		min-width: 0;
		font-size: 14px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.meter {
		height: 6px;
		border-radius: 3px;
		background: var(--border);
		overflow: hidden;
	}
	.meter b {
		display: block;
		height: 100%;
		background: var(--accent);
	}
	@media (max-width: 760px) {
		.big {
			font-size: 26px;
		}
	}
</style>
