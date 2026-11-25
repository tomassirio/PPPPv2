<!-- Send a Mystery Gift (POST /admin/gifts) to everyone or (to) one player: a fair item from the curated list, up to its
     cap, into their PC; or an event Pokémon (Gen 2's distributions, fixed movesets) into their party once it has room.
     Both arrive the next time they stand free in the overworld. Items / Pokémon tabs of tiles (an icon each: the
     Pokémon's sprite from the admin's ROM), then a summary bar with the amount and Send. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';
	import { nameOf } from '#lib/pictures.svelte.ts';

	let { to = null }: { to?: string | null } = $props();
	type Gift = { id: string; name: string; max: number; pokemon: boolean };

	/** An icon per item (no item art in the ROMs). */
	const ICONS: Record<string, string> = {
		'rare-candy': '🍬', 'fire-stone': '🔥', 'water-stone': '💧', thunderstone: '⚡', 'leaf-stone': '🍃', 'moon-stone': '🌙',
		'sun-stone': '☀️', everstone: '🪨', 'pp-up': '⏫', 'full-restore': '💊', 'max-revive': '💖', 'max-repel': '🌫️',
		'escape-rope': '🪢', 'ultra-ball': '🟡', 'great-ball': '🔵', 'gold-berry': '🍇', 'moomoo-milk': '🥛', nugget: '🪙',
		'amulet-coin': '💰', 'odd-egg': '🥚'
	};
	/** Event Pokémon shown by their sprite (Pokédex number). */
	const SPRITES: Record<string, number> = { celebi: 251, mew: 151, 'surfing-pikachu': 25 };

	let catalog = $state<Gift[]>([]);
	let tab = $state<'items' | 'pokemon'>('items');
	let gift = $state('');
	let quantity = $state(1);
	const chosen = $derived(catalog.find((g) => g.id === gift));
	const shown = $derived(catalog.filter((g) => g.pokemon === (tab === 'pokemon')));
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
	// "Celebi (GS Ball, Lv 30)" -> name "Celebi", note "GS Ball, Lv 30".
	const title = (g: Gift) => g.name.replace(/\s*\(.*\)$/, '');
	const note = (g: Gift) => g.name.match(/\((.*)\)$/)?.[1] ?? (g.pokemon ? 'Event' : `up to ${g.max}`);
	const what = $derived(chosen ? (chosen.pokemon ? title(chosen) : `${quantity}× ${title(chosen)}`) : '');
	const who = $derived(to ? nameOf(to) : 'every Pib');

	function pick(g: Gift) {
		gift = g.id;
		quantity = 1;
	}

	async function send(e: SubmitEvent) {
		e.preventDefault();
		if (!chosen) return;
		await act(
			`Mystery Gift (${what}) to ${who}`,
			() => api('POST', '/admin/gifts', { gift, quantity, to }),
			to ? undefined : `Send ${what} to every Pib? Everyone gets it once.`
		);
	}
</script>

<section class="panel gifts" aria-labelledby="gifts-h{to ? '-one' : ''}">
	<div class="head">
		<h2 id="gifts-h{to ? '-one' : ''}" class="pixel">Mystery Gift</h2>
		<div class="tabs" role="tablist" aria-label="Kind of gift">
			<button type="button" role="tab" aria-selected={tab === 'items'} onclick={() => (tab = 'items')}>Items</button>
			<button type="button" role="tab" aria-selected={tab === 'pokemon'} onclick={() => (tab = 'pokemon')}>Pokémon</button>
		</div>
	</div>
	<p class="hint">
		{tab === 'items'
			? 'Items land in the PC the next time they stand free in the overworld.'
			: 'Pokémon join the party once it has room. Like any traded Pokémon, they obey only up to what the badges allow.'}
	</p>

	<form onsubmit={send}>
		<div class="tiles" role="radiogroup" aria-label={tab === 'items' ? 'Items' : 'Event Pokémon'}>
			{#each shown as g (g.id)}
				<button type="button" class="tile" role="radio" aria-checked={gift === g.id} onclick={() => pick(g)}>
					<span class="icon" aria-hidden="true">
						{#if SPRITES[g.id]}
							<img src="/api/v1/game/icons/{SPRITES[g.id]}.png" alt="" />
						{:else}
							{ICONS[g.id] ?? '🎁'}
						{/if}
					</span>
					<span class="name">{title(g)}</span>
					<span class="note">{note(g)}</span>
				</button>
			{/each}
		</div>

		<div class="bar">
			<span class="summary">
				<span class="gift-glyph" aria-hidden="true">🎁</span>
				<span><b>{what || 'Pick a gift'}</b> <span class="arrow">→</span> {who}</span>
			</span>
			{#if chosen && chosen.max > 1}
				<span class="qty" role="group" aria-label="How many">
					<button type="button" aria-label="One fewer" disabled={quantity <= 1} onclick={() => quantity--}>−</button>
					<span aria-live="polite">{quantity}</span>
					<button type="button" aria-label="One more" disabled={quantity >= chosen.max} onclick={() => quantity++}>+</button>
				</span>
			{/if}
			<button class="primary go" disabled={!chosen}>{to ? 'Send' : 'Send to everyone'}</button>
		</div>
	</form>
</section>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
	}
	.head h2 {
		margin: 0;
	}
	.tabs {
		display: inline-flex;
		padding: 3px;
		gap: 2px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--muted) 14%, transparent);
	}
	.tabs button {
		height: 30px;
		padding: 0 14px;
		border: none;
		border-radius: 999px;
		background: transparent;
		color: var(--muted);
		font: 700 13px var(--body-font);
		cursor: pointer;
	}
	.tabs button[aria-selected='true'] {
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.2);
	}
	.hint {
		margin: 8px 0 12px;
	}
	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
		gap: 8px;
	}
	.tile {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
		height: auto !important;
		min-height: 96px;
		padding: 10px 6px !important;
		border: 1.5px solid var(--border);
		border-radius: 12px;
		background: color-mix(in srgb, var(--panel) 70%, transparent);
		color: var(--text);
		cursor: pointer;
		transition:
			border-color 0.12s,
			transform 0.12s,
			box-shadow 0.12s;
	}
	.tile:hover {
		border-color: var(--border-strong);
		transform: translateY(-1px);
	}
	.tile[aria-checked='true'] {
		border-color: var(--accent);
		background: color-mix(in srgb, var(--accent) 16%, var(--panel));
		box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 30%, transparent);
	}
	.icon {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		font-size: 26px;
		line-height: 1;
	}
	.icon img {
		width: 40px;
		height: 40px;
		image-rendering: pixelated;
	}
	.name {
		font: 700 13px var(--body-font);
		text-align: center;
		line-height: 1.2;
	}
	.note {
		font-size: 11px;
		color: var(--muted);
		text-align: center;
		line-height: 1.2;
	}
	.bar {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 10px;
		margin-top: 14px;
		padding: 10px 12px;
		border-radius: 12px;
		background: color-mix(in srgb, var(--muted) 10%, transparent);
	}
	.summary {
		flex: 1 1 200px;
		display: flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
		font-size: 14px;
	}
	.gift-glyph {
		font-size: 20px;
	}
	.arrow {
		color: var(--muted);
	}
	.qty {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		font: 700 16px var(--body-font);
	}
	.qty button {
		width: 34px;
		height: 34px !important;
		padding: 0 !important;
		border: 1px solid var(--border);
		border-radius: 50%;
		background: var(--panel);
		color: var(--text);
		font-size: 18px;
		cursor: pointer;
	}
	.qty button:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}
	.go {
		min-width: 140px;
		margin-left: auto;
	}
	@media (max-width: 520px) {
		.go {
			flex: 1 1 100%;
		}
	}
	button:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
	}
	@media (prefers-reduced-motion: reduce) {
		.tile {
			transition: none;
		}
		.tile:hover {
			transform: none;
		}
	}
</style>
