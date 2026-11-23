<!-- /watch (the Live tab): the link sessions running now between other Pibs, as a list of cards (both players, their
     game and trainer, battle or trade, time linked); none running: says so. ?u= a player of one: that one on the
     LiveStage, with a button back to the list. A Pib who is linked themselves is sent to /play by SubPage. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import { page } from '$app/state';
	import Avatar from '#lib/components/Avatar.svelte';
	import LiveStage from '#lib/components/LiveStage.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import FindButton from '#lib/components/FindButton.svelte';
	import LinkFinder from '#lib/components/LinkFinder.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { clock, link, since, watchLink } from '#lib/link.svelte.ts';
	import { clubClosed, versionOf } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	$effect(() => watchLink());
	$effect(() => watchBoard());

	const links = $derived(link.state?.links ?? []);
	let finding = $state(false); // the Find someone to link with dialog, as on /play
	// The one being watched (it stays on screen, saying it ended, once the cable comes out).
	const selected = $derived((page.url.searchParams.get('u') ?? '').toLowerCase());
	const who = (user: string) => {
		const r = board.rows.find((x) => x.userId === user);
		return [r?.trainerName, r ? versionOf(r.game, board.names) : null].filter(Boolean).join(' · ');
	};
</script>

<svelte:head><title>Live · PPPPv2</title></svelte:head>

<SubPage>
	<div class="live-page">
		{#if selected}
			<div class="bar">
				<a class="btn" href="/watch">
					<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
						><path d="M15 18l-6-6 6-6" /></svg
					>
					All live battles{#if links.length}<span class="count">{links.length}</span>{/if}
				</a>
			</div>
			{#key selected}<LiveStage id={selected} />{/key}
		{:else}
			<header class="head">
				<h1>Live</h1>
				<p class="muted">Link battles and trades going on right now.</p>
			</header>
			{#if links.length}
				<ul class="list">
					{#each links as p (p.a)}
						<li>
							<a class="card" href="/watch?u={encodeURIComponent(p.a)}">
								<span class="kind {p.kind === 'TRADE' ? 'trade' : 'battle'}"
									><span class="dot" aria-hidden="true"></span>{p.kind === 'TRADE' ? 'Trade' : 'Battle'} · {clock(since(p.since))}</span
								>
								<span class="pair">
									<span class="side"><Avatar user={p.a} size={44} /><span><b><Name user={p.a} /></b><span class="muted xs">{who(p.a)}</span></span></span>
									<span class="vs pixel" aria-hidden="true">VS</span>
									<span class="side end"><span><b><Name user={p.b} /></b><span class="muted xs">{who(p.b)}</span></span><Avatar user={p.b} size={44} /></span>
								</span>
								<span class="watch">Watch<span class="sr"> {nameOf(p.a)} and {nameOf(p.b)}</span> →</span>
							</a>
						</li>
					{/each}
				</ul>
			{:else if link.state}
				<section class="empty">
					<span class="icon" aria-hidden="true">
						<svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"
							><path d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5L21 6V3h-3L6.5 14.5M5 13l6 6M8 16l-4 4M3 19l2 2" /></svg
						>
					</span>
					<h2>No battles right now</h2>
					<p class="muted">When two Pibs plug in a link cable, you can watch both Game Boys here, live.</p>
					{#if clubClosed(board.rows.find((r) => r.userId === session.me?.id))}
						<!-- They can't link yet: no button to a battle they can't have, and why. -->
						<span class="locked-note"
							><svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"
								><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
							>Your Cable Club isn't open yet: bring Mr. Pokémon's Mystery Egg back to Prof. Elm first.</span
						>
					{:else}
						<FindButton onclick={() => (finding = true)} />
						<LinkFinder bind:open={finding} />
					{/if}
				</section>
			{/if}
		{/if}
	</div>
</SubPage>

<style>
	.live-page {
		width: 100%;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.bar {
		display: flex;
	}
	.count {
		min-width: 20px;
		height: 20px;
		padding: 0 6px;
		border-radius: 999px;
		background: #e3350d;
		color: #fff;
		font-size: 12px;
		display: inline-flex;
		align-items: center;
		justify-content: center;
	}
	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(min(100%, 440px), 1fr));
		gap: 12px;
	}
	.card {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 14px 16px;
		border-radius: 14px;
		border: 1px solid var(--border);
		background:
			radial-gradient(ellipse at 50% 0%, var(--link-tint), transparent 70%),
			var(--panel);
		color: var(--text);
		text-decoration: none;
	}
	.card:hover {
		border-color: var(--link);
	}
	.card:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.kind {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
	}
	.kind.battle {
		color: var(--danger);
	}
	.kind.trade {
		color: var(--accent-text);
	}
	.kind .dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: currentColor;
		animation: pulse 1.2s ease-in-out infinite;
	}
	@keyframes pulse {
		50% {
			opacity: 0.3;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.kind .dot {
			animation: none;
		}
	}
	.pair {
		display: grid;
		grid-template-columns: 1fr auto 1fr;
		align-items: center;
		gap: 10px;
	}
	.side {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}
	.side > span {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.side.end {
		justify-content: flex-end;
		text-align: right;
	}
	.vs {
		font-size: 13px;
		color: var(--accent-text);
	}
	.xs {
		font-size: 12px;
	}
	.watch {
		align-self: flex-end;
		font-size: 14px;
		font-weight: 700;
		color: var(--link);
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
	h1 {
		margin: 0;
		font-size: 24px;
	}
	.head p {
		margin: 4px 0 0;
	}
	.muted {
		color: var(--muted);
	}
	.btn {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		height: 38px;
		padding: 0 16px;
		border-radius: 10px;
		border: 1px solid var(--border-strong);
		background: var(--panel);
		color: var(--text);
		font-weight: 600;
		font-size: 14px;
		text-decoration: none;
		white-space: nowrap;
	}
	.btn:hover {
		border-color: var(--accent-text);
	}
	.btn:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		padding: 56px 24px;
		text-align: center;
		border: 1px dashed var(--border-strong);
		border-radius: 18px;
		background: var(--panel);
	}
	.empty .icon {
		color: var(--muted);
	}
	.empty h2 {
		margin: 4px 0 0;
		font-size: 20px;
	}
	.empty p {
		margin: 0 0 8px;
		max-width: 420px;
	}
	.locked-note {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 6px 12px;
		border-radius: 999px;
		border: 1px dashed var(--border-strong);
		color: var(--muted);
		font-size: 13px;
		font-weight: 600;
	}
</style>
