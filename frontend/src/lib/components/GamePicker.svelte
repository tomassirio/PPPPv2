<!-- First run: the player picks their cartridge, once. PUT /me/game refuses a second, different pick. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { byPickerOrder, color, cover, cry, titleScreen, type Cartridge } from '#lib/cartridges.ts';
	import { reveal } from '#lib/theme.svelte.ts';
	import { flushSync } from 'svelte';

	let { available, onpicked }: { available: Cartridge[]; onpicked: (game: string) => void } = $props();

	let choice = $state<string | null>(null);
	let saving = $state(false);
	let error = $state('');

	const chosen = $derived(available.find((c) => c.id === choice));
	const ordered = $derived(byPickerOrder(available));

	/** Picking (not confirming) spreads the version's colour from the cartridge, like the theme switch. */
	function pick(id: string, el: Element) {
		if (choice === id) return;
		reveal(() => flushSync(() => (choice = id)), el);
	}

	const VOLUME = 0.25, FADE = 0.5;
	/** The sign-in page's music switch (TitleMusic) turned off: the cries keep quiet too. */
	function muted() {
		try {
			return localStorage.getItem('pppp.titleMusic') === 'off';
		} catch {
			return false;
		}
	}

	async function start() {
		if (!choice || saving) return;
		saving = true;
		const sound = chosen && cry(chosen);
		if (sound && !muted()) {
			// The click is the gesture that lets it play; it keeps playing after the picker unmounts, fading out over its
			// last FADE seconds.
			const a = new Audio(sound);
			a.volume = VOLUME;
			const fade = () => {
				const left = a.duration - a.currentTime;
				if (left < FADE) a.volume = Math.max(0, VOLUME * (left / FADE));
				if (!a.paused && !a.ended) requestAnimationFrame(fade);
			};
			a.play().then(() => requestAnimationFrame(fade), () => {});
		}
		error = '';
		try {
			const res = await api<{ game: string }>('PUT', '/me/game', { cartridgeId: choice });
			onpicked(res.game);
		} catch (e) {
			error = e instanceof ApiError ? e.detail || 'Could not start that game.' : 'Network error, try again.';
			saving = false;
		}
	}
</script>

