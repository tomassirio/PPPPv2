<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { isAdmin, loadMe, session } from '#lib/session.svelte.ts';
	import { notice } from '#lib/components/admin/admin.svelte.ts';
	import CartridgesPanel from '#lib/components/admin/CartridgesPanel.svelte';
	import SettingsPanel from '#lib/components/admin/SettingsPanel.svelte';
	import PeoplePanel from '#lib/components/admin/PeoplePanel.svelte';
	import AccessRequestsPanel from '#lib/components/admin/AccessRequestsPanel.svelte';
	import UserMenu from '#lib/components/UserMenu.svelte';
	import '#lib/components/admin/admin.css';

	let loadFailed = $state(false);

	onMount(() => {
		loadMe().then(
			(me) => {
				if (!me) goto('/');
			},
			() => (loadFailed = true)
		);
	});
</script>

<svelte:head><title>Admin · Pibs&PibettesPlayPokemonV2</title></svelte:head>

<div class="admin">
	<header>
		<h1 class="pixel">Admin</h1>
		<span class="header-end"
			><a class="play" href="/play"
				><svg viewBox="0 0 12 12" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M3 1.5v9l7.5-4.5z" /></svg>Back to play</a
			>{#if session.me}<UserMenu me={session.me} />{/if}</span
		>
	</header>

	{#if session.me && isAdmin(session.me)}
		<!-- Always in the DOM (a live region); shows as a toast while it has something to say. -->
		<div class="notice-slot" role="status" aria-live="polite">
			{#if notice.text}
				<p class="notice" class:error={notice.error}>
					{#if notice.error}
						<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
							><path d="M12 3l10 18H2z" /><path d="M12 10v5M12 18h.01" /></svg
						>
					{:else}
						<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
							><path d="M5 12l5 5 9-10" /></svg
						>
					{/if}
					<span class="msg">{notice.text}</span>
					<button type="button" class="x" aria-label="Dismiss" onclick={() => (notice.text = '')}>
						<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
					</button>
				</p>
			{/if}
		</div>
		<main>
			<div class="column">
				<h2 class="group">Game</h2>
				<CartridgesPanel />
			</div>
			<div class="column">
				<h2 class="group">Access &amp; settings</h2>
				<AccessRequestsPanel />
				<SettingsPanel />
			</div>
		</main>
		<div class="wide">
			<PeoplePanel />
		</div>
	{:else if session.me}
		<section class="panel"><p>Admins only. <a href="/play">Go to play</a></p></section>
	{:else if loadFailed}
		<section class="panel" role="alert"><p>Couldn't load your session. The server may be restarting. <a href="/" data-sveltekit-reload>Back to sign in</a></p></section>
	{/if}
</div>
