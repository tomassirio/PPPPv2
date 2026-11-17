<!-- What PPPP V2 is, for first-time visitors, the group's numbers (public teaser: totals only, never who), and the
     way in. Drawn as an in-game text box: three features, then the totals. -->
<script lang="ts">
	import type { Teaser } from '#lib/teaser.js';
	import logo from '#lib/assets/ppppv2-logo.png';

	/** requested: this sign-in was turned into an access request (not on the list yet). */
	let { teaser, requested = false }: { teaser: Teaser | null; requested?: boolean } = $props();

	/** The three versions, in picker order, with their cartridge plastic. */
	const VERSIONS = [
		{ title: 'POKEMON_GLD', name: 'Gold', color: '#d4a72c' },
		{ title: 'PM_CRYSTAL', name: 'Crystal', color: '#5aaed6' },
		{ title: 'POKEMON_SLV', name: 'Silver', color: '#a3adb7' }
	];
	const split = $derived(VERSIONS.map((v) => ({ ...v, n: teaser?.versions?.[v.title] ?? 0 })));
	const total = $derived(Math.max(1, split.reduce((a, v) => a + v.n, 0)));
	const n = (v: number | undefined) => (v == null ? '–' : v.toLocaleString());
</script>

<section class="about" aria-labelledby="about-title">
	<h2 id="about-title"><img class="logo" src={logo} alt="Pibs&amp;Pibettes Play Pokémon V2" /></h2>
	<p class="pitch">
		Each player runs their own game on the server. Progress is scored on a shared board.<span class="more" aria-hidden="true">▼</span>
	</p>

	<ul class="pillars">
		<li class="p-own">
			<b>OWN SAVE</b>
			<span>One Game Boy per player, saved on the server.</span>
		</li>
		<li class="p-link">
			<b>LINK CABLE</b>
			<span>Trades and battles between players.</span>
		</li>
		<li class="p-ach">
			<b>SCOREBOARD</b>
			<span>Points, ranks and achievements.</span>
		</li>
	</ul>

	<!-- Placeholders until the teaser loads (or if the server is down), so the card keeps its shape. -->
	<div class="card" aria-label="Totals so far" role="group">
		<p class="card-title">SO FAR</p>
		<div class="split" role="img" aria-label={split.map((v) => `${v.n} on ${v.name}`).join(', ')}>
			{#each split as v (v.title)}
				<i style:flex-grow={teaser ? v.n / total : 1} style:background={v.color}></i>
			{/each}
		</div>
		<p class="legend" aria-hidden="true">
			{#each split as v (v.title)}<span><i style:background={v.color}></i>{teaser ? v.n : '–'} {v.name}</span>{/each}
		</p>
		<dl>
			<dt>PLAYERS</dt>
			<dd>{n(teaser?.players)}<span class="soft">&ensp;·&ensp;<span class="dot" class:live={teaser} aria-hidden="true"></span>{n(teaser?.online)} online</span></dd>
			<dt>CAUGHT</dt>
			<dd>{n(teaser?.caught)}<span class="soft">&nbsp;Pokémon, all players</span></dd>
			<dt>UNLOCKED</dt>
			<dd>{n(teaser?.achievements)}<span class="soft">&nbsp;achievements</span></dd>
			<dt>LINK</dt>
			<dd>{n(teaser?.trades)}<span class="soft">&nbsp;trades&ensp;·&ensp;</span>{n(teaser?.battles)}<span class="soft">&nbsp;battles</span></dd>
		</dl>
		<p class="phone-line">{n(teaser?.players)} players · <span class="dot" class:live={teaser} aria-hidden="true"></span>{n(teaser?.online)} online · {n(teaser?.caught)} caught</p>
	</div>

	{#if requested}
		<div class="requested" role="status">
			<span class="hourglass" aria-hidden="true">⌛</span>
			<div>
				<b>Access requested</b>
				<p class="long">You're not on the player list yet, so an admin has been asked to let you in. Nothing else to
					do: once they accept, sign in again and your Game Boy will be waiting.</p>
				<p class="short">An admin will let you in. Sign in again once they do.</p>
			</div>
		</div>
	{/if}

	<a class="cta" class:again={requested} href="/oauth2/authorization/google" data-sveltekit-reload>
		<!-- Google's G, in Poké Ball colours: red top, black band, white bottom, the button where the bar starts. -->
		<svg class="g" viewBox="0 0 24 24" aria-hidden="true">
			<defs>
				<linearGradient id="g-ball" x1="0" y1="0" x2="0" y2="24" gradientUnits="userSpaceOnUse">
					<stop offset="0.45" stop-color="#e04848" />
					<stop offset="0.45" stop-color="#202020" />
					<stop offset="0.55" stop-color="#202020" />
					<stop offset="0.55" stop-color="#f8f8f8" />
				</linearGradient>
			</defs>
			<path d="M18.13 6.86A8 8 0 1 0 20 12H12" fill="none" stroke="#202020" stroke-width="7" stroke-linejoin="round" />
			<path d="M18.13 6.86A8 8 0 1 0 20 12H12" fill="none" stroke="url(#g-ball)" stroke-width="4" />
			<circle cx="12" cy="12" r="2.3" fill="#f8f8f8" stroke="#202020" stroke-width="1.4" />
		</svg>
		{requested ? 'Check again' : 'Sign in with Google'}
	</a>
</section>

<style>
	/* The game's text box: a dark frame, a light gap, a thin inner line. */
	.about {
		position: relative;
		z-index: 1;
		width: 430px;
		box-sizing: border-box;
		padding: 26px 26px 22px;
		border-radius: 12px;
		/* Frosted glass: the world behind shows through, blurred, under a tint that keeps the text readable. */
		background: color-mix(in oklab, var(--panel) 80%, transparent);
		backdrop-filter: blur(12px) saturate(1.2);
		-webkit-backdrop-filter: blur(12px) saturate(1.2);
		border: 4px solid var(--text);
		box-shadow:
			inset 0 0 0 4px var(--panel),
			inset 0 0 0 6px var(--text),
			0 16px 40px rgb(0 0 0 / 0.3);
		color: var(--text);
	}
	h2 {
		margin: 0 0 14px;
		line-height: 0;
		font: 18px/1.6 var(--pixel-font);
	}
	.logo {
		display: block;
		width: 100%;
		max-width: 260px;
		height: auto;
		margin: 0 auto;
	}
	.pitch {
		margin: 0;
		font-size: 15px;
		line-height: 1.55;
		color: var(--muted);
	}
	.more {
		margin-left: 6px;
		font-size: 10px;
		color: var(--text);
	}
	@media (prefers-reduced-motion: no-preference) {
		.more {
			animation: blink 0.9s steps(1) infinite;
		}
	}
	@keyframes blink {
		50% {
			opacity: 0;
		}
	}

	.card {
		margin-top: 16px;
		padding: 12px 14px;
		border-radius: 8px;
		background: color-mix(in oklab, var(--text), transparent 94%);
	}
	.card-title {
		margin: 0 0 10px;
		font: 9px/1 var(--pixel-font);
		color: var(--accent-text);
	}
	dl {
		margin: 0;
		display: grid;
		grid-template-columns: auto 1fr;
		gap: 8px 14px;
		align-items: center;
	}
	dt {
		font: 8px/1 var(--pixel-font);
		color: var(--muted);
	}
	dd {
		margin: 0;
		font-size: 14px;
		font-weight: 600;
	}
	.phone-line {
		display: none;
		margin: 0;
		font-size: 12px;
		font-weight: 600;
	}
	.soft {
		font-weight: 500;
		color: var(--muted);
	}
	.pillars {
		margin: 16px 0 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 8px;
	}
	.pillars li {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 10px;
		border-radius: 8px;
		border: 2px solid var(--c);
		background: color-mix(in oklab, var(--c), transparent 90%);
		font-size: 12px;
		line-height: 1.4;
	}
	.pillars b {
		font: 8px/1.3 var(--pixel-font);
		color: var(--c);
	}
	.p-own {
		--c: #d4a72c;
	}
	.p-link {
		--c: #6ea3ff;
	}
	.p-ach {
		--c: #b07ce8;
	}
	.split {
		display: flex;
		gap: 2px;
		height: 8px;
		border-radius: 4px;
		overflow: hidden;
	}
	.split i {
		flex: 1 1 0;
		min-width: 4px;
		transition: flex-grow 0.6s ease;
	}
	.legend {
		margin: 6px 0 12px;
		display: flex;
		flex-wrap: wrap;
		gap: 4px 12px;
		font-size: 12px;
		color: var(--muted);
	}
	.legend span {
		display: inline-flex;
		align-items: center;
		gap: 5px;
	}
	.legend i {
		width: 8px;
		height: 8px;
		border-radius: 2px;
	}
	.dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		margin-right: 6px;
		border-radius: 50%;
		background: var(--muted);
	}
	.dot.live {
		background: #3fb950;
		box-shadow: 0 0 6px #3fb950;
	}

	.requested {
		margin-top: 16px;
		display: flex;
		gap: 12px;
		align-items: flex-start;
		padding: 12px 14px;
		border-radius: 8px;
		border: 2px solid var(--accent);
		background: color-mix(in oklab, var(--accent), transparent 85%);
	}
	.requested b {
		font: 10px/1.4 var(--pixel-font);
		color: var(--accent-text);
	}
	.requested p {
		margin: 6px 0 0;
		font-size: 13px;
		line-height: 1.45;
	}
	.short {
		display: none;
	}
	.hourglass {
		font-size: 22px;
		line-height: 1;
	}
	@media (prefers-reduced-motion: no-preference) {
		.hourglass {
			animation: turn 2.4s steps(2) infinite;
		}
	}
	@keyframes turn {
		50% {
			rotate: 180deg;
		}
	}
	.cta.again {
		background: var(--panel);
		color: var(--text);
		box-shadow: inset 0 0 0 2px var(--border-strong);
	}
	.cta {
		margin-top: 18px;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 12px;
		min-height: 50px;
		border-radius: 10px;
		background: var(--accent);
		color: #1d1f1c;
		font: 11px/1 var(--pixel-font);
		text-decoration: none;
		box-shadow: 0 4px 0 color-mix(in oklab, var(--accent), black 40%);
		transition:
			transform 60ms,
			box-shadow 60ms,
			background 120ms;
	}
	.cta:hover {
		background: var(--accent-hover);
	}
	.cta:active {
		transform: translateY(3px);
		box-shadow: 0 1px 0 color-mix(in oklab, var(--accent), black 40%);
	}
	.cta:focus-visible {
		outline: 3px solid var(--text);
		outline-offset: 3px;
	}
	.g {
		width: 24px;
		height: 24px;
		flex: none;
	}
	@media (prefers-reduced-motion: no-preference) {
		.cta:hover .g {
			animation: wobble 0.5s steps(4);
		}
	}
	/* The caught-ball wobble. */
	@keyframes wobble {
		25% {
			rotate: -15deg;
		}
		75% {
			rotate: 15deg;
		}
	}

	/* Phones: a compact strip under the device: title, the card's numbers on one line, the button. */
	@media (max-width: 899px) {
		.about {
			width: min(410px, calc(100vw - 24px));
			padding: 12px 14px;
			border-width: 3px;
			box-shadow:
				inset 0 0 0 3px var(--panel),
				inset 0 0 0 5px var(--text),
				0 10px 24px rgb(0 0 0 / 0.3);
		}
		h2 {
			margin: 0;
		}
		.logo {
			max-width: 150px;
		}
		.pitch,
		.card-title,
		.pillars,
		.legend,
		.split,
		dl {
			display: none;
		}
		.phone-line {
			display: block;
		}
		.requested {
			margin-top: 8px;
			padding: 8px 10px;
		}
		.long {
			display: none;
		}
		.short {
			display: block;
		}
		.card {
			margin-top: 8px;
			padding: 0;
			background: none;
		}
		.cta {
			margin-top: 10px;
			min-height: 44px;
		}
	}
</style>