<section class="pick" class:chosen={chosen} aria-labelledby="pick-title" style:--chosen={chosen ? (color(chosen) ?? 'var(--accent)') : 'var(--accent)'}>
	{#if chosen && titleScreen(chosen)}
		{@const loop = titleScreen(chosen)}
		<div class="backdrop" aria-hidden="true">
			<img class="fill" src={loop} alt="" />
			<img class="screen" src={loop} alt="" />
		</div>
	{/if}
	<h1 id="pick-title" class="pixel">Pick your game</h1>
	{#if available.length}
		<div class="lead" role="note">
			<svg class="lead-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
				><path d="M4 8h13l-3-3M20 16H7l3 3" /></svg
			>
			<p><b>They all link up.</b> Gold, Silver and Crystal trade and battle with each other. Each has Pokémon the others don't: pick yours and trade for the rest.</p>
		</div>
		<div class="carts" class:has-choice={choice !== null} role="radiogroup" aria-labelledby="pick-title">
			{#each ordered as c (c.id)}
				{@const art = cover(c)}
				<button
					type="button"
					class="cart-card"
					role="radio"
					aria-checked={choice === c.id}
					style:--game={color(c) ?? 'var(--border-strong)'}
					onclick={(e) => pick(c.id, e.currentTarget)}
				>
					<span class="shell" class:clear={c.title === 'PM_CRYSTAL'} aria-hidden="true">
						<span class="grip"></span>
						<span class="label-art">
							{#if art}
								<img src={art} alt="" width="360" height="360" />
							{:else}
								<span class="blank pixel">{c.title || c.id}</span>
							{/if}
						</span>
						<span class="arrow"></span>
						<span class="check">
							<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
						</span>
					</span>
					<span class="label">
						<b>{c.name}</b>
						<span class="system">{c.cgb ? 'Game Boy Color' : 'Game Boy'}</span>
					</span>
				</button>
			{/each}
		</div>
		<div class="go">
			<button type="button" class="start" disabled={!choice || saving} onclick={start}>
				{chosen ? `Start ${chosen.name}` : 'Choose a game'}
			</button>
			<div class="note" role="note">
				<svg class="note-icon" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
					><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
				>
				<p><b>You pick once.</b> You can't change games yourself: only an admin can reset yours, and that deletes your save and your score.</p>
			</div>
			{#if error}<p class="error" role="alert">{error}</p>{/if}
		</div>
	{:else}
		<p class="lead">No games yet. Ask the admin to upload the cartridges, then reload this page.</p>
	{/if}
</section>

<style>
	.pick {
		max-width: 1000px;
		margin: 0 auto;
		padding: 32px 16px 56px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 32px;
		text-align: center;
		overflow-x: clip;
	}
	h1 {
		margin: 0;
		font-size: 18px;
		line-height: 1.7;
		text-wrap: balance;
	}
	/* The intro, as a callout like the one-pick warning (same shape, neutral colour). */
	.lead {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		box-sizing: border-box;
		width: 100%;
		max-width: 520px;
		margin: -12px 0 0;
		padding: 12px 14px;
		border: 1px solid var(--border);
		border-radius: 12px;
		background: color-mix(in srgb, var(--panel) 88%, transparent);
		backdrop-filter: blur(6px);
		text-align: left;
	}
	.lead-icon {
		flex: none;
		margin-top: 1px;
		color: var(--link);
	}
	.lead p {
		margin: 0;
		font-size: 14px;
		line-height: 20px;
		color: var(--muted);
	}
	.lead b {
		color: var(--text);
	}
	.carts {
		display: grid;
		grid-template-columns: repeat(auto-fit, 210px);
		justify-content: center;
		gap: 40px;
		width: 100%;
	}
	/* The chosen game's title screen plays behind everything: sharp in the middle, a blurred copy filling the
	   sides, dimmed so the text stays readable. Swapped inside the circle reveal, so it spreads from the cartridge. */
	.backdrop {
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
		background: var(--bg);
	}
	.backdrop img {
		position: absolute;
		image-rendering: pixelated;
	}
	/* The sides: the same loop stretched to the full width, so each band (sky, clouds, sea, logo) carries on
	   sideways at its own height, then blurred. */
	.backdrop .fill {
		inset: -60px;
		width: calc(100% + 120px);
		height: calc(100% + 120px);
		object-fit: fill;
		filter: blur(40px) saturate(1.1);
	}
	.backdrop .screen {
		left: 50%;
		top: 50%;
		height: 100%;
		width: auto;
		aspect-ratio: 160 / 144;
		transform: translate(-50%, -50%);
		/* Feathered sides: the sharp screen dissolves into the stretched blur instead of ending in an edge. */
		mask-image: linear-gradient(to right, transparent, black 14%, black 86%, transparent);
	}
	.backdrop::after {
		content: '';
		position: absolute;
		inset: 0;
		background: radial-gradient(ellipse at 50% 45%, color-mix(in srgb, var(--bg) 70%, transparent), color-mix(in srgb, var(--bg) 85%, transparent));
	}
	.pick > :not(.backdrop) {
		position: relative;
		z-index: 1;
	}
	/* Portrait (phones): the sharp screen fits the width (the height would make it ~2.5x wider than the phone), the
	   blur fills above and below, and the screen's top and bottom feather into it, as its sides do on desktop. */
	@media (orientation: portrait) and (max-width: 760px) {
		.backdrop .fill {
			object-fit: cover;
		}
		/* The sharp title screen fits the width, centred behind the cartridges (their names sit on frosted pills),
		   and feathers into the blur above and below. */
		.backdrop .screen {
			width: 100%;
			height: auto;
			top: 52%;
			mask-image: linear-gradient(to bottom, transparent, black 12%, black 88%, transparent);
		}
	}
	/* The chosen version's colour glows behind the cartridges; it arrives with the circle reveal. */
	.carts {
		position: relative;
		isolation: isolate;
	}
	.carts::before {
		content: '';
		position: absolute;
		inset: -140px 0;
		z-index: -1;
		pointer-events: none;
		background: radial-gradient(closest-side, color-mix(in srgb, var(--chosen) 28%, transparent), transparent);
		opacity: 0;
	}
	.pick.chosen .carts::before {
		opacity: 1;
	}
	.cart-card {
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 14px;
		padding: 0;
		background: none;
		border: none;
		color: var(--text);
		text-align: center;
		cursor: pointer;
		transition: opacity 120ms ease;
	}
	/* A Game Boy cartridge: clipped top-right corner, grip ridges, recessed label, the insert arrow. */
	.shell {
		--plastic: var(--game);
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		box-sizing: border-box;
		width: 210px;
		max-width: 100%;
		aspect-ratio: 57 / 65;
		padding: 0 10%;
		border-radius: 6px 0 12px 12px;
		background:
			linear-gradient(160deg, rgb(255 255 255 / 0.28), transparent 38%),
			linear-gradient(to bottom, color-mix(in srgb, var(--plastic) 92%, white), color-mix(in srgb, var(--plastic) 78%, black));
		clip-path: polygon(0 0, 90% 0, 100% 8%, 100% 100%, 0 100%);
		box-shadow:
			inset 0 -6px 0 rgb(0 0 0 / 0.18),
			inset 3px 0 0 rgb(255 255 255 / 0.18);
		transition:
			transform 160ms ease,
			filter 160ms ease;
	}
	/* Crystal's plastic is see-through: lighter, with the board's green faintly behind it. */
	.shell.clear {
		background:
			linear-gradient(160deg, rgb(255 255 255 / 0.45), transparent 40%),
			linear-gradient(to bottom, rgb(40 120 90 / 0.25), transparent 30%, rgb(40 120 90 / 0.2) 70%, transparent),
			linear-gradient(to bottom, color-mix(in srgb, var(--plastic) 70%, white), color-mix(in srgb, var(--plastic) 85%, black));
	}
	.grip {
		width: 100%;
		flex: none;
		height: 20px;
		margin: 9px 0 7px;
		background: repeating-linear-gradient(to bottom, rgb(0 0 0 / 0.16) 0 2px, transparent 2px 6px);
		border-radius: 2px;
	}
	.label-art {
		box-sizing: border-box;
		flex: none;
		display: block;
		width: 100%;
		aspect-ratio: 1;
		padding: 4px;
		border-radius: 6px;
		background: color-mix(in srgb, var(--plastic) 70%, black);
		box-shadow: inset 0 2px 4px rgb(0 0 0 / 0.35);
	}
	.label-art img,
	.blank {
		display: block;
		width: 100%;
		height: 100%;
		object-fit: cover;
		border-radius: 3px;
	}
	.arrow {
		margin-top: auto;
		margin-bottom: 7%;
		width: 0;
		height: 0;
		border-left: 9px solid transparent;
		border-right: 9px solid transparent;
		border-top: 9px solid rgb(0 0 0 / 0.22);
	}
	/* Hover: the cartridge jiggles once, like it's being picked up, then rests tilted. */
	.cart-card:hover .shell {
		transform: translateY(-4px) rotate(-2deg);
		animation: jiggle 420ms ease;
	}
	@keyframes jiggle {
		0% { transform: translateY(0) rotate(0); }
		30% { transform: translateY(-6px) rotate(3deg); }
		60% { transform: translateY(-5px) rotate(-3.5deg); }
		100% { transform: translateY(-4px) rotate(-2deg); }
	}
	.cart-card[aria-checked='true'] .shell {
		transform: translateY(-6px) scale(1.03);
		filter: drop-shadow(0 0 14px color-mix(in srgb, var(--game) 70%, transparent));
	}
	.has-choice .cart-card[aria-checked='false'] {
		opacity: 0.55;
	}
	.has-choice .cart-card[aria-checked='false']:hover {
		opacity: 0.9;
	}
	.cart-card:focus-visible,
	.start:focus-visible {
		outline: 3px solid var(--text);
		outline-offset: 6px;
		border-radius: 12px;
	}
	.blank {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 12px;
		font-size: 11px;
		line-height: 1.6;
		text-align: center;
		overflow-wrap: anywhere;
		color: var(--muted);
		background: var(--panel);
	}
	.label {
		display: flex;
		flex-direction: column;
		gap: 4px;
		align-items: center;
	}
	.label b {
		font-size: 17px;
	}
	.system {
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.check {
		position: absolute;
		top: 2%;
		left: 50%;
		margin-left: -15px;
		width: 30px;
		height: 30px;
		border-radius: 50%;
		background: var(--game);
		color: #1d1f1c;
		display: flex;
		align-items: center;
		justify-content: center;
		box-shadow: 0 0 0 3px color-mix(in srgb, var(--game) 60%, black);
		opacity: 0;
		transform: scale(0.6);
		transition:
			opacity 120ms ease,
			transform 120ms ease;
	}
	.cart-card[aria-checked='true'] .check {
		opacity: 1;
		transform: scale(1);
	}
	.go {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12px;
	}
	.start {
		height: 52px;
		padding: 0 32px;
		border-radius: 10px;
		border: none;
		background: var(--chosen);
		color: #1d1f1c;
		font-weight: 700;
		font-size: 15px;
		cursor: pointer;
		transition: background 120ms ease;
	}
	.start:disabled {
		background: var(--accent);
		opacity: 0.5;
		cursor: default;
	}
	.error {
		margin: 0;
		max-width: 48ch;
		font-size: 13px;
		line-height: 19px;
		text-wrap: balance;
	}
	/* The one-pick warning: a callout with a lock, in the chosen game's colour once there is one. */
	.note {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		box-sizing: border-box;
		width: 100%;
		max-width: 460px;
		padding: 12px 14px;
		border: 1px solid color-mix(in srgb, var(--chosen) 55%, var(--border));
		border-radius: 12px;
		background: color-mix(in srgb, var(--chosen) 10%, var(--panel));
		text-align: left;
	}
	.note-icon {
		flex: none;
		margin-top: 1px;
		color: color-mix(in srgb, var(--chosen) 70%, var(--text));
	}
	.note p {
		margin: 0;
		font-size: 13px;
		line-height: 19px;
		color: var(--muted);
	}
	.note b {
		color: var(--text);
	}
	.error {
		color: var(--danger);
	}
	/* Phones: the three cartridges side by side in one row, smaller, and a full-width Start: picking needs no
	   scrolling. */
	@media (max-width: 760px) {
		/* One screen tall: Start and the warning sit at the bottom, where thumbs are. */
		.pick {
			box-sizing: border-box;
			min-height: calc(100svh - 57px);
			padding: 20px 16px calc(16px + env(safe-area-inset-bottom));
			gap: 18px;
		}
		.lead {
			margin-top: -8px;
			max-width: 420px;
		}
		.lead p {
			font-size: 13px;
			line-height: 19px;
		}
		/* A triangle: Gold and Silver on top, Crystal centred under them (picker order is Gold, Crystal, Silver). */
		.carts {
			grid-template-columns: repeat(2, minmax(0, 1fr));
			gap: 14px 18px;
			/* As wide as the height left allows (the triangle is ~1.14x its width tall plus the names), at most 340px. */
			max-width: min(300px, calc((100svh - 495px) * 0.88));
			min-width: 160px;
		}
		/* Short phones: a smaller triangle (and a shorter intro), so the page still fits one screen. */
		@media (max-height: 760px) {
			.carts {
				gap: 8px 12px;
			}
			.pick {
				gap: 12px;
				padding-top: 12px;
			}
			.lead p {
				font-size: 12px;
				line-height: 17px;
			}
			.label {
				margin-top: 2px;
			}
			.label b {
				font-size: 12px;
			}
		}
		/* The triangle sits centred in the room between the intro and Start. */
		.carts {
			margin: auto 0;
		}
		.cart-card:nth-child(2) {
			grid-column: 1 / -1;
			grid-row: 2;
			justify-self: center;
			width: calc(50% - 9px);
		}
		.cart-card:focus-visible {
			outline-color: var(--game);
		}
		.cart-card {
			min-width: 0;
		}
		.shell {
			width: 100%;
			padding: 0 8%;
		}
		.grip {
			height: 9px;
			margin: 6px 0 5px;
			background: repeating-linear-gradient(to bottom, rgb(0 0 0 / 0.16) 0 1px, transparent 1px 3px);
		}
		.label-art {
			padding: 2px;
		}
		.arrow {
			border-left-width: 6px;
			border-right-width: 6px;
			border-top-width: 6px;
		}
		.check {
			width: 24px;
			height: 24px;
			margin-left: -12px;
		}
		.label {
			margin-top: 8px;
		}
		.label b {
			padding: 3px 10px;
			border-radius: 999px;
			background: color-mix(in srgb, var(--panel) 82%, transparent);
			backdrop-filter: blur(6px);
			font-size: 13px;
			line-height: 17px;
		}
		.system {
			display: none;
		}
		.go {
			width: 100%;
			max-width: 420px;
		}
		.start {
			width: 100%;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.cart-card,
		.shell,
		.check,
		.start {
			transition: none;
		}
		.cart-card:hover .shell,
		.cart-card[aria-checked='true'] .shell {
			transform: none;
			animation: none;
		}
	}
</style>
