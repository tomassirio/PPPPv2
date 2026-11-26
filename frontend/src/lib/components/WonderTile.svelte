<!-- Wonder Trade on the Link cable card, in the card's own language: a section label like "I'm up for", then one row
     built like the Trade / Battle toggles (same height, border, sunk background, 38px icon tile), in Wonder Trade's
     purple: what it does, the state underneath (how many are waiting, when the next one opens, something on its way),
     and a chevron. locked: the card's own "Cable Club closed" (clubClosed), so both agree; it still opens the dialog,
     which says why. -->
<script lang="ts">
	import WonderTrade from '#lib/components/WonderTrade.svelte';
	import { untilTomorrow, watchPool, wonder } from '#lib/wonder.svelte.ts';

	let { locked = false }: { locked?: boolean } = $props();
	let open = $state(false);
	$effect(() => watchPool());
	const p = $derived(wonder.pool);
	const line = $derived(
		locked
			? 'Opens with your Cable Club'
			: !p
				? 'Swap one Pokémon a day, sight unseen'
				: p.incoming
					? `${p.incoming === 1 ? 'A Pokémon is' : `${p.incoming} Pokémon are`} on the way to you`
					: p.tradedToday
						? `Next one in ${untilTomorrow()}`
						: p.waiting
							? `${p.waiting} waiting from other Pibs`
							: 'Pool empty · yours waits for the next Pib'
	);
	const ready = $derived(!locked && !!p && !p.tradedToday);
</script>

<div class="wonder">
	<span class="label" data-drop="4">Wonder Trade</span>
	<button type="button" class="row" class:locked class:ready onclick={() => (open = true)}>
		<span class="ico" aria-hidden="true">
			{#if locked}
				<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"
					><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
				>
			{:else}
				<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"
					><circle cx="12" cy="12" r="9" /><path d="M3 12h6m6 0h6" /><circle cx="12" cy="12" r="3" /></svg
				>
			{/if}
		</span>
		<span class="words">
			<b>{locked ? 'Locked' : !p ? 'Send one, get a surprise' : p.tradedToday ? 'Traded today' : 'Send one, get a surprise'}</b>
			<span>{line}</span>
		</span>
		<svg class="end" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
			><path d="M9 6l6 6-6 6" /></svg
		>
	</button>
</div>
<WonderTrade bind:open {locked} />

<style>
	.wonder {
		display: flex;
		flex-direction: column;
		gap: 8px;
		flex: none;
	}
	.label {
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	/* The Trade / Battle toggles' shape, in Wonder Trade's colour. */
	.row {
		--t: var(--r-epic);
		width: 100%;
		min-height: 52px;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 7px 12px 7px 8px;
		border-radius: 12px;
		border: 1px solid var(--border);
		background: var(--sunk);
		color: var(--text);
		font: inherit;
		text-align: left;
		cursor: pointer;
	}
	.row:hover {
		border-color: color-mix(in oklab, var(--t) 55%, var(--border));
	}
	.row.locked {
		--t: var(--muted);
	}
	.row:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.ico {
		flex: none;
		width: 38px;
		height: 38px;
		display: grid;
		place-items: center;
		border-radius: 10px;
		background: color-mix(in oklab, var(--t) 16%, transparent);
		color: var(--t);
	}
	.ready .ico {
		background: var(--t);
		color: var(--panel);
	}
	.words {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.words b {
		font-size: 14px;
		line-height: 18px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.ready .words b {
		color: var(--t);
	}
	.words span {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.end {
		flex: none;
		color: var(--muted);
	}
	@media (prefers-reduced-motion: no-preference) {
		.row,
		.ico {
			transition:
				background-color 0.18s,
				border-color 0.18s,
				color 0.18s;
		}
	}
</style>
