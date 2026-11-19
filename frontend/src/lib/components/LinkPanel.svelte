<!-- Link cable card: your link record, ready to trade/battle toggles, everyone else who is ready (avatar, name, what they want, their game and
     party), the running session (two avatars joined by the cable, both parties, time left,
     Unplug), and Find someone to link with (LinkFinder: anyone online).
     State lives in link.svelte.ts. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import CableClubLocked from '#lib/components/CableClubLocked.svelte';
	import LinkError from '#lib/components/LinkError.svelte';
	import LinkFinder from '#lib/components/LinkFinder.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { act, clock, desk, left, link, roomName, since, verb, watchLink } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { clubClosed, place } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';
	import { cubicOut } from 'svelte/easing';

	$effect(() => watchLink());
	$effect(() => watchBoard());

	const me = $derived(session.me?.id);
	const s = $derived(link.state);
	const others = $derived(s?.lobby.filter((p) => p.userId !== me) ?? []);
	// Other Pibs' running links, to spectate.
	const live = $derived(s?.links.filter((p) => p.a !== me && p.b !== me) ?? []);
	// The card never makes its column scroll: a couple of each here, the rest one click away (Find someone, Live).
	const SHOWN = 2;
	const rowOf = (user: string) => board.rows.find((r) => r.userId === user);
	const mine = $derived(me ? rowOf(me)?.link : undefined);
	// Gold/Silver/Crystal before the Mystery Egg is back with Elm: nothing to link yet.
	const closed = $derived(me ? clubClosed(rowOf(me)) : false);
	let finding = $state(false);
	// Linked up: the finder is done. Left open, it would pop back up the moment the cable is unplugged.
	$effect(() => {
		if (s?.session) finding = false;
	});
	const parting = $derived(link.parting);
	// In a link room (the scoreboard row's location is the room alone there): the walk-in hint has done its job.
	const inLinkRoom = $derived(['Trade Center', 'Colosseum', 'Time Capsule'].includes(place(me ? rowOf(me)?.location : null).city));
	// The walk-in hint folds away (fades and closes up, the card's 14px gap with it) once they are inside.
	function fold(node: HTMLElement) {
		const h = node.offsetHeight;
		const cs = getComputedStyle(node);
		const pt = parseFloat(cs.paddingTop);
		const pb = parseFloat(cs.paddingBottom);
		const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
		return {
			duration: reduce ? 0 : 380,
			easing: cubicOut,
			css: (t: number) =>
				`box-sizing: border-box; overflow: hidden; opacity: ${t}; height: ${t * h}px; padding-top: ${t * pt}px; padding-bottom: ${t * pb}px; margin-bottom: ${(t - 1) * 14}px; transform: scale(${0.96 + 0.04 * t});`
		};
	}
	const gameOf = (user: string) => {
		const g = rowOf(user)?.game;
		return g ? board.names[g] : undefined;
	};
</script>

