<!-- A link's kind as an icon: two arrows swapping (trade) or crossed swords (battle). Decorative: the label says it. -->
<script lang="ts">
	let { kind, size = 16 }: { kind: 'TRADE' | 'BATTLE'; size?: number } = $props();
</script>

<svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
	{#if kind === 'TRADE'}<path d="M4 8h13l-3-3M20 16H7l3 3" />{:else}<path
			d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5L21 6V3h-3L6.5 14.5M5 13l6 6M8 16l-4 4M3 19l2 2"
		/>{/if}
</svg>

<style>
	svg {
		flex: none;
	}
</style>
