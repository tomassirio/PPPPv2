<!-- /watch (the Live tab): the link sessions running now between other Pibs. One at a time on the LiveStage, picked
     with the chips when there are several (?u= a player of the one to show; the first otherwise). None running: says so.
     A Pib who is linked themselves is sent to /play by SubPage. -->
<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import Avatar from '#lib/components/Avatar.svelte';
	import LiveStage from '#lib/components/LiveStage.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import { clock, link, since, watchLink } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	$effect(() => watchLink());

	const links = $derived(link.state?.links ?? []);
	const asked = $derived((page.url.searchParams.get('u') ?? '').toLowerCase());
	// The one asked for, while it runs or just ended (the stage says so); else the first running.
	const selected = $derived(asked || links[0]?.a || '');
	const pick = (user: string) => goto(`/watch?u=${encodeURIComponent(user)}`, { replaceState: true });
</script>

<svelte:head><title>Live · PPPP</title></svelte:head>

<SubPage>
	<div class="live-page">
		<header class="head">
			<div>
				<h1>Live</h1>
				<p class="muted">Link battles and trades going on right now.</p>
			</div>
			<a class="btn" href="/play">
				<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
					><path d="M15 18l-6-6 6-6" /></svg
				>
				Back to my game
			</a>
		</header>

		{#if links.length > 1}
			<div class="picker" role="group" aria-label="Running links">
				{#each links as p (p.a)}
					{@const on = selected === p.a || selected === p.b}
					<button type="button" class="chip" class:on aria-pressed={on} onclick={() => pick(p.a)}>
						<Avatar user={p.a} size={22} /><Avatar user={p.b} size={22} />
						<span><b>{nameOf(p.a)}</b> vs <b>{nameOf(p.b)}</b></span>
						<span class="muted">{p.kind === 'TRADE' ? 'Trade' : 'Battle'} · {clock(since(p.since))}</span>
					</button>
				{/each}
			</div>
		{/if}

		{#if selected}
			{#key selected}<LiveStage id={selected} />{/key}
		{:else if link.state}
			<section class="empty">
				<span class="icon" aria-hidden="true">
					<svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"
						><path d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5L21 6V3h-3L6.5 14.5M5 13l6 6M8 16l-4 4M3 19l2 2" /></svg
					>
				</span>
				<h2>No battles right now</h2>
				<p class="muted">When two Pibs plug in a link cable, you can watch both Game Boys here, live.</p>
				<a class="btn primary" href="/play">Find someone to battle</a>
			</section>
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
	.head {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: 16px;
		flex-wrap: wrap;
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
	.btn.primary {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--ink);
	}
	.btn.primary:hover {
		background: var(--accent-hover);
	}
	.btn:focus-visible,
	.chip:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.picker {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 6px 12px;
		border-radius: 999px;
		border: 1px solid var(--border);
		background: var(--panel);
		color: var(--text);
		font: inherit;
		font-size: 13px;
		cursor: pointer;
	}
	.chip.on {
		border-color: var(--link);
		background: var(--link-tint);
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
</style>
