<!-- The frame for pages besides /play (the scoreboard, Pib profiles): signs in (or back to /), the top bar, then the
     page's content. -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { goto } from '$app/navigation';
	import AppHeader from '#lib/components/AppHeader.svelte';
	import { loadMe, session } from '#lib/session.svelte.ts';

	let { children }: { children: Snippet } = $props();
	let failed = $state(false);

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
