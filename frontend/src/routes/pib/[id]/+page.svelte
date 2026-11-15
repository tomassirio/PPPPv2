<!-- /pib/{userId} (frontend/design/PlayV2.mock.html, #profile-jordan): a Pib's profile. Hero (avatar, name, game, status,
     where, playtime, rank, Trainer Score, Offer trade / Challenge while they are ready), party cards (types, item, HP,
     status, moves), achievements, badges, stats and their recent feed. GET /players/{id}/profile, fetched again when
     their scoreboard row changes; an older backend without it gets the scoreboard row and achievement catalogue. -->
<script lang="ts">
	import { page } from '$app/state';
	import Activity from '#lib/components/Activity.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import RarityBall from '#lib/components/RarityBall.svelte';
	import SubPage from '#lib/components/SubPage.svelte';
	import { speciesName } from '#lib/components/PartyIcons.svelte';
	import { api } from '#lib/api.ts';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { on } from '#lib/events.ts';
	import { act, left, link, watchLink } from '#lib/link.svelte.ts';
	import {
		badgeMasks,
		bitCount,
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
		versionOf,
		type CatalogueEntry,
		type Profile,
		type ScoreRow, score } from '#lib/play.ts';
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
	const got = $derived(achievements.filter((a) => a.unlocked).length);
	const tiers = $derived(
		RARITIES.map((r) => ({ rarity: r, got: achievements.filter((a) => a.rarity === r && a.unlocked).length, of: achievements.filter((a) => a.rarity === r).length }))
	);
	const BALL = { common: 'Poké Ball', uncommon: 'Great Ball', rare: 'Ultra Ball', epic: 'Master Ball', legendary: 'GS Ball' };
	const top = $derived(
		profile?.party.filter((m) => !m.egg).reduce<Profile['party'][number] | null>((best, m) => (!best || m.level > best.level ? m : best), null)
	);
	// Offer trade / Challenge: only while they wait in the lobby for that, and nobody involved is busy.
	const ready = $derived(link.state?.lobby.find((p) => p.userId === id)?.kind);
	const busy = $derived(!!(link.state?.session || link.state?.outgoing || link.state?.incoming));
	const waiting = $derived(link.state?.outgoing?.to === id ? link.state.outgoing : null);

	const badgeArt = import.meta.glob('../../../lib/assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;
	const art = (name: string, i: number) => badgeArt[`../../../lib/assets/badges/${name.toLowerCase()}.png`] ?? `/api/v1/game/badges/${i}.png`;
	let broken = $state<Record<string, boolean>>({});
</script>

<svelte:head><title>{you ? 'Your profile' : nameOf(id)} · Pibs&amp;PibettesPlayPokemonV2</title></svelte:head>

<SubPage>
	<nav class="crumb" aria-label="Breadcrumb"><a href="/scoreboard">← Scoreboard</a><span class="muted">/ {you ? 'You' : nameOf(id)}</span></nav>

	{#if missing}
		<p class="muted">No Pib called {id} has started a game.</p>
	{:else if !profile || !row || !status}
		<p class="muted" role="status">Loading…</p>
	{:else}
		<section class="card hero" aria-labelledby="pib-h">
			<div class="row who">
				<span class="av status-{status.kind}"><Avatar user={id} size={64} /><i class="status-dot" aria-hidden="true"></i></span>
				<div class="col">
					<h1 id="pib-h" class="pixel">{you ? 'Your profile' : nameOf(id)}</h1>
					{#if row.trainerName}<span class="muted small">Trainer <b class="pixel tname">{row.trainerName}</b></span>{/if}
					<div class="row xs wrap">
						{#if version}<span class="chip g-{version}"><span class="cart-dot"></span>{version}</span>{/if}
						<span class="chip status-{status.kind}"><span class="status-dot"></span>{status.label}</span>
						{#if row.location}
							<span class="row tight"
								><svg viewBox="0 0 20 20" width="14" height="14" fill="currentColor" aria-hidden="true" class="pin"
									><path d="M10 1a6 6 0 0 0-6 6c0 4.5 6 12 6 12s6-7.5 6-12a6 6 0 0 0-6-6zm0 8.5A2.5 2.5 0 1 1 10 4.5a2.5 2.5 0 0 1 0 5z" /></svg
								><b>{row.location}</b></span
							>
						{/if}
						{#if row.playTime ?? profile.stats.playTime}<span class="muted">· playtime {row.playTime ?? profile.stats.playTime}</span>{/if}
					</div>
				</div>
			</div>
			<div class="row numbers wrap">
				{#if rank >= 0}
					<div class="col end tight"><span class="pixel rank">#{rank + 1}</span><span class="muted xs">of {board.rows.length}</span></div>
				{/if}
				<div class="col end tight">
					<span class="pts big">{score(row)}<span class="sr-only"> points</span></span>
					<span class="xs"><span class="grade pixel">{row.grade}</span> <span class="muted">{row.title}</span></span>
				</div>
				{#if !you}
					<div class="col end tight">
						<div class="row tight">
							<button
								type="button"
								class="btn primary"
								disabled={ready !== 'TRADE' || busy}
								title={ready === 'TRADE' ? '' : 'Offer a trade once they are ready to trade'}
								onclick={() => act('POST', '/link/challenges', { userId: id })}>Offer trade</button
							>
							<button
								type="button"
								class="btn"
								disabled={ready !== 'BATTLE' || busy}
								title={ready === 'BATTLE' ? '' : 'Challenge them once they are ready to battle'}
								onclick={() => act('POST', '/link/challenges', { userId: id })}>Challenge</button
							>
						</div>
						{#if waiting}<span class="muted xs" role="status">Waiting for {nameOf(id)} to answer… {left(waiting.expiresAt)} s</span>{/if}
						{#if link.error}<span class="error xs" role="alert">{link.error}</span>{/if}
					</div>
				{/if}
			</div>
		</section>

		<div class="grid">
			<div class="col">
				<section class="card" aria-labelledby="party-h">
					<div class="between"><h2 id="party-h">Party · {profile.party.length} of 6</h2><span class="muted xs">From {you ? 'your' : `${nameOf(id)}’s`} game</span></div>
					{#if profile.party.length}
						<ul class="mons">
							{#each profile.party as m, i (i)}
								{@const pct = m.maxHp ? Math.round(((m.hp ?? 0) * 100) / m.maxHp) : (row.party?.[i]?.hpPercent ?? null)}
								<li class="mcard">
									<div class="row">
										<span class="mon" class:fnt={m.status === 'FNT'}>
											{#if m.egg}<span class="egg"></span>{:else}<img src="/api/v1/game/icons/{m.species}.png" alt="" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />{/if}
										</span>
										<div class="grow">
											{#if m.egg}
												<b>Egg</b>
												<div class="muted xs">It's making sounds inside</div>
											{:else}
												<div class="between"><b class="ell">{m.nickname || m.name}</b><span class="xs">Lv {m.level}</span></div>
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
												{#each m.moves as mv, k (k)}<li>{mv}</li>{/each}
											</ul>
										{/if}
									{/if}
								</li>
							{/each}
						</ul>
					{:else}
						<p class="muted small">No party yet.</p>
					{/if}
				</section>

				<section class="card" aria-labelledby="ach-h">
					<div class="between"><h2 id="ach-h">Achievements</h2><span class="muted small">{got} of {achievements.length}</span></div>
					{#if achievements.length}
						<ul class="legend" aria-label="Rarity tiers">
							{#each tiers as t (t.rarity)}
								<li class="r-{t.rarity}"><RarityBall rarity={t.rarity} size={18} /><span class="tier">{t.rarity}</span><span class="muted">{t.got} of {t.of}</span></li>
							{/each}
						</ul>
						<ul class="ach">
							{#each achievements as a (a.id)}
								{@const pct = progressPct(a)}
								<li class="ac r-{a.rarity}" class:locked={!a.unlocked}>
									<span title={BALL[a.rarity]}><RarityBall rarity={a.rarity} locked={!a.unlocked} /></span>
									<div class="col tight grow">
										<b class="small">{a.name}<span class="sr-only">, {a.rarity}, {a.unlocked ? 'unlocked' : 'locked'}</span></b>
										<span class="muted xs">{a.description}</span>
										{#if pct != null}
											<span class="prog xs muted"
												><span class="bar" role="meter" aria-label="Progress" aria-valuemin="0" aria-valuemax={a.goal} aria-valuenow={a.progress}
													><b style:width="{pct}%"></b></span
												>{pct}% · {a.progress} of {a.goal}</span
											>
										{/if}
									</div>
								</li>
							{/each}
						</ul>
					{:else}
						<p class="muted small">No achievements to show.</p>
					{/if}
				</section>
			</div>

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

				<section class="card" aria-labelledby="stats-h">
					<h2 id="stats-h">Stats</h2>
					<dl class="kv">
						<dt>Pokédex</dt>
						<dd>{profile.stats.caught} caught · {profile.stats.seen} seen</dd>
						{#if profile.stats.trades != null}<dt>Trades</dt><dd>{profile.stats.trades}</dd>{/if}
						{#if profile.stats.linkBattles != null}<dt>Link battles</dt><dd>{profile.stats.linkBattles}</dd>{/if}
						{#if profile.stats.playTime ?? row.playTime}<dt>Playtime</dt><dd>{profile.stats.playTime ?? row.playTime}</dd>{/if}
						<dt>Achievements</dt>
						<dd>{row.achievementsUnlocked} · {row.achievementPoints} pts</dd>
						{#if top}<dt>Top Pokémon</dt><dd>{top.nickname || top.name} · Lv {topLevel(profile.party)}</dd>{/if}
					</dl>
				</section>

				<section class="card" aria-labelledby="recent-h">
					<h2 id="recent-h">Recent</h2>
					<Activity events={profile.recent} limit={8} label="Recent" />
				</section>
			</div>
		</div>
	{/if}
</SubPage>

<style>
	h1 {
		margin: 0;
		font-size: 18px;
		line-height: 1.5;
	}
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
	.rank {
		font-size: 22px;
	}
	.pts {
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.pts.big {
		font-size: 26px;
	}
	.grade {
		padding: 3px 5px;
		border-radius: 4px;
		background: var(--accent-tint);
		color: var(--accent-text);
		font-size: 9px;
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

	.grid {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 360px;
		gap: 20px;
		align-items: start;
	}
	.mons {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 12px;
	}
	.mcard {
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
		width: 44px;
		height: 44px;
	}
	.mon img {
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
	.ach {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
		gap: 10px;
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
	.legend li {
		display: flex;
		align-items: center;
		gap: 6px;
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
		grid-template-columns: repeat(8, minmax(0, 1fr));
		gap: 6px;
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
		.grid {
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
		.hero {
			gap: 14px;
		}
		.numbers {
			width: 100%;
			justify-content: space-between;
		}
	}
</style>
