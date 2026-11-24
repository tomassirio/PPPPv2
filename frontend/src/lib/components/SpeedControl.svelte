<!-- Fast forward for the player's own console (PUT /api/v1/me/speed): one toggle, lit while on. On is the server's 4x
     (as fast as it manages, ~3x on the Pi), off is 1x. Locked off while a link cable is plugged in: both consoles then
     run in step, and the backend set them back to 1x when it plugged in. Sound plays at 1x only. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { link } from '#lib/link.svelte.ts';

	let { speed = 1 }: { speed?: number | null } = $props();
	const FAST = 4;

	let current = $state(1);
	let error = $state('');
	$effect(() => {
		current = speed ?? 1;
	});
	const linked = $derived(!!link.state?.session);
	$effect(() => {
		if (linked) current = 1; // plugging in resets both consoles to 1x
	});
	const fast = $derived(current > 1);

	async function toggle() {
		if (linked) return;
		const before = current;
		current = fast ? 1 : FAST;
		error = '';
		try {
			await api('PUT', '/me/speed', { speed: current });
		} catch (e) {
			current = before;
			error = e instanceof ApiError ? e.detail : 'Could not change speed';
		}
	}
</script>

<button
	type="button"
	class="speed"
	aria-pressed={fast}
	title={linked ? 'Fast forward is off while the link cable is plugged in' : error || (fast ? 'Fast forward on (no sound)' : 'Fast forward')}
	disabled={linked}
	onclick={toggle}
>
	<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 5l9 7-9 7zM12 5l9 7-9 7z" /></svg>
	Fast
</button>

<style>
	.speed {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		height: 26px;
		padding: 0 8px;
		border: none;
		border-radius: 6px;
		background: rgba(255, 255, 255, 0.08);
		color: #d6dade;
		font: 600 11px var(--body-font);
		letter-spacing: 0.04em;
		cursor: pointer;
		transition: background 120ms, color 120ms;
	}
	.speed:hover:not(:disabled) {
		background: rgba(255, 255, 255, 0.16);
	}
	.speed[aria-pressed='true'] {
		background: #d6dade;
		color: #1d1f1c;
	}
	.speed:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}
	.speed:focus-visible {
		outline: 2px solid #d6dade;
		outline-offset: 1px;
	}
	@media (prefers-reduced-motion: reduce) {
		.speed {
			transition: none;
		}
	}
</style>
