<!-- /dex: the Pibs' Pokédex (backend GroupDex, GET /dex/group): all 251, with every species any Pib has caught in
     colour, the ones only seen as a silhouette, and who was first to see and to catch each, and when. Pick one for its
     record. Most firsts on the side. Species names in Pokédex order from /notes/words; sprites from the player's ROM.
     Live: a first catch (DEX_FIRST) or any PROGRESS fetches it again. -->
<script lang="ts">
	import SubPage from '#lib/components/SubPage.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import Name from '#lib/components/Name.svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import { loadWords } from '#lib/notes.svelte.ts';
	import { session } from '#lib/session.svelte.ts';
	import { TIER_NAMES, type Tier } from '#lib/play.ts';

	type First = { user: string; at: number };
	type Dex = { seen: Record<string, First>; caught: Record<string, First> };
	type Filter = 'all' | 'caught' | 'seen' | 'missing';

	let names = $state<string[]>([]);
	let dex = $state<Dex>({ seen: {}, caught: {} });
	let filter = $state<Filter>('all');
	let picked = $state<number | null>(null);
	// Each species' Power Rating tier (S, M legends ... F): a seen or caught one is framed by it, as party cards are.
	let tiers = $state<(string | null)[]>([]);

	const load = () => api<Dex>('GET', '/dex/group').then((d) => (dex = d), () => {});
	$effect(() => {
		loadWords().then((w) => (names = w.words['Pokémon'] ?? []), () => {});
		api<(string | null)[]>('GET', '/game/tiers').then((t) => (tiers = t), () => {});
		load();
		let t: ReturnType<typeof setTimeout> | undefined;
		const soon = () => {
			clearTimeout(t);
			t = setTimeout(load, 1500);
		};
		const offs = [on('DEX_FIRST', soon), on('PROGRESS', soon)];
		return () => {
			clearTimeout(t);
			offs.forEach((off) => off());
		};
	});

	// Firsts are keyed by the games' spelling; names come from the same ROM tables, any case.
	const find = (map: Record<string, First>, name: string) => map[name] ?? Object.entries(map).find(([k]) => k.toLowerCase() === name.toLowerCase())?.[1];
	const entries = $derived(
		names.map((name, i) => {
			const caught = find(dex.caught, name), seen = find(dex.seen, name);
			return { n: i + 1, name, caught, seen, state: caught ? 'caught' : seen ? 'seen' : 'missing' };
		})
	);
	const caughtCount = $derived(entries.filter((e) => e.state === 'caught').length);
	const seenCount = $derived(entries.filter((e) => e.state !== 'missing').length);
	const shown = $derived(
		entries.filter((e) => filter === 'all' || (filter === 'caught' ? e.state === 'caught' : filter === 'seen' ? e.state === 'seen' : e.state === 'missing'))
	);
	const leaders = $derived(
		Object.entries(
			Object.values(dex.caught).reduce<Record<string, number>>((acc, f) => ((acc[f.user] = (acc[f.user] ?? 0) + 1), acc), {})
		)
			.sort((a, b) => b[1] - a[1])
			.slice(0, 5)
	);
	const current = $derived(picked ? entries[picked - 1] : null);
	const currentTier = $derived(current && current.state !== 'missing' ? ((tiers[current.n] as Tier | null) ?? null) : null);
	const when = (at: number) =>
		at ? new Date(at).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : 'before records began';
	const pad = (n: number) => String(n).padStart(3, '0');
	const FILTERS: [Filter, string][] = [
		['all', 'All'],
		['caught', 'Caught'],
		['seen', 'Seen only'],
		['missing', 'Missing']
	];
</script>

<svelte:head><title>Pibs' Pokédex · PPPPv2</title></svelte:head>

