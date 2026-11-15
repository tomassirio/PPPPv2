<!-- An achievement tier's ball: Poké (common), Great (uncommon), Ultra (rare), Master (epic), GS (legendary).
     locked: the same ball as a muted silhouette. Decorative; the caller labels the tier in text. -->
<script lang="ts">
	import type { Rarity } from '#lib/play.ts';

	let { rarity, locked = false, size = 30 }: { rarity: Rarity; locked?: boolean; size?: number } = $props();
	// Top half, bottom half.
	const COLORS: Record<Rarity, [string, string]> = {
		common: ['#e3350d', '#f4f4f4'],
		uncommon: ['#3a6fd8', '#f4f4f4'],
		rare: ['#2b2b2b', '#f4f4f4'],
		epic: ['#7a3fb5', '#f4f4f4'],
		legendary: ['#e2b23a', '#c4c9cf']
	};
	const [top, bottom] = $derived(COLORS[rarity]);
</script>

<svg class:locked viewBox="0 0 32 32" width={size} height={size} aria-hidden="true">
	<path d="M2 16a14 14 0 0 1 28 0z" fill={top} />
	<path d="M2 16a14 14 0 0 0 28 0z" fill={bottom} />
	{#if rarity === 'uncommon'}
		<path d="M7 6.5q3 3 3 8M25 6.5q-3 3-3 8" stroke="#e3350d" stroke-width="2.6" fill="none" />
	{:else if rarity === 'rare'}
		<path d="M6 7h5v7H8zM26 7h-5v7h3z" fill="#f2c94c" />
	{:else if rarity === 'epic'}
		<circle cx="8.5" cy="9.5" r="2.4" fill="#e87bb8" />
		<circle cx="23.5" cy="9.5" r="2.4" fill="#e87bb8" />
		<path d="M12.5 11V5.5l3.5 3.5 3.5-3.5V11" stroke="#fff" stroke-width="1.6" fill="none" stroke-linejoin="round" />
	{:else if rarity === 'legendary'}
		<text x="16" y="12" text-anchor="middle" font-size="7" font-weight="700" font-family="sans-serif" fill="#5a4300">GS</text>
	{/if}
	<path d="M2 16h28" stroke="#1d1f1c" stroke-width="2.4" />
	<circle cx="16" cy="16" r="14" fill="none" stroke="#1d1f1c" stroke-width="2" />
	<circle cx="16" cy="16" r="4.2" fill={bottom} stroke="#1d1f1c" stroke-width="2" />
</svg>

<style>
	svg {
		flex: none;
		display: block;
	}
	.locked {
		filter: grayscale(1);
		opacity: 0.4;
	}
</style>
