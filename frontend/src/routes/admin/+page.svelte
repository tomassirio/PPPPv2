<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { isAdmin, loadMe, session } from '#lib/session.svelte.ts';
	import { notice } from '#lib/components/admin/admin.svelte.ts';
	import CartridgesPanel from '#lib/components/admin/CartridgesPanel.svelte';
	import PlayersPanel from '#lib/components/admin/PlayersPanel.svelte';
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
			><a href="/play">Back to play</a>{#if session.me}<UserMenu me={session.me} />{/if}</span
		>
	</header>

	{#if session.me && isAdmin(session.me)}
		<p class="notice" class:error={notice.error} role="status" aria-live="polite">{notice.text}</p>
		<main>
			<div class="column">
				<h2 class="group">Game</h2>
				<CartridgesPanel />
				<PlayersPanel />
			</div>
			<div class="column">
				<h2 class="group">Players &amp; settings</h2>
				<AccessRequestsPanel />
				<PeoplePanel />
				<SettingsPanel />
			</div>
		</main>
	{:else if session.me}
		<section class="panel"><p>Admins only. <a href="/play">Go to play</a></p></section>
	{:else if loadFailed}
		<section class="panel" role="alert"><p>Couldn't load your session. The server may be restarting. <a href="/" data-sveltekit-reload>Back to sign in</a></p></section>
	{/if}
</div>