<SubPage>
	<div class="head">
		<div>
			<h1 class="pixel">Pibs' Pokédex</h1>
			<p class="muted small">Every Pokémon any Pib has seen or caught, and who got there first.</p>
		</div>
		<div class="totals">
			<span><b>{caughtCount}</b><span class="muted"> / 251 caught</span></span>
			<span><b>{seenCount}</b><span class="muted"> / 251 seen</span></span>
		</div>
	</div>
	<div class="bar" role="progressbar" aria-label="Caught" aria-valuemin="0" aria-valuemax="251" aria-valuenow={caughtCount}>
		<span class="seen" style:width="{(seenCount / 251) * 100}%"></span>
		<span class="caught" style:width="{(caughtCount / 251) * 100}%"></span>
	</div>

	<div class="layout">
		<section aria-label="Pokédex">
			<div class="chips" role="group" aria-label="Show">
				{#each FILTERS as [f, label] (f)}
					<button type="button" aria-pressed={filter === f} onclick={() => (filter = f)}>{label}</button>
				{/each}
			</div>
			{#if !names.length}
				<p class="muted small">Loading the Pokédex…</p>
			{:else}
				<ul class="grid">
					{#each shown as e (e.n)}
						<li>
							<button type="button" class="mon {e.state} {e.state !== 'missing' && tiers[e.n] ? `tier-card tier-${tiers[e.n]}` : ''}" class:on={picked === e.n} onclick={() => (picked = e.n)} aria-label="#{pad(e.n)} {e.state === 'missing' ? 'unknown' : e.name}, {e.state}">
								{#if e.state !== 'missing'}
									<img src="/api/v1/game/icons/{e.n}.png" alt="" loading="lazy" />
								{:else}
									<span class="q" aria-hidden="true">?</span>
								{/if}
								<span class="num">{pad(e.n)}</span>
								{#if e.caught}<span class="by"><Avatar user={e.caught.user} size={16} /></span>{/if}
							</button>
						</li>
					{/each}
				</ul>
			{/if}
		</section>

		<aside>
			{#if current}
				<div class="card detail {currentTier ? `tier-card tier-${currentTier}` : ''}" role="dialog" aria-label="#{pad(current.n)} {current.state === 'missing' ? 'unknown' : current.name}">
					<button type="button" class="x" aria-label="Close" onclick={() => (picked = null)}>×</button>
					<div class="title">
						{#if current.state !== 'missing'}
							<span class="pic">
								<img src="/api/v1/game/icons/{current.n}.png" alt="" />
								{#if currentTier}<span class="tier-tag tier-{currentTier}" aria-hidden="true">{currentTier}</span>{/if}
							</span>
						{/if}
						<div>
							<span class="muted xs">#{pad(current.n)}{#if currentTier} · Tier {currentTier}, {TIER_NAMES[currentTier]}{/if}</span>
							<h2>{current.state === 'missing' ? '???' : current.name}</h2>
						</div>
					</div>
					{#if current.caught}
						<p><span class="label">First caught</span><span class="who"><Avatar user={current.caught.user} size={32} /><span class="col"><b><Name user={current.caught.user} /></b><span class="muted xs">{when(current.caught.at)}</span></span></span></p>
					{/if}
					{#if current.seen}
						<p><span class="label">First seen</span><span class="who"><Avatar user={current.seen.user} size={32} /><span class="col"><b><Name user={current.seen.user} /></b><span class="muted xs">{when(current.seen.at)}</span></span></span></p>
					{/if}
					{#if !current.seen}<p class="muted small">No Pib has seen it yet. Be the first!</p>{:else if !current.caught}<p class="muted small">Seen, never caught. Up for grabs.</p>{/if}
				</div>
			{:else}
				<div class="card hint"><p class="muted small">Pick a Pokémon to see who found it first.</p></div>
			{/if}
			{#if leaders.length}
				<div class="card">
					<h2 class="label">Most first catches</h2>
					<ol class="leaders">
						{#each leaders as [user, n] (user)}
							<li class:me={user === session.me?.id}><Avatar {user} size={24} /><Name {user} /><b>{n}</b></li>
						{/each}
					</ol>
				</div>
			{/if}
		</aside>
	</div>
</SubPage>

<style>
	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-end;
		justify-content: space-between;
		gap: 12px;
		margin: 8px 0 10px;
	}
	h1 {
		margin: 0 0 6px;
		font-size: 20px;
	}
	.pixel {
		font-family: var(--pixel-font);
	}
	p {
		margin: 0;
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
	.totals {
		display: flex;
		gap: 16px;
		font-size: 14px;
	}
	.totals b {
		font-size: 20px;
	}
	.bar {
		position: relative;
		height: 8px;
		border-radius: 999px;
		background: var(--sunk);
		overflow: hidden;
		margin-bottom: 16px;
	}
	.bar span {
		position: absolute;
		inset: 0 auto 0 0;
		border-radius: 999px;
	}
	.bar .seen {
		background: color-mix(in srgb, var(--r-rare) 40%, transparent);
	}
	.bar .caught {
		background: var(--r-uncommon);
	}
	.layout {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 300px;
		gap: 16px;
		align-items: start;
	}
	.detail {
		position: relative;
		--frame-bg: var(--panel);
	}
	.x {
		position: absolute;
		top: 8px;
		right: 8px;
		width: 32px;
		height: 32px;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
		font-size: 22px;
		line-height: 1;
		cursor: pointer;
	}
	.x:hover {
		background: var(--sunk);
		color: var(--text);
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin-bottom: 10px;
	}
	.chips button {
		padding: 5px 12px;
		border: 1px solid var(--border-strong);
		border-radius: 999px;
		background: none;
		color: var(--text);
		font: 600 13px var(--body-font);
		cursor: pointer;
	}
	.chips button[aria-pressed='true'] {
		background: var(--accent-tint);
		border-color: var(--accent-text);
	}
	ul,
	ol {
		list-style: none;
		margin: 0;
		padding: 0;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
		gap: 6px;
	}
	.mon {
		position: relative;
		width: 100%;
		aspect-ratio: 1;
		display: grid;
		place-items: center;
		padding: 0;
		border: 1px solid var(--border);
		border-radius: 10px;
		background: var(--sunk);
		--frame-bg: var(--sunk);
		color: var(--muted);
		cursor: pointer;
	}
	.mon img {
		width: 80%;
		height: 80%;
		image-rendering: pixelated;
	}
	.mon.seen img {
		filter: brightness(0);
		opacity: 0.45;
	}
	.mon.caught {
		border-color: color-mix(in srgb, var(--r-uncommon) 50%, var(--border));
	}
	.mon.on {
		outline: 2px solid var(--accent-text);
	}
	.q {
		font: 16px var(--pixel-font);
		opacity: 0.4;
	}
	.num {
		position: absolute;
		left: 4px;
		bottom: 2px;
		font-size: 10px;
	}
	.by {
		position: absolute;
		right: 3px;
		top: 3px;
		display: flex;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	aside {
		display: flex;
		flex-direction: column;
		gap: 12px;
		position: sticky;
		top: 80px;
	}
	.card {
		padding: 14px 16px;
		border: 1px solid var(--border);
		border-radius: 14px;
		background: var(--panel);
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.title {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.title .pic {
		position: relative;
		display: block;
	}
	.title .pic .tier-tag {
		top: 0;
		right: 0;
	}
	.title img {
		width: 64px;
		height: 64px;
		image-rendering: pixelated;
	}
	h2 {
		margin: 0;
		font-size: 18px;
	}
	.detail p {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.label {
		margin: 0;
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.who {
		display: flex;
		align-items: center;
		gap: 10px;
		margin-top: 4px;
	}
	.who .col {
		display: flex;
		flex-direction: column;
	}
	.leaders li {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 4px 0;
		font-size: 14px;
	}
	.leaders li b {
		margin-left: auto;
	}
	.leaders li.me {
		font-weight: 700;
	}
	/* Phones: the side panel stays at the top of the page (it does not follow the scroll), and a picked Pokémon's
	   record opens as a sheet at the foot of the screen. */
	@media (max-width: 760px) {
		.layout {
			grid-template-columns: minmax(0, 1fr);
		}
		aside {
			order: -1;
			position: static;
		}
		.hint {
			display: none;
		}
		.detail {
			position: fixed;
			left: 8px;
			right: 8px;
			bottom: calc(8px + env(safe-area-inset-bottom));
			z-index: 20;
			box-shadow: 0 -8px 32px rgb(0 0 0 / 0.4);
		}
	}
</style>
