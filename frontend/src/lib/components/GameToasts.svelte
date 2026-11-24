<!-- The player's own scored progress (their PROGRESS events) as a card dropping over the top of the game screen, one
     at a time, so it is seen on any tab; and a ping when someone @mentions them in the chat. Each card: what it is as
     the games would shout it (GOTCHA!, BADGE GET!), the Pokémon's sprite or the badge from the player's ROM (else the
     tier's ball), the line itself, the points, framed in the tier's colour (tierOf); epic and legendary ones shine and
     sparkle. A bar runs down the time it stays. Text only (never HTML). Place inside a positioned container. -->
<script lang="ts">
	import { fly } from 'svelte/transition';
	import { backOut } from 'svelte/easing';
	import { on } from '#lib/events.ts';
	import { game } from '#lib/game.svelte.ts';
	import { JOHTO, tierOf, type ScoreEvent } from '#lib/play.ts';
	import { nameOf, usernameOf } from '#lib/pictures.svelte.ts';
	import { mentions } from '#lib/chat/bubble.ts';
	import type { ChatMessage } from '#lib/chat/messages.ts';
	import { session } from '#lib/session.svelte.ts';
	import RarityBall from '#lib/components/RarityBall.svelte';
	import { tip } from '#lib/tour.svelte.ts';

	const SHOW_MS = 4200;
	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	let queue = $state<ScoreEvent[]>([]);
	const current = $derived(queue[0]);

	$effect(() =>
		on('PROGRESS', (e: ScoreEvent) => {
			if (e.user === session.me?.id) queue = [...queue, e].slice(-5);
		})
	);
	// A Mystery Gift an admin sent just landed in their PC (backend MysteryGifts).
	$effect(() =>
		on('MYSTERY_GIFT', (g: { user: string; name: string; quantity: number }) => {
			if (g.user !== session.me?.id) return;
			queue = [...queue, { user: g.user, type: 'GIFT', detail: `${g.quantity}× ${g.name}`, points: 0, at: Date.now() }].slice(-5);
			navigator.vibrate?.([20, 30, 20, 30, 60]);
		})
	);
	// @mentions: their username or their nickname without spaces, as the bubbles read them.
	$effect(() =>
		on('CHAT_MESSAGE', (m: ChatMessage) => {
			const me = session.me?.id;
			if (!me || m.kind !== 'USER' || m.user === me) return;
			if (!mentions(m.text, [usernameOf(me).slice(1), nameOf(me).replace(/\s+/g, '')])) return;
			queue = [...queue, { user: m.user, type: 'MENTION', detail: m.text, points: 0, at: m.at }].slice(-5);
			navigator.vibrate?.([30, 40, 30]);
		})
	);
	$effect(() => {
		if (current) tip(current.type === 'MENTION' ? 'mention' : 'toast');
	});
	$effect(() => {
		if (!current) return;
		const t = setTimeout(() => (queue = queue.slice(1)), SHOW_MS);
		return () => clearTimeout(t);
	});

	const KICKERS: Record<string, string> = {
		CAUGHT: 'Gotcha!',
		SEEN: 'New Pokédex data',
		BADGE: 'Badge get!',
		LEVEL: 'Level up!',
		ACHIEVEMENT: 'Achievement!',
		TITLE: 'New title',
		HALL_OF_FAME: 'Hall of Fame!',
		LINK_WIN: 'Link battle won!',
		LINK_LOSS: 'Link battle',
		MENTION: 'You were mentioned',
		GIFT: 'Mystery Gift!'
	};
	// The card's main line: the thing itself, not a sentence about "You".
	function line(e: ScoreEvent): string {
		switch (e.type) {
			case 'BADGE':
				return `${e.detail} Badge`;
			case 'LEVEL':
				return `A Pokémon reached Lv. ${e.detail}`;
			case 'HALL_OF_FAME':
				return 'You became Champion';
			case 'LINK_WIN':
				return e.detail ? `You beat ${nameOf(e.detail)}` : 'You won';
			case 'LINK_LOSS':
				return e.detail ? `${nameOf(e.detail)} won this time` : 'Better luck next time';
			case 'MENTION':
				return `${nameOf(e.user)}: ${e.detail}`;
			case 'GIFT':
				return `${e.detail} in your PC. Save to keep it!`;
			default:
				return e.detail;
		}
	}
	// A sprite for catches and sightings (the Pokédex names in the player's own summary), the badge for badges.
	const badgeArt = import.meta.glob('../assets/badges/*.png', { eager: true, import: 'default' }) as Record<string, string>;
	function icon(e: ScoreEvent): string | null {
		if (e.type === 'CAUGHT' || e.type === 'SEEN') {
			const n = game.summary?.collection.names.findIndex((name) => name?.toLowerCase() === e.detail.toLowerCase()) ?? -1;
			return n >= 0 ? `/api/v1/game/icons/${n + 1}.png` : null;
		}
		if (e.type === 'BADGE') {
			const i = JOHTO.indexOf(e.detail);
			return i >= 0 ? `/api/v1/game/badges/${i}.png` : (badgeArt[`../assets/badges/${e.detail.toLowerCase()}.png`] ?? null);
		}
		return null;
	}
	const tier = (e: ScoreEvent) => (e.type === 'MENTION' ? 'ping' : e.type === 'GIFT' ? 'legendary' : tierOf(e));
	const loud = (e: ScoreEvent) => tier(e) === 'epic' || tier(e) === 'legendary';
	let broken = $state(false);
	$effect(() => {
		void current;
		broken = false;
	});
</script>

<div class="toasts" role="status" aria-live="polite">
	{#if current}
		{#key current}
			{@const src = icon(current)}
			<div
				class="toast {tier(current)}"
				class:loud={loud(current)}
				style:--show="{SHOW_MS}ms"
				in:fly={{ y: -24, duration: still ? 0 : 420, easing: backOut }}
				out:fly={{ y: -12, duration: still ? 0 : 200 }}
			>
				<span class="art" aria-hidden="true">
					{#if current.type === 'GIFT'}
						<span class="gift">🎁</span>
					{:else if current.type === 'MENTION'}
						<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"
							><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.9 1.9 0 0 0 3.4 0" /></svg
						>
					{:else if src && !broken}
						<img {src} alt="" onerror={() => (broken = true)} />
					{:else}
						<RarityBall rarity={tierOf(current)} size={30} />
					{/if}
				</span>
				<span class="words">
					<span class="kicker">{KICKERS[current.type] ?? current.type.toLowerCase()}</span>
					<span class="line">{line(current)}</span>
				</span>
				{#if current.points}<span class="points">+{current.points}</span>{/if}
				{#if loud(current) && !still}
					{#each [12, 38, 64, 88] as x, i (i)}
						<span class="sparkle" style:left="{x}%" style:--d="{0.15 + i * 0.18}s" aria-hidden="true">✦</span>
					{/each}
				{/if}
				<span class="timer" aria-hidden="true"></span>
			</div>
		{/key}
	{/if}
</div>

<style>
	.toasts {
		position: absolute;
		top: 12px;
		left: 50%;
		translate: -50% 0;
		width: min(92%, 400px);
		z-index: 4;
		pointer-events: none;
		display: grid;
	}
	.toast {
		--c: var(--r-common);
		grid-area: 1 / 1; /* the leaving card and the next overlap instead of stacking */
		position: relative;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 10px 14px 12px 10px;
		border-radius: 14px;
		overflow: hidden;
		background:
			linear-gradient(100deg, color-mix(in srgb, var(--c) 26%, transparent), transparent 55%),
			linear-gradient(180deg, rgb(30 34 42 / 0.96), rgb(16 19 24 / 0.96));
		color: #fff;
		border: 2px solid var(--c);
		box-shadow:
			0 10px 28px rgb(0 0 0 / 0.45),
			0 0 0 1px rgb(0 0 0 / 0.4),
			0 0 22px color-mix(in srgb, var(--c) 35%, transparent);
	}
	.toast.uncommon {
		--c: var(--r-uncommon);
	}
	.toast.rare {
		--c: var(--r-rare);
	}
	.toast.epic {
		--c: var(--r-epic);
	}
	.toast.legendary {
		--c: var(--r-legendary);
	}
	.toast.ping {
		--c: var(--link);
	}
	/* The sprite or badge on a little stand of the tier's colour. */
	.art {
		flex: none;
		display: grid;
		place-items: center;
		width: 44px;
		height: 44px;
		border-radius: 50%;
		background: radial-gradient(circle at 50% 40%, color-mix(in srgb, var(--c) 45%, #fff 10%), color-mix(in srgb, var(--c) 18%, #111) 70%);
		box-shadow: inset 0 -3px 0 rgb(0 0 0 / 0.25);
		color: #fff;
	}
	.art img {
		width: 40px;
		height: 40px;
		image-rendering: pixelated;
		object-fit: contain;
		filter: drop-shadow(0 2px 0 rgb(0 0 0 / 0.35));
	}
	.gift {
		font-size: 26px;
		line-height: 1;
	}
	.words {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}
	.kicker {
		font: 9px/1.2 var(--pixel-font);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: color-mix(in srgb, var(--c) 75%, #fff);
	}
	.line {
		font-size: 14px;
		font-weight: 700;
		line-height: 1.25;
		overflow: hidden;
		text-overflow: ellipsis;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
	}
	.points {
		flex: none;
		padding: 4px 8px;
		border-radius: 999px;
		background: var(--c);
		color: #14161a;
		font: 10px/1 var(--pixel-font);
	}
	/* The time the card stays, running out along its foot. */
	.timer {
		position: absolute;
		left: 0;
		bottom: 0;
		height: 3px;
		width: 100%;
		background: var(--c);
		transform-origin: left;
		animation: run var(--show) linear both;
		opacity: 0.8;
	}
	@keyframes run {
		to {
			transform: scaleX(0);
		}
	}
	/* Epic and legendary: a gleam sweeps across, sparkles pop. */
	.loud::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(105deg, transparent 35%, rgb(255 255 255 / 0.28) 50%, transparent 65%);
		translate: -100% 0;
		animation: gleam 1.4s 0.3s ease-in-out 2;
	}
	@keyframes gleam {
		to {
			translate: 100% 0;
		}
	}
	.sparkle {
		position: absolute;
		top: 50%;
		color: #fff;
		font-size: 12px;
		text-shadow: 0 0 6px var(--c);
		opacity: 0;
		animation: pop 0.9s var(--d) ease-out both;
	}
	@keyframes pop {
		30% {
			opacity: 1;
			scale: 1.3;
		}
		to {
			opacity: 0;
			translate: 0 -26px;
			scale: 0.4;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.timer,
		.loud::after {
			animation: none;
		}
	}
</style>
