<!-- The top bar on /play, /scoreboard and the Pib profiles: logo, Play | Scoreboard | Live | My profile (Live with a red
     dot while others are linked), "Linked with X"
     while a link cable is in, who is online and the account menu. Phones on /play get their own tabs instead of the nav. -->
<script lang="ts">
	import { page } from '$app/state';
	import Presence from '#lib/components/Presence.svelte';
	import UserMenu from '#lib/components/UserMenu.svelte';
	import { link, watchLink } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import type { Me } from '#lib/session.svelte.ts';
	import banner from '#lib/assets/banner-v2.png';
	import ppppLogo from '#lib/assets/ppppv2-logo.png';

	// ownTabs: the page has its own tabs on phones, so the nav hides there (/play once a game is picked).
	let { me, ownTabs }: { me: Me; ownTabs?: boolean } = $props();
	$effect(() => watchLink());

	const mine = $derived(`/pib/${encodeURIComponent(me.id)}`);
	const path = $derived(page.url.pathname);
	// Another Pib's profile belongs to the scoreboard.
	const current = $derived(path === mine ? mine : path.startsWith('/pib/') ? '/scoreboard' : path.startsWith('/watch') ? '/watch' : path);
	// Link sessions other Pibs are in: something to watch.
	const live = $derived((link.state?.links ?? []).filter((p) => p.a !== me.id && p.b !== me.id).length);
	const peer = $derived(link.state?.session?.peer);
</script>

<header class="app-header" class:on-play={ownTabs ?? path === '/play'}>
	<div class="brand">
		<a href="/play" class="home">
			<img class="logo" src={banner} alt="Pibs&amp;PibettesPlayPokemon V2" width="940" height="113" />
			<img class="short" src={ppppLogo} alt="PPPP V2" width="480" height="174" />
		</a>
		<nav aria-label="Main">
			{#each [['/play', 'Play'], ['/scoreboard', 'Scoreboard'], ['/watch', 'Live'], [mine, 'My profile']] as [href, label] (label)}
				<a {href} aria-current={current === href ? 'page' : undefined}
					>{label}{#if href === '/watch' && live}<span class="live-dot" aria-hidden="true"></span><span class="sr">, {live} running</span>{/if}</a
				>
			{/each}
		</nav>
		{#if peer}<span class="chip status-linked"><span class="status-dot"></span>Linked with {nameOf(peer)}</span>{/if}
	</div>
	<div class="user">
		<Presence />
		<UserMenu {me} />
	</div>
</header>

<style>
	header {
		position: relative;
		z-index: 2; /* above a page's fixed backdrop (the game picker's title screen) */
		background: color-mix(in srgb, var(--bg) 88%, transparent); /* the page colour: readable over that backdrop */
		backdrop-filter: blur(6px);
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		padding: 16px 32px;
		border-bottom: 1px solid var(--border);
	}
	.brand,
	.user {
		display: flex;
		align-items: center;
		gap: 16px;
		flex-wrap: wrap;
		min-width: 0;
	}
	.home {
		display: block;
	}
	/* The banner is cropped to its lettering, so this is the height of the letters themselves (as big as v1's). */
	.logo {
		display: block;
		height: 46px;
		width: auto;
		max-width: 100%;
	}
	/* Phones: the PPPP V2 logo instead of the long banner. */
	.short {
		display: none;
		height: 36px;
		width: auto;
	}
	nav {
		display: flex;
		gap: 2px;
	}
	nav a {
		height: 36px;
		padding: 0 14px;
		display: inline-flex;
		align-items: center;
		border-radius: 8px;
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
		text-decoration: none;
		white-space: nowrap;
	}
	nav a:hover {
		color: var(--text);
	}
	.live-dot {
		width: 7px;
		height: 7px;
		margin-left: 6px;
		border-radius: 50%;
		background: #e3350d;
		animation: live-pulse 1.4s ease-in-out infinite;
	}
	@keyframes live-pulse {
		50% {
			opacity: 0.35;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.live-dot {
			animation: none;
		}
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
	nav a[aria-current='page'] {
		color: var(--accent-text);
		background: var(--accent-tint);
	}
	a:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 3px 9px;
		border-radius: 999px;
		background: var(--link-tint);
		border: 1px solid var(--link);
		font-size: 12px;
		font-weight: 600;
		white-space: nowrap;
	}
	@media (max-width: 760px) {
		header {
			padding: 8px 16px;
			gap: 8px;
		}
		.logo {
			display: none;
		}
		.short {
			display: block;
		}
		.brand,
		.user {
			gap: 8px;
		}
		.user {
			margin-left: auto;
		}
		nav a {
			padding: 0 10px;
		}
		/* /play on a phone: one row, its own Play / Scores / Chat tabs under the header. */
		.on-play {
			flex-wrap: nowrap;
		}
		.on-play nav,
		.on-play .chip {
			display: none;
		}
	}
</style>
