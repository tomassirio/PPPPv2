<!-- An admin's god message (GOD_MESSAGE {text, at}, sent to this player only), written across their own Game Boy
     screen (GameScreen, not spectators'): glowing purple text, no box, fading in, staying long enough to read and
     fading out. Several in a row show one after another. Text only, never HTML. -->
<script lang="ts">
	import { on } from '#lib/events.ts';

	type Message = { text: string; at: number };
	let queue = $state<Message[]>([]);
	const current = $derived(queue[0]);

	$effect(() => on('GOD_MESSAGE', (m: Message) => queue.push(m)));

	// Slow fades (FADE_MS each way) around time to read: 4 s plus a bit per letter, at most 12 s.
	const FADE_MS = 3_000;
	const shownMs = (text: string) => 2 * FADE_MS + Math.min(12_000, 4_000 + text.length * 60);
	$effect(() => {
		if (!current) return;
		const t = setTimeout(() => queue.shift(), shownMs(current.text));
		return () => clearTimeout(t);
	});
</script>

<div class="slot" role="status" aria-live="assertive">
	{#if current}
		{#key current.at}
			<p style:--fade="{FADE_MS}ms" style:--out-at="{shownMs(current.text) - FADE_MS}ms">{current.text}</p>
		{/key}
	{/if}
</div>

<style>
	/* Across the top of the screen (GameScreen's .screen; sizes follow its width via the .fit container). */
	.slot {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 6% 8%;
		pointer-events: none;
		z-index: 3;
	}
	p {
		margin: 0;
		text-align: center;
		font-size: max(14px, 4.6cqw);
		font-weight: 800;
		line-height: 1.3;
		overflow-wrap: anywhere;
		color: #f3ecff;
		-webkit-text-stroke: max(1px, 0.25cqw) #4c1d95;
		paint-order: stroke fill;
		text-shadow:
			0 0 0.3em #a78bfa,
			0 0 0.8em #7c3aed,
			0 0 1.6em #5b21b6;
		animation:
			descend var(--fade) ease-out both,
			ascend var(--fade) ease-in var(--out-at) forwards;
	}
	@keyframes descend {
		from {
			opacity: 0;
			transform: translateY(-0.6em) scale(0.96);
			filter: blur(6px);
		}
	}
	@keyframes ascend {
		to {
			opacity: 0;
			transform: translateY(-0.3em);
			filter: blur(4px);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		p {
			animation-name: fade-in, fade-out;
		}
		@keyframes fade-in {
			from {
				opacity: 0;
			}
		}
		@keyframes fade-out {
			to {
				opacity: 0;
			}
		}
	}
</style>
