<!-- First run: the player picks their cartridge, once. PUT /me/game refuses a second, different pick. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { cover, type Cartridge } from '#lib/cartridges.ts';

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

<section class="pick" aria-labelledby="pick-title">
	<h1 id="pick-title" class="pixel">Pick your game</h1>
	{#if available.length}
		<p class="lead">You choose once. Your save, your score and your trades stay with this version.</p>
		<div class="carts" role="radiogroup" aria-labelledby="pick-title">
			{#each available as c (c.id)}
				{@const art = cover(c)}
				<button type="button" class="cart-card" role="radio" aria-checked={choice === c.id} onclick={() => (choice = c.id)}>
					{#if art}
						<img class="art" src={art} alt="" width="360" height="360" />
					{:else}
						<span class="art blank pixel" aria-hidden="true">{c.title || c.id}</span>
					{/if}
					<span class="name"><b>{c.name}</b><span class="radio" aria-hidden="true"></span></span>
					<span class="system">{c.cgb ? 'Game Boy Color' : 'Game Boy'}</span>
				</button>
			{/each}
		</div>
		<div class="go">
			<button type="button" class="start" disabled={!choice || saving} onclick={start}>
				{chosen ? `Start ${chosen.name}` : 'Choose a game'}
			</button>
			<p class="note">This can't be changed later.</p>
			{#if error}<p class="error" role="alert">{error}</p>{/if}
		</div>
	{:else}
		<p class="lead">No games yet. Ask the admin to upload the cartridges, then reload this page.</p>
	{/if}
</section>

<style>
	.pick {
		max-width: 960px;
		margin: 0 auto;
		padding: 56px 16px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 28px;
		text-align: center;
	}
	h1 {
		margin: 0;
		font-size: 18px;
		line-height: 1.7;
		text-wrap: balance;
	}
	.lead {
		margin: 0;
		max-width: 56ch;
		color: var(--muted);
		line-height: 21px;
	}
	.carts {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 16px;
		width: 100%;
	}
	.cart-card {
		flex: 1 1 220px;
		max-width: 290px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 20px;
		background: var(--panel);
		border: 1px solid var(--border);
		border-radius: 12px;
		color: var(--text);
		text-align: left;
		cursor: pointer;
	}
	.cart-card[aria-checked='true'] {
		border-color: var(--accent-text);
		box-shadow: inset 0 0 0 1px var(--accent-text);
	}
	.cart-card:focus-visible,
	.start:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
	}
	.art {
		display: block;
		width: 100%;
		max-width: 100%;
		aspect-ratio: 1;
		object-fit: cover;
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
	.cart-card[aria-checked='false'] .art {
		filter: saturate(0.85);
	}
	.system {
		margin-top: -8px;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.name {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		font-size: 16px;
	}
	.radio {
		flex: none;
		width: 18px;
		height: 18px;
		border-radius: 50%;
		border: 2px solid var(--border-strong);
	}
	[aria-checked='true'] .radio {
		border-color: var(--accent-text);
		background: radial-gradient(var(--accent) 0 45%, transparent 50%);
	}
	.go {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
	}
	.start {
		height: 48px;
		padding: 0 28px;
		border-radius: 8px;
		border: none;
		background: var(--accent);
		color: var(--ink);
		font-weight: 600;
		font-size: 14px;
		cursor: pointer;
	}
	.start:disabled {
		opacity: 0.5;
		cursor: default;
	}
	.note,
	.error {
		margin: 0;
		font-size: 13px;
		color: var(--muted);
	}
	.error {
		color: var(--danger);
	}
</style>
