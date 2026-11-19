<!-- "Find someone to link with": the Pibs online right now, each with what they're doing, their version and link record,
     and Offer trade / Offer battle. Live via PRESENCE and the link state; the offer's countdown shows at the top. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import { api } from '#lib/api.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { on, onOpen } from '#lib/events.ts';
	import { act, left, link, verb, watchLink } from '#lib/link.svelte.ts';
	import { clubClosed, playerStatus, versionOf, winRate } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	let { open = $bindable(false), nearby }: { open?: boolean; nearby?: string[] } = $props();
	const headingId = $props.id();

	type Viewer = { id: string; name: string; picture: string | null };
	let viewers = $state<Viewer[]>([]);
	let dialog = $state<HTMLDialogElement>();

	$effect(() => watchLink());
	$effect(() => watchBoard());
	$effect(() => {
		if (!open) return;
		const load = () =>
			api<Viewer[]>('GET', '/presence')
				.then((v) => (viewers = v))
				.catch(() => {});
		load();
		const offs = [on('PRESENCE', (v: Viewer[]) => (viewers = v)), onOpen(load)];
		return () => offs.forEach((off) => off());
	});
	$effect(() => {
		if (open && !dialog?.open) dialog?.showModal();
		if (!open && dialog?.open) dialog.close();
	});

	const me = $derived(session.me?.id);
	const others = $derived(viewers.filter((v) => v.id !== me && (!nearby || nearby.includes(v.id))));
	const busy = $derived(!!(link.state?.session || link.state?.outgoing));
	const outgoing = $derived(link.state?.outgoing ?? null);
	const rowOf = (id: string) => board.rows.find((r) => r.userId === id);
	const meClosed = $derived(clubClosed(rowOf(session.me?.id ?? '')));

	// Five Pibs a page; presence changes keep the page, unless it ran out of Pibs. Back to the first on reopening.
	const PER_PAGE = 5;
	let pageNo = $state(0);
	const pages = $derived(Math.max(1, Math.ceil(others.length / PER_PAGE)));
	const shown = $derived(others.slice(Math.min(pageNo, pages - 1) * PER_PAGE, (Math.min(pageNo, pages - 1) + 1) * PER_PAGE));
	$effect(() => {
		if (open) pageNo = 0;
	});
	$effect(() => {
		if (pageNo > pages - 1) pageNo = pages - 1;
	});
</script>

<dialog
	bind:this={dialog}
	class="panel"
	aria-labelledby={headingId}
	onclose={() => (open = false)}
	onclick={(e) => e.target === dialog && dialog.close()}
