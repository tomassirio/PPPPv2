<!-- Link cable card: your link record, ready to trade/battle toggles, everyone else who is ready (avatar, name, what they want, their game and
     party), the incoming challenge and the running session (two avatars joined by the cable, both parties, time left,
     Unplug), and Find someone to link with (LinkFinder: anyone online).
     State lives in link.svelte.ts. -->
<script lang="ts">
	import Avatar from '#lib/components/Avatar.svelte';
	import LinkChallenge from '#lib/components/LinkChallenge.svelte';
	import LinkFinder from '#lib/components/LinkFinder.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { act, clock, left, link, verb, watchLink } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	$effect(() => watchLink());
	$effect(() => watchBoard());

	const me = $derived(session.me?.id);
	const s = $derived(link.state);
	const others = $derived(s?.lobby.filter((p) => p.userId !== me) ?? []);
	const rowOf = (user: string) => board.rows.find((r) => r.userId === user);
	const mine = $derived(me ? rowOf(me)?.link : undefined);
	let finding = $state(false);
	const gameOf = (user: string) => {
		const g = rowOf(user)?.game;
		return g ? board.names[g] : undefined;
	};
</script>

<section class="panel card" class:linked={!!s?.session} aria-labelledby="link-h">
	{#if s?.session}
		{@const ss = s.session}
		<div class="head">
			<h2 id="link-h" class="blue">Link cable · {verb(ss.kind)}</h2>
			<span class="pixel timer" title="Time left"><span class="sr">Time left: </span>{clock(left(ss.endsAt))}</span>
		</div>
		<div class="cable-row" aria-hidden="true">
			{#if me}<Avatar user={me} size={44} />{/if}
			<span class="cable"></span>
			<span class="chip"><span class="dot"></span>connected</span>
			<span class="cable"></span>
			<Avatar user={ss.peer} size={44} />
		</div>
		<div class="names">
			<div class="side">
				<b>You</b>
				{#if me}<PartyIcons party={rowOf(me)?.party} />{/if}
			</div>
			<span class="sr">linked with</span>
			<div class="side end">
				<b>{nameOf(ss.peer)}</b>
				<PartyIcons party={rowOf(ss.peer)?.party} />
			</div>
		</div>
		<p class="muted">
			Both consoles run in step until someone unplugs or the 15-minute cap ends. Go to the Cable Club upstairs in any
			Pokémon Center.
		</p>
		<button class="ghost stop" onclick={() => act('DELETE', '/link/session')}>Unplug cable</button>
	{:else}
		<div class="head">
			<h2 id="link-h" class="blue title">
				<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
					><path d="M9 7H6a5 5 0 0 0 0 10h3M15 7h3a5 5 0 0 1 0 10h-3M8 12h8" /></svg
				>Link cable
			</h2>
			<span class="record" title="Your link record"
				><b class="w">{mine?.wins ?? 0}</b>–<b class="l">{mine?.losses ?? 0}</b><span class="muted"> · {mine?.trades ?? 0} {mine?.trades === 1 ? 'trade' : 'trades'}</span></span
			>
		</div>
		<div class="toggles" role="group" aria-label="Let others offer you">
			{#each ['TRADE', 'BATTLE'] as const as kind (kind)}
				{@const on = s?.ready === kind}
				<button class="toggle {kind === 'TRADE' ? 't-trade' : 't-battle'}" class:on aria-pressed={on} onclick={() => act('PUT', '/link/ready', { kind: on ? null : kind })}>
					<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
						{#if kind === 'TRADE'}<path d="M4 8h13l-3-3M20 16H7l3 3" />{:else}<path d="M14.5 17.5L3 6V3h3l11.5 11.5M13 19l6-6M16 16l4 4M19 21l2-2M9.5 17.5L21 6V3h-3L6.5 14.5M5 13l6 6M8 16l-4 4M3 19l2 2" />{/if}
					</svg>
					<span class="tl"><b>{on ? '✓ ' : ''}Ready to {verb(kind)}</b><small>{on ? 'Others can offer you one' : 'Let others offer you one'}</small></span>
				</button>
			{/each}
		</div>
		{#if s?.ready}
			<p class="muted">Stand in a Pokémon Center so you can walk straight up to the Cable Club.</p>
		{/if}

		{#if s?.outgoing}
			<p class="waiting" role="status">
				Offered to {verb(s.outgoing.kind)} with <b>{nameOf(s.outgoing.to)}</b>. Waiting… <span class="num">{left(s.outgoing.expiresAt)} s</span>
			</p>
		{/if}

		<LinkChallenge />

		<div class="sub">
			<span class="label">Ready now</span>
			<span class="muted xs">{others.length === 1 ? '1 Pib' : `${others.length} Pibs`}</span>
		</div>
		{#if others.length}
			<ul>
				{#each others as p (p.userId)}
					{@const g = gameOf(p.userId)}
					<li>
						<Avatar user={p.userId} />
						<div class="who">
							<b>{nameOf(p.userId)}</b>
							<span class={p.kind === 'TRADE' ? 'trade' : 'battle'}>Wants to {verb(p.kind)}{g ? ` · ${g}` : ''}</span>
							{#if rowOf(p.userId)?.party?.length}<PartyIcons party={rowOf(p.userId)?.party} size={22} hp={false} />{/if}
						</div>
						<button
							class="small"
							class:primary={p.kind === 'TRADE'}
							class:ghost={p.kind !== 'TRADE'}
							disabled={!!s?.outgoing || !!s?.incoming}
							onclick={() => act('POST', '/link/challenges', { userId: p.userId, kind: p.kind })}
						>
							{p.kind === 'TRADE' ? 'Trade' : 'Battle'}<span class="sr"> with {nameOf(p.userId)}</span>
						</button>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="empty muted">Nobody is waiting right now. You can still offer a trade or a battle to anyone online.</p>
		{/if}
		<button class="find" onclick={() => (finding = true)}>Find someone to link with</button>
		<LinkFinder bind:open={finding} />
	{/if}
	{#if link.error}<p class="error" role="alert">{link.error}</p>{/if}
</section>

<style>
	.card {
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.card,
	.card.linked {
		border-color: var(--link);
		box-shadow: inset 0 3px 0 var(--link);
	}
	.title {
		display: inline-flex;
		align-items: center;
		gap: 8px;
	}
	.record {
		font-size: 13px;
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
	}
	.record .w {
		color: var(--grass, #3fae4a);
	}
	.record .l {
		color: var(--danger);
	}
	.xs {
		font-size: 12px;
	}
	.toggles {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}
	button.toggle {
		height: auto;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 12px;
		border-radius: 12px;
		border: 1px solid var(--border-strong);
		background: var(--sunk);
		color: var(--text);
		text-align: left;
		cursor: pointer;
	}
	.toggle svg {
		flex: none;
		color: var(--muted);
	}
	.tl {
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
	}
	.tl b {
		font-size: 14px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.tl small {
		font-size: 11px;
		font-weight: 500;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.toggle.t-trade.on {
		border-color: var(--accent-text);
		background: var(--accent-tint);
	}
	.toggle.t-trade.on svg,
	.toggle.t-trade.on b {
		color: var(--accent-text);
	}
	.toggle.t-battle.on {
		border-color: var(--danger);
		background: color-mix(in oklab, var(--danger) 12%, transparent);
	}
	.toggle.t-battle.on svg,
	.toggle.t-battle.on b {
		color: var(--danger);
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
	.empty {
		padding: 12px;
		border-radius: 10px;
		border: 1px dashed var(--border-strong);
		text-align: center;
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
		flex: 1;
		min-width: 16px;
		height: 2px;
		background: repeating-linear-gradient(90deg, var(--link) 0 8px, transparent 8px 14px);
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
	.names {
		display: flex;
		flex-wrap: wrap;
		justify-content: space-between;
		gap: 8px;
	}
	.side {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.side.end {
		align-items: flex-end;
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
