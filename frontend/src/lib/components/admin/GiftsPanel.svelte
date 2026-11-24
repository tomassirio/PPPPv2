<!-- Send a Mystery Gift (POST /admin/gifts) to everyone or (to) one player: a fair item from the curated list, up to its
     cap, into their PC; or an event Pokémon (Gen 2's distributions, fixed movesets) into their party once it has room.
     Both arrive the next time they stand free in the overworld. Picked from chips (big enough for fingers). -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';
	import { nameOf } from '#lib/pictures.svelte.ts';

	let { to = null }: { to?: string | null } = $props();
	type Gift = { id: string; name: string; max: number; pokemon: boolean };

	let catalog = $state<Gift[]>([]);
	let gift = $state('');
	let quantity = $state(1);
	const chosen = $derived(catalog.find((g) => g.id === gift));
	const groups = $derived([
		{ label: 'Items', list: catalog.filter((g) => !g.pokemon) },
		{ label: 'Event Pokémon', list: catalog.filter((g) => g.pokemon) }
	]);
	$effect(() => {
		act('Load gifts', () => api<Gift[]>('GET', '/admin/gifts'), undefined, true).then((c) => {
			if (!c) return;
			catalog = c;
			gift ||= c[0]?.id ?? '';
		});
	});
	$effect(() => {
		if (chosen && quantity > chosen.max) quantity = chosen.max;
		if (quantity < 1) quantity = 1;
	});

	async function send(e: SubmitEvent) {
		e.preventDefault();
		if (!chosen) return;
		const who = to ? nameOf(to) : 'every Pib';
		const what = chosen.pokemon ? chosen.name : `${quantity}× ${chosen.name}`;
		await act(
			`Mystery Gift (${what}) to ${who}`,
			() => api('POST', '/admin/gifts', { gift, quantity, to }),
			to ? undefined : `Send ${what} to every Pib? Everyone gets it once.`
		);
	}
</script>

<section class="panel gifts" aria-labelledby="gifts-h{to ? '-one' : ''}">
	<h2 id="gifts-h{to ? '-one' : ''}" class="pixel">Mystery Gift</h2>
	<p class="hint">
		{to ? `To ${nameOf(to)}` : 'To every Pib'}, the next time they stand free in the overworld: items to the PC, Pokémon to the party (once
		there is room). Like any traded Pokémon, an event one obeys only up to what their badges allow.
	</p>
	<form onsubmit={send}>
		{#each groups as g (g.label)}
			{#if g.list.length}
				<fieldset>
					<legend>{g.label}</legend>
					<div class="chips">
						{#each g.list as item (item.id)}
							<button type="button" aria-pressed={gift === item.id} onclick={() => (gift = item.id)}>{item.name}</button>
						{/each}
					</div>
				</fieldset>
			{/if}
		{/each}
		<div class="send">
			{#if chosen && chosen.max > 1}
				<div class="qty" role="group" aria-label="How many">
					<button type="button" aria-label="One fewer" disabled={quantity <= 1} onclick={() => quantity--}>−</button>
					<span aria-live="polite">{quantity}</span>
					<button type="button" aria-label="One more" disabled={quantity >= chosen.max} onclick={() => quantity++}>+</button>
					<span class="cap">of {chosen.max}</span>
				</div>
			{/if}
			<button class="go" disabled={!chosen}>{to ? 'Send' : 'Send to everyone'}</button>
		</div>
	</form>
</section>

<style>
	fieldset {
		margin: 0 0 12px;
		padding: 0;
		border: none;
		min-width: 0;
	}
	legend {
		margin-bottom: 8px;
		font-size: 12px;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--muted);
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}
	.chips button {
		min-height: 36px;
		padding: 0 12px;
		border: 1px solid var(--border);
		border-radius: 999px;
		background: transparent;
		color: var(--text);
		font: 600 14px var(--body-font);
		cursor: pointer;
	}
	.chips button[aria-pressed='true'] {
		border-color: var(--accent);
		background: var(--accent);
		color: #1d1f1c;
	}
	.send {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}
	.qty {
		display: flex;
		align-items: center;
		gap: 8px;
		font: 700 16px var(--body-font);
	}
	.qty button {
		width: 40px;
		height: 40px;
		border: 1px solid var(--border);
		border-radius: 10px;
		background: transparent;
		color: var(--text);
		font-size: 20px;
		cursor: pointer;
	}
	.qty button:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}
	.cap {
		color: var(--muted);
		font-weight: 500;
		font-size: 13px;
	}
	.go {
		min-height: 44px;
		margin-left: auto;
	}
	button:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
	}
</style>
