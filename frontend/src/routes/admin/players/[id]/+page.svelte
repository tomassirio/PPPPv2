<!-- Admin: one player. Their Game Boy: its screen (live from /ws/watch/{id} while it runs, admins may watch any
     running console; a powered-off screen otherwise), its state, last action, when it shuts down, the game save
     (downloadable) and resume point, and Close. Their stats from the profile (GET /players/{id}/profile), and Reset. -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { isAdmin, loadMe, session } from '#lib/session.svelte.ts';
	import { notice, act } from '#lib/components/admin/admin.svelte.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import GiftsPanel from '#lib/components/admin/GiftsPanel.svelte';
	import FeedIcon from '#lib/components/FeedIcon.svelte';
	import GradeChip from '#lib/components/GradeChip.svelte';
	import GameScreen from '#lib/components/GameScreen.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import UserMenu from '#lib/components/UserMenu.svelte';
	import { activityText, ago, type Profile } from '#lib/play.ts';
	import { color, cover, type Cartridge } from '#lib/cartridges.ts';
	import { nameOf, needPicture, usernameOf } from '#lib/pictures.svelte.ts';
	import '#lib/components/admin/admin.css';

	type FileInfo = { size: number; modified: number } | null;
	type Session = {
		userId: string;
		cartridgeId: string | null;
		state: 'RUNNING' | 'IDLE' | 'PAUSED' | 'CLOSED';
		linkedWith: string | null;
		lastActive: number | null;
		closesAt: number | null;
		save: FileInfo;
		snapshot: FileInfo;
	};

	const id = $derived((page.params.id ?? '').toLowerCase());
	let gb = $state<Session | null>(null);
	let profile = $state<Profile | null>(null);
	let carts = $state<Cartridge[]>([]);
	let now = $state(Date.now());
	let loadFailed = $state(false);

	onMount(() => {
		loadMe().then(
			(me) => {
				if (!me) goto('/');
			},
			() => (loadFailed = true)
		);
	});

	async function loadSession() {
		gb = (await act('Load Game Boy', () => api<Session>('GET', `/admin/sessions/${encodeURIComponent(id)}`), undefined, true)) ?? gb;
		now = Date.now();
	}
	$effect(() => {
		if (!session.me || !isAdmin(session.me)) return;
		const user = id;
		needPicture(user);
		loadSession();
		api<Profile>('GET', `/players/${encodeURIComponent(user)}/profile`)
			.then((p) => (profile = p))
			.catch(() => (profile = null));
		api<Cartridge[]>('GET', '/admin/cartridges')
			.then((c) => (carts = c))
			.catch(() => {});
		const poll = setInterval(loadSession, 5000);
		const tick = setInterval(() => (now = Date.now()), 1000);
		return () => {
			clearInterval(poll);
			clearInterval(tick);
		};
	});

	async function closeGb() {
		await act(`Close ${id}'s Game Boy`, () => api('POST', `/admin/sessions/${encodeURIComponent(id)}/close`), `Close ${nameOf(id)}'s Game Boy now? Its state is parked; they resume where they were.`);
		loadSession();
	}
	// God message: shown only to them, over whatever page they have open; they need a tab open to get it.
	let godText = $state('');
	let online = $state<boolean | null>(null);
	$effect(() => on('PRESENCE', (viewers: { id: string }[]) => (online = viewers.some((v) => v.id === id))));
	const isOnline = $derived(online ?? profile?.row.online ?? false);
	async function sendGod(e: SubmitEvent) {
		e.preventDefault();
		const text = godText.trim();
		if (!text || !isOnline) return;
		const sent = await act(`God message to ${nameOf(id)}`, () => api('POST', `/admin/players/${encodeURIComponent(id)}/god-message`, { text }).then(() => true));
		if (sent) godText = '';
	}

	async function reset() {
		const done = await act(
			`Reset ${id}`,
			() => api('DELETE', `/admin/players/${encodeURIComponent(id)}`).then(() => true),
			`Reset ${nameOf(id)}'s run? This deletes their game choice, save, points and activity. They pick a game again. It cannot be undone.`
		);
		if (done) {
			profile = null;
			loadSession();
		}
	}

	const kb = (n: number) => (n < 1024 ? `${n} B` : `${Math.round(n / 1024)} KB`);
	const when = (t: number | null | undefined) => (t ? (now - t < 60_000 ? 'just now' : `${ago(now - t)} ago`) : '–');
	const left = (t: number | null) => {
		if (!t) return '';
		const s = Math.max(0, Math.round((t - now) / 1000));
		return s < 60 ? `${s} s` : `${Math.floor(s / 60)} min`;
	};
	// Paused: it stopped but its state could not be saved, so it stays in memory (Close tries again).
	const LABEL = { RUNNING: 'Running', IDLE: 'Idle', PAUSED: 'Paused (not saved)', CLOSED: 'Turned off' };
	const row = $derived(profile?.row);
	const pct = (n: number, of: number) => `${Math.min(100, Math.round((n / of) * 100))}%`;
	const cartOf = (id: string | null | undefined) => carts.find((c) => c.id === id);
	const cart = $derived(cartOf(gb?.cartridgeId ?? row?.game));
	// What the off screen says: why it is not running.
	const OFF = { IDLE: 'Paused', PAUSED: 'Paused', CLOSED: 'Off' };
