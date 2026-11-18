<!-- The reset button of the player's own Game Boy (POST /api/v1/me/reset): a chip on the bezel strip that asks first,
     inline, since everything not saved in the game is lost; the console boots again from the game's own save. It also
     pulls a link cable, which is the way out of a link room the partner is gone from. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { link } from '#lib/link.svelte.ts';

	let asking = $state(false);
	let busy = $state(false);
	let error = $state('');
	let root = $state<HTMLSpanElement>();
	let confirmButton = $state<HTMLButtonElement>();
	$effect(() => {
		if (asking) confirmButton?.focus();
	});

	async function reset() {
		busy = true;
		error = '';
		try {
			await api('POST', '/me/reset');
			asking = false;
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not reset';
		}
		busy = false;
	}
</script>

<svelte:window
	onpointerdown={(e) => {
		if (asking && root && !root.contains(e.target as Node)) asking = false;
	}}
	onkeydown={(e) => {
		if (asking && e.key === 'Escape') asking = false;
	}}
/>

<span class="reset" class:asking bind:this={root}>
	<button type="button" class="current" aria-expanded={asking} title={error || 'Reset the Game Boy'} onclick={() => (asking = !asking)}>
		<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"
			><path d="M3 12a9 9 0 1 0 3-6.7M3 4v5h5" /></svg
		>
		Reset
	</button>
	{#if asking}
		<span class="choices" role="group" aria-label="Reset the Game Boy?">
			<span class="q">{link.state?.session ? 'Unplug and lose' : 'Lose'} unsaved progress?</span>
			<button type="button" class="yes" bind:this={confirmButton} disabled={busy} onclick={reset}>Reset</button>
			<button type="button" onclick={() => (asking = false)}>Cancel</button>
		</span>
	{/if}
	{#if error}<span class="sr-only" role="alert">{error}</span>{/if}
</span>

<style>
	.reset {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		padding: 2px;
		border-radius: 6px;
		background: rgba(255, 255, 255, 0.08);
	}
	.choices {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		padding-left: 4px;
		border-left: 1px solid rgba(255, 255, 255, 0.15);
		animation: grow 120ms ease-out;
	}
	.q {
		color: #d6dade;
		font: 600 11px var(--body-font);
		padding: 0 4px;
		white-space: nowrap;
	}
	@keyframes grow {
		from {
			opacity: 0;
			translate: -6px 0;
		}
	}
	button {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		height: 22px;
		padding: 0 6px;
		border: none;
		border-radius: 4px;
		background: transparent;
		color: #d6dade;
		font: 600 11px var(--body-font);
		letter-spacing: 0.04em;
		cursor: pointer;
	}
	.current:hover,
	.asking .current {
		background: rgba(255, 255, 255, 0.12);
	}
	.yes {
		background: #e3350d;
		color: #fff;
	}
	button:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}
	button:focus-visible {
		outline: 2px solid #d6dade;
		outline-offset: 1px;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
	@media (prefers-reduced-motion: reduce) {
		.choices {
			animation: none;
		}
	}
</style>
