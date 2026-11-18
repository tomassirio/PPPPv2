<!-- An incoming link-cable challenge: who, the three steps, Accept/Decline and the time left. Inline in the Link
     cable card on desktop; sheet: a bottom sheet over the game on phones (each shows only at its own size);
     floating (pages besides /play): a card in the corner on desktop, the bottom sheet on phones. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import Avatar from '#lib/components/Avatar.svelte';
	import PartyIcons from '#lib/components/PartyIcons.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { act, CHALLENGE_MS, desk, left, link, verb, watchLink } from '#lib/link.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';

	let { sheet = false, floating = false }: { sheet?: boolean; floating?: boolean } = $props();
	$effect(() => watchLink());
	$effect(() => watchBoard());

	const c = $derived(link.state?.session ? null : link.state?.incoming);
	const row = $derived(c && board.rows.find((r) => r.userId === c.from));
	const facts = $derived(
		row ? [`${row.badges} badges`, `${row.caught} caught`, ...(row.game && board.names[row.game] ? [board.names[row.game]] : [])].join(' · ') : ''
	);
</script>

<!-- Always in the DOM so screen readers announce a challenge when it arrives. The countdown is kept out of the live text. -->
<div class="live" class:sheet class:float={floating} aria-live="assertive">
	{#if c}
		{#if sheet}<div class="scrim"></div>{/if}
		<div class="challenge" role="group" aria-labelledby="challenge-h-{sheet ? 's' : 'i'}">
			{#if sheet}<span class="grab" aria-hidden="true"></span>{/if}
			<div class="who">
				<Avatar user={c.from} size={sheet ? 48 : 40} />
				<div>
					<p class="title" id="challenge-h-{sheet ? 's' : 'i'}"><Name user={c.from} /> wants to {verb(c.kind)}</p>
					{#if facts}<p class="muted">{facts}</p>{/if}
				</div>
			</div>
			{#if row?.party?.length}
				<div class="their">
					<span class="muted"><Name user={c.from} />'s party</span>
					<PartyIcons party={row.party} size={30} />
				</div>
			{/if}
			<ol class="steps">
				<li><b aria-hidden="true">1</b><span>Accept when you're free to leave: outdoors or in a cave, no battle, menu or dialog open.</span></li>
				<li><b aria-hidden="true">2</b><span>You both Dig to your last Pokémon Center, upstairs, in front of the {desk(c.kind).side} receptionist ({desk(c.kind).room}).</span></li>
				<li><b aria-hidden="true">3</b><span>She starts talking to you. Answer, save, then {verb(c.kind)} as on a real Game Boy. Unplug any time.</span></li>
			</ol>
			<div class="actions">
				<button class="primary" onclick={() => act('POST', '/link/challenges/accept')}>Accept {verb(c.kind)}</button>
				<button class="ghost" onclick={() => act('POST', '/link/challenges/decline')}>Decline</button>
			</div>
			{#if link.error}<p class="error" role="alert">{link.error}</p>{/if}
			<div class="expiry" aria-live="off">
				<span class="bar"><span style:width="{Math.min(100, (left(c.expiresAt) * 100_000) / CHALLENGE_MS)}%"></span></span>
				<span class="muted">Expires in {left(c.expiresAt)} s</span>
			</div>
		</div>
	{/if}
</div>

<style>
	.challenge {
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 16px;
		border: 1px solid var(--accent-text);
		border-radius: 10px;
		background: var(--accent-tint);
	}
	.who {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
	}
	p {
		margin: 0;
	}
	.title {
		font-size: 16px;
		font-weight: 700;
	}
	.muted {
		font-size: 13px;
		color: var(--muted);
	}
	.their {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.their .muted {
		font-size: 12px;
	}
	.steps {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 10px;
		font-size: 14px;
		line-height: 20px;
	}
	.steps li {
		display: flex;
		gap: 12px;
		align-items: flex-start;
	}
	.steps b {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		background: var(--border);
		font-size: 12px;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.actions {
		display: flex;
		gap: 8px;
	}
	button {
		height: 44px;
		padding: 0 16px;
		border-radius: 8px;
		font-weight: 600;
		font-size: 14px;
		white-space: nowrap;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.primary {
		flex: 2;
		border: none;
		background: var(--accent);
		color: var(--ink);
	}
	.primary:hover {
		background: var(--accent-hover);
	}
	.ghost {
		flex: 1;
		border: 1px solid var(--border-strong);
		background: transparent;
		color: var(--text);
	}
	.expiry {
		display: flex;
		flex-direction: column;
		gap: 6px;
		text-align: center;
	}
	.error {
		margin: 0;
		font-size: 13px;
		color: var(--danger);
	}
	.expiry .muted {
		font-size: 12px;
	}
	.bar {
		display: block;
		height: 4px;
		border-radius: 2px;
		background: var(--border);
		overflow: hidden;
	}
	.bar span {
		display: block;
		height: 100%;
		background: var(--accent);
		transition: width 1s linear;
	}
	.grab {
		width: 40px;
		height: 4px;
		border-radius: 2px;
		background: var(--border-strong);
		align-self: center;
	}
	.sheet {
		display: none;
	}
	/* Other pages: a card in the bottom-right corner (phones get the bottom sheet below). */
	.live.float {
		display: block;
	}
	.float .challenge {
		position: fixed;
		right: max(24px, env(safe-area-inset-right));
		bottom: max(24px, env(safe-area-inset-bottom));
		z-index: 41;
		width: min(400px, calc(100vw - 32px));
		box-sizing: border-box;
		padding: 16px 18px;
		border: 1px solid var(--link);
		border-radius: 16px;
		background: var(--panel);
		box-shadow: 0 16px 48px rgb(0 0 0 / 0.35);
	}
	/* Phones: a bottom sheet over everything, the game dimmed behind it; the inline copy hides. */
	@media (max-width: 760px), (orientation: landscape) and (max-height: 520px) {
		.live:not(.sheet) {
			display: none;
		}
		.sheet {
			display: block;
		}
		.scrim {
			position: fixed;
			inset: 0;
			z-index: 40;
			background: var(--scrim);
		}
		.sheet .challenge {
			position: fixed;
			left: 0;
			right: 0;
			bottom: 0;
			z-index: 41;
			max-width: 560px;
			margin: 0 auto;
			max-height: 100dvh;
			overflow-y: auto;
			box-sizing: border-box;
			padding: 12px max(20px, env(safe-area-inset-right)) calc(24px + env(safe-area-inset-bottom)) max(20px, env(safe-area-inset-left));
			border: none;
			border-top: 1px solid var(--border-strong);
			border-radius: 24px 24px 0 0;
			background: var(--panel);
			gap: 16px;
		}
		.sheet .title {
			font-size: 18px;
		}
		.sheet button {
			height: 48px;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.bar span {
			transition: none;
		}
	}
</style>
