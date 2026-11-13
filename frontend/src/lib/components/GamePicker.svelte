<!-- First run: the player picks their cartridge, once. PUT /me/game refuses a second, different pick. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';

	type Cartridge = { id: string; title: string; size: number; cgb: boolean };
	let { available, onpicked }: { available: Cartridge[]; onpicked: (game: string) => void } = $props();

	let choice = $state<string | null>(null);
	let saving = $state(false);
	let error = $state('');

	/** "pokemon-crystal" -> "Pokemon Crystal". */
	const label = (id: string) => id.replace(/-/g, ' ').replace(/\b[a-z]/g, (c) => c.toUpperCase());
	/** Cartridge plastic by version; anything else gets the classic grey. */
	const plastic = (id: string) =>
		/gold/.test(id) ? '#d2aa2e' : /silver/.test(id) ? '#b9c1c9' : /crystal/.test(id) ? '#7ccbe0' : /red/.test(id) ? '#d8574f' : /blue/.test(id) ? '#5b83d6' : '#9a9a9a';

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
				<button type="button" class="cart-card" role="radio" aria-checked={choice === c.id} onclick={() => (choice = c.id)}>
					<span class="cart" style:background={plastic(c.id)} aria-hidden="true">
						<span class="sticker pixel">{c.title || c.id}<small>{c.cgb ? 'GAME BOY COLOR' : 'GAME BOY'}</small></span>
					</span>
					<span class="name"><b>{label(c.id)}</b><span class="radio" aria-hidden="true"></span></span>
				</button>
			{/each}
		</div>
		<div class="go">
			<button type="button" class="start" disabled={!choice || saving} onclick={start}>
				{choice ? `Start ${label(choice)}` : 'Choose a game'}
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
	.cart {
		height: 132px;
		border-radius: 8px 8px 22px 8px;
		padding: 10px;
		display: flex;
	}
	.sticker {
		flex: 1;
		min-width: 0;
		border-radius: 4px;
		background: rgba(255, 255, 255, 0.55);
		color: #1d1f1c;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 10px;
		font-size: 11px;
		line-height: 1.6;
		text-align: center;
		overflow-wrap: anywhere;
		padding: 0 6px;
	}
	.sticker small {
		font: 600 11px var(--body-font);
		letter-spacing: 0.08em;
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
