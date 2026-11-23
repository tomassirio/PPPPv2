<!-- The top bar on /play, /scoreboard and the Pib profiles: logo, Play | Scoreboard | Live | My profile (Live with a red
     dot while others are linked), "Linked with X"
     while a link cable is in, who is online and the account menu. Phones get a burger menu instead of the nav, the same on
     every page: /play's panels (the desktop's columns, one at a time) and the other pages, each with an icon. On /play
     (onpanel set) a panel item switches the panel under the screen; elsewhere it opens /play?panel=<id>. Live, a whole
     page, comes after a divider. The drawer opens with the logo and the player's trainer card (game, rank, Trainer
     Score, link status), which leads to their profile; Sign out sits at its bottom. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import { page } from '$app/state';
	import Presence from '#lib/components/Presence.svelte';
	import UserMenu from '#lib/components/UserMenu.svelte';
	import { link, watchLink } from '#lib/link.svelte.ts';
	import { logout } from '#lib/session.svelte.ts';
	import { nameOf, needPicture, usernameOf } from '#lib/pictures.svelte.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import GradeChip from '#lib/components/GradeChip.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { versionOf } from '#lib/play.ts';
	import type { Me } from '#lib/session.svelte.ts';
	import banner from '#lib/assets/banner-v2.png';
	import ppppLogo from '#lib/assets/ppppv2-logo.png';

	// panel/onpanel/badges: /play's shown panel, how to switch it, and what is new on each (chat count, activity).
	let { me, panel, onpanel, badges = {} }: { me: Me; panel?: string; onpanel?: (id: string) => void; badges?: Record<string, number | boolean> } =
		$props();
	let menu = $state<HTMLDialogElement>();
	const news = $derived(Object.entries(badges).some(([id, b]) => b && id !== panel));
	$effect(() => watchLink());
	$effect(() => {
		needPicture(me.id); // your nickname on the My profile tab
	});

	const mine = $derived(`/pib/${encodeURIComponent(me.id)}`);
	const path = $derived(page.url.pathname);
	// Another Pib's profile belongs to the scoreboard.
	const current = $derived(path === mine ? mine : path.startsWith('/pib/') ? '/scoreboard' : path.startsWith('/watch') ? '/watch' : path);
	// Link sessions other Pibs are in: something to watch.
	const live = $derived((link.state?.links ?? []).filter((p) => p.a !== me.id && p.b !== me.id).length);
	const peer = $derived(link.state?.session?.peer);
	// The trainer card at the top of the drawer: the player's row in the standings (rows come ranked).
	$effect(() => watchBoard());
	const rank = $derived(board.rows.findIndex((r) => r.userId === me.id));
	const row = $derived(rank < 0 ? null : board.rows[rank]);
	const version = $derived(row ? versionOf(row.game, board.names) : null);
	const NAV = $derived([['/play', 'Play'], ['/scoreboard', 'Scoreboard'], ['/watch', 'Live'], [mine, 'My profile']]);
	// The burger menu: a panel of /play, or a page. Icons are static SVG path data (24x24, stroked).
	// panel: shown under the game screen (page: its full-screen page, lit while on it); href: a page of its own.
	type Item = { label: string; d: string; panel?: string; page?: string; href?: string };
	const ITEMS = $derived<Item[]>([
		{ label: 'Play', panel: 'play', d: 'M6 6h12a4 4 0 0 1 4 4v4a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4v-4a4 4 0 0 1 4-4zM7 12h4M9 10v4M15 11h.01M18 13h.01' },
		{ label: 'Link cable', panel: 'link', d: 'M8 3v4m8-4v4M6 7h12v4a6 6 0 0 1-12 0zM12 17v4' },
		{ label: 'Your run', panel: 'run', d: 'M5 21V4M5 4h11l-2 4 2 4H5' },
		{ label: 'Scoreboard', panel: 'scores', page: '/scoreboard', d: 'M6 20v-8m6 8V4m6 16v-5' },
		{ label: 'Activity', panel: 'activity', d: 'M3 12h4l3-8 4 16 3-8h4' },
		{ label: 'Chat', panel: 'chat', d: 'M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z' },
		{ label: 'Live', href: '/watch', d: 'M12 12h.01M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8' }
	]);

	// While the cable is in, the game is where you belong: every in-app link that leaves /play is disabled
	// (<html data-linked>: greyed out in app.css, and clicks and Enter on them are stopped here, as CSS alone
	// would still let the keyboard through). Play stays open, so a Pib elsewhere can always get back.
	const leaves = (a: HTMLAnchorElement) => {
		const url = new URL(a.href, location.href);
		return url.origin === location.origin && !url.pathname.startsWith('/play') && !url.pathname.startsWith('/api/');
	};
	$effect(() => {
		const on = !!peer;
		document.documentElement.toggleAttribute('data-linked', on);
		if (!on) return;
		const stop = (e: Event) => {
			const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
			if (a && leaves(a) && (e.type === 'click' || (e as KeyboardEvent).key === 'Enter')) {
				e.preventDefault();
				e.stopPropagation();
			}
		};
		addEventListener('click', stop, true);
		addEventListener('keydown', stop, true);
		return () => {
			removeEventListener('click', stop, true);
			removeEventListener('keydown', stop, true);
			document.documentElement.removeAttribute('data-linked');
		};
	});