</script>

<svelte:head><title>{nameOf(id)} · Admin · PPPPv2</title></svelte:head>

<div class="admin">
	<header>
		<h1 class="pixel"><a class="crumb" href="/admin">Admin</a> / Player</h1>
		<span class="header-end"
			><a class="play" href="/play"
				><svg viewBox="0 0 12 12" width="11" height="11" fill="currentColor" aria-hidden="true"><path d="M3 1.5v9l7.5-4.5z" /></svg>Back to play</a
			>{#if session.me}<UserMenu me={session.me} />{/if}</span
		>
	</header>

	{#if session.me && isAdmin(session.me)}
		<div class="notice-slot" role="status" aria-live="polite">
			{#if notice.text}
				<p class="notice" class:error={notice.error}>
					<span class="msg">{notice.text}</span>
					<button type="button" class="x" aria-label="Dismiss" onclick={() => (notice.text = '')}>
						<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
					</button>
				</p>
			{/if}
		</div>

		<section class="panel who">
			<Avatar user={id} size={64} />
			<div class="grow">
				<h2 class="name">{nameOf(id)}</h2>
				<span class="muted">{usernameOf(id)} · {id}</span>
				<div class="chips">
					{#if row?.game}
						{@const c = cartOf(row.game)}
						<span class="chip game" style:--cart={c ? color(c) : null}>{c?.name ?? row.game}</span>
					{:else if profile !== null}
						<span class="chip">No game yet</span>
					{/if}
					{#if row?.trainerName}<span class="chip">Trainer {row.trainerName}</span>{/if}
					{#if row?.location}<span class="chip">{row.location}</span>{/if}
					{#if row}<span class="chip" class:on={row.online}><span class="dot"></span>{row.online ? 'Online' : 'Offline'}</span>{/if}
				</div>
			</div>
			<a class="btn" href="/pib/{encodeURIComponent(id)}">Public profile</a>
		</section>

		<main class="cols">
			<section class="panel" aria-labelledby="gb-h">
				<div class="between">
					<h2 id="gb-h" class="pixel">Game Boy</h2>
					{#if gb}<span class="state s-{gb.state.toLowerCase()}"><span class="dot"></span>{LABEL[gb.state]}</span>{/if}
				</div>
				<div class="gb">
					<div class="screen">
						{#if gb?.state === 'RUNNING'}
							<GameScreen watch={id} />
						{:else}
							<!-- Powered off (or paused): an unlit LCD in the same bezel as the live one. -->
							<div class="bezel">
								<div class="lcd" role="img" aria-label="{nameOf(id)}'s Game Boy is {gb ? LABEL[gb.state].toLowerCase() : 'loading'}">
									<span class="pixel">{gb ? OFF[gb.state as keyof typeof OFF] : '…'}</span>
								</div>
								<div class="strip"><span class="led" aria-hidden="true">●</span> {gb?.state === 'CLOSED' || !gb ? 'POWER OFF' : 'PAUSED'}</div>
							</div>
						{/if}
					</div>
					{#if gb}
						<div class="info">
							<div class="cart" style:--cart={cart ? color(cart) : null}>
								{#if cart && cover(cart)}<img src={cover(cart)} alt="" width="40" height="40" />{/if}
								<div>
									<span class="label">Game</span>
									<b>{cart?.name ?? gb.cartridgeId ?? 'Not picked yet'}</b>
								</div>
							</div>
							<dl class="tiles">
								<div><dt>Last action</dt><dd>{when(gb.lastActive)}</dd></div>
								<div>
									<dt>{gb.linkedWith ? 'Linked with' : gb.state === 'RUNNING' ? 'Pauses' : 'Shuts down'}</dt>
									<dd>{gb.linkedWith ? nameOf(gb.linkedWith) : gb.closesAt ? `in ${left(gb.closesAt)}` : '–'}</dd>
								</div>
								<div>
									<dt>Game save</dt>
									<dd>{#if gb.save}{kb(gb.save.size)}<small>{when(gb.save.modified)}</small>{:else}<span class="muted">None yet</span>{/if}</dd>
								</div>
								<div>
									<dt>Resume point</dt>
									<dd>{#if gb.snapshot}{kb(gb.snapshot.size)}<small>{when(gb.snapshot.modified)}</small>{:else}<span class="muted">None</span>{/if}</dd>
								</div>
							</dl>
							<div class="row">
								{#if gb.save}<a class="btn" href="/api/v1/admin/sessions/{encodeURIComponent(id)}/save" download>Download save</a>{/if}
								{#if gb.state !== 'CLOSED'}<button class="danger" onclick={closeGb}>Close Game Boy</button>{/if}
							</div>
						</div>
					{/if}
				</div>
			</section>

			<section class="panel" aria-labelledby="stats-h">
				<h2 id="stats-h" class="pixel">Stats</h2>
				{#if row && profile}
					<div class="score">
						<div>
							<span class="label">Trainer Score</span>
							<span class="big">{row.trainerScore}<small> / 1000</small></span>
							<span class="muted">{row.title}</span>
						</div>
						<GradeChip grade={row.grade} rating={row.trainerScore} size={14} />
					</div>
					<div class="bar" aria-hidden="true"><span style:width={pct(row.trainerScore, 1000)}></span></div>
					<dl class="tiles three">
						<div><dt>Points</dt><dd>{row.points.toLocaleString()}</dd></div>
						<div>
							<dt>Badges</dt>
							<dd>{row.badges}<small>of 16</small><span class="bar" aria-hidden="true"><span style:width={pct(row.badges, 16)}></span></span></dd>
						</div>
						<div>
							<dt>Pokédex</dt>
							<dd>{profile.stats.caught}<small>{profile.stats.seen} seen</small><span class="bar" aria-hidden="true"><span style:width={pct(profile.stats.caught, 251)}></span></span></dd>
						</div>
						<div><dt>Achievements</dt><dd>{row.achievementsUnlocked}<small>{row.achievementPoints} pts</small></dd></div>
						<div><dt>Link</dt><dd>{row.link?.wins ?? 0} W · {row.link?.losses ?? 0} L<small>{row.link?.trades ?? 0} trades</small></dd></div>
						<div><dt>Play time</dt><dd>{profile.stats.playTime ?? '–'}</dd></div>
					</dl>
					{#if row.party?.length}
						<div class="party">
							<span class="label">Party</span>
							<PartyIcons party={row.party} size={36} />
						</div>
					{/if}
				{:else}
					<p class="muted">No run yet: they have not picked a game.</p>
				{/if}
			</section>

			<section class="panel" aria-labelledby="recent-h">
				<h2 id="recent-h" class="pixel">Recent</h2>
				{#if profile?.recent.length}
					<ul class="recent">
						{#each profile.recent as e, i (i)}
							<li>
								<span class="icon"><FeedIcon {e} /></span>
								<span class="what">{activityText(e, nameOf(id), nameOf)}</span>
								<span class="muted">{when(e.at)}</span>
							</li>
						{/each}
					</ul>
				{:else}
					<p class="muted">Nothing yet.</p>
				{/if}
			</section>

			<section class="panel god" aria-labelledby="god-h">
				<h2 id="god-h" class="pixel">God message</h2>
				<p class="muted">Glows across {nameOf(id)}'s Game Boy screen for a few seconds. Nobody else sees it.</p>
				<form onsubmit={sendGod}>
					<label class="sr-only" for="god-text">Message</label>
					<!-- Enter sends; Shift+Enter starts a new line. -->
					<textarea
						id="god-text"
						bind:value={godText}
						maxlength="200"
						rows="2"
						placeholder="Thou shalt catch them all…"
						onkeydown={(e) => {
							if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
								e.preventDefault();
								e.currentTarget.form?.requestSubmit();
							}
						}}
					></textarea>
					<div class="between">
						<span class="muted small">{isOnline ? `${godText.trim().length} / 200` : `${nameOf(id)} has no tab open`}</span>
						<button class="send" disabled={!godText.trim() || !isOnline}>Send from above</button>
					</div>
				</form>
			</section>

			<GiftsPanel to={id} />

			<section class="panel danger-zone" aria-labelledby="danger-h">
				<h2 id="danger-h" class="pixel">Danger zone</h2>
				<p class="muted">Reset deletes their game choice, saves, points, achievements and activity. They pick a game again. It cannot be undone.</p>
				<button class="danger" onclick={reset}>Reset run</button>
			</section>
		</main>
	{:else if session.me}
		<section class="panel"><p>Admins only. <a href="/play">Go to play</a></p></section>
	{:else if loadFailed}
		<section class="panel" role="alert"><p>Couldn't load your session. <a href="/" data-sveltekit-reload>Back to sign in</a></p></section>
	{/if}
</div>

<style>
	.crumb {
		color: inherit;
		text-decoration: none;
	}
	.crumb:hover {
		text-decoration: underline;
	}
	.who {
		flex-direction: row !important;
		align-items: center;
		margin-bottom: 16px;
	}
	.grow {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.name {
		font-size: 18px !important;
		color: var(--text);
	}
	.cols {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 480px), 1fr));
		gap: 16px;
		align-items: start;
	}
	.between {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}
	.row {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.btn {
		display: inline-flex;
		align-items: center;
		height: 34px;
		padding: 0 14px;
		border-radius: 8px;
		border: 1px solid var(--link);
		background: var(--link-tint);
		color: var(--link);
		font-weight: 600;
		text-decoration: none;
		cursor: pointer;
	}
	/* Screen and its facts side by side; stacked when the panel is narrow. */
	.gb {
		display: flex;
		flex-wrap: wrap;
		gap: 20px;
		align-items: flex-start;
	}
	.screen {
		flex: 1 1 280px;
		max-width: 400px;
	}
	.info {
		flex: 1 1 220px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	/* The powered-off screen: GameScreen's bezel with an unlit LCD. */
	.bezel {
		background: #3b3f45;
		border-radius: 10px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.lcd {
		aspect-ratio: 160 / 144;
		border-radius: 2px;
		background: #6b7353;
		box-shadow: inset 0 0 24px rgb(0 0 0 / 0.45);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 12px;
		text-align: center;
		color: #3d4330;
		font-size: 14px;
	}
	.strip {
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.06em;
		color: #9aa0a6;
	}
	.led {
		color: #5a5f66;
	}
	.cart {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 10px 12px;
		border-radius: 10px;
		border: 1px solid var(--border);
		border-left: 4px solid var(--cart, var(--border));
	}
	.cart img {
		border-radius: 6px;
		object-fit: cover;
	}
	.cart div {
		display: flex;
		flex-direction: column;
	}
	.label,
	.tiles dt {
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.tiles {
		margin: 0;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.tiles > div {
		padding: 10px 12px;
		border-radius: 10px;
		background: var(--sunk);
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.tiles dd {
		margin: 0;
		font-weight: 600;
		display: flex;
		flex-direction: column;
	}
	.tiles small {
		font-weight: 400;
		color: var(--muted);
	}
	.state {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		font-weight: 600;
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--muted);
	}
	.s-running .dot {
		background: var(--grass);
	}
	.s-idle .dot {
		background: var(--accent);
	}
	.s-paused .dot {
		background: var(--danger);
	}
	.s-closed {
		color: var(--muted);
	}
	.recent {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
	}
	.recent li {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 7px 0;
		font-size: 13px;
		border-bottom: 1px solid var(--border);
	}
	.recent li:last-child {
		border-bottom: 0;
	}
	.recent .icon {
		width: 24px;
		height: 24px;
		display: grid;
		place-items: center;
		flex: none;
	}
	.recent .what {
		flex: 1;
		min-width: 0;
	}
	.recent .muted {
		white-space: nowrap;
	}
	.god {
		border-color: color-mix(in srgb, #8b5cf6 45%, var(--border));
	}
	.god p {
		margin: 0;
	}
	.god form {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.god textarea {
		resize: vertical;
		font: inherit;
		padding: 10px 12px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		background: var(--sunk);
		color: var(--text);
	}
	.god textarea:focus-visible {
		outline: 2px solid #8b5cf6;
		outline-offset: 1px;
	}
	.small {
		font-size: 12px;
	}
	.send {
		height: 34px;
		padding: 0 16px;
		border-radius: 8px;
		border: 1px solid #a78bfa;
		background: linear-gradient(180deg, #7c4dff, #5b2fd1);
		color: #fff;
		font-weight: 600;
		cursor: pointer;
		box-shadow: 0 0 14px rgb(124 77 255 / 0.45);
	}
	.send:disabled {
		opacity: 0.45;
		cursor: not-allowed;
		box-shadow: none;
	}
	.send:focus-visible {
		outline: 3px solid #c4b5fd;
		outline-offset: 2px;
	}
	.danger-zone {
		align-items: flex-start;
		border-color: color-mix(in srgb, var(--danger) 40%, var(--border));
	}
	.danger-zone p {
		margin: 0;
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin-top: 6px;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 3px 10px;
		border-radius: 999px;
		background: var(--sunk);
		border: 1px solid var(--border);
		font-size: 12px;
		font-weight: 600;
	}
	.chip.game {
		border-color: var(--cart, var(--border));
		color: var(--cart, var(--text));
	}
	.chip .dot {
		width: 7px;
		height: 7px;
	}
	.chip.on .dot {
		background: var(--grass);
	}
	.score {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}
	.score > div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.big {
		font-size: 28px;
		font-weight: 700;
		line-height: 1.1;
	}
	.big small {
		font-size: 14px;
		font-weight: 500;
		color: var(--muted);
	}
	.bar {
		display: block;
		height: 6px;
		border-radius: 3px;
		background: var(--border);
		overflow: hidden;
	}
	.bar span {
		display: block;
		height: 100%;
		border-radius: 3px;
		background: var(--accent);
	}
	.tiles.three {
		grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
	}
	.tiles .bar {
		margin-top: 4px;
		height: 4px;
		background: var(--border);
	}
	.party {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	/* Phones: the player's header as a centred stack (avatar, name, e-mail, chips), Public profile full width. */
	@media (max-width: 760px) {
		.who {
			flex-direction: column !important;
			text-align: center;
			gap: 10px;
		}
		.who .grow {
			align-items: center;
			width: 100%;
		}
		.who .muted {
			overflow-wrap: anywhere;
		}
		.chips {
			justify-content: center;
		}
		.who > .btn {
			align-self: stretch;
			justify-content: center;
		}
	}
</style>
