<!-- The frame for pages besides /play (the scoreboard, Pib profiles): signs in (or back to /), the top bar, then the
     page's content. An incoming trade or battle offer pops up here too; once linked, it goes to /play. -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { goto } from '$app/navigation';
	import AppHeader from '#lib/components/AppHeader.svelte';
	import LinkChallenge from '#lib/components/LinkChallenge.svelte';
	import { link, watchLink } from '#lib/link.svelte.ts';
	import { loadMe, session } from '#lib/session.svelte.ts';

	let { children }: { children: Snippet } = $props();
	let failed = $state(false);

	$effect(() => watchLink());
	// A link session started (they accepted, or someone accepted yours): off to the Game Boy.
	$effect(() => {
		if (link.state?.session) goto('/play');
	});

	$effect(() => {
		loadMe()
			.then((m) => {
				if (!m) goto('/');
			})
			.catch(() => (failed = true));
	});
</script>

{#if session.me}
	<AppHeader me={session.me} />
	<main>{@render children()}</main>
	<LinkChallenge sheet floating />
{:else if failed}
	<p class="error" role="alert">Couldn't load your session. The server may be restarting. <a href="/play">Try again</a></p>
{/if}

<style>
	main {
		max-width: 1480px;
		margin: 0 auto;
		padding: 20px 24px 40px;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		gap: 20px;
		min-width: 0;
	}
	.error {
		max-width: 420px;
		margin: 96px auto;
		padding: 0 16px;
		text-align: center;
		color: var(--muted);
	}
	@media (max-width: 640px) {
		main {
			padding: 16px;
		}
	}
</style>