>
	<div class="body">
		<header>
			<div>
				<h2 id={headingId}>{nearby ? 'Link with a nearby player' : 'Find someone to link with'}</h2>
				<p class="muted small">{others.length ? `${others.length} ${others.length === 1 ? 'Pib' : 'Pibs'} ${nearby ? 'nearby' : 'online'}` : nearby ? 'Nobody else is nearby right now.' : 'Nobody else is online right now.'}</p>
			</div>
			<button type="button" class="close" aria-label="Close" onclick={() => dialog?.close()}>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</header>

		<p class="heads-up">
			<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
				><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></svg
			>
			<span>When someone accepts, you both Dig to your last Pokémon Center, so be free to leave: outdoors or in a cave, no battle, menu or dialog open.</span>
		</p>

		{#if outgoing}
			<p class="waiting" role="status">
				Offered to {verb(outgoing.kind)} with <b><Name user={outgoing.to} /></b>. Waiting for an answer… {left(outgoing.expiresAt)} s
			</p>
		{/if}
		{#if link.error}<p class="error small" role="alert">{link.error}</p>{/if}

		{#if others.length}
			<ul class="pibs">
				{#each shown as v (v.id)}
					{@const row = rowOf(v.id)}
					{@const st = playerStatus(v.id, true, me, link.state?.lobby ?? [], link.links)}
					{@const linked = st.kind === 'linked'}
					{@const rate = winRate(row?.link)}
					<li>
						<a class="who" href="/pib/{encodeURIComponent(v.id)}" onclick={() => dialog?.close()}>
							<Avatar user={v.id} size={40} />
							<span class="col">
								<b><Name user={v.id} /></b>
								<span class="xs status-{st.kind}"><span class="status-dot"></span>{st.label}{#if row?.game && versionOf(row.game, board.names)}{@const ver = versionOf(row.game, board.names)}<span class="muted"> · <span class="ver-{ver}">{ver}</span></span>{/if}</span>
								{#if row}<span class="muted xs">{row.link?.wins ?? 0}–{row.link?.losses ?? 0}{rate != null ? ` · ${rate}% won` : ''} · {row.link?.trades ?? 0} trades</span>{/if}
							</span>
						</a>
						{#if row && clubClosed(row)}
							<span class="closed" title="{nameOf(v.id)} has to bring Mr. Pokémon's Mystery Egg back to Prof. Elm before they can link."
								><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"
									><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
								>Not available for the Cable Club yet</span
							>
						{:else if row}
							<div class="offers">
								<button type="button" class="btn primary" disabled={busy || linked || meClosed} onclick={() => act('POST', '/link/challenges', { userId: v.id, kind: 'TRADE' })}>Offer trade</button>
								<button type="button" class="btn battle" disabled={busy || linked || meClosed} onclick={() => act('POST', '/link/challenges', { userId: v.id, kind: 'BATTLE' })}>Challenge</button>
							</div>
						{:else}
							<span class="muted xs">Hasn't picked a game yet</span>
						{/if}
					</li>
				{/each}
			</ul>
			{#if pages > 1}
				<nav class="pager" aria-label="Pibs online, pages">
					<button type="button" class="btn" disabled={pageNo === 0} onclick={() => pageNo--}>← Previous</button>
					<span class="muted small">{pageNo * PER_PAGE + 1}–{pageNo * PER_PAGE + shown.length} of {others.length}</span>
					<button type="button" class="btn" disabled={pageNo >= pages - 1} onclick={() => pageNo++}>Next →</button>
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
		width: min(560px, calc(100vw - 32px));
		max-height: calc(100dvh - 48px);
		padding: 0;
		border: 1px solid var(--link);
		border-radius: 16px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 16px 48px rgb(0 0 0 / 0.35);
	}
	dialog:not([open]) {
		display: none;
	}
	dialog::backdrop {
		background: rgb(8 11 13 / 0.55);
	}
	.body {
		padding: 18px 20px 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 12px;
	}
	h2 {
		margin: 0 0 2px;
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
	.error {
		color: var(--danger);
		font-weight: 600;
	}
	.heads-up {
		display: flex;
		align-items: flex-start;
		gap: 8px;
		padding: 10px 12px;
		border-radius: 10px;
		background: var(--sunk);
		border: 1px solid var(--border);
		font-size: 13px;
		line-height: 18px;
		color: var(--muted);
	}
	.heads-up svg {
		flex: none;
		margin-top: 1px;
		color: var(--link);
	}
	/* Their Cable Club is still shut (no Mystery Egg back to Elm): no offers, a lock and why on hover. */
	.closed {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 5px 10px;
		border-radius: 999px;
		border: 1px dashed var(--border-strong);
		color: var(--muted);
		font-size: 12px;
		font-weight: 600;
		white-space: nowrap;
		cursor: help;
	}
	.pager {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.waiting {
		padding: 10px 12px;
		border-radius: 10px;
		background: var(--link-tint, var(--sunk));
		border: 1px solid var(--link);
		font-size: 13px;
	}
	button {
		font: inherit;
		cursor: pointer;
	}
	button:focus-visible,
	a:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.close {
		flex: none;
		width: 32px;
		height: 32px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
	}
	.close:hover {
		background: var(--sunk);
		color: var(--text);
	}
	.pibs {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 8px;
		overflow-y: auto;
	}
	.pibs li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
		padding: 10px 12px;
		border-radius: 12px;
		background: var(--sunk);
		border: 1px solid var(--border);
	}
	.who {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
		color: inherit;
		text-decoration: none;
	}
	.col {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.status-dot {
		margin-right: 5px;
	}
	.offers {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 6px;
		flex: none;
	}
	.btn {
		box-sizing: border-box;
		height: 34px;
		padding: 0 12px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		background: transparent;
		color: var(--text);
		font-size: 13px;
		font-weight: 600;
		white-space: nowrap;
	}
	.btn.primary {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--ink);
	}
	/* Battle in the Link cable card's battle red. */
	.btn.battle {
		background: var(--danger);
		border-color: var(--danger);
		color: #fff;
	}
	.btn:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}
</style>
