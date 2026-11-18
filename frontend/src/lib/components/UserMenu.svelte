<!-- The avatar in the top bar opens this menu: who you are, light/dark (Solrock/Lunatone, with a reveal animation), Pokédex banners (wide desktops), My profile (not on it already),
     Settings (key bindings), Admin (admins only), Play (on /admin only: elsewhere the top bar's tabs have it), Sign out. -->
<script lang="ts">
	import { nameOf, usernameOf } from '#lib/pictures.svelte.ts';
	import { page } from '$app/state';
	import { isAdmin, logout, type Me } from '#lib/session.svelte.ts';
	import { pending, watchRequests } from '#lib/requests.svelte.ts';
	import { switchTheme, theme } from '#lib/theme.svelte.ts';
	import { dexBanners, setDexBanners } from '#lib/dex.svelte.ts';
	import SettingsDialog from '#lib/components/SettingsDialog.svelte';

	let { me }: { me: Me } = $props();

	let open = $state(false);
	$effect(() => {
		if (isAdmin(me)) watchRequests();
	});
	const waiting = $derived(isAdmin(me) ? pending.count : 0);
	let root = $state<HTMLDivElement>();
	let trigger = $state<HTMLButtonElement>();
	let pictureFailed = $state(false);
	let settingsOpen = $state(false);

	const initials = $derived(
		nameOf(me.id)
			.split(/[\s@.]+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0].toUpperCase())
			.join('')
	);

	// Auto follows the OS until the player picks one; the toggle shows whichever is in effect.
	let osDark = $state(typeof matchMedia !== 'undefined' && matchMedia('(prefers-color-scheme: dark)').matches);
	$effect(() => {
		const q = matchMedia('(prefers-color-scheme: dark)');
		const onChange = () => (osDark = q.matches);
		q.addEventListener('change', onChange);
		return () => q.removeEventListener('change', onChange);
	});
	const dark = $derived(theme.value === 'dark' || (theme.value === 'auto' && osDark));

	function close(refocus = false) {
		open = false;
		if (refocus) trigger?.focus();
	}

	function onclick(e: MouseEvent) {
		if (open && root && !root.contains(e.target as Node)) close();
	}

	function onkeydown(e: KeyboardEvent) {
		if (open && e.key === 'Escape') close(true);
	}
</script>

<svelte:window {onclick} {onkeydown} />

