<!-- The player's own live screen: PNG frames from /ws/screen drawn on a 160x144 canvas, scaled with pixelated rendering.
     watch: another player's id instead: their screen from /ws/watch/{id}, read only, while they are linked (the socket
     closes with 4404 otherwise, and then it says so and stops trying).
     Other players' ghosts (drawn into the frames by the server) get their name under them, and everyone on screen (the
     player too) gets their chat line above their head for a few seconds, fading out. Text only, never HTML. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import type { Snippet } from 'svelte';
	import { backoff, nearbyPlayers, type ScreenPlayer } from '#lib/play.ts';
	import { on } from '#lib/events.ts';
	import { nameOf, needPicture } from '#lib/pictures.svelte.ts';
	import { nameColor, type ChatMessage } from '#lib/chat/messages.ts';
	import { session } from '#lib/session.svelte.ts';

	const BUBBLE_MS = 5000;

	// `controls` renders at the right of the bezel's status strip (e.g. the sound control); `leading` right after LIVE.
	let { controls, leading, watch }: { controls?: Snippet; leading?: Snippet; watch?: string } = $props();

	let canvas: HTMLCanvasElement;
	let connected = $state(false);
	let ended = $state(false); // watching: the player is not (or no longer) linked

	const overlay = $derived(ended ? 'Not linked' : connected ? null : 'Reconnecting…');
	// Who is on screen this frame (sprite top left, screen pixels; the player first, then ghosts), and their latest chat line.
	let ghosts = $state<ScreenPlayer[]>([]);
	export function nearby() {
		return nearbyPlayers(ghosts);
	}
	let said = $state<Record<string, { id: string; text: string }>>({});

	$effect(() =>
		on('CHAT_MESSAGE', (m: ChatMessage) => {
			if (m.kind !== 'USER') return;
			said[m.user] = { id: m.id, text: m.text };
			setTimeout(() => {
				if (said[m.user]?.id === m.id) delete said[m.user];
			}, BUBBLE_MS);
		})
	);

	$effect(() => {
		const ctx = canvas.getContext('2d')!;
		let ws: WebSocket | null = null;
		let attempt = 0;
		let timer: ReturnType<typeof setTimeout> | undefined;
		let stopped = false;
		// Decode one frame at a time; while busy keep only the newest.
		let busy = false;
		let pending: Blob | null = null;

		async function draw(blob: Blob) {
			busy = true;
			try {
				const bmp = await createImageBitmap(blob);
				ctx.drawImage(bmp, 0, 0, canvas.width, canvas.height);
				bmp.close();
			} catch {
				/* bad frame, skip */
			}
			busy = false;
			if (pending) {
				const next = pending;
				pending = null;
				draw(next);
			}
		}

		function connect() {
			const proto = location.protocol === 'https:' ? 'wss' : 'ws';
			ws = new WebSocket(`${proto}://${location.host}${watch ? `/ws/watch/${encodeURIComponent(watch)}` : '/ws/screen'}`);
			ws.binaryType = 'blob';
			ws.onopen = () => {
				connected = true;
				attempt = 0;
			};
			ws.onmessage = (e) => {
				if (typeof e.data === 'string') {
					ghosts = JSON.parse(e.data).ghosts ?? [];
					ghosts.forEach((g) => needPicture(g.user));
					return;
				}
				if (busy) pending = e.data;
				else draw(e.data);
			};
			ws.onclose = (e) => {
				connected = false;
				ghosts = [];
				if (watch && e.code === 4404) ended = true;
				else if (!stopped) timer = setTimeout(connect, backoff(attempt++));
			};
		}

		connect();
		return () => {
			stopped = true;
			clearTimeout(timer);
			ws?.close();
		};
	});
</script>

