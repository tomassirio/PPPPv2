<!-- First run: the player picks their cartridge, once. PUT /me/game refuses a second, different pick. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { color, cover, type Cartridge } from '#lib/cartridges.ts';

	let { available, onpicked }: { available: Cartridge[]; onpicked: (game: string) => void } = $props();

	let choice = $state<string | null>(null);
	let saving = $state(false);
	let error = $state('');

	const chosen = $derived(available.find((c) => c.id === choice));

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

<section class="pick" aria-labelledby="pick-title" style:--chosen={chosen ? (color(chosen) ?? 'var(--accent)') : 'var(--accent)'}>
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
					onclick={() => (choice = c.id)}
				>
					<span class="check" aria-hidden="true">
						<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
					</span>
					{#if art}
						<img class="art" src={art} alt="" width="360" height="360" />
					{:else}
						<span class="art blank pixel" aria-hidden="true">{c.title || c.id}</span>
					{/if}
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
		grid-template-columns: repeat(auto-fit, minmax(220px, 280px));
		justify-content: center;
		gap: 24px;
		width: 100%;
	}
	.cart-card {
		--ring: color-mix(in srgb, var(--game) 32%, transparent);
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 12px 12px 16px;
		background: var(--panel);
		border: 3px solid var(--game);
		border-radius: 16px;
		color: var(--text);
		text-align: left;
		cursor: pointer;
		transition:
			transform 120ms ease,
			box-shadow 120ms ease,
			opacity 120ms ease;
	}
	.cart-card:hover {
		transform: translateY(-3px);
		box-shadow: 0 10px 24px -12px var(--game);
	}
	.cart-card[aria-checked='true'] {
		background: color-mix(in srgb, var(--game) 12%, var(--panel));
		box-shadow:
			0 0 0 5px var(--ring),
			0 14px 30px -14px var(--game);
		transform: translateY(-3px);
	}
	.has-choice .cart-card[aria-checked='false'] {
		opacity: 0.6;
	}
	.has-choice .cart-card[aria-checked='false']:hover {
		opacity: 0.9;
	}
	.cart-card:focus-visible,
	.start:focus-visible {
		outline: 3px solid var(--text);
		outline-offset: 4px;
	}
	.art {
		display: block;
		width: 100%;
		max-width: 100%;
		height: auto;
		aspect-ratio: 1;
		object-fit: contain;
		border-radius: 8px;
		background: var(--border);
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
	}
	.label {
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding: 0 4px;
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
		top: -12px;
		right: -12px;
		width: 30px;
		height: 30px;
		border-radius: 50%;
		background: var(--game);
		color: #1d1f1c;
		display: flex;
		align-items: center;
		justify-content: center;
		box-shadow: 0 0 0 3px var(--bg);
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
		.check,
		.start {
			transition: none;
		}
		.cart-card:hover,
		.cart-card[aria-checked='true'] {
			transform: none;
		}
	}
</style>