<div class="menu" bind:this={root}>
	<button
		type="button"
		class="trigger"
		bind:this={trigger}
		aria-haspopup="true"
		aria-expanded={open}
		aria-controls="user-menu"
		aria-label="Account menu for {nameOf(me.id)}{waiting ? `, ${waiting} access ${waiting === 1 ? 'request' : 'requests'} waiting` : ''}"
		title="{nameOf(me.id)} · {usernameOf(me.id)}"
		onclick={() => (open = !open)}
	>
		{#if me.picture && !pictureFailed}
			<!-- Google photo URLs reject requests that carry a Referer. -->
			<img class="avatar" src={me.picture} alt="" referrerpolicy="no-referrer" onerror={() => (pictureFailed = true)} />
		{:else}
			<span class="avatar initials" aria-hidden="true">{initials}</span>
		{/if}
		{#if waiting}<span class="dot" aria-hidden="true">{waiting > 9 ? '9+' : waiting}</span>{/if}
		<svg class="caret" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"
			><path d="M6 9l6 6 6-6" /></svg
		>
	</button>

	{#if open}
		<div class="panel dropdown" id="user-menu">
			<div class="who">
				<strong>{nameOf(me.id)}</strong>
				<span>{usernameOf(me.id)}</span>
			</div>

			<div class="row">
				<span id="theme-label">Theme</span>
				<div class="toggle" role="group" aria-labelledby="theme-label">
					<button type="button" aria-pressed={!dark} aria-label="Light" title="Light (Solrock)" onclick={(e) => switchTheme('light', e.currentTarget)}>
						<img src="/theme/solrock.png" alt="" width="22" height="22" />
					</button>
					<button type="button" aria-pressed={dark} aria-label="Dark" title="Dark (Lunatone)" onclick={(e) => switchTheme('dark', e.currentTarget)}>
						<img src="/theme/lunatone.png" alt="" width="22" height="22" />
					</button>
				</div>
			</div>

			{#if !page.url.pathname.startsWith('/admin')}
				<label class="row dex-row">
					<span>Pokédex on the sides</span>
					<input type="checkbox" checked={dexBanners.on} onchange={(e) => setDexBanners(e.currentTarget.checked)} />
				</label>
			{/if}

			{#if page.url.pathname.startsWith('/admin')}
				<a class="item" href="/play">Play</a>
			{/if}
			{#if page.url.pathname !== `/pib/${encodeURIComponent(me.id)}`}
				<a class="item" href="/pib/{encodeURIComponent(me.id)}">My profile</a>
			{/if}
			<button
				type="button"
				class="item"
				onclick={() => {
					close();
					settingsOpen = true;
				}}>Settings</button
			>
			{#if isAdmin(me) && !page.url.pathname.startsWith('/admin')}
				<a class="item" href="/admin"
					>Admin{#if waiting}<span class="badge">{waiting} {waiting === 1 ? 'request' : 'requests'}</span>{/if}</a
				>
			{/if}
			<button type="button" class="item" onclick={logout}>Sign out</button>
		</div>
	{/if}
	<SettingsDialog bind:open={settingsOpen} />
</div>

<style>
	/* Only where /play shows the banners (its wide media query); elsewhere the option would do nothing. */
	.row.dex-row {
		display: none;
		cursor: pointer;
	}
	@media (min-width: 1520px) and (min-height: 600px) {
		.row.dex-row {
			display: flex;
		}
	}
	.dex-row input {
		width: 16px;
		height: 16px;
		margin: 0;
		accent-color: var(--accent-text);
		cursor: pointer;
	}
	.menu {
		position: relative;
	}
	/* Access requests waiting (admins): a red count on the avatar, and on the Admin item. */
	.dot {
		position: absolute;
		top: -2px;
		left: 26px;
		min-width: 18px;
		height: 18px;
		padding: 0 5px;
		box-sizing: border-box;
		border-radius: 9px;
		background: #e5383b;
		color: #fff;
		font: 700 11px/18px var(--font, system-ui);
		text-align: center;
		box-shadow: 0 0 0 2px var(--bg);
	}
	.badge {
		margin-left: 8px;
		padding: 1px 7px;
		border-radius: 999px;
		background: #e5383b;
		color: #fff;
		font-size: 11px;
		font-weight: 700;
	}
	.trigger {
		position: relative;
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 2px 6px 2px 2px;
		border: 1px solid transparent;
		border-radius: 999px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.trigger:hover,
	.trigger[aria-expanded='true'] {
		border-color: var(--border);
	}
	.avatar {
		width: 36px;
		height: 36px;
		border-radius: 50%;
		object-fit: cover;
		display: block;
	}
	.initials {
		background: #2a9d8f;
		color: #fff;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 13px;
		font-weight: 700;
	}
	.dropdown {
		position: absolute;
		top: calc(100% + 8px);
		right: 0;
		z-index: 20;
		min-width: 220px;
		max-width: calc(100vw - 32px);
		box-sizing: border-box;
		padding: 8px;
		display: flex;
		flex-direction: column;
		gap: 4px;
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.3);
	}
	.who {
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: 8px 10px 10px;
		border-bottom: 1px solid var(--border);
		margin-bottom: 4px;
	}
	.who strong {
		font-size: 14px;
		color: var(--text);
	}
	.who span {
		font-size: 12px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 6px 10px;
		font-size: 14px;
		color: var(--text);
	}
	/* Sun | moon, one pressed. */
	.toggle {
		display: flex;
		padding: 2px;
		border-radius: 999px;
		background: var(--bg);
		border: 1px solid var(--border);
	}
	.toggle button {
		width: 32px;
		height: 28px;
		display: flex;
		align-items: center;
		justify-content: center;
		border: none;
		border-radius: 999px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.toggle img {
		display: block;
		filter: grayscale(0.85) opacity(0.55);
	}
	.toggle button[aria-pressed='true'] img {
		filter: none;
	}
	@media (prefers-reduced-motion: no-preference) {
		.toggle img {
			transition: filter 0.3s, transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
		}
		/* Solrock spins up, Lunatone rocks into place when picked. */
		.toggle button[aria-label='Light'][aria-pressed='true'] img {
			transform: rotate(180deg);
		}
		.toggle button[aria-label='Dark'][aria-pressed='true'] img {
			transform: rotate(-25deg);
		}
	}
	.toggle button[aria-pressed='true'] {
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.25);
	}
	.item {
		display: block;
		width: 100%;
		padding: 8px 10px;
		border: none;
		border-radius: 6px;
		background: none;
		font: inherit;
		font-size: 14px;
		text-align: left;
		text-decoration: none;
		color: var(--text);
		cursor: pointer;
		box-sizing: border-box;
	}
	.item:hover {
		background: var(--border);
	}
	button:focus-visible,
	a:focus-visible {
		outline: 3px solid var(--accent-text, var(--accent));
		outline-offset: 2px;
	}
	@media (max-width: 760px) {
		.avatar {
			width: 32px;
			height: 32px;
		}
	}
</style>
