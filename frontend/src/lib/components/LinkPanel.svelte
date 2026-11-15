<!-- Link cable card: ready to trade/battle, everyone else who is ready (avatar, name, what they want, their game),
     the incoming challenge and the running session (two avatars joined by the cable, time left, Unplug).
     State lives in link.svelte.ts. -->
<script lang="ts">
	import Avatar from '#lib/components/Avatar.svelte';
	import LinkChallenge from '#lib/components/LinkChallenge.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { act, clock, left, link, verb, watchLink } from '#lib/link.svelte.ts';
	import { shortUser } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';

	$effect(() => watchLink());
	$effect(() => watchBoard());

	const me = $derived(session.me?.id);
	const s = $derived(link.state);
	const others = $derived(s?.lobby.filter((p) => p.userId !== me) ?? []);
	const gameOf = (user: string) => {
		const g = board.rows.find((r) => r.userId === user)?.game;
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
			<b>You</b>
			<span class="sr">linked with</span>
			<b>{shortUser(ss.peer)}</b>
		</div>
		<p class="muted">
			Both consoles run in step until someone unplugs or the 15-minute cap ends. Go to the Cable Club upstairs in any
			Pokémon Center.
		</p>
		<button class="ghost stop" onclick={() => act('DELETE', '/link/session')}>Unplug cable</button>
	{:else}
		<div class="head">
			<h2 id="link-h">Link cable</h2>
			<span class="muted">{others.length === 1 ? '1 Pib ready' : `${others.length} Pibs ready`}</span>
		</div>
		<div class="actions">
			{#each ['TRADE', 'BATTLE'] as const as kind (kind)}
				{@const on = s?.ready === kind}
				<button class="ghost" class:on aria-pressed={on} onclick={() => act('PUT', '/link/ready', { kind: on ? null : kind })}>
					{#if on}<span class="tick" aria-hidden="true">✓</span>{/if}Ready to {verb(kind)}
				</button>
			{/each}
		</div>
		{#if s?.ready}
			<p class="muted">Others can now challenge you. Stand in a Pokémon Center so you can walk straight into the Cable Club.</p>
		{/if}

		{#if s?.outgoing}
			<p class="muted">
				Waiting for {shortUser(s.outgoing.to)} to answer… <span class="num">{left(s.outgoing.expiresAt)} s</span>
			</p>
		{/if}

		<LinkChallenge />

		<hr />
		{#if others.length}
			<ul>
				{#each others as p (p.userId)}
					{@const g = gameOf(p.userId)}
					<li>
						<Avatar user={p.userId} />
						<div class="who">
							<b>{shortUser(p.userId)}</b>
							<span class={p.kind === 'TRADE' ? 'trade' : 'battle'}>Wants to {verb(p.kind)}{g ? ` · ${g}` : ''}</span>
						</div>
						<button
							class="small"
							class:primary={p.kind === 'TRADE'}
							class:ghost={p.kind !== 'TRADE'}
							disabled={!!s?.outgoing || !!s?.incoming}
							onclick={() => act('POST', '/link/challenges', { userId: p.userId })}
						>
							{p.kind === 'TRADE' ? 'Trade' : 'Battle'}<span class="sr"> with {shortUser(p.userId)}</span>
						</button>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="muted">Nobody else is ready yet.</p>
		{/if}
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
	.card.linked {
		border-color: var(--link);
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
	hr {
		width: 100%;
		margin: 0;
		border: none;
		border-top: 1px solid var(--border);
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
		justify-content: space-between;
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
	.actions {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}
	.actions > button {
		flex: 1 1 0;
		padding: 0 10px;
	}
	.tick {
		margin-right: 6px;
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
	.ghost.on {
		border-color: var(--accent-text);
		color: var(--accent-text);
		background: var(--accent-tint);
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
