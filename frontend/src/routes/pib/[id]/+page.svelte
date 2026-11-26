<!-- /pib/{userId} (frontend/design/PlayV2.mock.html, #profile-jordan): a Pib's profile. Hero (avatar, name, game, status,
     where, playtime, rank, Trainer Score, Offer trade / Challenge while they are ready), party cards (types, item, HP,
     status, moves), achievements, badges, stats and their recent feed. GET /players/{id}/profile, fetched again when
     their scoreboard row changes; an older backend without it gets the scoreboard row and achievement catalogue. -->
<script lang="ts">
	import NicknameEditor from '#lib/components/NicknameEditor.svelte';
	import Name from '#lib/components/Name.svelte';
	import RunSummary from '#lib/components/RunSummary.svelte';
	import FindButton from '#lib/components/FindButton.svelte';
	import LinkHistory from '#lib/components/LinkHistory.svelte';
	import LinkFinder from '#lib/components/LinkFinder.svelte';
	import KindIcon from '#lib/components/KindIcon.svelte';
	import CableClubLocked from '#lib/components/CableClubLocked.svelte';
	import { page } from '$app/state';
	import Activity from '#lib/components/Activity.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import RarityBall from '#lib/components/RarityBall.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import TrainerPicker from '#lib/components/TrainerPicker.svelte';
	import { speciesName } from '#lib/components/PartyIcons.svelte';
	import DexCard from '#lib/components/DexCard.svelte';
	import LocationMap from '#lib/components/LocationMap.svelte';
	import { DexHover } from '#lib/dexhover.svelte.ts';
	import { api } from '#lib/api.ts';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { on } from '#lib/events.ts';
	import { act, left, link, watchLink } from '#lib/link.svelte.ts';
	import {
		badgeMasks,
		bitCount,
		clubClosed,
		hpColor,
		JOHTO,
		KANTO,
		playerStatus,
		profileFrom,
		progressPct,
		RARITIES,
		shownAchievements,
		topLevel,
		TYPE_COLORS,
		TIER_NAMES,
		versionOf,
		type CatalogueEntry,
		type Profile,
		type ScoreRow } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	$effect(() => watchBoard());
	$effect(() => watchLink());

	const id = $derived((page.params.id ?? '').toLowerCase());
	let profile = $state<Profile | null>(null);
	let missing = $state(false);

	async function load(user: string) {
		const path = `/players/${encodeURIComponent(user)}`;
		let p: Profile | null = null;
		try {
			p = await api<Profile>('GET', `${path}/profile`);
		} catch {
			// No /profile on this backend (or a glitch): piece it together from the scoreboard and the catalogue.
			const rows = board.rows.length ? board.rows : await api<ScoreRow[]>('GET', '/scoreboard').catch(() => []);
			const row = rows.find((r) => r.userId === user);
			if (row) {
				const cat = await api<{ achievements: CatalogueEntry[] }>('GET', `${path}/achievements`).catch(() => null);
				p = profileFrom(row, cat?.achievements ?? [], board.activity, speciesName);
			}
		}
		if (user !== id) return; // navigated on meanwhile
		profile = p;
		missing = !p;
	}

	$effect(() => {
		profile = null;
		missing = false;
		load(id);
	});
	// Their row changed (new badge, party, location): fetch the profile again.
	$effect(() => {
		const user = id;
		return on('SCOREBOARD_CHANGED', (rows: ScoreRow[]) => {
			const r = rows.find((x) => x.userId === user);
			if (r && profile && JSON.stringify(r) !== JSON.stringify(profile.row)) load(user);
		});
	});

	const me = $derived(session.me?.id);
	const you = $derived(id === me);
	const rank = $derived(board.rows.findIndex((r) => r.userId === id));
	const row = $derived(board.rows[rank] ?? profile?.row);
	const status = $derived(row ? playerStatus(id, row.online, me, link.state?.lobby ?? [], link.links) : null);
	const version = $derived(row ? versionOf(row.game, board.names) : null);
	const masks = $derived(row ? badgeMasks(row) : { johto: 0, kanto: 0 });
	const johto = $derived(bitCount(masks.johto));
	const achievements = $derived(shownAchievements(profile?.achievements ?? []));
	// The profile below the hero, in tabs so each fits one screen.
	const TABS = [
		{ id: 'overview', label: 'Overview' },
		{ id: 'achievements', label: 'Achievements' },
		{ id: 'badges', label: 'Badges & stats', short: 'Stats' },
		{ id: 'recent', label: 'Recent' }
	] as const;
	let tab = $state<(typeof TABS)[number]['id']>(page.url.searchParams.has('ach') ? 'achievements' : 'overview');
	// A rarity tier picked in the legend shows only that tier's achievements; picking it again shows all.
	let tierFilter = $state<string | null>(null);
	// No pages and no endless list: one row per category (its count and a bar); the open one shows its achievements
	// as a grid of balls, and the ball picked shows its card.
	const categories = $derived([...new Set(achievements.map((a) => a.category ?? 'Other'))]);
	let openCat = $state<string | null>(null);
	let achPeek = $state<string | null>(null);
	const shownAch = $derived(tierFilter ? achievements.filter((a) => a.rarity === tierFilter) : achievements);
	// ?ach=<name> (from an activity entry): that achievement's page, scrolled to and ringed for a moment.
	const spotlight = $derived(page.url.searchParams.get('ach'));
	let spotted = $state<string | null>(null);
	$effect(() => {
		const want = spotlight;
		if (!want || !achievements.length) return;
		const at = achievements.findIndex((x) => x.name === want);
		if (at < 0) return;
		const a = achievements[at];
		tierFilter = null;
		openCat = a.category ?? 'Other';
		achPeek = a.id;
		requestAnimationFrame(() => {
			const el = document.getElementById(`ach-${a.id}`);
			if (!el) return;
			el.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' });
			spotted = a.id;
		});
	});
	const linkAch = $derived(achievements.filter((a) => a.category === 'Link'));
	// The Link achievement a ball is showing (hovered, focused or tapped): its card under the row.
	// A ball clicked pins its card (clicking it again unpins; another moves the pin); hover and focus only preview.
	let peek = $state<string | null>(null);
	let historyOpen = $state(false);
	let pinned = $state<string | null>(null);
	const peeked = $derived(linkAch.find((a) => a.id === peek) ?? null);
	// Link record: the profile's stats when the backend has them, else the scoreboard row's.
	const lr = $derived({
		wins: profile?.stats.linkWins ?? row?.link?.wins ?? 0,
		losses: profile?.stats.linkLosses ?? row?.link?.losses ?? 0,
		trades: profile?.stats.trades ?? row?.link?.trades ?? 0
	});
	const partners = $derived(profile?.stats.partners ?? []);
	let finding = $state(false); // the Find someone to link with dialog
	const got = $derived(achievements.filter((a) => a.unlocked).length);
	const tiers = $derived(
		RARITIES.map((r) => ({ rarity: r, got: achievements.filter((a) => a.rarity === r && a.unlocked).length, of: achievements.filter((a) => a.rarity === r).length }))
	);
	const BALL = { common: 'Poké Ball', uncommon: 'Great Ball', rare: 'Ultra Ball', epic: 'Master Ball', legendary: 'GS Ball' };
	const top = $derived(
		profile?.party.filter((m) => !m.egg).reduce<Profile['party'][number] | null>((best, m) => (!best || m.level > best.level ? m : best), null)
	);
	// Offer trade / Challenge: to this Pib directly, whenever neither of you is busy.
	const ready = $derived(link.state?.lobby.find((p) => p.userId === id)?.kind);
	const busy = $derived(!!(link.state?.session || link.state?.outgoing || link.state?.incoming));
	const waiting = $derived(link.state?.outgoing?.to === id ? link.state.outgoing : null);
	// Gold/Silver/Crystal before the Mystery Egg is back with Elm: no link for them (or for you, viewing someone).
	const closed = $derived(clubClosed(row));
	const meClosed = $derived(!you && clubClosed(board.rows.find((r) => r.userId === me)));
	// They are in a link session with someone (anyone can be offered a link otherwise).
	const theyBusy = $derived(!!link.state?.links?.some((l) => l.a === id || l.b === id));

	const badgeArt = import.meta.glob('../../../lib/assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;
	const art = (name: string, i: number) => badgeArt[`../../../lib/assets/badges/${name.toLowerCase()}.png`] ?? `/api/v1/game/badges/${i}.png`;
	let broken = $state<Record<string, boolean>>({});
	const dexHover = new DexHover(); // hovering a party member: its Pokédex card, as far as the viewer's Pokédex knows it
	const monNote = (m: { level: number; status: string | null; shiny: boolean }) =>
		[`Lv ${m.level}`, m.status, m.shiny && 'shiny'].filter(Boolean).join(' · ');
</script>

<svelte:head><title>{you ? 'Your profile' : nameOf(id)} · PPPPv2</title></svelte:head>

<SubPage>
	<nav class="crumb" aria-label="Breadcrumb"><a href="/scoreboard">← Scoreboard</a><span class="muted">/ {#if you}You{:else}<Name user={id} />{/if}</span></nav>

	{#if missing}
		<section class="card no-game">
			<Avatar user={id} size={72} />
			<NicknameEditor user={id} editable={you} />
			<!-- No game yet: an empty state, three cartridges in their plastic, what will fill in, the way on. -->
			<div class="empty-game">
				<span class="carts" aria-hidden="true">
					{#each ['#d4a72c', '#5aaed6', '#a3adb7'] as c (c)}
						<svg viewBox="0 0 12 14" width="30" height="35" shape-rendering="crispEdges" style:--c={c}>
							<path d="M0 0h10l2 2v12H0z" fill="var(--c)" />
							<path d="M2 3h8v7H2z" fill="color-mix(in oklab, var(--c), white 45%)" />
							<path d="M1 1h7v1H1z" fill="color-mix(in oklab, var(--c), black 25%)" />
						</svg>
					{/each}
				</span>
				<b class="empty-title">{you ? 'No game yet' : 'Not playing yet'}</b>
				{#if you}
					<p>Pick Gold, Silver or Crystal and this page fills in as you play:</p>
					<ul class="fills" aria-label="What your profile will show">
						{#each ['Party', 'Badges', 'Pokédex', 'Achievements', 'Link battles'] as f (f)}<li>{f}</li>{/each}
					</ul>
					<div class="actions">
						<a class="btn primary" href="/play">Pick your game</a>
						<a class="btn" href="/watch">Watch live battles</a>
					</div>
				{:else}
					<p>They haven't started a game. Their party, badges and achievements show up here once they do.</p>
					<div class="actions"><a class="btn" href="/scoreboard">Back to the scoreboard</a></div>
				{/if}
			</div>
		</section>
	{:else if !profile || !row || !status}
		<p class="muted" role="status">Loading…</p>
	{:else}
		<section class="card hero" aria-labelledby="pib-h">
			<div class="row who">
				<span class="av status-{status.kind}"><Avatar user={id} size={64} /><i class="status-dot" aria-hidden="true"></i></span>
				<div class="col">
					<NicknameEditor user={id} editable={you} headingId="pib-h" />
					{#if row.trainerName}<span class="muted small">Trainer <b class="pixel tname">{row.trainerName}</b></span>{/if}
					<div class="row xs wrap">
						{#if version}<span class="chip g-{version}"><span class="cart-dot"></span>{version}</span>{/if}
						<span class="chip status-{status.kind}"><span class="status-dot"></span>{status.label}</span>
						{#if row.location}
							<LocationMap pin={profile.pin} user={row.userId} label={row.location}>
								<span class="row tight"
									><svg viewBox="0 0 20 20" width="14" height="14" fill="currentColor" aria-hidden="true" class="pin"
										><path d="M10 1a6 6 0 0 0-6 6c0 4.5 6 12 6 12s6-7.5 6-12a6 6 0 0 0-6-6zm0 8.5A2.5 2.5 0 1 1 10 4.5a2.5 2.5 0 0 1 0 5z" /></svg
									><b class="loc">{row.location}</b></span
								>
							</LocationMap>
						{/if}
						{#if row.playTime ?? profile.stats.playTime}<span class="muted"><span class="sep">· </span>playtime {row.playTime ?? profile.stats.playTime}</span>{/if}
					</div>
				</div>
			</div>
			<div class="row numbers wrap">
				{#if you}<div class="trainer-slot"><TrainerPicker compact /></div>{/if}
				{#if rank >= 0}<div class="summary-slot"><RunSummary {row} {rank} total={board.rows.length} /></div>{/if}
			</div>
		</section>

		<div class="tabs" role="tablist" aria-label="Profile">
			{#each TABS as t (t.id)}
				<button type="button" role="tab" id="tab-{t.id}" aria-selected={tab === t.id} aria-controls="panel-{t.id}" class:on={tab === t.id} onclick={() => (tab = t.id)}
					>{#if 'short' in t}<span class="long">{t.label}</span><span class="short">{t.short}</span>{:else}{t.label}{/if}</button
				>
			{/each}
		</div>

		<div class="tabpanel" role="tabpanel" id="panel-{tab}" aria-labelledby="tab-{tab}">
			{#if tab === 'overview'}
				<div class="grid fill">
					<div class="col">
						<section class="card party" aria-labelledby="party-h">
							<div class="between"><h2 id="party-h">Party · {profile.party.length} of 6</h2></div>
								<ul class="mons">
									{#each profile.party as m, i (i)}
										{@const pct = m.maxHp ? Math.round(((m.hp ?? 0) * 100) / m.maxHp) : (row.party?.[i]?.hpPercent ?? null)}
										<li
											class="mcard {m.tier ? `tier-card tier-${m.tier}` : ''}"
											onpointerenter={m.egg ? undefined : (e) => dexHover.show(m.species, e.currentTarget, { side: 'above', note: monNote(m) })}
											onpointerleave={() => dexHover.hide()}
										>
											{#if m.tier}<span class="tier-tag tier-{m.tier}" title="{TIER_NAMES[m.tier]} tier">{m.tier}</span>{/if}
											<div class="row">
												<span class="mon" class:fnt={m.status === 'FNT'}>
													{#if m.egg}<span class="egg"></span>{:else}<img src="/api/v1/game/icons/{m.species}.png" alt="" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />{/if}
												</span>
												<div class="grow">
													{#if m.egg}
														<b>Egg</b>
														<div class="muted xs">It's making sounds inside</div>
													{:else}
														<div class="between"><b class="mname">{m.nickname || m.name}</b><span class="xs">Lv {m.level}</span></div>
														<div class="muted xs ell">{m.nickname && m.nickname !== m.name ? `${m.name} · ` : ''}{m.item ?? 'No item'}</div>
														{#if m.types.length || m.shiny}
															<div class="types">
																{#each m.types as t (t)}<span class="type" style:--t={TYPE_COLORS[t.toLowerCase()] ?? 'var(--border)'}>{t}</span>{/each}
																{#if m.shiny}<span class="type" style:--t="var(--accent)">shiny</span>{/if}
															</div>
														{/if}
													{/if}
												</div>
											</div>
											{#if !m.egg}
												<div class="col tight">
													<div class="between xs">
														<span class="muted">HP</span>
														<span class="row tight"
															>{#if m.status}<span class="st st-{m.status}">{m.status}</span>{/if}{#if m.maxHp}<span class="num">{m.hp} / {m.maxHp}</span>{:else if pct != null}<span
																	class="num">{pct}%</span
																>{/if}</span
														>
													</div>
													{#if pct != null}<span class="bar"><b style:width="{pct}%" style:background={hpColor(pct)}></b></span>{/if}
												</div>
												{#if m.moves.length}
													<ul class="moves" aria-label="Moves">
														{#each m.moves as mv, k (k)}
															{@const t = m.moveTypes?.[k]}
															<li class:typed={!!t} style:--t={t ? (TYPE_COLORS[t] ?? null) : null} title={t ? `${mv} · ${t}` : mv}>{mv}</li>
														{/each}
													</ul>
												{/if}
											{/if}
										</li>
									{/each}
									{#each Array(Math.max(0, 6 - profile.party.length)) as _, i (i)}
										<li class="mcard empty" aria-hidden="true"><div class="row"><span class="mon"></span><span class="muted small">Empty</span></div></li>
									{/each}
								</ul>
								{#if dexHover.card}<DexCard {...dexHover.card} />{/if}
						</section>
					</div>
					<div class="col">
						<!-- Link cable: the heart of v2. Record, trades, who they linked with, the Link achievements, and the buttons. -->
						<section class="card linkcard" aria-labelledby="link-h">
							<div class="between">
								<h2 id="link-h" class="linkh">
									<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
										><path d="M9 7H6a5 5 0 0 0 0 10h3M15 7h3a5 5 0 0 1 0 10h-3M8 12h8" /></svg
									>Link cable
								</h2>
								{#if closed}
									<span class="chip small-chip lock-chip"
										><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"
											><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
										>Locked</span
									>
								{:else}
									<span class="chip small-chip status-{status.kind}"><span class="status-dot"></span>{status.label}</span>
								{/if}
							</div>
							{#if closed}
								<CableClubLocked step={row?.cableClub ?? 0} who={you ? undefined : nameOf(id)} />
							{:else}
							<!-- Three numbers in one panel, like the points/rank/rating summary. -->
							<dl class="linkstats">
								<div><dt>Won</dt><dd class="w">{lr.wins}</dd></div>
								<div><dt>Lost</dt><dd class="l">{lr.losses}</dd></div>
								<div><dt>Trades</dt><dd>{lr.trades}</dd></div>
								{#if lr.wins + lr.losses}
									<span class="winbar" role="img" aria-label="{Math.round((lr.wins * 100) / (lr.wins + lr.losses))}% of link battles won"
										><b style:width="{(lr.wins * 100) / (lr.wins + lr.losses)}%"></b></span
									>
								{/if}
							</dl>
							<div class="linkrow">
								<span class="label">Linked with</span>
								{#if partners.length}
									<div class="partners">
										{#each partners as p (p)}<a href="/pib/{encodeURIComponent(p)}" title={nameOf(p)}><Avatar user={p} size={28} /></a>{/each}
									</div>
								{:else}
									<span class="muted small">Nobody yet</span>
								{/if}
							</div>
							{#if linkAch.length}
								<div class="col tight">
									<div class="between"><span class="label">Link achievements</span><span class="muted xs">{linkAch.filter((a) => a.unlocked).length} of {linkAch.length}</span></div>
									<!-- One row of balls; hovering, focusing or tapping one shows its card under the row (tap again to
									     close). The full cards are in the Achievements tab. -->
									<ul class="linkballs">
										{#each linkAch as a (a.id)}
											<li class:got={a.unlocked}>
												<button
													type="button"
													class:on={peek === a.id}
													aria-label="{a.name ?? 'Hidden achievement'}, {a.unlocked ? 'unlocked' : 'locked'}"
													aria-expanded={peek === a.id}
													onpointerenter={(e) => e.pointerType === 'mouse' && !pinned && (peek = a.id)}
													onpointerleave={(e) => e.pointerType === 'mouse' && !pinned && (peek = null)}
													onfocus={() => !pinned && (peek = a.id)}
													onblur={() => !pinned && (peek = null)}
													onclick={() => {
														pinned = pinned === a.id ? null : a.id;
														peek = pinned;
													}}><RarityBall rarity={a.rarity} locked={!a.unlocked} size={24} /></button
												>
											</li>
										{/each}
									</ul>
									{#if peeked}
										<div class="peek floating {peeked.rarity ?? 'common'}" role="status">
											<div class="between">
												<b>{peeked.name ?? '???'}</b>
												<span class="xs">{peeked.unlocked ? 'Unlocked' : 'Locked'} · {peeked.points} pts</span>
											</div>
											<span class="muted small">{peeked.description ?? 'A secret, until someone unlocks it.'}</span>
										</div>
									{/if}
								</div>
							{/if}
							<button type="button" class="history" onclick={() => (historyOpen = true)}>
								<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
									><path d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2" /></svg
								>
								<span>Link history</span>
							</button>
							<LinkHistory bind:open={historyOpen} user={id} {you} />
							{#if !you}
								<div class="offers">
									<button
										type="button"
										class="btn primary grow"
										disabled={busy || theyBusy || meClosed}
										onclick={() => act('POST', '/link/challenges', { userId: id, kind: 'TRADE' })}><KindIcon kind="TRADE" />Offer trade</button
									>
									<button
										type="button"
										class="btn grow battle"
										disabled={busy || theyBusy || meClosed}
										onclick={() => act('POST', '/link/challenges', { userId: id, kind: 'BATTLE' })}><KindIcon kind="BATTLE" />Challenge</button
									>
								</div>
								{#if waiting}<span class="muted xs" role="status">Waiting for <Name user={id} /> to answer… {left(waiting.expiresAt)} s</span>{/if}
								{#if meClosed}<span class="muted xs">Your Cable Club isn't open yet: bring Mr. Pokémon's Mystery Egg back to Prof. Elm first.</span>
								{:else if theyBusy}<span class="muted xs"><Name user={id} /> is linked with someone right now.</span>
								{:else if ready}<span class="muted xs"><Name user={id} /> is ready to {ready === 'TRADE' ? 'trade' : 'battle'}.</span>
								{:else if !busy}<span class="muted xs"><Name user={id} /> sees your offer wherever they are, for a minute.</span>{/if}
								{#if link.error}<span class="error xs" role="alert">{link.error}</span>{/if}
							{:else}
								<FindButton onclick={() => (finding = true)} />
								<LinkFinder bind:open={finding} />
							{/if}
							{/if}
						</section>
					</div>
				</div>
			{:else if tab === 'achievements'}
				<section class="card" aria-labelledby="ach-h">
					<div class="between">
						<h2 id="ach-h">Achievements</h2>
						<span class="pager">
							<span class="muted small">{tierFilter ? `${shownAch.filter((a) => a.unlocked).length} of ${shownAch.length} ${tierFilter}` : `${got} of ${achievements.length}`}</span>
						</span>
					</div>
					{#if achievements.length}
						<ul class="legend" aria-label="Rarity tiers">
							{#each tiers as t (t.rarity)}
								<li class="r-{t.rarity}">
									<button
										type="button"
										class:on={tierFilter === t.rarity}
										class:off={tierFilter !== null && tierFilter !== t.rarity}
										aria-pressed={tierFilter === t.rarity}
										title={tierFilter === t.rarity ? 'Show every tier' : `Show only ${t.rarity}`}
										onclick={() => {
											tierFilter = tierFilter === t.rarity ? null : t.rarity;
										}}><RarityBall rarity={t.rarity} size={18} /><span class="tier">{t.rarity}</span><span class="muted">{t.got} of {t.of}</span></button
									>
								</li>
							{/each}
						</ul>
							<ul class="shelves">
								{#each categories as c (c)}
									{@const inCat = shownAch.filter((x) => (x.category ?? 'Other') === c)}
									{@const done = inCat.filter((x) => x.unlocked).length}
									{#if inCat.length}
										<li class="shelf" class:open={openCat === c}>
											<button
												type="button"
												class="shelf-h"
												aria-expanded={openCat === c}
												onclick={() => {
													openCat = openCat === c ? null : c;
													achPeek = null;
												}}
											>
												<b>{c}</b>
												<span class="sbar" aria-hidden="true"><b style:width="{(done * 100) / inCat.length}%"></b></span>
												<span class="muted xs">{done} of {inCat.length}</span>
												<svg class="chev" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
													><path d="M6 9l6 6 6-6" /></svg
												>
											</button>
											{#if openCat === c}
												<ul class="trophies">
													{#each inCat as a (a.id)}
														<li>
															<button
																type="button"
																id="ach-{a.id}"
																class:on={achPeek === a.id}
																class:locked={!a.unlocked}
																class:spot={spotted === a.id}
																aria-label="{a.name ?? 'Hidden achievement'}, {a.rarity}, {a.unlocked ? 'unlocked' : 'locked'}"
																onclick={() => (achPeek = achPeek === a.id ? null : a.id)}
																><RarityBall rarity={a.rarity} locked={!a.unlocked} size={30} /><span class="tn">{a.name ?? '???'}</span></button
															>
														</li>
													{/each}
												</ul>
												{@const pk = inCat.find((x) => x.id === achPeek)}
												{@const pct = pk ? progressPct(pk) : null}
												<!-- Always there while the category is open, so picking a ball never resizes it. -->
												<div class="peek slot {pk?.rarity ?? 'common'}" class:empty={!pk} role="status">
													{#if pk}
														<div class="between">
															<b>{pk.name ?? '???'}</b>
															<span class="xs">{pk.unlocked ? 'Unlocked' : 'Locked'} · {pk.points} pts</span>
														</div>
														<span class="muted small">{pk.description ?? 'A secret, until someone unlocks it.'}</span>
														{#if pct != null}<span class="muted xs">{pct}% · {pk.progress} of {pk.goal}</span>{/if}
													{:else}
														<span class="muted small">Pick one to see what it takes.</span>
													{/if}
												</div>
											{/if}
										</li>
									{/if}
								{/each}
							</ul>
					{:else}
						<p class="muted small">No achievements to show.</p>
					{/if}
				</section>
			{:else if tab === 'badges'}
				<div class="grid badges fill">
					<div class="col">
						<section class="card" aria-labelledby="badges-h">
							<h2 id="badges-h">Badges</h2>
							{#each [{ label: 'Johto', names: JOHTO, mask: masks.johto, offset: 0 }, { label: 'Kanto', names: KANTO, mask: masks.kanto, offset: 8 }] as region (region.label)}
								<div class="col tight">
									<div class="between xs"><b>{region.label}</b><span class="muted">{bitCount(region.mask)} of 8</span></div>
									<ul class="bdg" aria-label="{region.label} badges">
										{#each region.names as n, j (n)}
											{@const earned = (region.mask & (1 << j)) !== 0}
											{@const label = `${n} Badge: ${earned ? 'earned' : 'not earned'}`}
											<li class:locked={!earned} title={label}>
												{#if broken[n]}
													<span class="letter" role="img" aria-label={label}>{n[0]}</span>
												{:else}
													<img src={art(n, region.offset + j)} alt={label} onerror={() => (broken[n] = true)} />
												{/if}
											</li>
										{/each}
									</ul>
									{#if region.offset === 0}
										<span class="muted xs">{johto ? `Latest: ${JOHTO[johto - 1]} Badge` : 'No badges yet'}{johto < 8 ? ` · next: ${JOHTO[johto]}` : ''}</span>
									{/if}
								</div>
							{/each}
						</section>
					</div>
					<div class="col">
						<section class="card stats" aria-labelledby="stats-h">
							<h2 id="stats-h">Stats</h2>
							<dl class="kv">
								<dt>Pokédex</dt>
								<dd>{profile.stats.caught} caught · {profile.stats.seen} seen</dd>
								{#if profile.stats.playTime ?? row.playTime}<dt>Playtime</dt><dd>{profile.stats.playTime ?? row.playTime}</dd>{/if}
								<dt>Achievements</dt>
								<dd>{row.achievementsUnlocked} · {row.achievementPoints} pts</dd>
								{#if top}<dt>Top Pokémon</dt><dd>{top.nickname || top.name} · Lv {topLevel(profile.party)}</dd>{/if}
								<dt>Party</dt>
								<dd>{profile.party.length} of 6</dd>
								<dt>Badges</dt>
								<dd>{johto} Johto · {bitCount(masks.kanto)} Kanto</dd>
								<dt>Link battles</dt>
								<dd>{lr.wins} won · {lr.losses} lost</dd>
								<dt>Trades</dt>
								<dd>{lr.trades}</dd>
								<dt>Linked with</dt>
								<dd>{partners.length ? `${partners.length} ${partners.length === 1 ? 'Pib' : 'Pibs'}` : 'Nobody yet'}</dd>
							</dl>
						</section>
					</div>
				</div>
			{:else}
				<section class="card" aria-labelledby="recent-h">
					<h2 id="recent-h">Recent</h2>
					<Activity events={profile.recent} limit={8} label="Recent" />
				</section>
			{/if}
		</div>
	{/if}
</SubPage>

<style>
	h2 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	p {
		margin: 0;
	}
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
	}
	.muted {
		color: var(--muted);
	}
	.small {
		font-size: 13px;
	}
	.xs {
		font-size: 12px;
	}
	.error {
		color: var(--danger);
	}
	.num {
		font-variant-numeric: tabular-nums;
	}
	.crumb {
		display: flex;
		gap: 8px;
		align-items: center;
		font-size: 13px;
	}
	.crumb a {
		color: var(--link);
		font-weight: 600;
		text-decoration: none;
	}
	a:focus-visible,
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.card {
		background: var(--panel);
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		min-width: 0;
	}
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
	}
	.row.wrap {
		flex-wrap: wrap;
		gap: 8px;
	}
	.tight {
		gap: 4px;
	}
	.col {
		display: flex;
		flex-direction: column;
		gap: 16px;
		min-width: 0;
	}
	.col.tight {
		gap: 6px;
	}
	.col.end {
		align-items: flex-end;
	}
	.between {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		flex-wrap: wrap;
	}
	.grow {
		flex: 1;
		min-width: 0;
	}
	.ell {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.hero {
		flex-direction: row;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: 20px;
	}
	.numbers {
		align-items: stretch;
		justify-content: flex-end;
	}
	.trainer-slot {
		display: flex;
		width: min(300px, 100%);
	}
	/* Wide screens: the trainer card and the points side by side whatever the trainer card says (its row is sized by
	   its content, so a short "Saved: blue." used to wrap the points under it and grow the header). */
	@media (min-width: 900px) {
		.row.wrap.numbers {
			flex-wrap: nowrap;
			flex: none;
		}
		.trainer-slot,
		.summary-slot {
			flex: none;
		}
	}
	.no-game {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		padding: 48px 24px;
		text-align: center;
	}
	.empty-game {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		box-sizing: border-box;
		width: 100%;
		max-width: 520px;
		margin-top: 6px;
		padding: 20px 18px;
		border: 1px dashed var(--border-strong);
		border-radius: 14px;
		background: var(--sunk);
	}
	.empty-game .carts {
		display: flex;
		gap: 8px;
	}
	.empty-game .carts svg:nth-child(2) {
		translate: 0 -4px;
	}
	.empty-title {
		font-size: 16px;
	}
	.empty-game p {
		color: var(--muted);
		font-size: 14px;
		line-height: 20px;
	}
	.fills {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: 6px;
		margin: 0;
		padding: 0;
		list-style: none;
	}
	.fills li {
		padding: 3px 10px;
		border-radius: 999px;
		border: 1px solid var(--border);
		background: var(--panel);
		font-size: 12px;
		font-weight: 600;
	}
	@media (max-width: 640px) {
		.no-game .actions {
			width: 100%;
			flex-direction: column;
		}
		.no-game .actions .btn {
			justify-content: center;
		}
	}
	.no-game p {
		margin: 0;
		max-width: 460px;
	}
	.no-game .actions {
		display: flex;
		gap: 10px;
		flex-wrap: wrap;
		justify-content: center;
		margin-top: 8px;
	}
	.no-game .btn {
		display: inline-flex;
		align-items: center;
		height: 38px;
		padding: 0 16px;
		border-radius: 10px;
		border: 1px solid var(--border-strong);
		background: var(--panel);
		color: var(--text);
		font-weight: 600;
		font-size: 14px;
		text-decoration: none;
	}
	.no-game .btn:hover {
		border-color: var(--accent-text);
	}
	.no-game .btn.primary {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--ink);
	}
	.no-game .btn.primary:hover {
		background: var(--accent-hover);
	}
	.no-game .btn:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.who {
		gap: 18px;
	}
	.av {
		position: relative;
		flex: none;
		display: flex;
	}
	.av .status-dot {
		position: absolute;
		right: -2px;
		bottom: -2px;
		width: 16px;
		height: 16px;
		box-sizing: border-box;
		border: 3px solid var(--panel);
	}
	.pin {
		color: var(--accent-text);
	}
	.numbers {
		gap: 24px;
	}
	.pts {
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.pts.big {
		font-size: 26px;
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 3px 9px;
		border-radius: 999px;
		background: var(--sunk);
		border: 1px solid var(--border);
		font-weight: 600;
		white-space: nowrap;
	}
	.cart-dot {
		width: 8px;
		height: 8px;
		border-radius: 2px;
	}
	.g-Gold {
		border-color: var(--cart-gold);
	}
	.g-Gold .cart-dot {
		background: var(--cart-gold);
	}
	.g-Silver {
		border-color: var(--cart-silver);
	}
	.g-Silver .cart-dot {
		background: var(--cart-silver);
	}
	.g-Crystal {
		border-color: var(--cart-crystal);
	}
	.g-Crystal .cart-dot {
		background: var(--cart-crystal);
	}
	.btn {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
		box-sizing: border-box; /* full-width buttons stay inside their card */
		height: 38px;
		padding: 0 14px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		background: transparent;
		color: var(--text);
		font-weight: 600;
		white-space: nowrap;
	}
	.btn.primary {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--ink);
	}
	.btn:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}

	/* The ball's card floats under the balls (over what follows), so nothing below moves when it opens. */
	.col.tight:has(> .linkballs) {
		position: relative;
	}
	.peek.floating {
		position: absolute;
		left: 0;
		right: 0;
		top: calc(100% + 6px);
		z-index: 5;
		box-shadow: 0 10px 28px rgb(0 0 0 / 0.4);
		background: var(--panel);
	}
	.linkballs {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}
	.linkballs li {
		display: inline-flex;
		border-radius: 50%;
		border: 1px solid var(--border);
	}
	.linkballs button {
		display: inline-flex;
		padding: 4px;
		border: none;
		border-radius: 50%;
		background: none;
		cursor: pointer;
	}
	.linkballs button.on {
		outline: 2px solid var(--accent-text);
		outline-offset: 1px;
	}
	.linkballs button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	/* Link history (LinkHistory.svelte): battles and trades, a page at a time. */
	.history {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		width: 100%;
		height: 42px;
		padding: 0 14px;
		border: 1px solid var(--border-strong);
		border-radius: 10px;
		background: var(--sunk);
		color: var(--text);
		font: 600 14px var(--body-font);
		cursor: pointer;
	}
	.history:hover {
		border-color: var(--link);
	}
	.history:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	/* The ball's card: its name, what it takes, locked or not, framed in its rarity's colour. */
	.peek {
		--c: var(--r-common);
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding: 10px 12px;
		border-radius: 10px;
		border: 1px solid color-mix(in srgb, var(--c) 60%, var(--border));
		border-left: 3px solid var(--c);
		background: var(--sunk);
	}
	.peek.uncommon {
		--c: var(--r-uncommon);
	}
	.peek.rare {
		--c: var(--r-rare);
	}
	.peek.epic {
		--c: var(--r-epic);
	}
	.peek.legendary {
		--c: var(--r-legendary);
	}
	.linkballs li.got {
		border-color: var(--accent-text);
		background: var(--accent-tint);
	}
	.tabs {
		display: flex;
		gap: 4px;
		border-bottom: 1px solid var(--border);
		overflow-x: auto;
	}
	.tabs button {
		padding: 10px 16px;
		border: none;
		border-bottom: 3px solid transparent;
		background: none;
		color: var(--muted);
		font: inherit;
		font-size: 14px;
		font-weight: 600;
		white-space: nowrap;
		cursor: pointer;
	}
	.tabs .short {
		display: none;
	}
	.tabs button:hover {
		color: var(--text);
	}
	.tabs button.on {
		color: var(--accent-text);
		border-bottom-color: var(--accent);
	}
	.tabs button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: -3px;
	}
	.tabpanel {
		min-width: 0;
	}
	.grid {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 360px;
		gap: 20px;
		align-items: start;
	}
	/* Badges at their size (8 x 64px), the stats taking the rest. */
	.grid.badges {
		grid-template-columns: auto minmax(0, 1fr);
	}
	/* Overview: the party card runs as tall as the link card, its six slots sharing the height. */
	.grid.fill {
		align-items: stretch;
	}
	.grid.fill > .col > .card {
		flex: 1;
	}
	.party,
	.stats {
		height: 100%;
		box-sizing: border-box;
	}
	.party .mons {
		flex: 1;
		grid-auto-rows: 1fr;
	}
	.mons {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 12px;
	}
	/* Free party slots stay visible: six places, filled or not. */
	.mcard.empty {
		background: none;
		border-style: dashed;
		justify-content: center;
	}
	.mcard.empty .mon {
		border-radius: 9px;
		border: 1px dashed var(--border-strong);
		box-sizing: border-box;
	}
	.mname {
		white-space: nowrap; /* names and nicknames are 10 letters at most: shown whole */
	}
	.linkcard {
		border-color: var(--link);
		box-shadow: inset 0 3px 0 var(--link);
	}
	.linkh {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		color: var(--link);
	}
	.small-chip {
		font-size: 12px;
		padding: 2px 8px;
	}
	.linkstats {
		margin: 0;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		border-radius: 12px;
		background: var(--sunk);
		border: 1px solid var(--border);
		overflow: hidden;
	}
	.linkstats > div {
		padding: 10px 14px;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.linkstats > div + div {
		border-left: 1px solid var(--border);
	}
	.linkstats dt {
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.linkstats dd {
		margin: 0;
		font-size: 26px;
		font-weight: 800;
		line-height: 1.1;
		font-variant-numeric: tabular-nums;
	}
	.linkstats dd.w {
		color: var(--grass, #3fae4a);
	}
	.linkstats dd.l {
		color: var(--danger);
	}
	.winbar {
		grid-column: 1 / -1;
		display: block;
		height: 5px;
		background: color-mix(in oklab, var(--danger) 45%, transparent);
	}
	.winbar b {
		display: block;
		height: 100%;
		background: var(--grass, #3fae4a);
	}
	.label {
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.linkrow {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 30px;
	}
	.partners {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}
	.partners a {
		display: flex;
		border-radius: 50%;
	}
	/* Two equal buttons that always fit the card. */
	.offers {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}
	/* Challenge in the battle red, as in the Link cable card and Find someone. */
	.offers .btn.battle {
		background: var(--danger);
		border-color: var(--danger);
		color: #fff;
	}
	.lock-chip {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		color: var(--muted);
	}
	.offers .btn {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.btn.wide {
		width: 100%;
	}
	.summary-slot {
		width: min(340px, 100%);
	}
	.mcard {
		--frame-bg: var(--sunk);
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 12px;
		display: flex;
		flex-direction: column;
		gap: 10px;
		background: var(--sunk);
		min-width: 0;
	}
	.mon {
		position: relative;
		flex: none;
		display: grid; /* the sprite centred in its slot */
		place-items: center;
		width: 44px;
		height: 44px;
	}
	.mon img {
		display: block;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
	}
	.mon.fnt img {
		filter: grayscale(1);
		opacity: 0.45;
	}
	.egg {
		position: absolute;
		inset: 18% 24%;
		border-radius: 50% 50% 46% 46% / 60% 60% 40% 40%;
		background: #f4f1e8;
		box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.25);
	}
	.types {
		display: flex;
		gap: 4px;
		flex-wrap: wrap;
		margin-top: 5px;
	}
	/* Pastel type labels with dark ink: the same in both themes. */
	.type {
		font-size: 10px;
		font-weight: 700;
		letter-spacing: 0.04em;
		text-transform: uppercase;
		padding: 2px 6px;
		border-radius: 4px;
		background: var(--t);
		color: #1d1f1c;
	}
	.st {
		padding: 1px 5px;
		border-radius: 4px;
		background: var(--danger);
		color: #fff;
		font-size: 10px;
		font-weight: 700;
	}
	.st-PSN {
		background: #9b5bb5;
	}
	.st-PAR {
		background: #c99a12;
	}
	.bar {
		display: block;
		height: 6px;
		border-radius: 3px;
		background: var(--border);
		overflow: hidden;
	}
	.bar b {
		display: block;
		height: 100%;
	}
	.moves {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 4px;
	}
	.moves li {
		font-size: 12px;
		padding: 3px 7px;
		border-radius: 5px;
		border: 1px solid var(--border);
		background: var(--panel);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	/* A move in its type's colour: tinted fill, the colour as a left edge. */
	.moves li.typed {
		border-color: color-mix(in srgb, var(--t) 55%, var(--border));
		border-left: 4px solid var(--t);
		background: color-mix(in srgb, var(--t) 22%, var(--panel));
		font-weight: 600;
	}
	.pager {
		display: inline-flex;
		align-items: center;
		gap: 8px;
	}
	.pg {
		width: 28px;
		height: 28px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		background: var(--panel);
		color: var(--text);
		font-size: 16px;
		line-height: 1;
		cursor: pointer;
	}
	.pg:disabled {
		opacity: 0.35;
		cursor: default;
	}
	.pg:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.ach {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
		gap: 10px;
	}
	/* Phones: a row per category, opening to a grid of balls; the ball tapped shows its card (.peek). */
	.shelves {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.shelf {
		border: 1px solid var(--border);
		border-radius: 12px;
		background: var(--sunk);
		overflow: hidden;
	}
	.shelf.open {
		border-color: var(--border-strong);
	}
	.shelf-h {
		width: 100%;
		display: grid;
		grid-template-columns: 1fr 64px auto auto;
		align-items: center;
		gap: 10px;
		padding: 12px 12px;
		border: none;
		background: none;
		color: var(--text);
		font: inherit;
		font-size: 14px;
		text-align: left;
		cursor: pointer;
	}
	.sbar {
		height: 6px;
		border-radius: 999px;
		background: var(--border);
		overflow: hidden;
	}
	.sbar b {
		display: block;
		height: 100%;
		background: var(--accent);
	}
	.chev {
		color: var(--muted);
		transition: rotate 0.15s;
	}
	.open .chev {
		rotate: 180deg;
	}
	.trophies {
		list-style: none;
		margin: 0;
		padding: 4px 10px 10px;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(84px, 1fr));
		gap: 6px;
	}
	.trophies button {
		width: 100%;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
		padding: 8px 2px 6px;
		border: 1px solid transparent;
		border-radius: 10px;
		background: none;
		color: var(--text);
		font: inherit;
		cursor: pointer;
	}
	.trophies button.on {
		border-color: var(--accent-text);
		background: var(--accent-tint);
	}
	.trophies button.locked .tn {
		color: var(--muted);
	}
	.trophies button.spot {
		outline: 2px solid var(--accent-text);
	}
	.tn {
		max-width: 100%;
		font-size: 10px;
		line-height: 12px;
		text-align: center;
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
	}
	.shelf-h:focus-visible,
	.trophies button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: -3px;
	}
	.peek.slot {
		margin: 0 10px 10px;
		min-height: 84px;
		justify-content: center;
	}
	.peek.slot.empty {
		align-items: center;
		border-style: dashed;
		border-left-width: 1px;
	}
	/* Reached from an activity entry: a ring of light pulses round the achievement, then stays faintly. */
	.ac.spot {
		position: relative;
		z-index: 1;
		outline: 2px solid var(--accent-text);
		outline-offset: 3px;
		animation: spotlight 1.4s ease-out 3;
	}
	@keyframes spotlight {
		0% {
			box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent) 70%, transparent);
			transform: scale(1);
		}
		30% {
			transform: scale(1.03);
		}
		100% {
			box-shadow: 0 0 0 14px color-mix(in srgb, var(--accent) 0%, transparent);
			transform: scale(1);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.ac.spot {
			animation: none;
		}
	}
	.ac {
		display: flex;
		gap: 10px;
		align-items: flex-start;
		padding: 10px;
		border-radius: 10px;
		border: 2px solid var(--r);
	}
	/* Rarity: the frame keeps its colour when locked; only the content dims. */
	.r-common {
		--r: var(--r-common);
	}
	.r-uncommon {
		--r: var(--r-uncommon);
	}
	.r-rare {
		--r: var(--r-rare);
	}
	.r-epic {
		--r: var(--r-epic);
	}
	.r-legendary {
		--r: var(--r-legendary);
	}
	.ac.locked .col {
		opacity: 0.6;
	}
	.legend {
		display: flex;
		flex-wrap: wrap;
		gap: 6px 16px;
		font-size: 12px;
	}
	.legend button {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 3px 10px 3px 4px;
		border-radius: 999px;
		border: 1px solid transparent;
		background: none;
		color: inherit;
		font: inherit;
		cursor: pointer;
		transition: opacity 150ms, background 150ms;
	}
	.legend button:hover {
		border-color: var(--border-strong);
	}
	.legend button.on {
		border-color: var(--r);
		background: color-mix(in srgb, var(--r) 14%, transparent);
	}
	.legend button.off {
		opacity: 0.45;
	}
	.legend button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.tier {
		font-weight: 700;
		text-transform: capitalize;
		color: var(--r);
	}
	.prog {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.prog .bar {
		flex: 1;
		height: 4px;
	}
	.prog .bar b {
		background: var(--r);
	}
	.tname {
		font-size: 10px;
		font-weight: 400;
	}
	.bdg {
		display: grid;
		grid-template-columns: repeat(8, minmax(0, 64px));
		gap: 10px;
	}
	.bdg li {
		aspect-ratio: 1;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.bdg img {
		width: 100%;
		height: 100%;
		object-fit: contain;
		image-rendering: pixelated;
	}
	.bdg .letter {
		width: 100%;
		height: 100%;
		box-sizing: border-box;
		border-radius: 50%;
		border: 2px solid var(--accent);
		display: flex;
		align-items: center;
		justify-content: center;
		font: 700 11px / 1 var(--pixel-font);
		color: var(--accent-text);
	}
	/* Not earned yet: the same art, greyed out. */
	.bdg .locked img,
	.bdg .locked .letter {
		filter: grayscale(1) brightness(0.55);
		opacity: 0.45;
	}
	.kv {
		margin: 0;
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 8px 12px;
		font-size: 13px;
	}
	.kv dt {
		color: var(--muted);
	}
	.kv dd {
		margin: 0;
		font-weight: 600;
		text-align: right;
		font-variant-numeric: tabular-nums;
	}
	@media (max-width: 1180px) {
		.grid,
		.grid.badges {
			grid-template-columns: minmax(0, 1fr);
		}
	}
	@media (max-width: 820px) {
		.mons {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}
	@media (max-width: 640px) {
		.mons {
			grid-template-columns: minmax(0, 1fr);
		}
		/* Phones: the header as a centred stack (avatar, name, trainer, chips), then the trainer and points cards full
		   width; the tabs share the width instead of scrolling sideways. */
		.hero {
			gap: 14px;
		}
		.who {
			width: 100%;
			flex-direction: column;
			align-items: center;
			gap: 10px;
			text-align: center;
		}
		.who > .col {
			align-items: center;
		}
		.who :global(.wrap) {
			justify-content: center;
		}
		.numbers {
			width: 100%;
			flex-direction: column;
			gap: 12px;
		}
		.trainer-slot,
		.summary-slot {
			width: 100%;
		}
		.tabs {
			gap: 0;
			overflow-x: visible;
		}
		.tabs button {
			flex: 1 1 auto;
			padding: 10px 6px;
			font-size: 13px;
		}
		.tabs .long,
		.who .sep {
			display: none;
		}
		.tabs .short {
			display: inline;
		}
	}
</style>
