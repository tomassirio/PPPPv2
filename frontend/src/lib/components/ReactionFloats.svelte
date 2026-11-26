<!-- Spectators' reactions (WATCH_REACTION {user, from, reaction}, sent from /watch) floating up the Game Boy screen of
     `user`: the player sees them on /play, everyone watching on /watch. Each one is the chat's Pikachu face with the
     sender's name under it, rising and fading over 2.6 s; at most 40 at once. Decoration only (aria-hidden). -->
<script lang="ts">
	import { on } from '#lib/events.ts';
	import { REACTIONS } from '#lib/chat/messages.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	let { user }: { user: string | undefined } = $props();
	type Float = { key: number; from: string; src: string; x: number };
	let floats = $state<Float[]>([]);
	let seq = 0;

	$effect(() =>
		on('WATCH_REACTION', (e: { user: string; from: string; reaction: string }) => {
			const r = REACTIONS.find((x) => x.id === e.reaction);
			if (!r || e.user !== user) return;
			const key = ++seq;
			floats = [...floats.slice(-39), { key, from: e.from, src: r.src, x: 8 + Math.random() * 72 }];
			setTimeout(() => (floats = floats.filter((f) => f.key !== key)), 2600);
		})
	);
</script>

<div class="floats" aria-hidden="true">
	{#each floats as f (f.key)}
		<span class="float" style:left="{f.x}%"><img src={f.src} alt="" width="40" height="40" /><small>{nameOf(f.from)}</small></span>
	{/each}
</div>

<style>
	.floats {
		position: absolute;
		inset: 0;
		overflow: hidden;
		pointer-events: none;
	}
	.float {
		position: absolute;
		bottom: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		animation: rise 2.6s ease-out forwards;
	}
	.float img {
		width: clamp(24px, 12cqw, 48px);
		height: auto;
		image-rendering: pixelated;
		filter: drop-shadow(0 2px 3px rgb(0 0 0 / 0.5));
	}
	.float small {
		font-size: 11px;
		font-weight: 700;
		color: #fff;
		text-shadow: 0 1px 2px #000;
		white-space: nowrap;
	}
	@keyframes rise {
		0% {
			bottom: 0;
			opacity: 0;
			transform: scale(0.6);
		}
		15% {
			opacity: 1;
			transform: scale(1.1);
		}
		25% {
			transform: scale(1);
		}
		100% {
			bottom: 80%;
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.float {
			animation-name: fade;
			bottom: 30%;
		}
	}
	@keyframes fade {
		0%,
		100% {
			opacity: 0;
		}
		20%,
		70% {
			opacity: 1;
		}
	}
</style>
