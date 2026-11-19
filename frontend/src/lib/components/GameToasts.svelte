<!-- The player's own scored progress (their PROGRESS events) as a banner over the top of the game screen, one at a
     time, so it is seen on any tab; and a ping when someone @mentions them in the chat. Text only (never HTML).
     Place inside a positioned container. -->
<script lang="ts">
	import { on } from '#lib/events.ts';
	import { activityText, type ScoreEvent } from '#lib/play.ts';
	import { nameOf, usernameOf } from '#lib/pictures.svelte.ts';
	import { mentions } from '#lib/chat/bubble.ts';
	import type { ChatMessage } from '#lib/chat/messages.ts';
	import { session } from '#lib/session.svelte.ts';

	const SHOW_MS = 3500;

	let queue = $state<ScoreEvent[]>([]);
	const current = $derived(queue[0]);

	$effect(() =>
		on('PROGRESS', (e: ScoreEvent) => {
			if (e.user === session.me?.id) queue = [...queue, e].slice(-5);
		})
	);
	// @mentions: their username or their nickname without spaces, as the bubbles read them.
	$effect(() =>
		on('CHAT_MESSAGE', (m: ChatMessage) => {
			const me = session.me?.id;
			if (!me || m.kind !== 'USER' || m.user === me) return;
			if (!mentions(m.text, [usernameOf(me).slice(1), nameOf(me).replace(/\s+/g, '')])) return;
			queue = [...queue, { user: m.user, type: 'MENTION', detail: m.text, points: 0, at: m.at }].slice(-5);
			navigator.vibrate?.([30, 40, 30]);
		})
	);
	$effect(() => {
		if (!current) return;
		const t = setTimeout(() => (queue = queue.slice(1)), SHOW_MS);
		return () => clearTimeout(t);
	});

	const tone = (type: string) => (type === 'MENTION' ? 'ping' : type === 'SEEN' || type === 'LEVEL' ? 'info' : 'great');
	// "You is now Gym Leader (C)" reads wrong; titles get their own sentence, and 0-point lines no "+0".
	const text = (e: ScoreEvent) =>
		e.type === 'MENTION' ? `${nameOf(e.user)}: ${e.detail}` : e.type === 'TITLE' ? `New title: ${e.detail}` : activityText(e, 'You', nameOf);
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
	/* Someone @mentioned you: link blue, with a bell. */
	.toast.ping {
		border-color: var(--link);
	}
	.toast.ping::before {
		content: '🔔 ';
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
