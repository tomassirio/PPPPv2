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
	const n = (v: number | undefined) => (v == null ? '–' : v.toLocaleString());
</script>

<section class="about" aria-labelledby="about-title">
	<h2 id="about-title"><img class="logo" src={logo} alt="Pibs&amp;Pibettes Play Pokémon V2" /></h2>
	<p class="pitch">
		Your own Game Boy, linked to everyone else's.<span class="more" aria-hidden="true">▼</span>
	</p>

	<ul class="pillars">
		<!-- 16x16 pixel icons, drawn in the tile's plastic: a floppy disk, a link cable, a trophy. -->
		<li class="p-own">
			<p class="head">
				<svg viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">
					<path d="M1 1h11l3 3v11H1z" fill="currentColor" />
					<path d="M4 1h7v5H4z" fill="var(--panel)" />
					<path d="M8 2h2v3H8z" fill="currentColor" />
					<path d="M3 9h10v6H3z" fill="var(--panel)" />
					<path d="M4 10h8v1H4zM4 12h6v1H4z" fill="currentColor" opacity="0.6" />
				</svg>
				<b>OWN SAVE</b>
			</p>
			<span>One Game Boy per player, saved on the server.</span>
		</li>
		<li class="p-link">
			<p class="head">
				<svg viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">
					<path d="M2 1h1v2H2zM4 1h1v2H4zM11 13h1v2h-1zM13 13h1v2h-1z" fill="currentColor" />
					<path d="M1 3h5v5H1zM10 8h5v5h-5z" fill="currentColor" />
					<path d="M2 4h3v1H2zM11 11h3v1h-3z" fill="var(--panel)" />
					<path d="M3 8h1v4h4V5h4v3h-1V6H9v7H3z" fill="currentColor" />
				</svg>
				<b>LINK CABLE</b>
			</p>
			<span>Trades and battles between players.</span>
		</li>
		<li class="p-ach">
			<p class="head">
				<svg viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">
					<path d="M4 1h8v6h-1v1h-1v1H6V8H5V7H4z" fill="currentColor" />
					<path d="M1 2h3v1H2v2h1v1h1v1H2V6H1zM12 2h3v5h-2V6h1V5h1V3h-2z" fill="currentColor" />
					<path d="M7 9h2v3H7zM4 12h8v3H4z" fill="currentColor" />
					<path d="M6 2h1v4H6z" fill="var(--panel)" opacity="0.5" />
					<path d="M5 13h6v1H5z" fill="var(--panel)" />
				</svg>
				<b>SCOREBOARD</b>
			</p>
			<span>Points, ranks and achievements.</span>
		</li>
	</ul>

	<!-- Placeholders until the teaser loads (or if the server is down), so the card keeps its shape. -->
	<div class="card" aria-label="Totals so far" role="group">
		<p class="card-title">SO FAR</p>
		<!-- Who plays which: a little cartridge per version, in its plastic. -->
		<ul class="carts">
			{#each split as v (v.title)}
				<li style:--c={v.color} class:none={teaser && !v.n}>
					<svg viewBox="0 0 12 14" shape-rendering="crispEdges" aria-hidden="true">
						<path d="M0 0h10l2 2v12H0z" fill="var(--c)" />
						<path d="M2 3h8v7H2z" fill="color-mix(in oklab, var(--c), white 45%)" />
						<path d="M1 1h7v1H1z" fill="color-mix(in oklab, var(--c), black 25%)" />
					</svg>
					<b>{teaser ? v.n : '–'}</b><span>{v.name}</span>
				</li>
			{/each}
		</ul>
		<dl class="stats">
			<div>
				<dt>PLAYERS</dt>
				<dd><b>{n(teaser?.players)}</b><span class="soft"><span class="dot" class:live={teaser} aria-hidden="true"></span>{n(teaser?.online)} online</span></dd>
			</div>
			<div>
				<dt>CAUGHT</dt>
				<dd><b>{n(teaser?.caught)}</b><span class="soft">Pokémon, all players</span></dd>
			</div>
			<div>
				<dt>UNLOCKED</dt>
				<dd><b>{n(teaser?.achievements)}</b><span class="soft">achievements</span></dd>
			</div>
			<div>
				<dt>LINK</dt>
				<dd><b>{n(teaser?.trades)}<small>/</small>{n(teaser?.battles)}</b><span class="soft">trades / battles</span></dd>
			</div>
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
	/* Four big numbers, two by two, like a stats screen. */
	.stats {
		margin: 12px 0 0;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}
	.stats > div {
		padding: 10px 12px;
		border-radius: 6px;
		background: color-mix(in oklab, var(--text), transparent 95%);
	}
	dt {
		font: 8px/1 var(--pixel-font);
		color: var(--muted);
	}
	dd {
		margin: 6px 0 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	dd b {
		font: 20px/1 var(--pixel-font);
		color: var(--text);
	}
	dd small {
		margin: 0 4px;
		font-size: 14px;
		color: var(--muted);
	}
	dd .soft {
		font-size: 12px;
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
		grid-template-columns: repeat(3, minmax(0, 1fr));
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
	/* The icon over the title: side by side, SCOREBOARD would not fit a third of the card. */
	.head {
		margin: 0;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 6px;
	}
	.head svg {
		width: 24px;
		height: 24px;
		flex: none;
		color: var(--c);
	}
	.pillars b {
		font: 8px/1.3 var(--pixel-font);
		white-space: nowrap;
		color: color-mix(in oklab, var(--c), var(--text) 45%); /* Silver alone is too faint on the light theme */
	}
	/* The three cartridges' plastic, in picker order (VERSIONS). */
	.p-own {
		--c: #d4a72c;
	}
	.p-link {
		--c: #5aaed6;
	}
	.p-ach {
		--c: #a3adb7;
	}
	.carts {
		margin: 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 8px;
	}
	.carts li {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 12px;
		color: var(--muted);
	}
	.carts li.none {
		opacity: 0.55;
	}
	.carts svg {
		width: 18px;
		height: 21px;
		flex: none;
	}
	.carts b {
		font: 14px/1 var(--pixel-font);
		color: var(--text);
	}
	.dot {
		display: inline-block;
		width: 8px;
		height: 8px;
		margin-right: 6px;
		vertical-align: 1px; /* centred on the digits; an empty inline-block would sit on the baseline and lift the row */
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
		.carts,
		.stats {
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
