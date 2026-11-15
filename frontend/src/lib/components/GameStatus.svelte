<!-- "Your run": the player's Trainer Score, grade, title and rank (scoreboard), their party as cards (icon, name,
     level, status, HP; from the game summary where it has a party box, else their scoreboard row), badges, Pokédex
     progress, play time, and achievements linking to their profile. The game summary is read from the console's RAM
     (GET /api/v1/game/state, kept current by GAME_STATE). Badges are sprites, greyed out until earned: the Kanto ones
     from src/lib/assets/badges, others (Gold/Silver/Crystal's Johto 8) drawn from the player's ROM by
     /api/v1/game/badges/{index}.png, a letter in a circle if that fails. Pips only for a profile without named badges. -->
<script lang="ts">
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { game, watchGame } from '#lib/game.svelte.ts';
	import { hpColor, ordinal } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';
	import { speciesName } from '#lib/components/PartyIcons.svelte';

	const badgeArt = import.meta.glob('../assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;
	const art = (name: string, i: number) => badgeArt[`../assets/badges/${name.toLowerCase()}.png`] ?? `/api/v1/game/badges/${i}.png`;
	let broken = $state<Record<number, boolean>>({});

	$effect(() => watchGame());
	$effect(() => watchBoard());

	const summary = $derived(game.summary);
	const rank = $derived(board.rows.findIndex((r) => r.userId === session.me?.id));
	const mine = $derived(rank >= 0 ? board.rows[rank] : null);
	const counter = (label: string) => summary?.counters.find((c) => c.label === label);
	const dex = $derived(counter('Pokédex'));
	const seen = $derived(counter('Seen'));
	const badgeCount = $derived(counter('Badges'));
	const named = $derived(summary?.badges ?? []);
	const earned = $derived(named.length ? named.filter((b) => b.earned).length : (badgeCount?.value ?? 0));
	const max = $derived(named.length || badgeCount?.max || 0);
	// Rows of 8: one for an 8-badge game, Johto then Kanto for Gold/Silver/Crystal. got = earned in the row.
	const rows = $derived(
		max > 8
			? [
					{ label: 'Johto badges', got: named.length ? named.slice(0, 8).filter((b) => b.earned).length : Math.min(earned, 8) },
					{ label: 'Kanto badges', got: named.length ? named.slice(8).filter((b) => b.earned).length : Math.max(0, earned - 8) }
				]
			: max
				? [{ label: 'Badges', got: earned }]
				: []
	);
	// One shape for both sources: Pokémon Red's party box (names, HP) or the scoreboard row's party (Gen 2).
	type Card = { icon: number; name: string; sub: string; level: number; hp: number | null; status: string | null; shiny: boolean; egg: boolean };
	const party = $derived<Card[]>(
		summary?.party.length
			? summary.party.map((m) => ({
					icon: m.icon,
					name: m.name,
					sub: m.name === m.species ? '' : m.species,
					level: m.level,
					hp: m.maxHp ? Math.round((m.hp * 100) / m.maxHp) : null,
					status: m.status,
					shiny: false,
					egg: false
				}))
			: (mine?.party ?? []).map((m) => ({
					icon: m.species,
					name: m.egg ? 'Egg' : speciesName(m.species),
					sub: m.egg ? 'Hatching soon' : '',
					level: m.level,
					hp: m.hpPercent ?? null,
					status: m.status ?? null,
					shiny: !!m.shiny,
					egg: !!m.egg
				}))
	);
</script>

<section class="panel card" aria-labelledby="run-h">
	<div class="head">
		<h2 id="run-h">Your run</h2>
		{#if mine}
			<span class="score"
				><span class="pts">{mine.trainerScore}<span class="sr-only"> of 1000, grade</span></span> <span class="grade pixel">{mine.grade}</span>
				<span class="muted">{ordinal(rank + 1)}</span></span
			>
		{/if}
	</div>
	{#if mine}
		{#if mine.trainerName}<div class="line"><span class="muted">Trainer</span><b>{mine.trainerName}</b></div>{/if}
		<div class="line"><span class="muted">Title</span><b>{mine.title}</b></div>
	{/if}
	{#if party.length || mine}
		<div class="line"><span class="muted">Party</span><span class="muted xs">live from your game</span></div>
		<ul class="cards" aria-label="Your party">
			{#each party as m, i (i)}
				<li class="pslot">
					<span class="mon" class:fnt={m.status === 'FNT'}>
						{#if !m.egg}<img src="/api/v1/game/icons/{m.icon}.png" alt="" onerror={(e) => ((e.currentTarget as HTMLImageElement).style.visibility = 'hidden')} />{/if}
						{#if m.shiny}<span class="shiny" title="Shiny">✦</span>{/if}
					</span>
					<span class="info">
						<span class="line"><b class="ell">{m.name}</b>{#if !m.egg}<span class="xs muted lv">Lv {m.level}</span>{/if}</span>
						<span class="line xs"
							><span class="muted ell">{m.sub}</span>{#if m.status}<span class="st st-{m.status}">{m.status}</span>{/if}</span
						>
						{#if m.hp != null && !m.egg}
							<span class="hpbar" role="meter" aria-label="HP" aria-valuemin="0" aria-valuemax="100" aria-valuenow={m.hp}
								><b style:width="{m.hp}%" style:background={hpColor(m.hp)}></b></span
							>
						{/if}
					</span>
				</li>
			{/each}
			{#each Array(Math.max(0, 6 - party.length)) as _, i (i)}
				<li class="pslot empty" aria-hidden="true"><span class="mon"></span><span class="xs muted">Empty</span></li>
			{/each}
		</ul>
	{/if}
	{#if summary}
		{#each rows as row, r (row.label)}
			<div class="line wrap" class:later={r > 0 && !row.got}>
				<span class="muted">{row.label}</span>
				{#if named.length}
					<ul class="badges" aria-label="{row.label}: {row.got} of 8 earned">
						{#each named.slice(r * 8, r * 8 + 8) as b, j (b.name)}
							{@const i = r * 8 + j}
							{@const label = `${b.name} Badge (${b.place}): ${b.earned ? 'earned' : 'not earned'}`}
							<li class:locked={!b.earned} title={label}>
								{#if broken[i]}
									<span class="letter" role="img" aria-label={label}>{b.name[0]}</span>
								{:else}
									{@const src = art(b.name, i)}
									<img {src} alt={label} class:rom={src.startsWith('/api/')} onerror={() => (broken[i] = true)} />
								{/if}
							</li>
						{/each}
					</ul>
				{:else}
					<span class="pips" role="img" aria-label="{row.got} of 8 {row.label}">
						{#each Array(8) as _, i (i)}
							<i class:got={i < row.got}></i>
						{/each}
					</span>
				{/if}
			</div>
		{/each}
		{#if dex}
			<div class="dex">
				<div class="line">
					<span class="muted">Pokédex</span>
					<span class="muted">{dex.value} of {dex.max} caught{seen ? ` · ${seen.value} seen` : ''}</span>
				</div>
				<span class="bar" role="meter" aria-label="Pokédex caught" aria-valuemin="0" aria-valuemax={dex.max} aria-valuenow={dex.value}
					><b style:width="{dex.max ? (dex.value * 100) / dex.max : 0}%"></b></span
				>
			</div>
		{/if}
		{#if summary.playTime}
			<div class="line"><span class="muted">Time played</span><span>{summary.playTime}</span></div>
		{/if}
	{:else}
		<p class="muted">Badges and your Pokédex show up here once your game is past the title screen.</p>
	{/if}
	{#if mine && session.me}
		<div class="line">
			<span class="muted">Achievements</span>
			<a href="/pib/{encodeURIComponent(session.me.id)}">{mine.achievementsUnlocked} unlocked · see all →</a>
		</div>
	{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		font-size: 14px;
	}
	.head,
	.line {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-width: 0;
	}
	.wrap {
		flex-wrap: wrap;
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
	.muted {
		color: var(--muted);
	}
	p.muted {
		font-size: 13px;
		line-height: 19px;
	}
	.pts {
		font-size: 22px;
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}
	.score {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.grade {
		padding: 4px 6px;
		border-radius: 4px;
		background: var(--accent-tint);
		color: var(--accent-text);
		font-size: 9px;
	}
	.xs {
		font-size: 12px;
	}
	.ell {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	a {
		font-weight: 600;
		color: var(--link);
		text-decoration: none;
	}
	a:hover {
		text-decoration: underline;
	}
	a:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	/* Party: two columns of cards. */
	.cards {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
	}
	.pslot {
		display: flex;
		gap: 10px;
		align-items: center;
		padding: 8px 10px;
		border-radius: 10px;
		background: var(--sunk);
		border: 1px solid var(--border);
		min-width: 0;
	}
	/* Free party slots stay visible: six places, filled or not. */
	.pslot.empty {
		background: none;
		border-style: dashed;
	}
	.pslot.empty .mon {
		border-radius: 7px;
		border: 1px dashed var(--border-strong);
		box-sizing: border-box;
	}
	.lv {
		flex: none;
		white-space: nowrap;
	}
	.mon {
		position: relative;
		flex: none;
		width: 30px;
		height: 30px;
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
	.shiny {
		position: absolute;
		top: -5px;
		right: -4px;
		font-size: 11px;
		font-weight: 700;
		color: var(--accent-text);
	}
	.info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.info .line {
		gap: 6px;
	}
	.st {
		flex: none;
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
	.hpbar {
		display: block;
		height: 5px;
		margin-top: 3px;
		border-radius: 3px;
		background: var(--border);
		overflow: hidden;
	}
	.hpbar b {
		display: block;
		height: 100%;
	}
	.pips {
		display: flex;
		gap: 5px;
	}
	.pips i {
		width: 22px;
		height: 22px;
		box-sizing: border-box;
		border-radius: 50%;
		border: 2px solid var(--border-strong);
	}
	.pips i.got {
		background: var(--accent);
		border-color: var(--accent);
	}
	.badges {
		display: flex;
		gap: 5px;
		margin: 0;
		padding: 0;
		list-style: none;
	}
	.badges li {
		width: 24px;
		height: 24px;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.badges img {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}
	/* The ROM's 16x16 trainer-card art: crisp pixels. */
	.badges img.rom {
		image-rendering: pixelated;
	}
	.badges .letter {
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
	/* Not earned yet: the same art, greyed out (v1's look); the alt text and tooltip say "not earned". */
	.badges .locked img,
	.badges .locked .letter {
		filter: grayscale(1) brightness(0.55);
		opacity: 0.45;
	}
	.dex {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.bar {
		display: block;
		height: 8px;
		border-radius: 4px;
		background: var(--border);
		overflow: hidden;
	}
	.bar b {
		display: block;
		height: 100%;
		background: var(--accent);
		transition: width 0.4s ease;
	}
	@media (prefers-reduced-motion: reduce) {
		.bar b {
			transition: none;
		}
	}
	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		.card {
			padding: 16px;
			gap: 12px;
		}
		.pts {
			font-size: 20px;
		}
		/* Compact on the Scores tab: Kanto's row only once it starts. */
		.later {
			display: none;
		}
		.pips i,
		.badges li {
			width: 20px;
			height: 20px;
		}
	}
</style>
