<!-- A Pib's link history (GET /players/{id}/link-history, newest first): Battles (won or lost, against whom, both teams
     as last read: the game keeps no list of who fought) and Trades (link trades and Wonder Trades: what left, what
     arrived, with whom), a page at a time. Opened from the Link cable card on the profile. -->
<script lang="ts">
	import { api } from '#lib/api.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import Name from '#lib/components/Name.svelte';
	import { loadWords } from '#lib/notes.svelte.ts';

	type Mon = { species: number; level: number; shiny: boolean };
	type Entry = { kind: 'BATTLE' | 'TRADE' | 'WONDER'; at: number; partner: string | null; result: 'WIN' | 'LOSS' | null; mine: Mon[] | null; theirs: Mon[] | null; gave: Mon | null; got: Mon | null };

	let { open = $bindable(false), user, you = false }: { open?: boolean; user: string; you?: boolean } = $props();
	let dialog = $state<HTMLDialogElement>();
	let entries = $state<Entry[] | null>(null);
	let names = $state<string[]>([]);
	let tab = $state<'battles' | 'trades'>('battles');
	let pageNo = $state(0);
	const PER_PAGE = 5;

	$effect(() => {
		if (open && !dialog?.open) {
			entries = null;
			pageNo = 0;
			api<Entry[]>('GET', `/players/${encodeURIComponent(user)}/link-history`).then((e) => (entries = e), () => (entries = []));
			loadWords().then((w) => (names = w.words['Pokémon'] ?? []), () => {});
			dialog?.showModal();
			dialog?.focus();
		}
		if (!open && dialog?.open) dialog.close();
	});

	const battles = $derived((entries ?? []).filter((e) => e.kind === 'BATTLE'));
	const trades = $derived((entries ?? []).filter((e) => e.kind !== 'BATTLE'));
	const list = $derived(tab === 'battles' ? battles : trades);
	const pages = $derived(Math.max(1, Math.ceil(list.length / PER_PAGE)));
	const shown = $derived(list.slice(pageNo * PER_PAGE, pageNo * PER_PAGE + PER_PAGE));
	const name = (m: Mon) => names[m.species - 1] ?? `#${m.species}`;
	const when = (at: number) => new Date(at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
	const who = $derived(you ? 'You' : 'They');
</script>

{#snippet mon(m: Mon, size = 40)}
	<span class="mon" title="{name(m)} Lv {m.level}{m.shiny ? ' (shiny)' : ''}">
		<img src="/api/v1/game/icons/{m.species}.png" alt={name(m)} width={size} height={size} />
		{#if m.shiny}<span class="shiny" aria-hidden="true">✦</span>{/if}
	</span>
{/snippet}

<dialog bind:this={dialog} tabindex="-1" aria-labelledby="lh-h" onclose={() => (open = false)} onclick={(e) => e.target === dialog && dialog?.close()}>
	<div class="body">
		<header>
			<h2 id="lh-h">Link history</h2>
			<button type="button" class="close" aria-label="Close" onclick={() => dialog?.close()}>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</header>
		<div class="tabs" role="tablist">
			<button type="button" role="tab" aria-selected={tab === 'battles'} onclick={() => ((tab = 'battles'), (pageNo = 0))}>Battles <span class="n">{battles.length}</span></button>
			<button type="button" role="tab" aria-selected={tab === 'trades'} onclick={() => ((tab = 'trades'), (pageNo = 0))}>Trades <span class="n">{trades.length}</span></button>
		</div>

		{#if entries === null}
			<p class="muted small">Loading…</p>
		{:else if !list.length}
			<p class="empty muted small">{tab === 'battles' ? 'No link battles recorded yet.' : 'No trades recorded yet.'} {tab === 'trades' ? 'Trades before today\'s update were only counted, not kept.' : ''}</p>
		{:else}
			<ul class="list">
				{#each shown as e (e.at + e.kind + (e.partner ?? ''))}
					<li class="entry">
						<div class="top">
							{#if e.kind === 'BATTLE'}
								<span class="chip {e.result === 'WIN' ? 'win' : 'loss'}">{e.result === 'WIN' ? 'Won' : 'Lost'}</span>
							{:else}
								<span class="chip {e.kind === 'WONDER' ? 'wonder' : 'trade'}">{e.kind === 'WONDER' ? 'Wonder Trade' : 'Trade'}</span>
							{/if}
							{#if e.partner}
								<span class="partner">{e.kind === 'BATTLE' ? 'vs' : 'with'} <Avatar user={e.partner} size={20} /><b><Name user={e.partner} /></b></span>
							{:else if e.kind === 'WONDER'}
								<span class="muted small">into the pool, waiting for a Pib</span>
							{/if}
							<span class="when muted xs">{when(e.at)}</span>
						</div>
						{#if e.kind === 'BATTLE'}
							<div class="teams">
								<div class="team"><span class="label">{who}</span><span class="mons">{#each e.mine ?? [] as m, i (i)}{@render mon(m, 32)}{:else}<span class="muted xs">not recorded</span>{/each}</span></div>
								<div class="team"><span class="label">{e.partner ? 'Them' : 'Opponent'}</span>
									<span class="mons"
										>{#each e.theirs ?? [] as m, i (i)}{@render mon(m, 32)}{:else}<span class="muted xs">{e.mine ? 'not seen' : 'not recorded'}</span>{/each}</span
									>
								</div>
							</div>
						{:else}
							<div class="swap">
								<div class="side">
									<span class="label">Sent</span>
									{#if e.gave}{@render mon(e.gave)}<span class="small">{name(e.gave)} <span class="muted">Lv {e.gave.level}</span></span>{:else}<span class="muted xs">earlier</span>{/if}
								</div>
								<svg class="arrow" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
									><path d="M4 8h13l-3-3M20 16H7l3 3" /></svg
								>
								<div class="side">
									<span class="label">Got</span>
									{#if e.got}{@render mon(e.got)}<span class="small">{name(e.got)} <span class="muted">Lv {e.got.level}</span></span>{:else}<span class="muted xs">still waiting</span>{/if}
								</div>
							</div>
						{/if}
					</li>
				{/each}
			</ul>
			{#if pages > 1}
				<nav class="pager" aria-label="Pages">
					<button type="button" class="pg" disabled={pageNo === 0} onclick={() => pageNo--}>← Newer</button>
					<span class="muted small">{pageNo + 1} / {pages}</span>
					<button type="button" class="pg" disabled={pageNo >= pages - 1} onclick={() => pageNo++}>Older →</button>
				</nav>
			{/if}
		{/if}
	</div>
</dialog>

<style>
	dialog {
		position: fixed;
		inset: 0;
		margin: auto;
		width: min(560px, calc(100vw - 16px));
		max-height: calc(100dvh - 32px);
		padding: 0;
		border: 1px solid var(--link);
		border-radius: 16px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 16px 48px rgb(0 0 0 / 0.35);
		text-align: left;
	}
	dialog:focus {
		outline: none;
	}
	dialog:not([open]) {
		display: none;
	}
	dialog::backdrop {
		background: rgb(8 11 13 / 0.55);
	}
	.body {
		padding: 18px 18px 18px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	h2 {
		margin: 0;
		font-size: 18px;
	}
	p {
		margin: 0;
	}
	.muted {
		color: var(--muted);
	}
	.small {
		font-size: 13px;
	}
	.xs {
		font-size: 12px;
	}
	.label {
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.close {
		width: 32px;
		height: 32px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.close:hover {
		background: var(--sunk);
		color: var(--text);
	}
	.tabs {
		display: flex;
		gap: 4px;
		border-bottom: 1px solid var(--border);
	}
	.tabs button {
		padding: 8px 14px;
		border: none;
		border-bottom: 2px solid transparent;
		background: none;
		color: var(--muted);
		font: 600 14px var(--body-font);
		cursor: pointer;
	}
	.tabs button[aria-selected='true'] {
		color: var(--text);
		border-bottom-color: var(--link);
	}
	.n {
		margin-left: 4px;
		padding: 0 6px;
		border-radius: 999px;
		background: var(--sunk);
		font-size: 11px;
	}
	.empty {
		padding: 20px 0;
		text-align: center;
	}
	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.entry {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 10px 12px;
		border-radius: 12px;
		border: 1px solid var(--border);
		background: var(--sunk);
	}
	.top {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 8px;
	}
	.when {
		margin-left: auto;
	}
	.partner {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		font-size: 13px;
	}
	.chip {
		padding: 2px 8px;
		border-radius: 999px;
		font-size: 11px;
		font-weight: 800;
		letter-spacing: 0.04em;
		text-transform: uppercase;
	}
	.chip.win {
		background: color-mix(in srgb, var(--hp-ok) 22%, transparent);
		color: var(--hp-ok);
	}
	.chip.loss {
		background: color-mix(in srgb, var(--danger) 20%, transparent);
		color: var(--danger);
	}
	.chip.trade {
		background: var(--accent-tint);
		color: var(--accent-text);
	}
	.chip.wonder {
		background: color-mix(in srgb, var(--r-epic) 20%, transparent);
		color: var(--r-epic);
	}
	.teams {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.team {
		display: grid;
		grid-template-columns: 64px 1fr;
		align-items: center;
	}
	.mons {
		display: flex;
		flex-wrap: wrap;
		gap: 2px;
	}
	.mon {
		position: relative;
		display: inline-flex;
	}
	.mon img {
		image-rendering: pixelated;
	}
	.shiny {
		position: absolute;
		top: -2px;
		right: -2px;
		color: var(--r-legendary);
		font-size: 10px;
	}
	.swap {
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		align-items: center;
		gap: 8px;
	}
	.side {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		text-align: center;
	}
	.arrow {
		color: var(--muted);
	}
	.pager {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.pg {
		padding: 6px 12px;
		border: 1px solid var(--border-strong);
		border-radius: 999px;
		background: none;
		color: var(--text);
		font: 600 13px var(--body-font);
		cursor: pointer;
	}
	.pg:disabled {
		opacity: 0.4;
		cursor: default;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
</style>
