<!-- Konami code on /play (see konami.svelte.ts): the PIBSIGNO. opera. The curtains open on a psychedelic stage: his face
     dances in the spotlight, orbited by a kaleidoscope of more faces, the legendary birds sway in the balconies and a
     chorus of Pokémon (from the player's own ROM) hops along the footlights, while a square-wave aria plays and the
     surtitles translate. Act II is a scandal: the tempo doubles into a can-can with drums, a kick line of his faces in
     fishnets, a disco ball, confetti, a crown and a rose in his teeth, then lipstick kisses and internet acronyms (LMAO,
     L33T, ...) all over the screen. Act III is deep fried: a boom, the reprise bass boosted and clipped, the picture
     blown out, noisy and burnt, laser eyes, emoji spam and meme captions. Then the curtains close, Poké Balls are thrown
     like roses and he gets caught; then they open again onto the page as it was. The first time a player enters the code
     (with a game picked), POST /me/konami queues the prima donna, a shiny Jigglypuff, as a Mystery Gift.
     Skip: the button, Esc or a click. Reduced motion: the same stage, standing still. -->
<script lang="ts">
	import { konami } from '#lib/konami.svelte.ts';
	import { aria, gotcha } from '#lib/teaser.ts';
	import face from '#lib/assets/konami-face.webp';
	import { api } from '#lib/api.js';

	const BEAT = 0.4; // seconds; the CSS dances on --beat too
	const REPRISE: [number, number][] = [
		[67, 1], [72, 1], [75, 1], [79, 2], [77, 1], [75, 1], [74, 1], [72, 1], [74, 3],
		[79, 1], [83, 1], [86, 1], [84, 6]
	];
	// C minor, 3/4. [MIDI note, beats], 0 = rest: a bar of curtain, the aria, the climb, the high C held to the end.
	const MELODY: [number, number][] = [
		[0, 3],
		[67, 1], [72, 1], [75, 1], [79, 2], [77, 1], [75, 1], [74, 1], [72, 1], [74, 3],
		[75, 1], [77, 1], [79, 1], [80, 1], [82, 1], [84, 1], [86, 2], [84, 1], [83, 3],
		[79, 1], [83, 1], [86, 1], [84, 6],
		// Act II, the can-can: C major, 2/4, in eighths.
		...galop([67, 67, 69, 72, 71, 69, 74, 74, 74, 76, 71, 72, 69, 69, 69, 72, 71, 69, 67, 79, 77, 76, 74, 72, 74, 76, 77, 79]),
		...galop([67, 67, 69, 72, 71, 69, 74, 74]),
		[79, 0.5], [84, 0.5], [84, 1],
		// Act III, deep fried: Act I's tune again with the high C.
		...REPRISE
	];
	function galop(notes: number[]): [number, number][] {
		return notes.map((n) => [n, 0.5]);
	}
	const oompah = (root: number): [number, number][] => [[root, 1], [root + 12, 1], [root + 12, 1]];
	const gallop = (root: number): [number, number][] => [[root, 0.5], [root + 12, 0.5], [root + 7, 0.5], [root + 12, 0.5]];
	const BASS: [number, number][] = [
		...[48, 48, 48, 44, 43, 51, 44, 41, 43, 43].flatMap(oompah),
		[48, 6],
		...[48, 43, 43, 41, 43, 48, 43, 48, 43, 48].flatMap(gallop),
		...[48, 48, 44, 43, 43].flatMap(oompah),
		[48, 6]
	];
	// Silent through Act I's start, a kick on each bar from the climb, then kick-snare eighths for the can-can.
	const DRUMS: [number, number][] = [
		[0, 15],
		...Array.from({ length: 7 }, (): [number, number] => [1, 3]),
		...Array.from({ length: 20 }, (): [number, number][] => [[1, 0.5], [2, 0.5]]).flat(),
		// Act III: a boom to open it, boom-clap, a boom to close it.
		[3, 1], [2, 1], [1, 1],
		...Array.from({ length: 5 }, (): [number, number][] => [[1, 1], [2, 1], [1, 1]]).flat(),
		[3, 3]
	];
	const SCORE_S = MELODY.reduce((s, [, b]) => s + b, 0) * BEAT; // 30.8

	type Phase = 'overture' | 'aria' | 'climax' | 'finale' | 'cancan' | 'lmao' | 'fried' | 'bravo' | 'outro';
	// When each phase starts, in beats.
	const PHASES: [Phase, number][] = [
		['overture', 0], ['aria', 3], ['climax', 15], ['finale', 27], ['cancan', 36], ['lmao', 48], ['fried', 56], ['bravo', 77]
	];
	const at = (p: Phase) => PHASES.find(([q]) => q === p)![1] * BEAT * 1000;
	const SURTITLES: Record<Phase, string> = {
		overture: 'ATTO I · Il Pib Selvatico',
		aria: '♪ O PIBSIGNO., stella del Johto! ♪',
		climax: '♪ Tutti i Pokémon cantano per te! ♪',
		finale: '♪ PIBSIIIIIGNOOOOO.! ♪',
		cancan: '♪ ATTO II · Il Can-can del Pib ♪',
		lmao: 'AY LMAO! Che vergogna! Ancora! ANCORA!',
		fried: 'ATTO III · Il Pib Fritto 🔥🔥🔥',
		bravo: '🅱️ravo! 🅱️ravissimo! 😂👌',
		outro: ''
	};
	const BRAVO_MS = 4000;
	const OUTRO_MS = 1400; // the curtains opening onto the page
	// In the balconies, from the player's own ROM: Ho-Oh, Suicune, Lugia.
	const LEGENDS = [250, 245, 249];
	// The chorus, by Pokédex number: the singers and the ones who'd try anyway.
	const CHORUS = [39, 25, 113, 202, 143, 175, 131, 122, 54, 94, 183, 241];

	type Ball = { left: number; size: number; delay: number; fall: number; spin: number };
	type Bit = { left: number; top: number; delay: number; fall: number; hue: number; turn: number };
	const bits = (n: number, delay: number): Bit[] =>
		Array.from({ length: n }, () => ({
			left: Math.random() * 100,
			top: 8 + Math.random() * 84,
			delay: Math.random() * delay,
			fall: 2.5 + Math.random() * 2.5,
			hue: Math.random() * 360,
			turn: (Math.random() - 0.5) * 60
		}));
	const act2 = (p: Phase | null) => p === 'cancan' || p === 'lmao' || p === 'fried' || p === 'bravo';
	const act3 = (p: Phase | null) => p === 'fried' || p === 'bravo';

	const ACRONYMS = [
		'AY LMAO', 'LOL', 'L33T', 'ROFL', 'LMFAO', 'OMG', 'WTF', 'BRB', 'YOLO', 'FTW', 'GG EZ', 'PWNED', 'NOOB', '1337',
		'XD', 'AFK', 'NGL', 'TBH', 'FR FR', 'IRL', 'SMH', 'IKR', 'KEK', 'POG', 'OP PLS NERF', 'BASED', 'RATIO', 'W', 'GOAT'
	];
	const EMOJIS = ['😂', '👌', '💯', '🔥', '🅱️', '😤', '🤣', '‼️', '👀', '😩', '🙏', '😳', '🤑', '💀'];
	type Shout = { text: string; left: number; top: number; size: number; turn: number; delay: number; style: number; hue: number };
	/** n of list, each at a random place, angle and size, popping in over `over` seconds. */
	const shouts = (list: string[], n: number, over: number, min: number, max: number): Shout[] =>
		Array.from({ length: n }, (_, i) => ({
			text: i < list.length ? list[i] : list[Math.floor(Math.random() * list.length)],
			left: 6 + Math.random() * 88,
			top: 8 + Math.random() * 78,
			size: min + Math.random() * (max - min),
			turn: (Math.random() - 0.5) * 70,
			delay: Math.random() * over,
			style: Math.floor(Math.random() * 3),
			hue: Math.random() * 360
		})).sort(() => Math.random() - 0.5);

	let phase = $state<Phase | null>(null);
	let opened = $state(false);
	let still = $state(false);
	let balls = $state<Ball[]>([]);
	let confetti = $state<Bit[]>([]);
	let kisses = $state<Bit[]>([]);
	let lols = $state<Shout[]>([]);
	let emojis = $state<Shout[]>([]);
	let gifted = $state(false);
	let seen = konami.at; // coming back to /play doesn't replay the last one
	let stop = () => {};

	$effect(() => {
		if (konami.at === seen) return;
		seen = konami.at;
		still = matchMedia('(prefers-reduced-motion: reduce)').matches;
		phase = 'overture';
		opened = false;
		balls = [];
		confetti = [];
		kisses = [];
		lols = [];
		emojis = [];
		gifted = false;
		api<{ gift: boolean }>('POST', '/me/konami').then((r) => (gifted = r.gift), () => {});
		const quiet = aria(BEAT, MELODY, BASS, DRUMS, at('fried') / 1000);
		const timers = [
			setTimeout(() => (opened = true), 60),
			...PHASES.slice(1).map(([p, beat]) => setTimeout(() => (phase = p), beat * BEAT * 1000)),
			setTimeout(() => !still && (confetti = bits(70, 3)), at('cancan')),
			setTimeout(() => ((kisses = bits(12, 3)), (lols = shouts([...ACRONYMS].sort(() => Math.random() - 0.5), 22, 3, 18, 84))), at('lmao')),
			setTimeout(() => (emojis = shouts(EMOJIS, 36, 6, 28, 120)), at('fried')),
			setTimeout(bravo, SCORE_S * 1000),
			setTimeout(outro, SCORE_S * 1000 + BRAVO_MS),
			setTimeout(end, SCORE_S * 1000 + BRAVO_MS + OUTRO_MS)
		];
		stop = () => {
			timers.forEach(clearTimeout);
			quiet();
		};
		return () => stop();
	});

	function bravo() {
		opened = false;
		gotcha();
		if (still) return;
		balls = Array.from({ length: 30 }, () => ({
			left: Math.random() * 100,
			size: 22 + Math.random() * 26,
			delay: Math.random() * 1.6,
			fall: 2 + Math.random() * 1.6,
			spin: (Math.random() < 0.5 ? -1 : 1) * (180 + Math.random() * 540)
		}));
	}

	/** Everything behind the closed curtains goes, then they open onto the page. */
	function outro() {
		phase = 'outro';
		setTimeout(() => (opened = true), 50);
	}

	function end() {
		stop();
		phase = null;
		balls = [];
		confetti = [];
		kisses = [];
		lols = [];
		emojis = [];
	}
</script>

<svelte:window onkeydown={(e) => phase && e.key === 'Escape' && end()} />

{#if phase}
	<!-- Esc and the Skip button are the keyboard ways out. -->
	<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
	<div class="opera {phase}" class:act2={act2(phase)} class:fried={act3(phase)} class:still style:--beat="{BEAT}s" onclick={end}>
		<div class="swirl" aria-hidden="true"></div>
		<div class="rings" aria-hidden="true"></div>

		<div class="balcony" aria-hidden="true">
			{#each LEGENDS as n (n)}
				<span class="legend">
					<img src="/api/v1/game/icons/{n}.png" alt="" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />
					<span class="tiara">👑</span>
					<span class="lost">👌</span>
				</span>
			{/each}
		</div>

		<div class="stage" aria-hidden="true">
			<div class="orbit outer">
				{#each { length: 12 } as _, i (i)}
					<img src={face} alt="" style:--i={i} style:--n={12} />
				{/each}
			</div>
			<div class="orbit inner">
				{#each { length: 8 } as _, i (i)}
					<img src={face} alt="" style:--i={i} style:--n={8} />
				{/each}
			</div>
			<div class="spotlight"></div>
			<div class="diva">
				<img src={face} alt="" />
				<span class="crown">👑</span>
				<span class="rose">🌹</span>
				<span class="eye left"></span>
				<span class="eye right"></span>
				{#each ['♪', '♫', '♪', '♬', '♫', '♪'] as note, i (i)}
					<span class="note" style:--i={i}>{note}</span>
				{/each}
			</div>
		</div>

		<div class="disco" aria-hidden="true"></div>
		<div class="sparkles" aria-hidden="true"></div>

		<div class="kickline" aria-hidden="true">
			{#each { length: 7 } as _, i (i)}
				<div class="dancer" style:--i={i}>
					<span class="plume"></span>
					<img src={face} alt="" />
					<span class="arm left"></span>
					<span class="arm right"></span>
					<span class="corset"></span>
					<span class="leg left"></span>
					<span class="leg right"></span>
					<span class="skirt"></span>
				</div>
			{/each}
		</div>

		<div class="chorus" aria-hidden="true">
			{#each CHORUS as n, i (n)}
				<span class="singer" style:--i={i}>
					<img src="/api/v1/game/icons/{n}.png" alt="" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />
				</span>
			{/each}
		</div>

		<div class="curtain left" class:opened aria-hidden="true"></div>
		<div class="curtain right" class:opened aria-hidden="true"></div>

		{#each confetti as c, i (i)}
			<span
				class="confetti"
				aria-hidden="true"
				style:left="{c.left}%"
				style:--delay="{c.delay}s"
				style:--fall="{c.fall}s"
				style:--hue={c.hue}
			></span>
		{/each}
		{#each kisses as k, i (i)}
			<span class="kiss" aria-hidden="true" style:left="{k.left}%" style:top="{k.top}%" style:--delay="{k.delay}s" style:--turn="{k.turn}deg">💋</span>
		{/each}
		{#each lols as l, i (i)}
			<span
				class="shout lol s{l.style}"
				aria-hidden="true"
				style:left="{l.left}%"
				style:top="{l.top}%"
				style:font-size="{l.size}px"
				style:--turn="{l.turn}deg"
				style:--delay="{l.delay}s"
				style:--hue={l.hue}>{l.text}</span
			>
		{/each}
		{#each emojis as e, i (i)}
			<span
				class="shout emoji"
				aria-hidden="true"
				style:left="{e.left}%"
				style:top="{e.top}%"
				style:font-size="{e.size}px"
				style:--turn="{e.turn}deg"
				style:--delay="{e.delay}s">{e.text}</span
			>
		{/each}
		{#if phase === 'fried'}
			<p class="meme top" aria-hidden="true">When u enter the Konami code</p>
			<p class="meme bottom" aria-hidden="true">and PIBSIGNO. hits different 😂👌</p>
		{/if}
		<div class="fry" aria-hidden="true"></div>

		{#if phase !== 'outro'}<p class="surtitle" role="status">{SURTITLES[phase]}</p>{/if}

		{#each balls as b, i (i)}
			<span
				class="ball"
				aria-hidden="true"
				style:left="{b.left}%"
				style:--size="{b.size}px"
				style:--delay="{b.delay}s"
				style:--fall="{b.fall}s"
				style:--spin="{b.spin}deg"
			></span>
		{/each}
		{#if phase === 'bravo'}
			<p class="textbox">
				<img src={face} alt="" />
				<span>
					Gotcha! PIBSIGNO. was caught!
					{#if gifted}<br />And he left you a Mystery Gift! 🎁{/if}
				</span>
			</p>
		{/if}

		<button type="button" class="skip" onclick={end}>Skip ✕</button>
	</div>
{/if}

<style>
	.opera {
		position: fixed;
		inset: 0;
		z-index: 60;
		overflow: hidden;
		background: #12001f;
		cursor: pointer;
		animation: fade-in 0.3s ease-out;
	}
	@keyframes fade-in {
		from {
			opacity: 0;
		}
	}

	/* The psychedelic backdrop: a slow rainbow swirl whose hues keep turning, and rings rushing out of the middle.
	   Soft gradients only, nothing flashing. */
	.swirl {
		position: absolute;
		inset: -60%;
		background: repeating-conic-gradient(from 0deg, #ff2d75, #ffb800 30deg, #3dffa8 60deg, #2f7bff 90deg, #c03dff 120deg, #ff2d75 150deg);
		filter: saturate(1.4) blur(2px);
		opacity: 0.75;
		animation:
			spin 12s linear infinite,
			hue 6s linear infinite;
	}
	.climax .swirl,
	.finale .swirl {
		animation-duration: 5s, 2.5s;
	}
	.rings {
		position: absolute;
		inset: 0;
		background: repeating-radial-gradient(circle at 50% 45%, transparent 0 28px, rgb(255 255 255 / 0.22) 30px 34px, transparent 36px 64px);
		animation: rush calc(var(--beat) * 3) linear infinite;
		mix-blend-mode: overlay;
	}
	@keyframes spin {
		to {
			rotate: 1turn;
		}
	}
	@keyframes hue {
		to {
			filter: saturate(1.4) blur(2px) hue-rotate(1turn);
		}
	}
	@keyframes rush {
		from {
			scale: 1;
		}
		to {
			scale: 1.9;
		}
	}

	/* The legendary birds (and Suicune) watch from the balconies, swaying to the music. */
	.balcony {
		position: absolute;
		top: clamp(48px, 9vh, 90px);
		inset-inline: 0;
		display: flex;
		justify-content: space-between;
		padding-inline: 4vw;
		pointer-events: none;
		z-index: 2; /* Suicune in front of the disco ball */
	}
	.legend {
		--w: min(20vmin, 168px);
		position: relative;
		width: var(--w);
		animation: sway calc(var(--beat) * 6) ease-in-out infinite alternate;
	}
	.legend img {
		display: block;
		width: 100%;
		aspect-ratio: 1;
		image-rendering: pixelated;
		filter: drop-shadow(0 0 2px #fff) drop-shadow(0 0 12px rgb(255 255 255 / 0.7));
	}
	/* Ho-Oh on the left turns to face the stage. */
	.legend:first-child img {
		scale: -1 1;
	}
	.legend:nth-child(2) {
		--w: min(15vmin, 120px);
		animation-delay: calc(var(--beat) * -3);
	}
	/* Act II: each legend gets a little crown and flashes the circle game (an upside-down 👌, held low: you lost). */
	.tiara,
	.lost {
		position: absolute;
		line-height: 1;
		opacity: 0;
		scale: 0;
		transition:
			opacity 0.2s,
			scale 0.45s cubic-bezier(0.2, 1.8, 0.5, 1);
		filter: drop-shadow(0 2px 2px rgb(0 0 0 / 0.5));
	}
	.tiara {
		left: 50%;
		top: -6%;
		font-size: calc(var(--w) * 0.24);
		translate: -50% 0;
		rotate: 10deg;
	}
	.lost {
		right: 2%;
		bottom: 4%;
		font-size: calc(var(--w) * 0.26);
		rotate: 180deg;
		transition-delay: 0.3s;
		animation: wiggle calc(var(--beat) * 2) ease-in-out infinite;
	}
	.act2 .tiara,
	.act2 .lost {
		opacity: 1;
		scale: 1;
	}
	@keyframes wiggle {
		50% {
			translate: 0 -6px;
		}
	}
	@keyframes sway {
		from {
			transform: translateY(-6px) rotate(-6deg);
		}
		to {
			transform: translateY(6px) rotate(6deg);
		}
	}

	/* The stage: the diva in the spotlight with two rings of faces orbiting her, each in its own colours. */
	.stage {
		position: absolute;
		left: 50%;
		top: 48%;
		width: 0;
		height: 0;
	}
	.orbit {
		position: absolute;
		inset: 0;
		animation: spin 9s linear infinite;
	}
	.orbit.outer {
		--r: min(44vmin, 380px);
		--s: min(11vmin, 84px);
		animation-direction: reverse;
		animation-duration: 14s;
	}
	.orbit.inner {
		--r: min(29vmin, 240px);
		--s: min(13vmin, 100px);
	}
	.climax .orbit,
	.finale .orbit {
		animation-duration: 3.5s;
	}
	.orbit img {
		position: absolute;
		left: calc(var(--s) / -2);
		top: calc(var(--s) / -2);
		width: var(--s);
		height: var(--s);
		border-radius: 50%;
		object-fit: cover;
		transform: rotate(calc(var(--i) / var(--n) * 1turn)) translateY(calc(var(--r) * -1));
		filter: hue-rotate(calc(var(--i) / var(--n) * 1turn)) saturate(2.2) contrast(1.1);
		box-shadow: 0 0 14px rgb(255 255 255 / 0.6);
		animation: pulse calc(var(--beat) * 2) ease-in-out infinite;
		animation-delay: calc(var(--i) * var(--beat) / -2);
	}
	.finale .orbit img {
		--r: min(36vmin, 300px);
	}
	@keyframes pulse {
		50% {
			scale: 1.25;
		}
	}
	.spotlight {
		position: absolute;
		left: -50vmax;
		top: -50vmax;
		width: 100vmax;
		height: 100vmax;
		background: radial-gradient(circle, rgb(255 250 220 / 0.55) 0 12vmin, transparent 34vmin);
		pointer-events: none;
	}
	.diva {
		--d: min(36vmin, 280px);
		position: absolute;
		left: calc(var(--d) / -2);
		top: calc(var(--d) / -2);
		width: var(--d);
		height: var(--d);
		animation: dance calc(var(--beat) * 2) ease-in-out infinite;
		transition: scale 0.6s cubic-bezier(0.3, 1.6, 0.5, 1);
	}
	.finale .diva {
		scale: 1.35;
	}
	.diva img {
		width: 100%;
		height: 100%;
		border-radius: 50%;
		object-fit: cover;
		box-shadow:
			0 0 0 4px #fff,
			0 0 0 10px #ffd23f,
			0 0 50px 16px rgb(255 80 200 / 0.8);
		animation: glow 3s linear infinite;
	}
	.finale .diva img {
		animation:
			glow 1s linear infinite,
			belt 0.12s linear infinite;
	}
	@keyframes dance {
		0%,
		100% {
			transform: translateY(0) rotate(-10deg) scale(1, 1);
		}
		25% {
			transform: translateY(-18px) rotate(0) scale(0.96, 1.06);
		}
		50% {
			transform: translateY(0) rotate(10deg) scale(1.06, 0.94);
		}
		75% {
			transform: translateY(-18px) rotate(0) scale(0.96, 1.06);
		}
	}
	@keyframes glow {
		to {
			filter: hue-rotate(1turn);
		}
	}
	/* Belting the high C: a little tremble. */
	@keyframes belt {
		25% {
			translate: 2px -1px;
		}
		75% {
			translate: -2px 1px;
		}
	}
	.note {
		position: absolute;
		left: calc(50% + (var(--i) - 2.5) * 14%);
		top: 10%;
		font: 700 clamp(20px, 4vmin, 34px) / 1 serif;
		color: #fff;
		text-shadow:
			0 0 8px #ff3df0,
			0 2px 0 #000;
		opacity: 0;
		animation: rise calc(var(--beat) * 6) ease-out infinite;
		animation-delay: calc(var(--i) * var(--beat));
	}
	@keyframes rise {
		10% {
			opacity: 1;
		}
		to {
			opacity: 0;
			transform: translate(calc((var(--i) - 2.5) * 18px), -32vmin) rotate(calc((var(--i) - 2.5) * 12deg));
		}
	}

	/* The chorus line along the footlights. */
	.chorus {
		position: absolute;
		bottom: 0;
		inset-inline: 0;
		display: flex;
		justify-content: center;
		align-items: flex-end;
		gap: min(1.2vw, 12px);
		padding: 10px 8px max(14px, env(safe-area-inset-bottom));
		background: linear-gradient(transparent, rgb(0 0 0 / 0.55) 40%);
	}
	.singer {
		position: relative;
		width: min(7.2vw, 64px);
		aspect-ratio: 1;
		animation: hop var(--beat) ease-out infinite alternate;
		animation-delay: calc(var(--i) * var(--beat) / 2 * -1);
	}
	.singer img {
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
		filter: drop-shadow(0 0 6px rgb(255 255 255 / 0.7));
	}
	.climax .singer,
	.finale .singer {
		animation-duration: calc(var(--beat) / 2);
	}
	@keyframes hop {
		from {
			transform: translateY(0);
		}
		to {
			transform: translateY(-14px);
		}
	}

	/* Red velvet curtains with a gold fringe: closed, they open at the start and close for the bows. */
	.curtain {
		position: absolute;
		top: 0;
		bottom: 0;
		width: 51%;
		z-index: 2;
		background:
			linear-gradient(transparent calc(100% - 14px), #d9a520 calc(100% - 14px)),
			repeating-linear-gradient(90deg, #5c0013 0, #a5102a 22px, #d3203e 34px, #a5102a 46px, #5c0013 68px);
		box-shadow: inset 0 -30px 40px rgb(0 0 0 / 0.4);
		transition: translate 1.2s cubic-bezier(0.6, 0, 0.3, 1);
	}
	.curtain.left {
		left: 0;
	}
	.curtain.right {
		right: 0;
	}
	.curtain.left.opened {
		translate: -96% 0;
	}
	.curtain.right.opened {
		translate: 96% 0;
	}

	/* Surtitles, as in an opera house: a black bar above the stage. */
	.surtitle {
		position: absolute;
		top: 10px;
		left: 50%;
		translate: -50% 0;
		z-index: 7;
		width: min(92vw, 640px);
		box-sizing: border-box;
		margin: 0;
		padding: 10px 14px;
		text-align: center;
		font: 11px/1.7 var(--pixel-font);
		color: #ffe36b;
		background: rgb(0 0 0 / 0.82);
		border: 2px solid #d9a520;
		border-radius: 4px;
	}

	.skip {
		position: absolute;
		right: max(12px, env(safe-area-inset-right));
		bottom: max(12px, env(safe-area-inset-bottom));
		z-index: 4;
		padding: 6px 10px;
		font: 9px/1 var(--pixel-font);
		color: #fff;
		background: rgb(0 0 0 / 0.6);
		border: 2px solid #fff;
		border-radius: 4px;
		cursor: pointer;
	}
	.skip:focus-visible {
		outline: 3px solid #ffe36b;
		outline-offset: 2px;
	}

	/* Poké Balls thrown on stage like roses. */
	.ball {
		position: absolute;
		z-index: 3;
		top: calc(var(--size) * -1.5);
		width: var(--size);
		height: var(--size);
		border-radius: 50%;
		background:
			radial-gradient(circle, #fff 0 14%, #1d1f1c 15% 24%, transparent 25%),
			linear-gradient(#e3350d 0 45%, #1d1f1c 45% 55%, #f4f4f4 55%);
		box-shadow:
			inset 0 0 0 calc(var(--size) * 0.06) #1d1f1c,
			0 4px 10px rgb(0 0 0 / 0.3);
		animation: fall var(--fall) cubic-bezier(0.45, 0, 0.8, 1) var(--delay) both;
	}
	@keyframes fall {
		from {
			transform: translateY(0) rotate(0);
		}
		to {
			transform: translateY(calc(100svh + var(--size) * 3)) rotate(var(--spin));
		}
	}

	/* The game's dialogue box over the closed curtains. */
	.textbox {
		position: absolute;
		top: 50%;
		left: 50%;
		translate: -50% -50%;
		z-index: 3;
		width: min(92vw, 420px);
		box-sizing: border-box;
		margin: 0;
		padding: 14px 16px;
		font: 12px/1.8 var(--pixel-font);
		color: #1d1f1c;
		background: #fff;
		border: 3px solid #1d1f1c;
		border-radius: 6px;
		box-shadow:
			inset 0 0 0 3px #fff,
			inset 0 0 0 5px #1d1f1c,
			0 8px 24px rgb(0 0 0 / 0.35);
		animation: pop 0.25s ease-out 0.8s both;
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.textbox img {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 50%;
		object-fit: cover;
		box-shadow: 0 0 0 2px #1d1f1c;
	}
	@keyframes pop {
		from {
			opacity: 0;
			transform: translateY(-8px);
		}
	}

	/* Act II: the scandal. */
	.stage {
		transition:
			top 0.8s cubic-bezier(0.3, 1.4, 0.5, 1),
			scale 0.8s cubic-bezier(0.3, 1.4, 0.5, 1);
	}
	.act2 .stage {
		top: 41%;
		scale: 0.75;
	}
	.act2 .swirl {
		animation-duration: 2.5s, 1.2s;
	}
	.act2 .orbit {
		animation-duration: 1.8s;
	}
	.act2 .diva {
		animation-duration: var(--beat);
	}
	/* A crown on his head and a rose between his teeth. */
	.crown,
	.rose {
		position: absolute;
		font-size: calc(var(--d) * 0.3);
		line-height: 1;
		opacity: 0;
		scale: 3;
		transition:
			opacity 0.3s,
			scale 0.5s cubic-bezier(0.3, 1.6, 0.5, 1);
		filter: drop-shadow(0 3px 4px rgb(0 0 0 / 0.5));
	}
	.crown {
		left: 50%;
		top: -24%;
		translate: -50% 0;
		rotate: -12deg;
	}
	.rose {
		left: 44%;
		top: 70%;
		rotate: -40deg;
	}
	.act2 .crown,
	.act2 .rose {
		opacity: 1;
		scale: 1;
	}
	.act2 .rose {
		transition-delay: 0.4s;
	}

	/* The disco ball comes down on its string; its light specks sweep the room. */
	.disco {
		--b: min(11vmin, 84px);
		position: absolute;
		left: 50%;
		top: calc(var(--b) * -1.6);
		width: var(--b);
		height: var(--b);
		margin-left: calc(var(--b) / -2);
		z-index: 1;
		border-radius: 50%;
		background:
			radial-gradient(circle at 32% 30%, rgb(255 255 255 / 0.95), transparent 32%),
			repeating-linear-gradient(0deg, rgb(40 40 60 / 0.6) 0 1px, transparent 1px 12.5%),
			repeating-linear-gradient(90deg, rgb(40 40 60 / 0.6) 0 1px, transparent 1px 12.5%),
			radial-gradient(circle at 40% 40%, #f4f4ff, #9a9ab0 60%, #4a4a60);
		background-size: 100%, 100%, 200% 100%, 100%;
		box-shadow: 0 0 40px 10px rgb(255 255 255 / 0.45);
		transition: top 1.2s cubic-bezier(0.3, 1.3, 0.5, 1);
		animation: mirror 1s linear infinite;
	}
	.disco::before {
		content: '';
		position: absolute;
		left: 50%;
		bottom: 100%;
		width: 2px;
		height: 100vh;
		background: #ccc;
	}
	.act2 .disco {
		top: 58px;
	}
	@keyframes mirror {
		to {
			background-position: 0 0, 0 0, 200% 0, 0 0;
		}
	}
	.sparkles {
		position: absolute;
		inset: -50%;
		background:
			radial-gradient(circle, #fff 0 2px, transparent 3px) 0 0 / 90px 70px,
			radial-gradient(circle, #ffe4fb 0 1.5px, transparent 2.5px) 40px 30px / 120px 90px;
		opacity: 0;
		transition: opacity 0.8s;
		animation: spin 8s linear infinite;
		pointer-events: none;
	}
	.act2 .sparkles {
		opacity: 0.55;
	}

	/* The kick line: his faces in frilly skirts and fishnets, kicking on the beat, alternate legs. */
	.kickline {
		--f: min(9vmin, 72px);
		position: absolute;
		left: 50%;
		bottom: calc(min(7.2vw, 64px) + 34px);
		display: flex;
		gap: min(2vmin, 18px);
		translate: -50% 120%;
		opacity: 0;
		transition:
			translate 0.7s cubic-bezier(0.3, 1.4, 0.5, 1),
			opacity 0.3s;
	}
	.act2 .kickline {
		translate: -50% 0;
		opacity: 1;
	}
	.dancer {
		position: relative;
		width: calc(var(--f) * 1.3);
		height: calc(var(--f) * 3.2);
		animation: hop var(--beat) ease-out infinite alternate;
		--skin: #f2c49b;
		--frill: hsl(calc(330 + var(--i) * 40) 95% 60%);
	}
	/* The whole line leans one way, then the other, every two bars. */
	.act2 .kickline {
		animation: lean calc(var(--beat) * 4) ease-in-out infinite alternate;
	}
	@keyframes lean {
		from {
			rotate: -3deg;
		}
		to {
			rotate: 3deg;
		}
	}
	.dancer > * {
		position: absolute;
	}
	.dancer img {
		left: calc(var(--f) * 0.15);
		top: 0;
		width: var(--f);
		height: var(--f);
		border-radius: 50%;
		object-fit: cover;
		box-shadow: 0 0 0 2px #fff;
		filter: hue-rotate(calc(var(--i) * 50deg)) saturate(1.8);
		z-index: 3;
	}
	/* An ostrich plume over the head, bobbing. */
	.plume {
		left: calc(var(--f) * 0.55);
		top: calc(var(--f) * -0.55);
		width: calc(var(--f) * 0.32);
		height: calc(var(--f) * 0.8);
		border-radius: 50% 50% 40% 40% / 70% 70% 30% 30%;
		background: radial-gradient(ellipse at 50% 30%, #fff 0 15%, var(--frill) 60%);
		box-shadow: 0 0 10px var(--frill);
		transform-origin: 50% 100%;
		animation: flounce calc(var(--beat) * 2) ease-in-out infinite;
		z-index: 2;
	}
	/* A laced corset under the chin. */
	.corset {
		left: calc(var(--f) * 0.4);
		top: calc(var(--f) * 0.92);
		width: calc(var(--f) * 0.5);
		height: calc(var(--f) * 0.55);
		border-radius: 35% 35% 15% 15%;
		background:
			repeating-linear-gradient(0deg, transparent 0 4px, #fff 4px 5px) 50% 0 / 30% 100% no-repeat,
			linear-gradient(90deg, #111, var(--frill) 30% 70%, #111);
		z-index: 4;
	}
	/* Arms out to the neighbours' shoulders, swinging with the kicks. */
	.arm {
		top: calc(var(--f) * 1);
		width: calc(var(--f) * 0.62);
		height: calc(var(--f) * 0.11);
		border-radius: 99px;
		background: var(--skin);
		box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.2);
		animation: swing calc(var(--beat) * 2) ease-in-out infinite;
		z-index: 3;
	}
	.arm.left {
		right: calc(var(--f) * 0.82);
		transform-origin: 100% 50%;
		--way: 1;
	}
	.arm.right {
		left: calc(var(--f) * 0.82);
		transform-origin: 0 50%;
		--way: -1;
	}
	@keyframes swing {
		0%,
		100% {
			rotate: calc(var(--way) * 12deg);
		}
		50% {
			rotate: calc(var(--way) * -18deg);
		}
	}
	/* The petticoat: layers of frills that flip up on every kick. */
	.skirt {
		left: 0;
		top: calc(var(--f) * 1.35);
		width: 100%;
		height: calc(var(--f) * 0.55);
		border-radius: 45% 45% 50% 50% / 30% 30% 70% 70%;
		background:
			radial-gradient(circle at 50% 100%, #fff 0 22%, transparent 23%) 0 100% / 20% 45% repeat-x,
			radial-gradient(circle at 50% 100%, var(--frill) 0 22%, transparent 23%) 10% 70% / 20% 45% repeat-x,
			repeating-linear-gradient(90deg, var(--frill) 0 7px, #fff 7px 10px);
		box-shadow: 0 4px 0 -1px #fff;
		transform-origin: 50% 0;
		animation: flounce var(--beat) ease-in-out infinite;
		z-index: 5;
	}
	@keyframes flounce {
		50% {
			rotate: 8deg;
			scale: 1.12 0.85;
		}
	}
	/* Fishnets with a red garter and red shoes; a high kick each beat, alternating legs, rippling down the line. */
	.leg {
		top: calc(var(--f) * 1.65);
		width: calc(var(--f) * 0.17);
		height: calc(var(--f) * 1.4);
		transform-origin: 50% 0;
		background:
			linear-gradient(transparent 14%, #e3001b 14% 22%, transparent 22%),
			repeating-linear-gradient(45deg, transparent 0 3px, #111 3px 4px),
			repeating-linear-gradient(-45deg, transparent 0 3px, #111 3px 4px),
			var(--skin);
		border-bottom: calc(var(--f) * 0.14) solid #e3001b;
		border-radius: 40% 40% 2px 2px;
		animation: kick calc(var(--beat) * 2) cubic-bezier(0.5, 0, 0.3, 1) infinite;
		animation-delay: calc(var(--i) * -0.05s);
		z-index: 1;
	}
	.leg.left {
		left: 33%;
		--way: 1;
	}
	.leg.right {
		left: 53%;
		--way: -1;
		animation-delay: calc(var(--beat) * -1 + var(--i) * -0.05s);
	}
	@keyframes kick {
		0%,
		50%,
		100% {
			rotate: 0deg;
		}
		25% {
			rotate: calc(var(--way) * 168deg);
		}
	}

	.confetti {
		position: absolute;
		top: -20px;
		z-index: 1;
		width: 8px;
		height: 14px;
		background: hsl(var(--hue) 95% 60%);
		animation:
			drop var(--fall) linear var(--delay) infinite,
			flutter 0.6s ease-in-out infinite alternate;
	}
	@keyframes drop {
		to {
			translate: 0 calc(100svh + 40px);
		}
	}
	@keyframes flutter {
		from {
			transform: rotate(-30deg) rotateY(0);
		}
		to {
			transform: rotate(30deg) rotateY(180deg);
		}
	}

	/* Lipstick kisses slammed onto the screen. */
	.kiss {
		position: absolute;
		z-index: 3;
		font-size: clamp(30px, 8vmin, 64px);
		rotate: var(--turn);
		translate: -50% -50%;
		animation: smooch 0.35s cubic-bezier(0.2, 1.6, 0.5, 1) var(--delay) both;
		pointer-events: none;
	}
	@keyframes smooch {
		from {
			opacity: 0;
			scale: 3.5;
		}
	}

	/* Internet acronyms and emoji, popping in at random places, angles and sizes, then wobbling. */
	.shout {
		position: absolute;
		z-index: 3;
		line-height: 1;
		white-space: nowrap;
		translate: -50% -50%;
		rotate: var(--turn);
		pointer-events: none;
		animation:
			slam 0.35s cubic-bezier(0.2, 1.6, 0.5, 1) var(--delay) both,
			wobble calc(var(--beat) * 2) ease-in-out calc(var(--delay) + 0.35s) infinite;
	}
	/* Three looks: the meme caption, the Comic Sans rainbow, the pixel shout. */
	.lol.s0 {
		font-family: Impact, 'Anton', 'Arial Black', sans-serif;
		color: #fff;
		-webkit-text-stroke: 0.06em #000;
		paint-order: stroke fill;
		text-transform: uppercase;
	}
	.lol.s1 {
		font-family: 'Comic Sans MS', 'Comic Neue', cursive;
		font-weight: 700;
		color: hsl(var(--hue) 100% 60%);
		text-shadow:
			0.05em 0.05em 0 hsl(calc(var(--hue) + 180) 100% 45%),
			0 0 0.3em #fff;
	}
	.lol.s2 {
		font-family: var(--pixel-font);
		font-size: 0.6em;
		color: hsl(var(--hue) 100% 70%);
		text-shadow: 0.08em 0.08em 0 #000;
	}
	.emoji {
		z-index: 4;
		animation:
			slam 0.3s cubic-bezier(0.2, 1.6, 0.5, 1) var(--delay) both,
			bulge calc(var(--beat) * 2) steps(2) calc(var(--delay) + 0.3s) infinite;
	}
	@keyframes slam {
		from {
			opacity: 0;
			scale: 4;
		}
	}
	@keyframes wobble {
		50% {
			rotate: calc(var(--turn) * -0.4);
			scale: 1.12;
		}
	}
	@keyframes bulge {
		50% {
			scale: 1.35;
		}
	}

	/* Act III: deep fried. Colours blown out, a crunchy noise overlay, a burnt vignette, the stage jerking in and out. */
	.fried {
		filter: saturate(4.5) contrast(1.9) brightness(1.08);
	}
	.fry {
		position: absolute;
		inset: 0;
		z-index: 6;
		pointer-events: none;
		opacity: 0;
		background:
			radial-gradient(circle, transparent 35%, rgb(255 70 0 / 0.55)),
			url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='1.1' numOctaves='2'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
		mix-blend-mode: hard-light;
	}
	.fried .fry {
		opacity: 0.55;
	}
	/* The diva comes back to the middle, over the emoji, so her laser eyes show. */
	.fried .stage {
		z-index: 4;
		top: 44%;
		scale: 1;
		animation: jerk calc(var(--beat) * 2) steps(1) infinite;
	}
	@keyframes jerk {
		0% {
			transform: scale(1);
		}
		25% {
			transform: scale(1.18) rotate(-4deg);
		}
		50% {
			transform: scale(0.94) rotate(3deg);
		}
		75% {
			transform: scale(1.1) rotate(0);
		}
	}
	/* Laser eyes: a lens flare on each lens of his sunglasses. */
	.eye {
		position: absolute;
		top: 41%;
		width: 34%;
		aspect-ratio: 1;
		translate: -50% -50%;
		border-radius: 50%;
		background: radial-gradient(circle, #fff 0 7%, #ffe000 11%, #ff2a00 20%, rgb(255 0 0 / 0.45) 34%, transparent 62%);
		opacity: 0;
		scale: 0;
		transition:
			opacity 0.2s,
			scale 0.4s cubic-bezier(0.2, 1.8, 0.5, 1);
		animation: flare calc(var(--beat)) ease-in-out infinite alternate;
		z-index: 2;
		pointer-events: none;
	}
	.eye::before {
		content: '';
		position: absolute;
		left: -150%;
		top: 47%;
		width: 400%;
		height: 6%;
		background: linear-gradient(90deg, transparent, #fff 45% 55%, transparent);
	}
	.eye.left {
		left: 37%;
	}
	.eye.right {
		left: 64%;
	}
	.fried .eye {
		opacity: 1;
		scale: 1;
	}
	@keyframes flare {
		to {
			filter: brightness(1.4);
			rotate: 20deg;
		}
	}
	/* Curtain call: the curtains close over all of it; the balls and the textbox land in front. */
	.bravo .curtain {
		z-index: 5;
	}
	.bravo .ball {
		z-index: 5;
	}
	.bravo .textbox {
		z-index: 7;
	}

	/* The curtains are all that is left; with them open, the page shows through as it was. */
	.outro {
		background: transparent;
		pointer-events: none;
	}
	.outro > :not(.curtain) {
		display: none;
	}

	/* Meme captions, top and bottom. */
	.meme {
		position: absolute;
		left: 50%;
		z-index: 5;
		margin: 0;
		width: min(96vw, 900px);
		translate: -50% 0;
		text-align: center;
		font: clamp(22px, 6vmin, 58px) / 1.05 Impact, 'Anton', 'Arial Black', sans-serif;
		text-transform: uppercase;
		color: #fff;
		-webkit-text-stroke: 0.07em #000;
		paint-order: stroke fill;
		animation: slam 0.3s cubic-bezier(0.2, 1.6, 0.5, 1) both;
	}
	.meme.top {
		top: 62px;
	}
	.meme.bottom {
		bottom: calc(min(7.2vw, 64px) + 30px);
		animation-delay: 0.6s;
	}

	/* Reduced motion: the stage stands still. */
	.still,
	.still * {
		animation: none !important;
		transition: none !important;
	}
</style>
