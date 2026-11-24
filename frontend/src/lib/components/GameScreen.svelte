<!-- The player's own live screen: PNG frames from /ws/screen drawn on a 160x144 canvas, scaled with pixelated rendering.
     watch: another player's id instead: their screen from /ws/watch/{id}, read only, while they are linked (the socket
     closes with 4404 otherwise, and then it says so and stops trying).
     Other players' ghosts (drawn into the frames by the server) get their name under them, and everyone on screen (the
     player too) gets their chat line above their head for a few seconds, fading out. Text only, never HTML. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import GodMessage from '#lib/components/GodMessage.svelte';
	import type { Snippet } from 'svelte';
	import { backoff, nearbyPlayers, type ScreenPlayer } from '#lib/play.ts';
	import { on } from '#lib/events.ts';
	import { nameOf, needPicture, usernameOf } from '#lib/pictures.svelte.ts';
	import { nameColor, type ChatMessage } from '#lib/chat/messages.ts';
	import { bubble, letters, type BubbleContext } from '#lib/chat/bubble.ts';
	import { board } from '#lib/board.svelte.ts';
	import { game } from '#lib/game.svelte.ts';
	import { session } from '#lib/session.svelte.ts';
	import { tip } from '#lib/tour.svelte.ts';
	import { api } from '#lib/api.ts';
	import type { ScreenNote } from '#lib/notes.svelte.ts';

	const BUBBLE_MS = 5000;

	// `controls` renders at the right of the bezel's status strip (e.g. the sound control); `leading` right after LIVE.
	let { controls, leading, watch }: { controls?: Snippet; leading?: Snippet; watch?: string } = $props();

	let canvas: HTMLCanvasElement;
	let connected = $state(false);
	let ended = $state(false); // watching: the player is not (or no longer) linked
	let notPlaying = $state(false); // an admin watching: their console is not running (4410); keeps retrying
	// The server shut the console down because nobody touched it for a while (close code 4408): no reconnecting
	// until the player says so. Continue bumps `wake`, which opens the screen (and the console) again.
	let away = $state(false);
	$effect(() => {
		if (away && !watch) tip('away');
	});
	let wake = $state(0);
	let continueButton = $state<HTMLButtonElement>();
	$effect(() => {
		if (away) continueButton?.focus(); // keyboard players land on it
	});

	const overlay = $derived(
		ended ? 'Not linked' : away ? 'Paused while you were away' : notPlaying ? 'Not playing right now' : connected ? null : 'Reconnecting…'
	);
	// Who is on screen this frame (sprite top left, screen pixels; the player first, then ghosts), and their latest chat line.
	let ghosts = $state<ScreenPlayer[]>([]);
	// Notes in view (lib/notes.svelte.ts), and the one the player stands on.
	let notes = $state<ScreenNote[]>([]);
	const reading = $derived(notes.find((n) => n.here) ?? null);
	$effect(() => {
		if (reading && !reading.mine && !watch) tip('note');
	});
	export function nearby() {
		return nearbyPlayers(ghosts);
	}
	let said = $state<Record<string, { id: string; text: string }>>({});
	// Who @mentions can name (their username, or their nickname without spaces) and the Pokémon names of the
	// player's own game, for the bubbles.
	const bubbleCtx = $derived<BubbleContext>({
		players: board.rows.map((r) => ({ id: r.userId, handles: [usernameOf(r.userId).slice(1), nameOf(r.userId).replace(/\s+/g, '')] })),
		species: (game.summary?.collection?.names ?? []).map((n) => n ?? '')
	});

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
		void wake; // Continue after being away: connect again
		const ctx = canvas.getContext('2d')!;
		let ws: WebSocket | null = null;
		let attempt = 0;
		let timer: ReturnType<typeof setTimeout> | undefined;
		let stopped = false;
		// Decode one frame at a time; while busy keep only the newest.
		let busy = false;
		let pending: Blob | null = null;
		// Messages only store the newest picture and name tags; one paint per animation frame uses them. Handling
		// each message fully (at 4x: 120 pictures plus tag updates a second) could not keep up on phones, and the
		// browser queues messages rather than dropping them, so the game played back late and in slow motion.
		let nextBlob: Blob | null = null;
		let nextGhosts: string | null = null;
		let painting = false;
		function paint() {
			painting = false;
			if (nextGhosts !== null) {
				const m = JSON.parse(nextGhosts);
				ghosts = m.ghosts ?? [];
				notes = m.notes ?? [];
				ghosts.forEach((g) => needPicture(g.user));
				nextGhosts = null;
			}
			const blob = nextBlob;
			nextBlob = null;
			if (!blob) return;
			if (busy) pending = blob;
			else draw(blob);
		}
		function schedule() {
			if (painting) return;
			painting = true;
			requestAnimationFrame(paint);
		}

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
				// A watcher's socket opens even when the server closes it right away (4410): it only counts as
				// back once a frame arrives (onmessage), so retries keep backing off.
				if (!watch) attempt = 0;
			};
			ws.onmessage = (e) => {
				notPlaying = false;
				attempt = 0;
				if (typeof e.data === 'string') nextGhosts = e.data;
				else nextBlob = e.data;
				schedule();
			};
			ws.onclose = (e) => {
				connected = false;
				ghosts = [];
				notPlaying = !!watch && e.code === 4410;
				if (watch && e.code === 4404) ended = true;
				else if (!watch && e.code === 4408) away = true;
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
				<!-- Notes on the ground: a glowing mark on each tile that has one, as Dark Souls' messages. -->
				{#each notes as n (n.id)}
					<span class="note-mark" class:mine={n.mine} style:left="{((n.x + 8) / 160) * 100}%" style:top="{((n.y + 12) / 144) * 100}%" aria-hidden="true"></span>
				{/each}
				{#each ghosts as g (g.user)}
					{@const left = `${((g.x + 8) / 160) * 100}%`}
					{#if said[g.user]}
						{#key said[g.user].id}
							{@const b = bubble(said[g.user].text, bubbleCtx)}
							{#if 'special' in b && b.special === 'youlost'}
								<!-- The circle game: an upside-down OK sign held on the sprite's right thigh (facing us: our left,
								     about 5 px in and 13.5 down of its 16x16). -->
								<div class="tag leg" class:ghost={g.user !== (watch ?? session.me?.id)} style:left="{((g.x + 5) / 160) * 100}%" style:top="{((g.y + 13.5) / 144) * 100}%" aria-hidden="true">
									<span class="youlost">👌</span>
								</div>
							{:else}
								<div class="tag above" class:ghost={g.user !== (watch ?? session.me?.id)} style:left style:top="{(g.y / 144) * 100}%" aria-hidden="true">
									{#if 'emoji' in b}
										<p class="said-emoji">{b.emoji}</p>
									{:else if 'special' in b}
										{#if b.special === 'heart'}
											<svg class="heart" viewBox="0 0 7 6" shape-rendering="crispEdges"
												><path fill="#e0403f" d="M1 0h2v1h1V0h2v1h1v2H6v1H5v1H4v1H3V5H2V4H1V3H0V1h1z" /><path fill="#fff" d="M1 1h1v1H1z" /></svg
											>
										{:else if b.special === 'notice'}
											<span class="notice pixel">!</span>
										{:else}
											<span class="zzz pixel"><i>Z</i><i>Z</i><i>Z</i></span>
										{/if}
									{:else}
										<p class="said" class:whisper={b.whisper}>
											<span class="text"
												>{#each b.segs as seg, k (k)}{#if seg.kind === 'text'}{seg.text}{:else if seg.kind === 'mention'}<b class="mention" style:color={nameColor(seg.user)}>{seg.text}</b
														>{:else if seg.kind === 'mon'}<img class="mon" src="/api/v1/game/icons/{seg.species}.png" alt={seg.text} title={seg.text} />{:else if seg.kind === 'whisper' || seg.kind === 'glitch'}<span
															class={seg.kind}
															data-text={seg.text}>{seg.text}</span
														>{:else}<span class={seg.kind}>{#each letters(seg.text) as ch, i (i)}<span style:--i={i}>{ch}</span>{/each}</span>{/if}{/each}</span
											>
										</p>
									{/if}
								</div>
							{/if}
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
				{#if away}
					<div class="overlay pixel away">
						<span aria-hidden="true">{overlay}</span>
						<button
							bind:this={continueButton}
							type="button"
							class="continue"
							onclick={() => {
								away = false;
								wake++;
							}}>Continue</button
						>
					</div>
				{:else}
					<div class="overlay pixel" aria-hidden="true">{overlay}</div>
				{/if}
			{/if}
			{#if reading && !overlay && !watch}
				<div class="note-read" role="status">
					<p class="note-text">“{reading.text}”</p>
					<p class="note-meta">
						<span>{reading.mine ? 'Your note' : ''}{#if !reading.mine}<Name user={reading.user} />{/if} · 👍 {reading.good}</span>
						{#if reading.mine}
							<button type="button" onclick={() => api('DELETE', `/notes/${reading.id}`).catch(() => {})}>Remove</button>
						{:else if !reading.rated}
							<button type="button" onclick={() => api('POST', `/notes/${reading.id}/good`).catch(() => {})}>Good</button>
						{/if}
					</p>
				</div>
			{/if}
			{#if !watch}<GodMessage />{/if}
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
	/* A note on the ground: an orange glow, pulsing, at the foot of its tile. */
	.note-mark {
		position: absolute;
		width: 6%;
		aspect-ratio: 2;
		translate: -50% -50%;
		border-radius: 50%;
		background: radial-gradient(closest-side, #fff6d0, #ffb347 45%, rgb(255 140 40 / 0) 100%);
		filter: drop-shadow(0 0 4px #ff9d2e);
		pointer-events: none;
		animation: note-glow 2s ease-in-out infinite;
	}
	.note-mark.mine {
		background: radial-gradient(closest-side, #fff, #ffd56b 45%, rgb(255 200 80 / 0) 100%);
	}
	@keyframes note-glow {
		50% {
			opacity: 0.55;
			scale: 0.85;
		}
	}
	/* Standing on one: its words across the foot of the screen, in the game's own type. */
	.note-read {
		position: absolute;
		left: 3%;
		right: 3%;
		bottom: 3%;
		z-index: 3;
		padding: 8px 10px;
		border-radius: 8px;
		background: rgb(20 14 6 / 0.82);
		border: 2px solid #ffb347;
		box-shadow: 0 0 14px rgb(255 157 46 / 0.5);
		color: #fff3dc;
		text-align: center;
		animation: note-in 0.3s ease-out;
	}
	@keyframes note-in {
		from {
			opacity: 0;
			translate: 0 6px;
		}
	}
	.note-text {
		margin: 0 0 6px;
		font: clamp(9px, 2.6cqw, 14px) / 1.5 var(--pixel-font);
		text-shadow: 0 0 8px #ff9d2e;
	}
	.note-meta {
		margin: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 10px;
		font-size: 12px;
		opacity: 0.9;
	}
	.note-meta button {
		padding: 3px 10px;
		border: 1px solid #ffb347;
		border-radius: 999px;
		background: transparent;
		color: #ffd9a3;
		font: 700 12px var(--body-font);
		cursor: pointer;
		pointer-events: auto;
	}
	@media (prefers-reduced-motion: reduce) {
		.note-mark,
		.note-read {
			animation: none;
		}
	}
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
	/* Away: the message and a way back. */
	.overlay.away {
		flex-direction: column;
		gap: 14px;
	}
	.continue {
		font: 600 max(12px, 2.6cqw) / 1 var(--body-font);
		padding: 0.7em 1.4em;
		border-radius: 8px;
		border: 2px solid #0f380f;
		background: #0f380f;
		color: #cadc9f;
		cursor: pointer;
	}
	.continue:hover {
		background: #306230;
	}
	.continue:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
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
	/* Another player's bubble: see-through like the ghost under it (Ghosts.ALPHA, 192 of 256). Opacity on the
	   wrapper, so bubble and tail fade as one. */
	.above.ghost {
		opacity: 0.75;
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
	/* A speech bubble in the game's own text-box style: white, a dark rounded frame, a tail down to the speaker. */
	.said {
		position: relative;
		margin: 0 0 0.9cqw;
		font-size: max(11px, 2.6cqw);
		font-weight: 600;
		line-height: 1.3;
		padding: 0.35em 0.7em;
		border-radius: 0.7em;
		background: #fff;
		color: #1d1f1c;
		border: max(2px, 0.4cqw) solid #283038;
		box-shadow: 0 0.3cqw 0 rgb(0 0 0 / 0.25);
		overflow-wrap: anywhere;
		text-align: center;
		width: max-content;
		max-width: 100%;
		animation: said 5s linear forwards; /* BUBBLE_MS */
		transform-origin: 50% 100%;
	}
	/* The tail: the frame's colour behind, white in front, so it joins the bubble's border. */
	.said::before,
	.said::after {
		content: '';
		position: absolute;
		left: 50%;
		top: 100%;
		translate: -50% 0;
		border-style: solid;
		border-color: transparent;
	}
	.said::before {
		border-width: max(6px, 1.2cqw) max(5px, 1cqw) 0;
		border-top-color: #283038;
	}
	.said::after {
		margin-top: calc(-1 * max(3px, 0.65cqw));
		border-width: max(4px, 0.8cqw) max(3px, 0.6cqw) 0;
		border-top-color: #fff;
	}
	.said .text {
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
	/* Effects inside a bubble (lib/chat/bubble.ts): one element per letter where letters move on their own. */
	.rainbow span,
	.shout span,
	.wobble span {
		display: inline-block;
		white-space: pre;
	}
	/* **rainbow**: every letter its own colour, the colours flowing along and the letters riding a wave. */
	.rainbow span {
		color: hsl(calc(var(--i) * 32deg) 85% 45%);
		animation:
			rainbow-wave 1.1s ease-in-out infinite,
			rainbow-hue 2.4s linear infinite;
		animation-delay: calc(var(--i) * -0.09s), calc(var(--i) * -0.12s);
	}
	/* !!shout!!: big, heavy and shaking, each letter out of step. */
	.shout {
		font-size: 1.3em;
		font-weight: 900;
		color: #d62f2f;
		letter-spacing: 0.02em;
	}
	.shout span {
		animation: shout 0.18s steps(2) infinite;
		animation-delay: calc(var(--i) * -0.05s);
	}
	@keyframes shout {
		0% {
			transform: translate(-0.06em, 0.05em) rotate(-4deg);
		}
		50% {
			transform: translate(0.06em, -0.05em) rotate(4deg);
		}
	}
	/* ~~wobble~~: letters sway like a Poké Ball about to break open. */
	.wobble span {
		transform-origin: 50% 100%;
		animation: wobble-letter 1.4s ease-in-out infinite;
		animation-delay: calc(var(--i) * -0.1s);
	}
	@keyframes wobble-letter {
		0%,
		100% {
			transform: rotate(0);
		}
		25% {
			transform: rotate(-14deg);
		}
		75% {
			transform: rotate(14deg);
		}
	}
	/* _glitch_: MissingNo. — red and cyan copies split off it now and then, and it flickers. */
	.glitch {
		position: relative;
		display: inline-block;
		animation: glitch-flicker 2.2s steps(1) infinite;
	}
	.glitch::before,
	.glitch::after {
		content: attr(data-text);
		position: absolute;
		inset: 0;
		pointer-events: none;
		mix-blend-mode: multiply;
	}
	.glitch::before {
		color: #ff2a6d;
		animation: glitch-a 1.6s steps(1) infinite;
	}
	.glitch::after {
		color: #05d9e8;
		animation: glitch-b 1.6s steps(1) infinite;
	}
	@keyframes glitch-a {
		0%,
		100% {
			transform: none;
			clip-path: inset(0 0 100% 0);
		}
		10% {
			transform: translate(-0.12em, 0);
			clip-path: inset(10% 0 50% 0);
		}
		40% {
			transform: translate(0.1em, 0);
			clip-path: inset(55% 0 5% 0);
		}
		70% {
			transform: none;
			clip-path: inset(0 0 100% 0);
		}
	}
	@keyframes glitch-b {
		0%,
		100% {
			transform: none;
			clip-path: inset(100% 0 0 0);
		}
		20% {
			transform: translate(0.12em, 0);
			clip-path: inset(40% 0 20% 0);
		}
		55% {
			transform: translate(-0.1em, 0);
			clip-path: inset(5% 0 70% 0);
		}
		80% {
			transform: none;
			clip-path: inset(100% 0 0 0);
		}
	}
	@keyframes glitch-flicker {
		0%,
		100% {
			opacity: 1;
		}
		46% {
			opacity: 0.35;
		}
		48% {
			opacity: 1;
		}
		90% {
			opacity: 0.6;
		}
	}
	/* ((whisper)): small, faded italics; a whole whispered line gets a dashed bubble. */
	.whisper {
		font-size: 0.85em;
		font-style: italic;
		font-weight: 500;
		color: #6b7177;
	}
	.said.whisper {
		border-style: dashed;
		background: rgb(255 255 255 / 0.82);
		font-size: max(10px, 2.2cqw);
	}
	.said.whisper::before,
	.said.whisper::after {
		display: none;
	}
	/* @name: in that player's colour. */
	.mention {
		font-weight: 800;
		filter: saturate(1.3) brightness(0.8);
	}
	/* A Pokémon's name: its little icon instead. */
	.mon {
		display: inline-block;
		height: 1.7em;
		width: auto;
		margin: -0.35em 0;
		vertical-align: middle;
		image-rendering: pixelated;
	}
	/* <3: a pixel heart beating. */
	.heart {
		display: block;
		width: max(16px, 4.4cqw);
		height: auto;
		margin-bottom: 0.6cqw;
		filter: drop-shadow(0 0.3cqw 0 rgb(0 0 0 / 0.3));
		animation:
			thumbs 5s ease-out forwards,
			beat 0.7s ease-in-out infinite;
	}
	@keyframes beat {
		0%,
		60%,
		100% {
			scale: 1;
		}
		20% {
			scale: 1.25;
		}
		40% {
			scale: 0.95;
		}
	}
	/* ?: the red "!" an NPC gets when they spot you. */
	.notice {
		display: grid;
		place-items: center;
		width: max(14px, 3.6cqw);
		height: max(16px, 4.2cqw);
		margin-bottom: 0.8cqw;
		border: max(2px, 0.4cqw) solid #283038;
		border-radius: 3px;
		background: #fff;
		color: #e0403f;
		font-size: max(9px, 2.4cqw);
		animation: notice 5s ease-out forwards;
	}
	@keyframes notice {
		0% {
			opacity: 0;
			transform: translateY(6px);
		}
		4% {
			opacity: 1;
			transform: translateY(-4px);
		}
		8%,
		80% {
			opacity: 1;
			transform: none;
		}
		100% {
			opacity: 0;
		}
	}
	/* zzz: three Z's drifting up and away, one after another. */
	.zzz {
		position: relative;
		display: block;
		width: max(24px, 6cqw);
		height: max(26px, 7cqw);
		color: #fff;
		font-size: max(8px, 2.2cqw);
		text-shadow: 0 0 2px #283038, 0 0 2px #283038;
	}
	.zzz i {
		position: absolute;
		left: 20%;
		bottom: 0;
		font-style: normal;
		opacity: 0;
		animation: zzz 2.4s ease-out infinite;
	}
	.zzz i:nth-child(2) {
		animation-delay: 0.8s;
		font-size: 1.25em;
	}
	.zzz i:nth-child(3) {
		animation-delay: 1.6s;
		font-size: 1.5em;
	}
	@keyframes zzz {
		0% {
			opacity: 0;
			transform: translate(0, 0);
		}
		20% {
			opacity: 1;
		}
		100% {
			opacity: 0;
			transform: translate(1.6em, -2.2em);
		}
	}
	/* :youlost: the circle game: an OK sign upside down, low on the right leg. */
	.leg {
		transform: translate(-50%, -50%);
	}
	.youlost {
		display: block;
		font-size: max(9px, 2.6cqw);
		line-height: 1;
		rotate: 180deg;
		filter: drop-shadow(0 0 1px rgb(0 0 0 / 0.6));
		animation: thumbs 5s ease-out forwards;
	}
	@keyframes rainbow-hue {
		to {
			filter: hue-rotate(360deg);
		}
	}
	/* A lone "ok": a thumbs up pops up instead of a bubble. */
	.said-emoji {
		margin: 0 0 0.6cqw;
		font-size: max(18px, 5cqw);
		line-height: 1;
		filter: drop-shadow(0 0.3cqw 0 rgb(0 0 0 / 0.3));
		animation: thumbs 5s ease-out forwards; /* BUBBLE_MS */
		transform-origin: 50% 100%;
	}
	@keyframes thumbs {
		0% {
			opacity: 0;
			transform: translateY(6px) scale(0.3) rotate(-25deg);
		}
		6% {
			opacity: 1;
			transform: translateY(-2px) scale(1.25) rotate(8deg);
		}
		10%,
		80% {
			opacity: 1;
			transform: none;
		}
		100% {
			opacity: 0;
			transform: translateY(-4px);
		}
	}
	/* Pops up from the speaker's head, stays, fades out over the last second. */
	@keyframes said {
		0% {
			opacity: 0;
			transform: translateY(4px) scale(0.6);
		}
		5% {
			opacity: 1;
			transform: translateY(-1px) scale(1.06);
		}
		8%,
		80% {
			opacity: 1;
			transform: none;
		}
		100% {
			opacity: 0;
			transform: translateY(-2px);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.rainbow span,
		.shout span,
		.wobble span,
		.glitch,
		.glitch::before,
		.glitch::after,
		.heart,
		.notice,
		.zzz i,
		.youlost,
		.said-emoji,
		.said {
			animation: none;
		}
		/* Still pictures instead: the Z's stacked, no colour split. */
		.zzz i {
			opacity: 1;
		}
		.zzz i:nth-child(2) {
			transform: translate(0.8em, -1em);
		}
		.zzz i:nth-child(3) {
			transform: translate(1.6em, -2.1em);
		}
		.glitch::before,
		.glitch::after {
			display: none;
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