{#snippet kindIcon(kind: 'TRADE' | 'BATTLE', size: number)}
	<svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
		{#if kind === 'TRADE'}<path d="M4 8h13l-3-3M20 16H7l3 3" />{:else}<path d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5L21 6V3h-3L6.5 14.5M5 13l6 6M8 16l-4 4M3 19l2 2" />{/if}
	</svg>
{/snippet}

<section class="panel card {s?.session ? (s.session.kind === 'TRADE' ? 'k-trade' : 'k-battle') : ''}" class:linked={!!s?.session} aria-labelledby="link-h">
	{#if s?.session}
		{@const ss = s.session}
		{@const d = desk(ss.kind)}
		<div class="head">
			<h2 id="link-h" class="kind-title">{@render kindIcon(ss.kind, 18)}{ss.kind === 'TRADE' ? 'Trading' : 'Battling'}</h2>
			<span class="pixel timer" title="Linked for"><span class="sr">Linked for </span>{clock(since(ss.since))}</span>
		</div>
		<div class="cable-row {ss.kind === 'TRADE' ? 'r-trade' : 'r-battle'}" aria-hidden="true">
			<span class="av left">{#if me}<Avatar user={me} size={44} />{/if}</span>
			<span class="cable l"><i class="packet"></i></span>
			<span class="chip"><span class="dot"></span>{@render kindIcon(ss.kind, 14)}connected</span>
			<span class="cable r"><i class="packet"></i></span>
			<span class="av right"><Avatar user={ss.peer} size={44} /></span>
		</div>
		<div class="names">
			<div class="side">
				<b>You</b>
				{#if me}<PartyIcons party={rowOf(me)?.party} />{/if}
			</div>
			<span class="sr">linked with</span>
			<div class="side end">
				<b><Name user={ss.peer} /></b>
				<PartyIcons party={rowOf(ss.peer)?.party} />
			</div>
		</div>
		{#if !inLinkRoom && !ss.wrongRoom}
			<!-- Only until they are inside: answer the receptionist, save, walk in (arrows are locked meanwhile). -->
			<p class="where" transition:fold>
				<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
					><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
				>
				<span><b>Answer the {d.side} receptionist and save.</b> Arrows unlock in the {d.room}.</span>
			</p>
		{/if}
		{#if ss.wrongRoom}
			<p class="error" role="alert">
				You walked into the {roomName(ss.wrongRoom)}, but this link is a {verb(ss.kind)}. Walk back out and talk to the {d.side} receptionist.
			</p>
		{:else if ss.peerWrongRoom}
			<p class="error" role="alert">
				<Name user={ss.peer} /> walked into the {roomName(ss.peerWrongRoom)}, but this link is a {verb(ss.kind)}. They need the {d.side} receptionist.
			</p>
		{/if}
		<button class="ghost stop" title="The link has no time limit. Closing the browser for a minute unplugs too." onclick={() => act('DELETE', '/link/session')}
			>Unplug cable</button
		>
	{:else if closed}
		<div class="head">
			<h2 id="link-h" class="blue title">
				<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
					><path d="M9 7H6a5 5 0 0 0 0 10h3M15 7h3a5 5 0 0 1 0 10h-3M8 12h8" /></svg
				>Link cable
			</h2>
			<span class="lock muted xs"
				><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"
					><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
				>Locked</span
			>
		</div>
		<CableClubLocked step={rowOf(me!)?.cableClub ?? 0} />
	{:else}
		<div class="head">
			<h2 id="link-h" class="blue title">
				<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
					><path d="M9 7H6a5 5 0 0 0 0 10h3M15 7h3a5 5 0 0 1 0 10h-3M8 12h8" /></svg
				>Link cable
			</h2>
			<span class="record" aria-label="Your link record: {mine?.wins ?? 0} won, {mine?.losses ?? 0} lost, {mine?.trades ?? 0} trades">
				<span><b class="w">{mine?.wins ?? 0}</b> W</span>
				<span><b class="l">{mine?.losses ?? 0}</b> L</span>
				<span class="sep"></span>
				<span><b>{mine?.trades ?? 0}</b> {mine?.trades === 1 ? 'trade' : 'trades'}</span>
			</span>
		</div>
		{#if parting}
			<div class="parting-slot">
				<div class="cable-row parting" role="status">
					<span class="av left">{#if me}<Avatar user={me} size={44} />{/if}</span>
					<span class="cable l"></span>
					<span class="chip off"><span class="dot"></span>unplugged</span>
					<span class="cable r"></span>
					<span class="av right"><Avatar user={parting.peer} size={44} /></span>
				</div>
			</div>
		{/if}
		<div class="controls" class:split={!others.length}>
		<div class="ups">
		<div class="toggles-head"><span class="label">I'm up for</span></div>
		<div class="toggles" role="group" aria-label="I'm up for">
			{#each ['TRADE', 'BATTLE'] as const as kind (kind)}
				{@const on = s?.ready === kind}
				<button class="toggle {kind === 'TRADE' ? 't-trade' : 't-battle'}" class:on aria-pressed={on} title="Let others offer you a {verb(kind)}" onclick={() => act('PUT', '/link/ready', { kind: on ? null : kind })}>
					<span class="ico">{@render kindIcon(kind, 18)}</span>
					<b class="tl">{kind === 'TRADE' ? 'Trade' : 'Battle'}</b>
					{#if on}<span class="ready"><span class="live-dot" aria-hidden="true"></span>Ready</span>{/if}
				</button>
			{/each}
		</div>
		</div>
		{#if !others.length}
			<!-- One line when nobody is waiting; beside the toggles when the card is wide. -->
			<div class="nobody">
				<span class="muted">Nobody is waiting.</span>
				<button class="find small" onclick={() => (finding = true)}>Find someone online</button>
			</div>
		{/if}
		</div>

		{#if s?.outgoing}
			<p class="waiting" role="status">
				Offered to {verb(s.outgoing.kind)} with <b><Name user={s.outgoing.to} /></b>. Waiting… <span class="num">{left(s.outgoing.expiresAt)} s</span>
			</p>
		{/if}


		{#if others.length}
			<div class="sub">
				<span class="label">Ready now</span>
				<span class="muted xs">{others.length === 1 ? '1 Pib' : `${others.length} Pibs`}</span>
			</div>
			<ul>
				{#each others.slice(0, SHOWN) as p (p.userId)}
					{@const g = gameOf(p.userId)}
					<li>
						<Avatar user={p.userId} />
						<div class="who">
							<b><Name user={p.userId} /></b>
							<span class={p.kind === 'TRADE' ? 'trade' : 'battle'}>Wants to {verb(p.kind)}{g ? ` · ${g}` : ''}</span>
							{#if rowOf(p.userId)?.party?.length}<PartyIcons party={rowOf(p.userId)?.party} size={22} hp={false} />{/if}
						</div>
						<button
							class="small"
							class:primary={p.kind === 'TRADE'}
							class:battle={p.kind !== 'TRADE'}
							disabled={!!s?.outgoing || !!s?.incoming}
							onclick={() => act('POST', '/link/challenges', { userId: p.userId, kind: p.kind })}
						>
							{@render kindIcon(p.kind, 14)}{p.kind === 'TRADE' ? 'Trade' : 'Challenge'}<span class="sr"> {nameOf(p.userId)}</span>
						</button>
					</li>
				{/each}
			</ul>
			<button class="find" onclick={() => (finding = true)}
				>{others.length > SHOWN ? `See all ${others.length} ready · find someone online` : 'Find someone else online'}</button
			>
		{/if}
		{#if live.length}
			<div class="sub">
				<span class="label">Live now</span>
				{#if live.length > SHOWN}<a class="xs more" href="/watch">All {live.length} on Live →</a>{:else}<span class="muted xs">watch, read only</span>{/if}
			</div>
			<ul>
				{#each live.slice(0, SHOWN) as p (p.a)}
					<li>
						<Avatar user={p.a} />
						<div class="who">
							<b><Name user={p.a} /> vs <Name user={p.b} /></b>
							<span class={p.kind === 'TRADE' ? 'trade' : 'battle'}>{p.kind === 'TRADE' ? 'Trading' : 'Battling'} · {clock(since(p.since))}</span>
						</div>
						<a class="small watch" href="/watch?u={encodeURIComponent(p.a)}">Watch<span class="sr"> {nameOf(p.a)} and {nameOf(p.b)}</span></a>
					</li>
				{/each}
			</ul>
		{/if}
		<LinkFinder bind:open={finding} />
	{/if}
	<!-- An incoming challenge shows the error in its own card (LinkChallenge, in the corner): once is enough. -->
	{#if !(s?.incoming && !s.session)}<LinkError />{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.card {
		container-type: inline-size;
	}
	.controls {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.ups {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	/* Wide card, nobody waiting: the toggles and "Nobody is waiting" share one row. */
	@container (min-width: 480px) {
		.controls.split {
			flex-direction: row;
			align-items: flex-end;
			gap: 16px;
		}
		.controls.split .ups {
			flex: 1 1 auto;
			min-width: 0;
		}
		.controls.split .nobody {
			flex: none;
			flex-direction: column;
			align-items: flex-end;
			gap: 6px;
			padding-top: 0;
			border-top: none;
		}
	}
	.card {
		--kind: var(--link);
		border-color: var(--kind);
		box-shadow: inset 0 3px 0 var(--kind);
	}
	.card.linked {
		background-color: color-mix(in oklab, var(--kind) 7%, var(--panel));
		box-shadow: inset 0 5px 0 var(--kind);
	}
	.card {
		position: relative;
	}
	.card::after {
		content: '';
		position: absolute;
		inset: auto 12px 0;
		height: 3px;
		background: var(--kind);
		transform: scaleX(0);
		transform-origin: left;
	}
	.card.linked::after {
		transform: scaleX(1);
	}
	@media (prefers-reduced-motion: no-preference) {
		.card::after {
			transition: transform 450ms ease-in-out;
		}
		.card {
			transition: background-color 280ms ease-out, border-color 280ms ease-out, box-shadow 280ms ease-out;
		}
	}
	.title {
		display: inline-flex;
		align-items: center;
		gap: 8px;
	}
	/* Your record as one pill: won, lost | trades. */
	.record {
		display: inline-flex;
		align-items: center;
		gap: 10px;
		padding: 4px 12px;
		border-radius: 999px;
		background: var(--sunk);
		border: 1px solid var(--border);
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
	}
	.record b {
		font-size: 14px;
		color: var(--text);
		font-variant-numeric: tabular-nums;
	}
	.record b.w {
		color: var(--grass, #3fae4a);
	}
	.record b.l {
		color: var(--danger);
	}
	.record .sep {
		width: 1px;
		height: 14px;
		background: var(--border-strong);
	}
	.toggles-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 8px;
		margin-bottom: -6px;
	}
	.xs {
		font-size: 12px;
	}
	.toggles {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}
	/* Trade / Battle: a kind-coloured icon tile, the name and a hint; picked, the card takes the kind's colour and a tick. */
	button.toggle {
		--t: var(--accent-text);
		position: relative;
		height: 52px;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 0 12px 0 8px;
		border-radius: 12px;
		border: 1px solid var(--border);
		background: var(--sunk);
		color: var(--text);
		text-align: left;
		cursor: pointer;
	}
	button.toggle.t-battle {
		--t: var(--danger);
	}
	button.toggle:hover {
		border-color: color-mix(in oklab, var(--t) 55%, var(--border));
	}
	.ico {
		flex: none;
		width: 38px;
		height: 38px;
		display: grid;
		place-items: center;
		border-radius: 10px;
		background: color-mix(in oklab, var(--t) 16%, transparent);
		color: var(--t);
	}
	.tl {
		flex: 1;
		min-width: 0;
		font-size: 14px;
		line-height: 18px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	/* Ready: a pill with a dot breathing in the kind's colour. */
	.ready {
		flex: none;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 3px 8px;
		border-radius: 999px;
		background: color-mix(in oklab, var(--t) 18%, transparent);
		color: var(--t);
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.04em;
		text-transform: uppercase;
	}
	.live-dot {
		flex: none;
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--t);
	}
	@media (prefers-reduced-motion: no-preference) {
		.live-dot {
			animation: pulse 1.6s ease-in-out infinite;
		}
	}
	button.toggle.on {
		border-color: var(--t);
		background: color-mix(in oklab, var(--t) 12%, var(--sunk));
		box-shadow: 0 0 0 1px var(--t) inset;
	}
	button.toggle.on .ico {
		background: var(--t);
		color: var(--panel);
	}
	button.toggle.on .tl {
		color: var(--t);
	}
	@media (prefers-reduced-motion: no-preference) {
		button.toggle,
		.ico {
			transition: background-color 0.18s, border-color 0.18s, color 0.18s, box-shadow 0.18s;
		}
	}
	.card.k-trade {
		--kind: var(--accent-text);
	}
	.card.k-battle {
		--kind: var(--danger);
	}
	.kind-title {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		color: var(--kind);
	}
	.where {
		margin: 0;
		display: flex;
		align-items: flex-start;
		gap: 8px;
		padding: 8px 12px;
		border-radius: 10px;
		background: color-mix(in oklab, var(--kind) 10%, transparent);
		font-size: 13px;
		line-height: 18px;
		color: var(--muted);
	}
	.where svg {
		flex: none;
		margin-top: 1px;
		color: var(--kind);
	}
	.where b {
		color: var(--text);
	}
	.sub {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-top: 12px;
		border-top: 1px solid var(--border);
	}
	.label {
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.waiting {
		padding: 10px 12px;
		border-radius: 10px;
		background: var(--link-tint, var(--sunk));
		border: 1px solid var(--link);
		font-size: 13px;
	}
	.nobody {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
		padding-top: 12px;
		border-top: 1px solid var(--border);
	}
	.nobody button.find {
		width: auto;
		flex: none;
	}
	button.find {
		width: 100%;
		box-sizing: border-box;
		border: 1px solid var(--link);
		background: var(--link-tint, transparent);
		color: var(--link);
		cursor: pointer;
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}
	h2 {
		margin: 0;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.blue {
		color: var(--link);
	}
	.timer {
		font-size: 12px;
		font-variant-numeric: tabular-nums;
	}
	p {
		margin: 0;
		font-size: 14px;
	}
	.muted {
		font-size: 13px;
		line-height: 19px;
		color: var(--muted);
	}
	.num {
		font-variant-numeric: tabular-nums;
	}
	.watch {
		padding: 6px 12px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		color: var(--text);
		text-decoration: none;
		white-space: nowrap;
	}
	.more {
		color: var(--link);
		font-weight: 600;
		text-decoration: none;
	}
	.more:hover {
		text-decoration: underline;
	}
	.watch:focus-visible,
	.more:focus-visible {
		outline: 2px solid var(--link);
		outline-offset: 2px;
	}
	.lock {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 3px 8px;
		border-radius: 999px;
		border: 1px solid var(--border);
	}
	.error {
		font-size: 13px;
		color: var(--danger);
	}
	.cable-row {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.cable {
		position: relative;
		flex: 1;
		min-width: 16px;
		height: 2px;
		background: repeating-linear-gradient(90deg, var(--link) 0 8px, transparent 8px 14px);
	}
	.cable.l {
		transform-origin: left;
	}
	.cable.r {
		transform-origin: right;
	}
	.av {
		display: inline-flex;
	}
	.packet {
		display: none;
	}
	.chip svg {
		flex: none;
	}
	.chip.off {
		background: color-mix(in oklab, var(--danger) 12%, transparent);
		color: var(--danger);
	}
	.chip.off .dot {
		background: var(--danger);
	}
	.parting-slot {
		display: grid;
		grid-template-rows: 1fr;
	}
	.parting-slot > .cable-row {
		min-height: 0;
		overflow: hidden;
	}
	.parting .cable {
		background: repeating-linear-gradient(90deg, var(--danger) 0 8px, transparent 8px 14px);
		transform: scaleX(0);
	}
	@media (prefers-reduced-motion: no-preference) {
		.linked .cable {
			animation: draw 520ms cubic-bezier(0.3, 0.8, 0.3, 1) both;
		}
		.linked .chip {
			animation: plug 520ms 420ms cubic-bezier(0.3, 1.6, 0.5, 1) both;
		}
		.linked .av.left {
			animation: in-left 420ms ease-out both;
		}
		.linked .av.right {
			animation: in-right 420ms ease-out both;
		}
		.linked .dot {
			animation: pulse 1.6s 1s ease-in-out infinite;
		}
		/* Trade: a packet each way along the cable. Battle: the swords clash and the row shakes. */
		.r-trade .packet {
			display: block;
			position: absolute;
			top: -3px;
			width: 8px;
			height: 8px;
			border-radius: 50%;
			background: var(--kind);
			opacity: 0;
		}
		.r-trade .l .packet,
		.r-trade .r .packet {
			animation: swap-right 1.8s 1s ease-in-out infinite;
		}
		.r-trade .r .packet {
			animation-name: swap-left;
		}
		.r-battle .chip svg {
			animation: clash 1.4s 1s ease-in-out infinite;
		}
		.r-battle {
			animation: shake 420ms 480ms ease-in-out both;
		}
		.parting .av.left {
			animation: out-left 600ms ease-in both;
		}
		.parting .av.right {
			animation: out-right 600ms ease-in both;
		}
		.parting .cable {
			animation: snap 600ms ease-in both;
		}
		.parting .chip {
			animation: pop-out 1.4s ease-out both;
		}
		.parting {
			animation: fade-out 1.2s ease-in both;
		}
		.parting-slot {
			animation: collapse-row 420ms 1.15s cubic-bezier(0.4, 0, 0.2, 1) both;
		}
	}
	@keyframes draw {
		from {
			transform: scaleX(0);
		}
	}
	@keyframes plug {
		from {
			transform: scale(0.2);
			opacity: 0;
		}
	}
	@keyframes in-left {
		from {
			transform: translateX(-12px);
			opacity: 0;
		}
	}
	@keyframes in-right {
		from {
			transform: translateX(12px);
			opacity: 0;
		}
	}
	@keyframes pulse {
		50% {
			opacity: 0.35;
			transform: scale(0.7);
		}
	}
	@keyframes swap-right {
		0% {
			left: 0;
			opacity: 0;
		}
		15%,
		85% {
			opacity: 1;
		}
		100% {
			left: calc(100% - 8px);
			opacity: 0;
		}
	}
	@keyframes swap-left {
		0% {
			left: calc(100% - 8px);
			opacity: 0;
		}
		15%,
		85% {
			opacity: 1;
		}
		100% {
			left: 0;
			opacity: 0;
		}
	}
	@keyframes clash {
		0%,
		60%,
		100% {
			transform: none;
		}
		70% {
			transform: scale(1.5) rotate(-12deg);
		}
		80% {
			transform: scale(1.2) rotate(10deg);
		}
	}
	@keyframes shake {
		25% {
			transform: translateX(-3px);
		}
		75% {
			transform: translateX(3px);
		}
	}
	@keyframes snap {
		from {
			transform: scaleX(1);
		}
		to {
			transform: scaleX(0);
		}
	}
	@keyframes out-left {
		to {
			transform: translateX(-10px);
		}
	}
	@keyframes out-right {
		to {
			transform: translateX(10px);
		}
	}
	@keyframes pop-out {
		from {
			transform: scale(1.25);
		}
	}
	@keyframes fade-out {
		0%,
		65% {
			opacity: 1;
		}
		100% {
			opacity: 0;
		}
	}
	@keyframes collapse-row {
		from {
			grid-template-rows: 1fr;
			margin-bottom: 0;
		}
		to {
			grid-template-rows: 0fr;
			margin-bottom: -14px;
		}
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 4px 9px;
		border-radius: 999px;
		background: var(--link-tint);
		color: var(--link);
		font-size: 12px;
		font-weight: 600;
		white-space: nowrap;
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--link);
	}
	/* One line each: who, then their party on the right. */
	.names {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.side {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		min-width: 0;
	}
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	li {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
	}
	.who {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 3px;
	}
	.who > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.who span {
		font-size: 13px;
		line-height: 19px;
	}
	.trade {
		color: var(--accent-text);
	}
	.battle {
		color: var(--danger);
	}
	button {
		height: 40px;
		padding: 0 16px;
		border-radius: 8px;
		font-weight: 600;
		font-size: 14px;
		white-space: nowrap;
	}
	button.small {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		flex: none;
		height: 32px;
		padding: 0 12px;
		font-size: 13px;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	button:disabled {
		opacity: 0.5;
		cursor: default;
	}
	.ghost {
		border: 1px solid var(--border-strong);
		background: transparent;
		color: var(--text);
	}
	.ghost.stop {
		border-color: var(--danger);
		color: var(--danger);
	}
	.primary {
		border: 1px solid var(--accent);
		background: var(--accent);
		color: var(--ink);
	}
	button.battle {
		border: 1px solid var(--danger);
		background: var(--danger);
		color: #fff;
	}
	.primary:hover {
		background: var(--accent-hover);
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
		white-space: nowrap;
	}
</style>
