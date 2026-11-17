<!-- The player's own live screen: PNG frames from /ws/screen drawn on a 160x144 canvas, scaled with pixelated rendering.
     Other players' ghosts (drawn into the frames by the server) get a name label here, and their chat lines float above
     them for a few seconds. Text only, never HTML. -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { backoff } from '#lib/play.ts';
	import { on } from '#lib/events.ts';
	import { nameOf, needPicture } from '#lib/pictures.svelte.ts';
	import { nameColor, type ChatMessage } from '#lib/chat/messages.ts';

	type Ghost = { user: string; x: number; y: number };
	const BUBBLE_MS = 5000;

	// `controls` renders at the right of the bezel's status strip (e.g. the sound control); `leading` right after LIVE.
	let { controls, leading }: { controls?: Snippet; leading?: Snippet } = $props();

	let canvas: HTMLCanvasElement;
	let connected = $state(false);

	const overlay = $derived(connected ? null : 'Reconnecting…');
	// Where the server drew other players this frame (sprite top left, screen pixels), and their latest chat line.
	let ghosts = $state<Ghost[]>([]);
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
			ws = new WebSocket(`${proto}://${location.host}/ws/screen`);
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
			ws.onclose = () => {
				connected = false;
				ghosts = [];
				if (!stopped) timer = setTimeout(connect, backoff(attempt++));
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
			<canvas bind:this={canvas} width="160" height="144" aria-label="Live game screen"></canvas>
			{#if !overlay}
				{#each ghosts as g (g.user)}
					<div class="ghost" style:left="{((g.x + 8) / 160) * 100}%" style:top="{(g.y / 144) * 100}%" aria-hidden="true">
						{#if said[g.user]}<p class="said">{said[g.user].text}</p>{/if}
						<span class="who" style:color={nameColor(g.user)}>{nameOf(g.user)}</span>
					</div>
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
	/* Anchored at the ghost's head, centred; grows upwards: chat bubble, then the name. Sizes follow the screen width. */
	.ghost {
		position: absolute;
		transform: translate(-50%, -100%);
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.6cqw;
		pointer-events: none;
		max-width: 45%;
		padding-bottom: 0.4cqw;
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
