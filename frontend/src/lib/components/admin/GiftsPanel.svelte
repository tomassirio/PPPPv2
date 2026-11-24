<!-- Send a Mystery Gift (POST /admin/gifts): a fair item from the curated list, up to its cap, to everyone or (to) one
     player. It lands in their PC the next time they stand free in the overworld. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';
	import { nameOf } from '#lib/pictures.svelte.ts';

	let { to = null }: { to?: string | null } = $props();
	type Gift = { id: string; name: string; max: number };

	let catalog = $state<Gift[]>([]);
	let gift = $state('');
	let quantity = $state(1);
	const chosen = $derived(catalog.find((g) => g.id === gift));
	$effect(() => {
		act('Load gifts', () => api<Gift[]>('GET', '/admin/gifts'), undefined, true).then((c) => {
			if (!c) return;
			catalog = c;
			gift ||= c[0]?.id ?? '';
		});
	});
	$effect(() => {
		if (chosen && quantity > chosen.max) quantity = chosen.max;
	});

	async function send(e: SubmitEvent) {
		e.preventDefault();
		if (!chosen) return;
		const who = to ? nameOf(to) : 'every Pib';
		await act(
			`Mystery Gift (${quantity}× ${chosen.name}) to ${who}`,
			() => api('POST', '/admin/gifts', { gift, quantity, to }),
			to ? undefined : `Send ${quantity}× ${chosen.name} to every Pib? Everyone gets it once, in their PC.`
		);
	}
</script>

<section class="panel gifts" aria-labelledby="gifts-h{to ? '-one' : ''}">
	<h2 id="gifts-h{to ? '-one' : ''}" class="pixel">Mystery Gift</h2>
	<p class="hint">
		{to ? `Goes to ${nameOf(to)}'s PC` : 'Goes to every Pib’s PC'} the next time they stand free in the overworld. Only fair items, capped.
	</p>
	<form class="row" onsubmit={send}>
		<label class="sr-only" for="gift-item{to ? '-one' : ''}">Gift</label>
		<select id="gift-item{to ? '-one' : ''}" bind:value={gift}>
			{#each catalog as g (g.id)}
				<option value={g.id}>{g.name} (up to {g.max})</option>
			{/each}
		</select>
		<label class="sr-only" for="gift-qty{to ? '-one' : ''}">How many</label>
		<input id="gift-qty{to ? '-one' : ''}" class="qty" type="number" min="1" max={chosen?.max ?? 1} bind:value={quantity} />
		<button disabled={!chosen}>{to ? 'Send' : 'Send to everyone'}</button>
	</form>
</section>

<style>
	.qty {
		width: 64px;
	}
</style>