</script>

<header class="app-header" class:on-play={path === '/play'}>
	<div class="brand">
		<button type="button" class="burger" aria-label="Menu{news ? ', something new' : ''}" aria-haspopup="dialog" onclick={() => {
				menu?.showModal();
				menu?.focus(); // the drawer itself, not its first item (a focus ring there looked like a selection)
			}}>
			<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
				><path d="M4 7h16M4 12h16M4 17h16" /></svg
			>
			{#if news}<span class="news" aria-hidden="true"></span>{/if}
		</button>
		<a href="/play" class="home">
			<img class="logo" src={banner} alt="Pibs&amp;PibettesPlayPokemon V2" width="940" height="113" />
			<img class="short" src={ppppLogo} alt="PPPP V2" width="480" height="174" />
		</a>
		<nav aria-label="Main">
			{#each NAV as [href, label] (href)}
				<a {href} class:play={href === '/play'} aria-current={current === href ? 'page' : undefined} title={href === mine ? `My profile · ${usernameOf(me.id)}` : undefined}
					>{#if href === '/play'}<svg class="play-icon" viewBox="0 0 12 12" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M3 1.5v9l7.5-4.5z" /></svg>{/if}{href === mine ? nameOf(me.id) : label}{#if href === '/watch' && live}<span class="live-dot" aria-hidden="true"></span><span class="sr">, {live} running</span>{/if}</a
				>
			{/each}
		</nav>
		{#if peer}<span class="chip status-linked"><span class="status-dot"></span>Linked with <Name user={peer} /></span>{/if}
	</div>
	<div class="user">
		<Presence />
		<UserMenu {me} />
	</div>
</header>

<!-- Phones: the menu as a drawer from the left. Escape, a pick or a tap outside closes it. -->
<dialog class="drawer" bind:this={menu} aria-label="Menu" tabindex="-1" onclick={(e) => e.target === menu && menu.close()}>
	<nav class="drawer-body" aria-label="Menu">
		<div class="drawer-top">
			<img class="drawer-logo" src={ppppLogo} alt="PPPP V2" width="480" height="174" />
			<button type="button" class="drawer-close" aria-label="Close menu" onclick={() => menu?.close()}>
				<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</div>
		<a class="card" href={mine} aria-current={current === mine ? 'page' : undefined} onclick={() => menu?.close()}>
			<Avatar user={me.id} size={48} />
			<span class="card-text">
				<strong class="card-name">{nameOf(me.id)}</strong>
				<span class="card-line">
					{#if version}<span class="ver-{version}">{version}</span>{/if}
					{#if row}<span>#{rank + 1} of {board.rows.length}</span>{:else}<span>{usernameOf(me.id)}</span>{/if}
				</span>
				{#if row}
					<span class="card-line"><span class="score">{row.trainerScore}</span> {row.title} <GradeChip grade={row.grade} /></span>
				{/if}
				{#if peer}<span class="card-line linked"><span class="status-dot"></span>Linked with <Name user={peer} /></span>{/if}
			</span>
			<svg class="chev" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
				><path d="M9 6l6 6-6 6" /></svg
			>
			<span class="sr">, your profile</span>
		</a>
		{#each ITEMS as it, i (it.label)}
			{@const badge = it.panel ? badges[it.panel] : false}
			{@const here = it.panel ? (onpanel ? panel === it.panel : current === it.page) : current === it.href}
			{#if it.href && !ITEMS[i - 1]?.href}<hr />{/if}
			{#snippet body()}
				<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
					><path d={it.d} /></svg
				>
				{it.label}
				{#if badge}<span class="count">{typeof badge === 'number' ? (badge > 99 ? '99+' : badge) : 'new'}</span>{/if}
				{#if it.href === '/watch' && live}<span class="live-dot" aria-hidden="true"></span><span class="sr">, {live} running</span>{/if}
			{/snippet}
			{#if it.panel && onpanel}
				<button
					type="button"
					class="item"
					aria-current={here ? 'true' : undefined}
					onclick={() => {
						onpanel(it.panel!);
						menu?.close();
					}}>{@render body()}</button
				>
			{:else}
				<a class="item" href={it.href ?? `/play?panel=${it.panel}`} aria-current={here ? 'page' : undefined} onclick={() => menu?.close()}>{@render body()}</a>
			{/if}
		{/each}
		<button type="button" class="item sign-out" onclick={logout}>
			<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
				><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H3" /></svg
			>
			Sign out
		</button>
	</nav>
</dialog>

<style>
	header {
		position: relative;
		/* Its own layer: above the game picker's fixed backdrop, and the account menu inside it above every page's
		   content (scoreboard rows set their own z-index). Under dialogs and sheets. */
		z-index: 20;
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
	.brand nav {
		display: flex;
		gap: 2px;
	}
	.brand nav a {
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
	.brand nav a:hover {
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
	.brand nav a[aria-current='page'] {
		color: var(--accent-text);
		background: var(--accent-tint);
	}
	/* Play: the way back into your game, a button of its own. */
	.brand nav a.play {
		display: inline-flex;
		align-items: center;
		gap: 7px;
		margin-right: 6px;
		color: var(--ink);
		background: var(--accent);
		font-weight: 800;
		box-shadow: 0 2px 0 color-mix(in srgb, var(--accent) 55%, #000), 0 4px 14px -4px color-mix(in srgb, var(--accent) 70%, transparent);
		transition: transform 0.12s, box-shadow 0.12s;
	}
	.brand nav a.play:hover {
		color: var(--ink);
		background: color-mix(in srgb, var(--accent) 88%, #fff);
		transform: translateY(-1px);
	}
	.brand nav a.play:active {
		transform: translateY(1px);
		box-shadow: 0 1px 0 color-mix(in srgb, var(--accent) 55%, #000);
	}
	.brand nav a.play[aria-current='page'] {
		color: var(--ink);
		background: var(--accent);
		box-shadow: 0 0 0 2px var(--panel), 0 0 0 4px var(--accent), 0 4px 14px -4px color-mix(in srgb, var(--accent) 70%, transparent);
	}
	@media (prefers-reduced-motion: reduce) {
		.brand nav a.play {
			transition: none;
		}
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
	.burger {
		display: none;
		position: relative;
		flex: none;
		width: 40px;
		height: 40px;
		align-items: center;
		justify-content: center;
		margin-left: -8px;
		padding: 0;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--text);
	}
	.burger:focus-visible,
	.item:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.news {
		position: absolute;
		top: 7px;
		right: 6px;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--danger);
	}
	.drawer {
		position: fixed;
		inset: 0 auto 0 0;
		width: min(300px, 85vw);
		max-width: none;
		height: 100dvh;
		max-height: none;
		margin: 0;
		padding: 0;
		border: none;
		border-right: 1px solid var(--border);
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 0 32px rgb(0 0 0 / 0.35);
	}
	.drawer[open] {
		animation: slide-in 0.2s ease-out;
	}
	@keyframes slide-in {
		from {
			translate: -100% 0;
		}
	}
	.drawer::backdrop {
		background: rgb(8 11 13 / 0.55);
	}
	.drawer-body {
		min-height: 100%;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: calc(16px + env(safe-area-inset-top)) 12px calc(16px + env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left));
	}
	/* At the bottom of the drawer, apart from where to go. */
	.sign-out {
		margin-top: auto;
		border-top: 1px solid var(--border);
		border-radius: 0 0 10px 10px;
	}
	.drawer-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 4px 0 12px;
	}
	.drawer-logo {
		display: block;
		height: 44px;
		width: auto;
	}
	.drawer-close {
		width: 40px;
		height: 40px;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
	}
	.card {
		display: flex;
		align-items: center;
		gap: 12px;
		margin: 14px 0 10px;
		padding: 12px;
		border: 1px solid var(--border);
		border-radius: 14px;
		background: var(--sunk);
		color: var(--text);
		text-decoration: none;
	}
	.card[aria-current] {
		border-color: var(--accent);
	}
	.card-text {
		flex: 1 1 auto;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 3px;
	}
	.card-name {
		font-size: 16px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.card-line {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 4px 8px;
		font-size: 13px;
		color: var(--muted);
	}
	.card-line .score {
		font-weight: 700;
		color: var(--text);
		font-variant-numeric: tabular-nums;
	}
	.card-line.linked {
		color: var(--link);
	}
	.card .chev {
		flex: none;
		color: var(--muted);
	}
	.drawer-close:focus-visible,
	.card:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.drawer:focus {
		outline: none;
	}
	.drawer hr {
		margin: 10px 12px;
		border: none;
		border-top: 1px solid var(--border);
	}
	.item svg {
		flex: none;
		color: var(--muted);
	}
	.item[aria-current] svg {
		color: inherit;
	}
	.item {
		height: 48px;
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 0 12px;
		border: none;
		border-radius: 10px;
		background: none;
		color: var(--text);
		font: 600 16px var(--body-font, inherit);
		text-align: left;
		text-decoration: none;
	}
	.item[aria-current] {
		color: var(--accent-text);
		background: var(--accent-tint);
	}
	.count {
		margin-left: auto;
		min-width: 20px;
		height: 20px;
		padding: 0 6px;
		box-sizing: border-box;
		border-radius: 999px;
		background: var(--danger);
		color: #fff;
		font-size: 11px;
		line-height: 20px;
		text-align: center;
	}
	@media (prefers-reduced-motion: reduce) {
		.drawer[open] {
			animation: none;
		}
	}
	@media (max-width: 760px) {
		.burger {
			display: flex;
		}
		.brand nav {
			display: none;
		}
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
		header {
			flex-wrap: nowrap;
		}
		/* /play shows the link as a banner over the screen. */
		.on-play .chip {
			display: none;
		}
	}
</style>
