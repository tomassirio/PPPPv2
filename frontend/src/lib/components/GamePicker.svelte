<!-- First run: the player picks their cartridge, once. PUT /me/game refuses a second, different pick. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { color, cover, type Cartridge } from '#lib/cartridges.ts';
	import { reveal } from '#lib/theme.svelte.ts';
	import { flushSync } from 'svelte';

	let { available, onpicked }: { available: Cartridge[]; onpicked: (game: string) => void } = $props();

	let choice = $state<string | null>(null);
	let saving = $state(false);
	let error = $state('');

	const chosen = $derived(available.find((c) => c.id === choice));

	/** Picking (not confirming) spreads the version's colour from the cartridge, like the theme switch. */
	function pick(id: string, el: Element) {
		if (choice === id) return;
		reveal(() => flushSync(() => (choice = id)), el);
	}

	async function start() {
		if (!choice || saving) return;
		saving = true;
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
	<h1 id="pick-title" class="pixel">Pick your game</h1>
	{#if available.length}
		<p class="lead">Pokémon Gold, Silver and Crystal trade and battle with each other. Each has Pokémon the others don't, so pick yours and trade for the rest.</p>
		<div class="carts" class:has-choice={choice !== null} role="radiogroup" aria-labelledby="pick-title">
			{#each available as c (c.id)}
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
			<p class="note">You pick once and can't change it yourself. Only an admin can reset your game, and a reset deletes your save and your score.</p>
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
		padding: 56px 16px;
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
	.lead {
		margin: -12px 0 0;
		max-width: 58ch;
		color: var(--muted);
		line-height: 22px;
		text-wrap: balance;
	}
	.carts {
		display: grid;
		grid-template-columns: repeat(auto-fit, 210px);
		justify-content: center;
		gap: 40px;
		width: 100%;
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
	.note,
	.error {
		margin: 0;
		max-width: 48ch;
		font-size: 13px;
		line-height: 19px;
		color: var(--muted);
		text-wrap: balance;
	}
	.error {
		color: var(--danger);
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
