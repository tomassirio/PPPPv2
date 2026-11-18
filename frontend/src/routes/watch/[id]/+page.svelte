<!-- /watch/{userId}: spectate a running link, both Game Boys side by side (stacked on a phone), read only: the screens
     come from /ws/watch/{id} and this page sends no buttons. When the cable comes out it says so. -->
<script lang="ts">
	import { page } from '$app/state';
	import Avatar from '#lib/components/Avatar.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import { clock, link, since, watchLink } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	$effect(() => watchLink());

	const id = $derived(decodeURIComponent(page.params.id ?? '').toLowerCase());
	const pair = $derived(link.state?.links.find((p) => p.a === id || p.b === id));
	// Remember who was at the other end, so both screens stay up (saying "Not linked") once it ends.
	let last = $state<{ a: string; b: string; kind: string } | null>(null);
	$effect(() => {
		if (pair) last = { a: pair.a, b: pair.b, kind: pair.kind };
	});
	const shown = $derived(pair ?? last);
</script>

<svelte:head><title>Watch {nameOf(id)} · PPPP</title></svelte:head>

<SubPage>
	<section class="watch">
		{#if shown}
			<header>
				<h1>{shown.kind === 'TRADE' ? 'Trade' : 'Link battle'}: {nameOf(shown.a)} vs {nameOf(shown.b)}</h1>
				<p class="muted">
					{#if pair}<span class="live">●</span> Live · linked {clock(since(pair.since))} · you're watching, nothing you press reaches their games{:else}This link has ended.{/if}
				</p>
			</header>
			<div class="screens">
				{#each [shown.a, shown.b] as user (user)}
					<figure>
						<figcaption><Avatar user={user} size={28} /> <b>{nameOf(user)}</b></figcaption>
						<div class="screen"><GameScreen watch={user} /></div>
					</figure>
				{/each}
			</div>
		{:else if link.state}
			<p class="muted">{nameOf(id)} is not linked with anyone right now.</p>
		{/if}
		<p><a href="/play">← Back to your game</a></p>
	</section>
</SubPage>

<style>
	.watch {
		max-width: 1200px;
		margin: 0 auto;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	h1 {
		margin: 0;
		font-size: 22px;
	}
	header p {
		margin: 4px 0 0;
	}
	.live {
		color: var(--danger);
	}
	.screens {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
		gap: 16px;
	}
	figure {
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	figcaption {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.screen {
		height: min(60vh, 520px);
	}
	.muted {
		color: var(--muted);
	}
</style>
