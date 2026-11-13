<!-- Link cable: who is ready to trade or battle, challenges, the running session.
     GET /api/v1/link is the whole picture for this player; LOBBY_CHANGED, LINK_CHALLENGE and LINK_SESSION say when to fetch it again. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { on, onOpen } from '#lib/events.ts';
	import { shortUser } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';

	type Kind = 'TRADE' | 'BATTLE';
	type Challenge = { from: string; to: string; kind: Kind; expiresAt: number };
	type LinkState = {
		lobby: { userId: string; kind: Kind }[];
		ready: Kind | null;
		incoming: Challenge | null;
		outgoing: Challenge | null;
		session: { peer: string; kind: Kind; endsAt: number } | null;
		now: number;
	};

	const verb = (k: Kind) => (k === 'TRADE' ? 'trade' : 'battle');

	let link = $state<LinkState | null>(null);
	let error = $state('');
	let skew = 0; // server clock minus this browser's
	let now = $state(Date.now());

	const me = $derived(session.me?.id);
	const others = $derived(link?.lobby.filter((p) => p.userId !== me) ?? []);
	const left = (until: number) => Math.max(0, Math.ceil((until - now) / 1000));
	const clock = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

	function show(state: LinkState) {
		skew = state.now - Date.now();
		now = state.now;
		link = state;
	}

	async function load() {
		try {
			show(await api<LinkState>('GET', '/link'));
		} catch {
			/* keep what we have; the next event or reconnect fetches again */
		}
	}

	async function act(method: 'PUT' | 'POST' | 'DELETE', path: string, body?: unknown) {
		error = '';
		try {
			show(await api<LinkState>(method, path, body));
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Something went wrong';
			load();
		}
	}

	$effect(() => {
		const mine = (e: { from?: string; to?: string; a?: string; b?: string }) =>
			[e.from, e.to, e.a, e.b].includes(session.me?.id) && load();
		const offs = [onOpen(load), on('LOBBY_CHANGED', load), on('LINK_CHALLENGE', mine), on('LINK_SESSION', mine)];
		load();
		const tick = setInterval(() => (now = Date.now() + skew), 1000);
		return () => {
			offs.forEach((off) => off());
			clearInterval(tick);
		};
	});
</script>

<section class="panel card" aria-labelledby="link-h">
	<h2 id="link-h">Link cable</h2>

	{#if link?.session}
		{@const s = link.session}
		<p><strong>Linked with {shortUser(s.peer)}</strong> to {verb(s.kind)}</p>
		<p class="muted">Time left: <span class="num">{clock(left(s.endsAt))}</span></p>
		<button class="ghost" onclick={() => act('DELETE', '/link/session')}>Unplug cable</button>
	{:else}
		<div class="actions">
			{#each ['TRADE', 'BATTLE'] as const as kind (kind)}
				<button
					class="ghost"
					class:on={link?.ready === kind}
					aria-pressed={link?.ready === kind}
					onclick={() => act('PUT', '/link/ready', { kind: link?.ready === kind ? null : kind })}
				>
					Ready to {verb(kind)}
				</button>
			{/each}
		</div>

		{#if link?.outgoing}
			<p class="muted">
				Waiting for {shortUser(link.outgoing.to)} to answer… <span class="num">{left(link.outgoing.expiresAt)} s</span>
			</p>
		{/if}

		{#if others.length}
			<ul>
				{#each others as p (p.userId)}
					<li>
						<span class="who"><strong>{shortUser(p.userId)}</strong> <span class="muted">wants to {verb(p.kind)}</span></span>
						<button class="ghost small" disabled={!!link?.outgoing} onclick={() => act('POST', '/link/challenges', { userId: p.userId })}>
							{p.kind === 'TRADE' ? 'Trade' : 'Battle'}<span class="sr"> with {shortUser(p.userId)}</span>
						</button>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="muted">Nobody else is ready yet.</p>
		{/if}
	{/if}

	<!-- Always in the DOM so screen readers announce a challenge when it arrives. The countdown is kept out of the live text. -->
	<div aria-live="assertive">
		{#if link?.incoming && !link.session}
			{@const c = link.incoming}
			<div class="prompt">
				<p><strong>{shortUser(c.from)} wants to {verb(c.kind)}</strong></p>
				<p class="muted">Accept, then go to the Cable Club upstairs in any Pokémon Center.</p>
				<div class="actions">
					<button class="primary" onclick={() => act('POST', '/link/challenges/accept')}>Accept</button>
					<button class="ghost" onclick={() => act('POST', '/link/challenges/decline')}>Decline</button>
					<span class="muted num" aria-live="off">{left(c.expiresAt)} s left</span>
				</div>
			</div>
		{/if}
	</div>

	{#if link?.session}
		<p class="muted">Go to the Cable Club upstairs in any Pokémon Center.</p>
	{/if}
	{#if error}<p class="error" role="alert">{error}</p>{/if}
</section>

<style>
	.card {
		padding: 16px 20px;
		display: flex;
		flex-direction: column;
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
	p {
		margin: 0;
		font-size: 14px;
	}
	.muted {
		font-size: 13px;
		color: var(--muted);
	}
	.num {
		font-variant-numeric: tabular-nums;
	}
	.error {
		font-size: 13px;
		color: var(--danger);
	}
	ul {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		font-size: 14px;
	}
	.who {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.actions {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}
	.actions > button {
		flex: 1 1 0;
	}
	button {
		height: 44px;
		padding: 0 14px;
		border-radius: 8px;
		font-weight: 600;
		font-size: 14px;
		white-space: nowrap;
	}
	button.small {
		flex: none;
		height: 36px;
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
		box-shadow: inset 0 0 0 1px var(--accent-text);
	}
	.primary {
		border: none;
		background: var(--accent);
		color: var(--ink);
	}
	.primary:hover {
		background: var(--accent-hover);
	}
	.prompt {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 12px;
		border: 1px solid var(--accent-text);
		border-radius: 10px;
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
	}
</style>
