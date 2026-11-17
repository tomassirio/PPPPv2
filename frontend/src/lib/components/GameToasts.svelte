<!-- The player's own scored progress (their PROGRESS events) as a banner over the top of the game screen, one at a
     time, so it is seen on any tab. Text only (never HTML). Place inside a positioned container. -->
<script lang="ts">
	import { on } from '#lib/events.ts';
	import { activityText, type ScoreEvent } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	const SHOW_MS = 3500;

	let queue = $state<ScoreEvent[]>([]);
	const current = $derived(queue[0]);

	$effect(() =>
		on('PROGRESS', (e: ScoreEvent) => {
			if (e.user === session.me?.id) queue = [...queue, e].slice(-5);
		})
	);
	$effect(() => {
		if (!current) return;
		const t = setTimeout(() => (queue = queue.slice(1)), SHOW_MS);
		return () => clearTimeout(t);
	});

	const tone = (type: string) => (type === 'SEEN' || type === 'LEVEL' ? 'info' : 'great');
	// "You is now Gym Leader (C)" reads wrong; titles get their own sentence, and 0-point lines no "+0".
	const text = (e: ScoreEvent) => (e.type === 'TITLE' ? `New title: ${e.detail}` : activityText(e, 'You', nameOf));
</script>

<div class="toasts" role="status" aria-live="polite">
	{#if current}
		{#key current}
			<p class="toast {tone(current.type)}">{text(current)}{current.points ? ` +${current.points}` : ''}</p>
		{/key}
	{/if}
</div>

<style>
	.toasts {
		position: absolute;
		top: 12px;
		left: 50%;
		translate: -50% 0;
		width: min(92%, 420px);
		z-index: 4;
		pointer-events: none;
		display: flex;
		justify-content: center;
	}
	.toast {
		margin: 0;
		padding: 8px 14px;
		border-radius: 10px;
		background: rgb(17 22 26 / 0.88);
		color: #fff;
		font-size: 13px;
		font-weight: 600;
		text-align: center;
		box-shadow: 0 6px 20px rgb(0 0 0 / 0.35);
		border: 2px solid #8f9dff;
		animation: drop 0.25s ease-out;
	}
	.toast.great {
		border-color: #f5c542;
	}
	@keyframes drop {
		from {
			opacity: 0;
			translate: 0 -8px;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.toast {
			animation: none;
		}
	}
</style>