<!-- .fit is a size container the play page can stretch; the bezel then hugs the largest 160:144 screen that fits. -->
<div class="fit">
	<div class="bezel">
		<div class="screen">
			<canvas bind:this={canvas} width="160" height="144" aria-label={watch ? `${nameOf(watch)}'s screen` : 'Live game screen'}></canvas>
			{#if !overlay}
				{#each ghosts as g (g.user)}
					{@const left = `${((g.x + 8) / 160) * 100}%`}
					{#if said[g.user]}
						{#key said[g.user].id}
							<div class="tag above" style:left style:top="{(g.y / 144) * 100}%" aria-hidden="true">
								<p class="said">{said[g.user].text}</p>
							</div>
						{/key}
					{/if}
					{#if g.user !== (watch ?? session.me?.id)}
						<div class="tag below" style:left style:top="{((g.y + 16) / 144) * 100}%" aria-hidden="true">
							<span class="who" style:color={nameColor(g.user)}><Name user={g.user} /></span>
						</div>
					{/if}
				{/each}
			{/if}
			{#if overlay}
				<div class="overlay pixel" aria-hidden="true">{overlay}</div>
			{/if}
			<p class="sr-only" role="status">{overlay ?? ''}</p>
		</div>
		<div class="strip">
			<span class="left">
				<span class="live" class:off={!!overlay}><span class="dot" aria-hidden="true">●</span> {overlay ? 'OFFLINE' : 'LIVE'}</span>
				{@render leading?.()}
			</span>
			{@render controls?.()}
		</div>
	</div>
</div>

<style>
	.fit {
		container-type: inline-size;
		display: flex;
		justify-content: center;
		align-items: flex-start;
		height: 100%;
	}
	.bezel {
		background: #3b3f45;
		border-radius: 10px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 8px;
		box-sizing: border-box;
		/* 100% normally; the play page sets --bezel-width when it gives .fit a fixed height. */
		width: var(--bezel-width, 100%);
	}
	.screen {
		position: relative;
		aspect-ratio: 160 / 144;
		width: 100%;
		background: #cadc9f;
		border-radius: 2px;
		overflow: hidden;
	}
	canvas {
		display: block;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
	}
	.overlay {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		text-align: center;
		padding: 16px;
		background: rgb(202 220 159 / 0.85);
		color: #0f380f;
		font-size: 14px;
		line-height: 1.6;
	}
	/* Centred on a player: the chat bubble grows up from their head, the name hangs under their feet. Sizes follow
	   the screen width. */
	.tag {
		position: absolute;
		display: flex;
		justify-content: center;
		pointer-events: none;
		max-width: 45%;
	}
	.above {
		transform: translate(-50%, -100%);
		padding-bottom: 0.4cqw;
	}
	.below {
		transform: translateX(-50%);
		padding-top: 0.2cqw;
	}
	.who {
		font-size: max(10px, 2.4cqw);
		font-weight: 600;
		line-height: 1.2;
		padding: 0.1em 0.4em;
		border-radius: 0.4em;
		background: rgb(0 0 0 / 0.6);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		max-width: 100%;
	}
	.said {
		margin: 0;
		font-size: max(10px, 2.4cqw);
		line-height: 1.3;
		padding: 0.3em 0.6em;
		border-radius: 0.6em;
		background: #fff;
		color: #111;
		box-shadow: 0 1px 0 rgb(0 0 0 / 0.35);
		overflow-wrap: anywhere;
		text-align: center;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
		width: max-content;
		max-width: 100%;
		animation: said 5s linear forwards; /* BUBBLE_MS */
	}
	/* Pops in, stays, fades out over the last second. */
	@keyframes said {
		0% {
			opacity: 0;
		}
		4%,
		80% {
			opacity: 1;
		}
		100% {
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.said {
			animation: none;
		}
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		margin: 0;
	}
	.strip {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 28px;
	}
	.left {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.live {
		white-space: nowrap;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		color: #d6dade;
	}
	.live.off {
		color: var(--muted);
	}
	/* On air: stream connected. */
	.live:not(.off) .dot {
		color: #ff4d4f;
	}
	@media (max-width: 640px) {
		.bezel {
			padding: 8px;
			border-radius: 8px;
		}
		.overlay {
			font-size: 11px;
		}
	}
</style>
