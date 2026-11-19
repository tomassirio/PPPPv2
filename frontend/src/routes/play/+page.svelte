<!-- /play: the player's own game (location sign, screen, pad); the Link cable and "Your run" cards; one card with
     Scoreboard / Activity / Chat tabs. Desktop = three columns (frontend/design/PlayV2.mock.html, #play), two with the
     board underneath on tablets; phone = Play / Scores / Chat tabs under the header, a challenge comes up as a bottom
     sheet and a linked session as a banner over the screen. Held sideways, Link / Scores / Chat are sheets. -->
<script lang="ts">
	import LocationMap from '#lib/components/LocationMap.svelte';
	import Name from '#lib/components/Name.svelte';
	import { goto } from '$app/navigation';
	import { dexBanners } from '#lib/dex.svelte.ts';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { loadMe, session } from '#lib/session.svelte.ts';
	import { currentShell } from '#lib/shell.svelte.ts';
	import { inkFor } from '#lib/shells.ts';
	import Chat from '#lib/components/Chat.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import ResetControl from '#lib/components/ResetControl.svelte';
	import SpeedControl from '#lib/components/SpeedControl.svelte';
	import GameStatus from '#lib/components/GameStatus.svelte';
	import GameToasts from '#lib/components/GameToasts.svelte';
	import ChatOverlay from '#lib/components/ChatOverlay.svelte';
	import ChatOverlayToggle from '#lib/components/ChatOverlayToggle.svelte';
	import PokedexBanner from '#lib/components/PokedexBanner.svelte';
	import Gamepad from '#lib/components/Gamepad.svelte';
	import GamePicker from '#lib/components/GamePicker.svelte';
	import type { Cartridge } from '#lib/cartridges.ts';
	import LinkPanel from '#lib/components/LinkPanel.svelte';
	import LinkFinder from '#lib/components/LinkFinder.svelte';
	import Scoreboard from '#lib/components/Scoreboard.svelte';
	import Activity from '#lib/components/Activity.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import LinkChallenge from '#lib/components/LinkChallenge.svelte';
	import { board } from '#lib/board.svelte.ts';
	import { act, clock, link, since } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import SoundControl from '#lib/components/SoundControl.svelte';
	import AppHeader from '#lib/components/AppHeader.svelte';
	import { place } from '#lib/play.ts';
	import ShellPicker from '#lib/components/ShellPicker.svelte';
	import ViewportDebug from '#lib/components/ViewportDebug.svelte';
	import PokeballRain from '#lib/components/PokeballRain.svelte';
	import { page } from '$app/state';

	type Tab = 'play' | 'link' | 'scores' | 'chat';
	let tab = $state<Tab>('play');
	let screen = $state<ReturnType<typeof GameScreen>>();
	let talking = $state(false);
	function talk() {
		if (link.state?.session || !screen?.nearby().length) return false;
		talking = true;
		return true;
	}
	$effect(() => {
		if (link.state?.session) talking = false;
	});
	// The desktop's right-hand card.
	type SideTab = 'scores' | 'activity' | 'chat';
	let sideTab = $state<SideTab>('scores');
	const SIDE_TABS: { id: SideTab; label: string }[] = [
		{ id: 'scores', label: 'Scoreboard' },
		{ id: 'activity', label: 'Activity' },
		{ id: 'chat', label: 'Chat' }
	];

	// Phone tabs (Play / Scores / Chat under the header; held sideways an icon rail with Link instead of Play): icons
	// (static SVG paths, not user content) and badges for what changed on other tabs.
	const TABS: { id: Tab; label: string; icon: string }[] = [
		{ id: 'play', label: 'Play', icon: '<rect x="3" y="6" width="18" height="12" rx="4"/><path d="M7 12h4M9 10v4"/><circle cx="15.5" cy="11" r="1"/><circle cx="17.5" cy="13.5" r="1"/>' },
		{ id: 'link', label: 'Link', icon: '<path d="M8 3v4m8-4v4M6 7h12v4a6 6 0 0 1-12 0z"/><path d="M12 17v4"/>' },
		{ id: 'scores', label: 'Scores', icon: '<path d="M6 20v-8m6 8V4m6 16v-5"/>' },
		{ id: 'chat', label: 'Chat', icon: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>' }
	];
	// Keep the phone's screen awake while /play is open and visible (Screen Wake Lock; ignored where unsupported).
	$effect(() => {
		if (!me || !('wakeLock' in navigator)) return;
		let lock: WakeLockSentinel | null = null;
		const request = () => {
			if (document.visibilityState === 'visible') navigator.wakeLock.request('screen').then((l) => (lock = l)).catch(() => {});
		};
		request();
		document.addEventListener('visibilitychange', request);
		return () => {
			document.removeEventListener('visibilitychange', request);
			lock?.release();
		};
	});

	// Phone layout (portrait, or held sideways): tabs instead of columns.
	const PHONE = '(max-width: 760px), (orientation: landscape) and (max-height: 520px)';
	let phone = $state(typeof matchMedia !== 'undefined' && matchMedia(PHONE).matches);
	$effect(() => {
		const q = matchMedia(PHONE);
		const onChange = () => (phone = q.matches);
		q.addEventListener('change', onChange);
		return () => q.removeEventListener('change', onChange);
	});
	// Landscape (phone held sideways): Link, Scores and Chat are sheets over the game; a tap outside the sheet and the
	// rail closes it.
	const LANDSCAPE = '(orientation: landscape) and (max-height: 520px)';
	let landscape = $state(typeof matchMedia !== 'undefined' && matchMedia(LANDSCAPE).matches);
	$effect(() => {
		const q = matchMedia(LANDSCAPE);
		const onChange = () => (landscape = q.matches);
		q.addEventListener('change', onChange);
		return () => q.removeEventListener('change', onChange);
	});
	// Landscape big screen: the screen fills the height and the controls float over it, translucent.
	let bigScreen = $state(read('pppp.bigScreen') === 'on');
	function read(key: string) {
		try {
			return localStorage.getItem(key);
		} catch {
			return null;
		}
	}
	function toggleBigScreen() {
		bigScreen = !bigScreen;
		try {
			localStorage.setItem('pppp.bigScreen', bigScreen ? 'on' : 'off');
		} catch {
			/* private mode: not remembered */
		}
	}
	function closeSheetOnOutsideTap(e: PointerEvent) {
		if (!landscape || tab === 'play') return;
		const t = e.target as Element;
		if (t.closest('.mid, .board, .tabs, header, .shells, .challenge')) return; // sheet, rail, corners, colour picker, challenge
		e.preventDefault();
		e.stopPropagation(); // the tap only closes the sheet; it must not also press a game button
		swallowClick = true; // A/B/Start/Select act on the click that follows
		setTimeout(() => (swallowClick = false), 500); // no click came (a drag): don't eat the next real one
		tab = 'play';
	}
	let swallowClick = false;
	function swallowClosingClick(e: MouseEvent) {
		if (!swallowClick) return;
		swallowClick = false;
		e.preventDefault();
		e.stopPropagation();
	}

	let unreadChat = $state(0);
	let newScores = $state(false);
	const chatShown = $derived(phone ? tab === 'chat' : sideTab === 'chat');
	const scoresShown = $derived(phone ? tab === 'scores' : sideTab !== 'chat');
	const badges = $derived<Record<Tab, number | boolean>>({
		play: false,
		link: false,
		scores: newScores,
		chat: unreadChat
	});
	$effect(() => {
		if (chatShown) unreadChat = 0;
		if (scoresShown) newScores = false;
	});
	// Held sideways, Link and "Your run" share a sheet. Upright there is no Link tab: the Link cable card heads Scores,
	// and "Your run" is the player's own profile (their row in the standings).
	const linkOn = $derived(tab === 'link' || (!landscape && tab === 'scores'));
	const runOn = $derived(landscape && tab === 'link');
	// Phone held sideways: no Link tab upright, so don't leave it selected when the phone turns.
	$effect(() => {
		if (!landscape && tab === 'link') tab = 'scores';
	});
	$effect(() => {
		const offs = [
			on('CHAT_MESSAGE', (m: { kind: string }) => {
				if (!chatShown && m.kind === 'USER') unreadChat++; // only people's messages, not system lines
			}),
			// An admin reset this player's run: back to the game picker.
			on('PLAYER_RESET', (e: { user: string }) => {
				if (myGame && e.user === me?.id) myGame = { ...myGame, game: null };
			}),
			on('PROGRESS', () => {
				if (!scoresShown) newScores = true; // a new line in the activity feed
			})
		];
		return () => offs.forEach((off) => off());
	});

	// Desktop looks the same on every monitor: the layout is designed at a laptop's size and the whole page is
	// zoomed to fit the window (an office monitor renders like the laptop at ~120%, a small window shrinks it), so
	// nothing ever scrolls. The spare width of a wider window goes to the Pokédex banners. Between 0.6 and 1.6.
	const DESKTOP = '(min-width: 1100px) and (min-height: 600px)';
	const REF_W = 1728;
	const REF_H = 968;
	let uiZoom = $state(1);
	let pageH = $state(0);
	$effect(() => {
		const fit = () => {
			uiZoom = matchMedia(DESKTOP).matches
				? Math.min(1.6, Math.max(0.6, Math.min(innerWidth / REF_W, innerHeight / REF_H)))
				: 1;
			pageH = innerHeight / uiZoom;
		};
		fit();
		addEventListener('resize', fit);
		return () => removeEventListener('resize', fit);
	});

	// The screen frame sizes itself to the height left over; on desktop the controller matches its width.
	let screenSlot = $state<HTMLDivElement>();
	// Desktop: the game column is as wide as the biggest screen that fits the height left after the location sign
	// and the controller's own height, capped by the width the row leaves after the other two columns' minimums
	// (340 + 360), the two 24px gaps and main's 32px side padding. The screen slot gets exactly the bezel's height;
	// whatever height is left (when the width cap wins) goes to the controller, which stretches: never a gap between
	// screen and controls. Bezel: 32px of padding across, 68px of padding + LIVE strip down, the LCD 160:144.
	let gameEl = $state<HTMLElement>();
	let mainW = $state(0);
	let fit = $state({ w: 0, slot: 0 });
	function measure() {
		const g = gameEl;
		if (!g) return;
		const pad = g.querySelector<HTMLElement>('.pad');
		const shell = pad?.querySelector<HTMLElement>('.shell');
		const parts = shell ? [...shell.children].filter((c) => getComputedStyle(c).position !== 'absolute') : [];
		if (!pad || !shell || !parts.length) return;
		const cs = getComputedStyle(shell);
		const shellH = parseFloat(cs.paddingTop) + parseFloat(cs.paddingBottom) + parts.reduce((h, c) => h + (c as HTMLElement).offsetHeight, 0) + (parts.length - 1) * parseFloat(cs.rowGap || '0');
		const padExtra = [...pad.children].filter((c) => c !== shell).reduce((h, c) => h + (c as HTMLElement).offsetHeight, 0);
		const shown = [...g.children].filter((c) => getComputedStyle(c).display !== 'none') as HTMLElement[];
		const others = shown.filter((c) => c !== screenSlot && c !== pad).reduce((h, c) => h + c.offsetHeight, 0);
		const gaps = (shown.length - 1) * parseFloat(getComputedStyle(g).rowGap || '0');
		const avail = g.clientHeight - others - gaps - shellH - padExtra - 2; // height the screen slot may take (2px for rounding)
		const cap = mainW ? mainW - 64 - 48 - 340 - 360 : Infinity;
		const w = Math.max(0, Math.round(Math.min(((avail - 68) * 160) / 144 + 32, cap)));
		fit = { w, slot: Math.round(((w - 32) * 144) / 160 + 68) };
	}
	$effect(() => {
		if (!gameEl) return;
		void mainW;
		void where.city; // the location sign coming or going changes the height left
		void link.state?.session;
		queueMicrotask(measure); // after the sign is (un)rendered
		const watch = new ResizeObserver(() => measure());
		watch.observe(gameEl);
		return () => watch.disconnect();
	});
	let frameWidth = $state(0);
	$effect(() => {
		const frame = screenSlot?.querySelector('.bezel');
		if (!frame) return;
		const watch = new ResizeObserver(([e]) => (frameWidth = Math.round(e.borderBoxSize[0].inlineSize)));
		watch.observe(frame);
		return () => watch.disconnect();
	});

	const me = $derived(session.me);
	// Where the player is, from their scoreboard row (the location sign over the screen).
	const where = $derived(place(board.rows.find((r) => r.userId === me?.id)?.location));
	let loadFailed = $state(false);
	// The player's own cartridge: null until /me/game answers, then `game` is null until they pick one.
	let myGame = $state<{ game: string | null; available: Cartridge[]; speed: number | null } | null>(null);

	$effect(() => {
		loadMe()
			.then((m) => {
				if (!m) goto('/');
			})
			.catch(() => (loadFailed = true));
	});

	// Only once signed in.
	$effect(() => {
		if (me) api<NonNullable<typeof myGame>>('GET', '/me/game').then((g) => (myGame = g));
	});
	// Cartridge id -> name, for "Pokémon Gold" next to other players.
	$effect(() => {
		if (myGame) board.names = Object.fromEntries(myGame.available.map((c) => [c.id, c.name]));
	});
</script>

<svelte:window onpointerdowncapture={closeSheetOnOutsideTap} onclickcapture={swallowClosingClick} />

{#if me && myGame && !myGame.game}
	<AppHeader {me} ownTabs={false} />
	<GamePicker available={myGame.available} onpicked={(g) => (myGame = { ...myGame!, game: g })} />
{:else if me && myGame}
	<div class="page play-root" class:big={bigScreen} style:zoom={uiZoom === 1 ? null : uiZoom} style:--page-h={uiZoom === 1 ? null : `${pageH}px`} style:--plate={currentShell.value.shell} style:--plate-ink={inkFor(currentShell.value.shell)}>
		<AppHeader {me} />

		<div class="stage">
			<aside class="dex" class:off-dex={!dexBanners.on} aria-label="Pokédex, first half"><PokedexBanner half={1} /></aside>
		<main bind:clientWidth={mainW}>
			<section class="game" aria-label="Game" bind:this={gameEl} style:--frame-w={frameWidth ? `${frameWidth}px` : null} style:--game-w={fit.w > 0 ? `${fit.w}px` : null} style:--slot-h={fit.slot > 68 ? `${fit.slot}px` : null}>
				{#if link.state?.session}
					{@const ls = link.state.session}
					<div class="linked-banner">
						<span class="pair" aria-hidden="true"><Avatar user={me.id} /><span class="cable"></span><Avatar user={ls.peer} /></span>
						<span class="lb-text"
							><b>Linked with <Name user={ls.peer} /></b><span>{ls.kind === 'TRADE' ? 'Trade' : 'Battle'} · linked {clock(since(ls.since))}</span></span
						>
						<button type="button" class="unplug" onclick={() => act('DELETE', '/link/session')}>Unplug</button>
					</div>
				{/if}
				{#if link.state?.session || where.city}
					{@const ls = link.state?.session}
					{@const label = ls ? (ls.kind === 'TRADE' ? 'Trade Center' : 'Colosseum') : where.city}
					<p class="sign" class:linked={!!ls}>
						<LocationMap user={me.id} {label}>
						<svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"
							><path d="M10 1a6 6 0 0 0-6 6c0 4.5 6 12 6 12s6-7.5 6-12a6 6 0 0 0-6-6zm0 8.5A2.5 2.5 0 1 1 10 4.5a2.5 2.5 0 0 1 0 5z" /></svg
						>
						<span class="sr-only">You are at </span>
						<span class="place pixel">{label.toUpperCase()}</span>
						{#if ls || where.spot}<span class="sub">{ls ? `Cable Club · with ${nameOf(ls.peer)}` : where.spot}</span>{/if}
						</LocationMap>
					</p>
				{/if}
				<div class="screen-slot" bind:this={screenSlot}>
					<GameScreen bind:this={screen}>
						{#snippet leading()}<SpeedControl speed={myGame?.speed} /><ResetControl />{/snippet}
						{#snippet controls()}<span class="bezel-controls"><ChatOverlayToggle /><SoundControl /></span>{/snippet}
					</GameScreen>
					<GameToasts />
					<ChatOverlay hidden={chatShown} />
				</div>
				<nav class="tabs" aria-label="Panels">
					{#each TABS.filter((t) => t.id !== (landscape ? 'play' : 'link')) as t (t.id)}
						{@const badge = badges[t.id]}
						<button
							type="button"
							aria-pressed={tab === t.id}
							aria-label="{t.label}{badge ? `, ${typeof badge === 'number' ? `${badge} new` : 'new'}` : ''}"
							onclick={() => (tab = t.id)}
						>
							<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
								>{@html t.icon}</svg
							>
							<span>{t.label}</span>
							{#if badge}<span class="badge" aria-hidden="true">{typeof badge === 'number' ? (badge > 99 ? '99+' : badge) : ''}</span>{/if}
						</button>
					{/each}
					<button type="button" class="big-toggle" aria-pressed={bigScreen} aria-label="Big screen" title="Big screen" onclick={toggleBigScreen}>
						<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
							><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" /></svg
						>
					</button>
					<span class="rail-picker"><ShellPicker up /></span>
				</nav>
				<div class="pad" class:off={tab !== 'play'}>
					<Gamepad {talk} />
					{#if talking}
						<LinkFinder bind:open={talking} nearby={screen?.nearby() ?? []} />
					{/if}
				</div>
			</section>
			<section class="mid" class:sheet-off={tab !== 'link'} aria-label="Link cable and your run">
				<div class="slot link" class:p-off={!linkOn}><LinkPanel /></div>
				<div class="slot run" class:p-off={!runOn}><GameStatus /></div>
			</section>
			<section class="board panel" class:sheet-off={tab !== 'scores' && tab !== 'chat'} aria-label="Scoreboard, activity and chat">
				<div class="board-tabs" role="group" aria-label="Show">
					{#each SIDE_TABS as t (t.id)}
						{@const unread = t.id === 'chat' ? unreadChat : 0}
						<button type="button" aria-pressed={sideTab === t.id} aria-label="{t.label}{unread ? `, ${unread} new` : ''}" onclick={() => (sideTab = t.id)}
							>{t.label}{#if unread}<span class="count" aria-hidden="true">{unread > 99 ? '99+' : unread}</span>{/if}</button
						>
					{/each}
				</div>
				<div class="pane" class:d-off={sideTab !== 'scores'} class:p-off={tab !== 'scores'}><Scoreboard /></div>
				<div class="pane" class:d-off={sideTab !== 'activity'} class:p-off={true}><Activity label="Activity" /></div>
				<div class="pane chat-pane" class:d-off={sideTab !== 'chat'} class:p-off={tab !== 'chat'}><Chat /></div>
			</section>
		</main>
			<aside class="dex" class:off-dex={!dexBanners.on} aria-label="Pokédex, second half"><PokedexBanner half={2} /></aside>
		</div>
		{#if page.url.searchParams.has('debug')}<ViewportDebug />{/if}
	</div>
	<LinkChallenge sheet />
	<PokeballRain />
{:else if loadFailed}
	<section class="load-error" role="alert">
		<p>Couldn't load your session. The server may be restarting.</p>
		<p><a href="/" data-sveltekit-reload>Back to sign in</a> · <button type="button" onclick={() => location.reload()}>Try again</button></p>
	</section>
{/if}

<style>
	.load-error {
		max-width: 420px;
		margin: 96px auto;
		padding: 0 16px;
		text-align: center;
		color: var(--muted);
	}
	.load-error button {
		background: none;
		border: none;
		padding: 0;
		color: var(--accent-text);
		text-decoration: underline;
	}
	.page {
		min-height: 100vh;
	}
	/* The location sign over the screen: pin, landmark in the pixel font, the spot inside it. */
	.sign {
		flex: none;
		margin: 0;
		/* As wide as the Game Boy screen and the controller under it (--frame-w = the bezel's measured width). */
		width: var(--frame-w, auto);
		max-width: 100%;
		align-self: center;
		box-sizing: border-box;
		display: flex;
		align-items: center;
		border-radius: 8px;
		border: 2px solid var(--border-strong);
		background: var(--sunk);
		min-width: 0;
	}
	.sign :global(.where) {
		flex: 1;
		min-width: 0;
		padding: 8px 10px;
		gap: 10px;
	}
	.sign svg {
		flex: none;
		width: 18px;
		height: 18px;
		color: var(--accent-text);
	}
	.sign .place {
		flex: 1;
		min-width: 0;
		font-size: 11px;
		line-height: 1.5;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.sign .sub {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
	}
	.sign.linked {
		border-color: var(--link);
	}
	.sign.linked svg {
		color: var(--link);
	}
	/* Tablets: game | link + your run, the board full width underneath (the mock's 2-column step). */
	@media (min-width: 761px) and (max-width: 1099px), (min-width: 761px) and (max-height: 599px) and (min-height: 521px) {
		main {
			display: grid;
			grid-template-columns: minmax(340px, 440px) minmax(0, 1fr);
			align-items: start;
		}
		.game {
			max-width: none;
		}
		.board {
			grid-column: 1 / -1;
			contain: none;
			height: 640px;
		}
	}
	main {
		display: flex;
		flex-wrap: wrap;
		gap: 24px;
		padding: 24px 32px;
		max-width: 1440px;
		margin: 0 auto;
		box-sizing: border-box;
		/* Columns stretch to the tallest one (the game), so chat fills the rest of the screen. */
		align-items: stretch;
	}
	.game {
		flex: 1 1 520px;
		max-width: 560px;
		display: flex;
		flex-direction: column;
		gap: 16px;
		min-width: 0;
	}
	.screen-slot {
		position: relative;
	}
	.bezel-controls {
		display: flex;
		align-items: center;
		gap: 4px;
	}
	.pad {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	/* Middle column: Link cable, then Your run. */
	.mid {
		flex: 1 1 320px;
		display: flex;
		flex-direction: column;
		gap: 16px;
		min-width: 0;
	}
	.slot {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	/* Right column: one card, Scoreboard / Activity / Chat. */
	.board {
		flex: 1 1 340px;
		/* Its size never drives the row height: it is as tall as the game column. */
		contain: size;
		min-height: 480px;
		display: flex;
		flex-direction: column;
		min-width: 0;
		overflow: hidden;
	}
	.board-tabs {
		flex: none;
		display: flex;
	}
	.board-tabs button {
		flex: 1 1 0;
		height: 48px;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
		border: none;
		background: transparent;
		color: var(--muted);
		font-weight: 600;
		font-size: 14px;
		box-shadow: inset 0 -1px 0 var(--border);
	}
	.board-tabs button[aria-pressed='true'] {
		color: var(--accent-text);
		box-shadow: inset 0 -3px 0 var(--accent);
	}
	.board-tabs .count {
		min-width: 18px;
		height: 18px;
		padding: 0 5px;
		box-sizing: border-box;
		border-radius: 999px;
		background: var(--danger);
		color: #fff;
		font-size: 11px;
		line-height: 18px;
	}
	.pane {
		--bleed: 20px; /* the signed-in player's scoreboard row reaches the card's edges */
		flex: 1 1 0;
		min-height: 0;
		overflow-y: auto;
		padding: 20px var(--bleed);
		display: flex;
		flex-direction: column;
	}
	.pane.d-off {
		display: none;
	}
	.chat-pane {
		padding: 0;
		overflow: hidden;
	}
	.chat-pane > :global(*) {
		flex: 1 1 auto;
		min-height: 0;
	}
	/* Chat sits inside the tabbed card: no second card around it, and the tab names it. */
	.chat-pane :global(.chat.panel) {
		border: none;
		border-radius: 0;
		background: none;
	}
	.chat-pane :global(.chat.panel > header > h2) {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
		white-space: nowrap;
	}
	/* Phones only: the linked session over the screen (desktop shows it in the Link cable card). */
	.linked-banner {
		display: none;
	}
	.tabs {
		display: none;
	}
	/* Landscape only (beats the phone tab-bar's ".tabs button" rule). */
	.tabs .big-toggle,
	.tabs .rail-picker {
		display: none;
	}
	/* Pokédex side banners on desktop only (see the media query after the desktop one). */
	.stage {
		display: contents;
	}
	.dex {
		display: none;
	}

	/* Desktop: the page is exactly one screen tall and never scrolls. The game screen takes the height the
	   controller leaves; a column that still doesn't fit (very short windows) scrolls inside itself. */
	@media (min-width: 1100px) and (min-height: 600px) {
		/* Only while /play is shown (this CSS stays loaded after navigating to /admin). */
		:global(html:has(.play-root)),
		:global(body:has(.play-root)) {
			overflow: hidden; /* one-screen layout: the page never scrolls, only panels inside it */
		}
		.page {
			/* Zoomed (see uiZoom): px lengths are scaled by the zoom, viewport units not reliably, so the height
			   comes from JS as the window height in unzoomed px. */
			height: var(--page-h, 100dvh);
			min-height: 0;
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		.page > :global(header) {
			flex: none;
		}
		.stage {
			display: flex;
			flex: 1 1 0;
			min-height: 0;
		}
		main {
			flex: 1 1 auto;
			min-height: 0;
			width: 100%;
			flex-wrap: nowrap;
		}
		.game,
		.mid {
			min-height: 0;
			overflow-y: auto;
		}
		/* Windows taller than the reference shape: the screen is width-bound, so the slot stops at the bezel's
		   height and screen + controller stay together, centred, instead of leaving a gap between them. */
		/* All three columns share the same top and bottom edges: the game starts at the top, the screen takes every
		   pixel of height the controller leaves, and the controller sits on the bottom edge. */
		.game {
			container-type: inline-size;
			justify-content: flex-start;
			overflow: hidden; /* sized to fit by measure(): never a scrollbar, not even for a rounding pixel */
			flex: 0 0 var(--game-w, 40%);
			min-width: 380px;
			max-width: none; /* the base 560px cap would squeeze the measured screen and open a gap */
		}
		.mid {
			flex: 1 1 0;
			min-width: 340px;
		}
		/* Your run stretches to the bottom edge, level with the controller and the board. */
		.mid > .slot.run {
			flex: 1 0 auto;
		}
		.mid > .slot.run > :global(.card) {
			flex: 1 0 auto;
		}
		.board {
			flex: 1 1 0;
			min-width: 360px;
		}
		.screen-slot {
			flex: 0 0 var(--slot-h, 60%);
			min-height: 200px;
		}
		/* The controller takes whatever height is left and spreads its buttons over it. */
		.pad {
			flex: 1 1 auto;
		}
		.pad :global(.shell) {
			flex: 1 1 auto;
			justify-content: space-evenly;
		}
		.screen-slot :global(.fit) {
			container-type: size;
			/* Largest screen that fits: bezel padding 32px across, 68px of padding + LIVE strip down. */
			--bezel-width: min(100cqw, (100cqh - 68px) * 160 / 144 + 32px);
		}
		/* Every pixel of height goes to the screen: tighter page padding and controller. */
		main {
			padding-top: 16px;
			padding-bottom: 16px;
		}
		.pad :global(.shell) {
			padding: 16px 24px;
			gap: 16px;
		}
		.pad {
			width: var(--frame-w, 100%);
			min-width: min(380px, 100%); /* d-pad + A/B need this much on very short windows */
			max-width: 100%;
			align-self: center;
		}
		.board {
			min-height: 0;
		}
	}
	/* The Pokédex as two banners, first half left and second half right, on every desktop: the zoom (uiZoom) makes
	   the page at least REF_W = 1728px wide in its own px, so a 1440px window has the same room as a 1728px one.
	   Each banner gets at least 210px (for 126 entries, 5 columns of ~29px cells at the reference height); the
	   three columns shrink toward their minimum (~1290px) to make room, the banners grow into any margin left. */
	@media (min-width: 1100px) and (min-height: 600px) {
		.dex {
			display: block;
		}
		.dex.off-dex {
			display: none; /* turned off in the account menu */
		}
		.dex {
			flex: 0 1 clamp(210px, 13vw, 260px);
			min-width: 160px;
			padding: 16px 12px;
			box-sizing: border-box;
		}
		/* The columns take the whole width between the Pokédex banners; the banners keep their own width. */
		main {
			flex: 1 1 auto;
			max-width: none;
		}
	}
	:global(.page :focus-visible) {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}

	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		/* Only while /play is shown (this CSS stays loaded after navigating to /admin). */
		:global(html:has(.play-root)),
		:global(body:has(.play-root)) {
			overflow: hidden;
		}
		/* The phone tab bar picks what shows, not the desktop card's tabs. */
		.board-tabs {
			display: none;
		}
		.board {
			contain: none;
		}
		.pane.d-off {
			display: flex;
		}
		.pane.p-off,
		.slot.p-off {
			display: none;
		}
		.pane {
			--bleed: 14px;
			padding: 12px var(--bleed);
		}
		.chat-pane {
			padding: 0;
		}
		.sign {
			display: none;
		}
	}
	@media (max-width: 760px) {
		/* App-like: the page is one screen tall. Tabs under the header, then the screen, then the active tab fills
		   the rest and scrolls inside itself. 100svh: the height with the browser's bars shown, measured
		   right on Android Chrome and Firefox and iOS Chrome. A fixed inset: 0 page takes the large viewport (bars
		   hidden) and innerHeight read at load can be stale, both hid the tab bar below the screen. */
		.page {
			position: fixed;
			top: 0;
			left: 0;
			right: 0;
			height: 100svh;
			min-height: 0; /* the base 100vh is the bars-hidden height on phones: it overrode every height above */
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		.page > :global(header) {
			flex: none;
		}
		main {
			flex: 1 1 0;
			min-height: 0;
			display: flex;
			flex-direction: column;
			flex-wrap: nowrap;
			/* Full width on every tab: the desktop centring (margin: auto) made the column, and the screen in
			   it, shrink to whatever the active tab contained. */
			width: 100%;
			max-width: none;
			margin: 0;
			padding: 0 6px calc(8px + env(safe-area-inset-bottom));
			gap: 8px;
		}
		.game {
			display: contents;
		}
		.screen-slot {
			order: 0;
			flex: none;
			/* The screen is width-bound here, so every pixel of gutter is screen height: thin gutters, and a thin
			   bezel (4px sides, 6px top, 6px under the strip). Width 100vw - 12px gutter - 8px bezel; height adds
			   6 + 4 gap + 28 strip + 6 = 44px. */
			height: min(calc((100vw - 12px - 8px) * 144 / 160 + 44px), 50dvh);
		}
		.screen-slot :global(.fit) {
			container-type: size;
			--bezel-width: min(100cqw, (100cqh - 44px) * 160 / 144 + 8px);
		}
		.screen-slot :global(.fit .bezel) {
			padding: 6px 4px;
			gap: 4px;
		}
		/* Link and Scores cards and the board's panes become the tabs' contents. */
		.mid,
		.board {
			display: contents;
		}
		.pad,
		.slot,
		.pane {
			order: 1;
			flex: 1 1 0;
			min-height: 0;
			max-width: none;
			overflow-y: auto;
		}
		/* Scores: the Link cable card on top (scrolls itself if long), the ranking under it scrolls. */
		.slot.link {
			flex: 0 1 auto;
			max-height: 45%;
		}
		.linked-banner {
			order: 0;
			flex: none;
			display: flex;
			align-items: center;
			gap: 10px;
			padding: 8px 10px;
			border: 1px solid var(--link);
			border-radius: 12px;
			background: var(--link-tint);
			font-size: 13px;
		}
		.linked-banner .pair {
			flex: 1 1 auto;
			min-width: 0;
			display: flex;
			align-items: center;
			gap: 6px;
		}
		.linked-banner .cable {
			flex: 1;
			min-width: 12px;
			height: 2px;
			background: repeating-linear-gradient(90deg, var(--link) 0 8px, transparent 8px 14px);
		}
		.lb-text {
			flex: none;
			display: flex;
			flex-direction: column;
		}
		.lb-text span {
			font-size: 12px;
			color: var(--link);
			font-variant-numeric: tabular-nums;
		}
		.unplug {
			flex: none;
			height: 36px;
			padding: 0 12px;
			border: 1px solid var(--danger);
			border-radius: 8px;
			background: transparent;
			color: var(--danger);
			font-weight: 600;
		}
		/* The controller never scrolls: it keeps its full height and the screen above gives way instead. */
		.screen-slot {
			flex: 0 1 auto;
			min-height: 120px;
		}
		.pad {
			flex: 1 0 auto;
			overflow: hidden;
		}
		.chat-pane {
			overflow: hidden; /* the chat scrolls its own list; the message box stays at the bottom */
		}
		.off {
			display: none;
		}
		.tabs {
			order: -1;
			flex: none;
			display: flex;
			margin: 0 -6px;
			border-bottom: 1px solid var(--border);
		}
		.tabs button {
			position: relative;
			flex: 1 1 0;
			height: 44px;
			display: flex;
			align-items: center;
			justify-content: center;
			gap: 6px;
			padding: 0;
			border: none;
			background: transparent;
			font-size: 14px;
			font-weight: 600;
			color: var(--muted);
		}
		.tabs button svg {
			display: none;
		}
		/* The selected tab in the player's plate colour (pulled toward the text colour on light, like names). */
		.tabs button[aria-pressed='true'] {
			color: color-mix(in oklab, var(--plate, var(--accent-text)), var(--text) var(--name-dim));
			box-shadow: inset 0 -3px 0 var(--accent);
		}
		.badge {
			position: static;
			min-width: 8px;
			height: 16px;
			padding: 0 4px;
			box-sizing: border-box;
			border-radius: 999px;
			background: var(--danger);
			color: #fff;
			font-size: 10px;
			line-height: 16px;
			text-align: center;
		}
		.badge:empty {
			width: 8px;
			min-width: 0;
			height: 8px;
			padding: 0;
		}
	}
	/* Phone held sideways: a Game Boy Advance. D-pad left, screen in the middle, A/B right, Select/Start under
	   the screen; no header or tabs. The pad's own boxes become grid items via display: contents. */
	@media (orientation: landscape) and (max-height: 520px) {
		/* One rule for alignment: the screen is centred, everything else is centred in the gutter beside it
		   (d-pad over the rail on the left, A/B over Select/Start on the right; the bottom pair share a centre line). */
		.page {
			--screen-w: calc((100dvh - 16px - 76px) * 160 / 144 + 32px);
			--gutter: max(0px, calc((100vw - var(--screen-w)) / 2));
			--bottom-row: 37px; /* centre line of the rail and Select/Start, from the bottom */
		}
		.page.big {
			--gutter: min(260px, 34vw); /* the screen fills everything; controls use the outer band */
		}
		.page {
			height: 100dvh;
			min-height: 0;
			display: flex;
			flex-direction: column;
			overflow: hidden;
		}
		.dex,
		.off,
		.page > :global(header .brand) {
			display: none;
		}
		/* Online + account menu top-right, floating over the corner. */
		.page > :global(header) {
			position: fixed;
			inset: 8px max(12px, env(safe-area-inset-right)) auto max(12px, env(safe-area-inset-left));
			z-index: 32;
			padding: 0;
			border: none;
			background: none;
			pointer-events: none;
		}
		.page > :global(header > *) {
			pointer-events: auto;
		}
		.stage,
		.game,
		.pad,
		.pad.off {
			display: contents;
		}
		main {
			flex: 1 1 0;
			min-height: 0;
			max-width: none;
			display: grid;
			grid-template-columns: 1fr auto 1fr;
			grid-template-rows: 1fr;
			grid-template-areas: 'dpad screen ab';
			align-items: center;
			justify-items: center;
			gap: 6px 12px;
			padding: 8px max(20px, env(safe-area-inset-right)) 8px max(20px, env(safe-area-inset-left));
		}
		.screen-slot {
			grid-area: screen;
			/* Full height: Select/Start sit under A/B, not under the screen. */
			height: calc(100dvh - 16px);
			width: calc((100dvh - 16px - 76px) * 160 / 144 + 32px);
		}
		.screen-slot :global(.fit) {
			container-type: size;
			--bezel-width: min(100cqw, (100cqh - 76px) * 160 / 144 + 32px);
		}
		.pad :global(.shell),
		.pad :global(.row) {
			display: contents;
		}
		/* Controls pinned near the edges, a little below the middle, where thumbs rest. */
		.pad :global(.dpad),
		.pad :global(.ab) {
			position: fixed;
			z-index: 20;
			top: 50%;
			translate: -50% -50%;
		}
		.pad :global(.dpad) {
			left: calc(var(--gutter) / 2);
		}
		.pad :global(.ab) {
			left: calc(100vw - var(--gutter) / 2);
		}
		.pad :global(.meta) {
			position: fixed;
			z-index: 20;
			left: calc(100vw - var(--gutter) / 2);
			bottom: var(--bottom-row);
			translate: -50% 50%;
			gap: 8px;
		}
		.pad :global(.controls) {
			display: none;
		}
		/* The colour picker lives inside the rail pill here, not in the pad's corner. */
		.pad :global(.picker-corner) {
			display: none;
		}
		.tabs .rail-picker {
			display: flex;
			align-items: center;
			padding: 0 9px;
		}
		.tabs .big-toggle {
			display: flex;
		}
		.big-toggle[aria-pressed='true'] {
			color: color-mix(in oklab, var(--plate, var(--accent-text)), var(--text) var(--name-dim));
			background: var(--bg);
		}
		/* Big screen: the game stretched edge to edge (wider pixels, on purpose); the controls float over it. */
		.big .screen-slot {
			position: fixed;
			inset: 0;
			z-index: 1;
			width: auto;
			height: auto;
		}
		.big .screen-slot :global(.fit) {
			container-type: normal;
			height: 100%;
		}
		.big .screen-slot :global(.bezel) {
			width: 100%;
			height: 100%;
			padding: 0;
			gap: 0;
			border-radius: 0;
		}
		.big .screen-slot :global(.screen) {
			aspect-ratio: auto;
			flex: 1 1 0;
			min-height: 0;
			border-radius: 0;
		}
		.big .screen-slot :global(.strip) {
			position: fixed;
			top: 10px;
			left: 50%;
			translate: -50% 0;
			z-index: 33;
			min-height: 0;
			padding: 2px 12px;
			border-radius: 999px;
			background: rgb(0 0 0 / 0.5);
		}
		.big .pad :global(.dpad),
		.big .pad :global(.ab),
		.big .pad :global(.meta) {
			position: fixed;
			z-index: 20;
			opacity: 0.55;
		}

		/* Other tabs: an icon rail in the bottom-left corner; Link, Scores and Chat open as a sheet on the right,
		   with the game still visible. Play closes it. */
		.tabs {
			position: fixed;
			left: max(8px, calc(var(--gutter) / 2));
			bottom: var(--bottom-row);
			translate: max(-50%, calc(8px - var(--gutter) / 2)) 50%;
			z-index: 31;
			display: flex;
			gap: 2px;
			padding: 4px;
			border: 1px solid var(--border);
			border-radius: 999px;
			background: var(--panel);
			box-shadow: 0 4px 16px rgb(0 0 0 / 0.3);
		}
		.tabs button {
			position: relative;
			width: 40px;
			height: 40px;
			padding: 0;
			display: flex;
			align-items: center;
			justify-content: center;
			border: none;
			border-radius: 50%;
			background: transparent;
			color: var(--muted);
		}
		.tabs button[aria-pressed='true'] {
			color: color-mix(in oklab, var(--plate, var(--accent-text)), var(--text) var(--name-dim));
			background: var(--bg);
			box-shadow: none;
		}
		.tabs button svg {
			display: block;
		}
		.tabs button span:not(.badge) {
			position: absolute;
			width: 1px;
			height: 1px;
			overflow: hidden;
			clip: rect(0 0 0 0);
		}
		.badge {
			position: absolute;
			top: 2px;
			right: 2px;
			min-width: 8px;
			height: 14px;
			padding: 0 3px;
			box-sizing: border-box;
			border-radius: 999px;
			background: var(--danger);
			color: #fff;
			font-size: 9px;
			line-height: 14px;
		}
		.badge:empty {
			width: 8px;
			height: 8px;
			padding: 0;
		}
		.mid,
		.board {
			position: fixed;
			top: 8px; /* over the online/account corner, which is fine while a sheet is open */
			right: max(8px, env(safe-area-inset-right));
			bottom: 8px;
			z-index: 33;
			width: min(420px, 52vw);
			box-sizing: border-box;
			padding: 8px;
			border: 1px solid var(--border);
			border-radius: 14px;
			background: var(--bg);
			box-shadow: 0 8px 32px rgb(0 0 0 / 0.4);
			overflow-y: auto;
		}
		.board {
			padding: 0;
			min-height: 0; /* the desktop minimum (480px) is taller than the sheet: it hid the message box */
		}
		.mid {
			gap: 10px;
		}
		.pane {
			flex: 1 1 0;
		}
		/* Sheets slide in from the right and back out (kept rendered, moved off-screen when closed). */
		.mid,
		.board {
			display: flex;
			flex-direction: column;
			transition:
				translate 0.25s ease,
				visibility 0.25s;
		}
		.mid.sheet-off,
		.board.sheet-off {
			translate: calc(100% + 24px) 0;
			visibility: hidden;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.mid,
		.board {
			transition: none;
		}
	}
</style>
