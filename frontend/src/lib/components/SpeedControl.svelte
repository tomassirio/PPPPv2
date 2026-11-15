<!-- Emulation speed of the player's own console: 1x, 2x, 4x (PUT /api/v1/me/speed). Locked to 1x while a link
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

	async function set(s: number) {
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

<span class="speed" role="group" aria-label="Game speed" title={linked ? 'Speed is locked to 1× while the link cable is plugged in' : error || 'Game speed (sound plays at 1× only)'}>
	{#each SPEEDS as s (s)}
		<button type="button" aria-pressed={current === s} disabled={linked && s !== 1} onclick={() => set(s)}>{s}×</button>
	{/each}
	{#if linked}<span class="sr-only">Locked to 1× while linked</span>{/if}
</span>

<style>
	.speed {
		display: inline-flex;
		gap: 2px;
		padding: 2px;
		border-radius: 6px;
		background: rgba(255, 255, 255, 0.08);
	}
	button {
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
	button[aria-pressed='true'] {
		background: #d6dade;
		color: #1d1f1c;
	}
	button:disabled {
		opacity: 0.35;
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
</style>
