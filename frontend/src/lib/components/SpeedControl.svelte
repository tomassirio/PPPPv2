<!-- Emulation speed of the player's own console (PUT /api/v1/me/speed): a chip with the current speed; clicking it
     opens the choices inline on the same strip, picking one closes it again. Locked to 1x while a link
     cable is plugged in: both consoles then run in step, and the backend set them back to 1x when it plugged in.
     Sound plays at 1x only. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { link } from '#lib/link.svelte.ts';

	let { speed = 1 }: { speed?: number | null } = $props();
	const SPEEDS = [1, 2, 4];

	let current = $state(1);
	let error = $state('');
	$effect(() => {
		current = speed ?? 1;
	});
	const linked = $derived(!!link.state?.session);
	$effect(() => {
		if (linked) current = 1; // plugging in resets both consoles to 1x
	});

	let open = $state(false);
	let root = $state<HTMLSpanElement>();
	$effect(() => {
		if (linked) open = false;
	});

	async function set(s: number) {
		open = false;
		if (s === current || linked) return;
		const before = current;
		current = s;
		error = '';
		try {
			await api('PUT', '/me/speed', { speed: s });
		} catch (e) {
			current = before;
			error = e instanceof ApiError ? e.detail : 'Could not change speed';
		}
	}
</script>

<svelte:window
	onpointerdown={(e) => {
		if (open && root && !root.contains(e.target as Node)) open = false;
	}}
	onkeydown={(e) => {
		if (open && e.key === 'Escape') open = false;
	}}
/>

<span class="speed" class:open bind:this={root}>
	<button
		type="button"
		class="current"
		aria-expanded={open}
		aria-label="Game speed {current}×{linked ? ', locked while the link cable is plugged in' : ''}"
		title={linked ? 'Speed is locked to 1× while the link cable is plugged in' : error || 'Game speed (sound plays at 1× only)'}
		disabled={linked}
		onclick={() => (open = !open)}
	>
		<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 5l9 7-9 7zM12 5l9 7-9 7z" /></svg>
		{current}×
	</button>
	{#if open}
		<span class="choices" role="group" aria-label="Pick a speed">
			{#each SPEEDS as s (s)}
				<button type="button" aria-pressed={current === s} onclick={() => set(s)}>{s}×</button>
			{/each}
		</span>
	{/if}
</span>

<style>
	.speed {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		padding: 2px;
		border-radius: 6px;
		background: rgba(255, 255, 255, 0.08);
	}
	.choices {
		display: inline-flex;
		gap: 2px;
		padding-left: 2px;
		border-left: 1px solid rgba(255, 255, 255, 0.15);
		animation: grow 120ms ease-out;
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
		min-width: 30px;
		height: 22px;
		padding: 0 6px;
		border: none;
		border-radius: 4px;
		background: transparent;
		color: #d6dade;
		font: 600 11px var(--body-font);
		letter-spacing: 0.04em;
		font-variant-numeric: tabular-nums;
		cursor: pointer;
	}
	.current:hover:not(:disabled),
	.open .current {
		background: rgba(255, 255, 255, 0.12);
	}
	.choices button[aria-pressed='true'] {
		background: #d6dade;
		color: #1d1f1c;
	}
	button:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}
	button:focus-visible {
		outline: 2px solid #d6dade;
		outline-offset: 1px;
	}
	@media (prefers-reduced-motion: reduce) {
		.choices {
			animation: none;
		}
	}
</style>
