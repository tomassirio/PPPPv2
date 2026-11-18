<!-- A place on the ladder (rank from 1, of total). 1, 2 and 3 in gold, silver and bronze with a slow gleam; the rest
     coloured from green just under the podium to red at the bottom. animate: when the rank changes while shown, going up
     rises softly with a glow and "▲n", going down sinks dull with "▼n" (none with reduced motion). prefix: e.g. "#".
     medal: the podium as struck metal coins (size in px; from 44 with a ribbon), else as metal lettering. -->
<script lang="ts">
	let {
		rank,
		total,
		animate = false,
		prefix = '',
		medal = false,
		size = 20
	}: { rank: number; total: number; animate?: boolean; prefix?: string; medal?: boolean; size?: number } = $props();

	const podium = $derived(rank === 1 ? 'gold' : rank === 2 ? 'silver' : rank === 3 ? 'bronze' : null);
	/** Green (hue 140) at 4th, red (hue 25) last, through yellow; mixed toward the text on light themes. */
	const color = $derived.by(() => {
		if (podium) return undefined;
		const t = total > 4 ? Math.min(1, Math.max(0, (rank - 4) / (total - 4))) : 0;
		return `color-mix(in oklab, oklch(0.78 0.16 ${Math.round(140 - 115 * t)}), var(--text) var(--name-dim))`;
	});

	let move = $state<{ dir: 'up' | 'down'; by: number; key: number } | null>(null);
	let last: number | undefined;
	$effect(() => {
		const now = rank;
		if (animate && last !== undefined && now !== last) {
			move = { dir: now < last ? 'up' : 'down', by: Math.abs(now - last), key: Date.now() };
			const done = move.key;
			setTimeout(() => {
				if (move?.key === done) move = null;
			}, 2200);
		}
		last = now;
	});
</script>

<span class="rankno">
	{#key move?.key}
		{#if medal && podium}
			<span class="medal {podium} {move?.dir ?? ''}" class:big={size >= 44} style:--s="{size}px">
				{#if size >= 44}<span class="ribbon" aria-hidden="true"></span>{/if}
				<span class="coin c-{podium}"><span class="pixel">{rank}</span></span>
			</span>
		{:else}
			<span class="n pixel {podium ?? ''} {move?.dir ?? ''}" style:color>{prefix}{rank}</span>
		{/if}
	{/key}
	{#if move}
		{#key move.key}<span class="delta {move.dir}" aria-hidden="true">{move.dir === 'up' ? '▲' : '▼'}{move.by}</span>{/key}
		<span class="sr" role="status">{move.dir === 'up' ? `Up ${move.by} to ${rank}` : `Down ${move.by} to ${rank}`}</span>
	{/if}
</span>

<style>
	.rankno {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: 4px;
	}
	.n {
		display: inline-block;
		font-variant-numeric: tabular-nums;
	}
	/* The podium: metal gradients with a gleam passing every few seconds. */
	.gold,
	.silver,
	.bronze {
		background-size: 250% 100%;
		-webkit-background-clip: text;
		background-clip: text;
		color: transparent;
		animation: gleam 5s ease-in-out infinite;
		filter: drop-shadow(0 1px 0 rgb(0 0 0 / 0.25));
	}
	.gold {
		background-image: linear-gradient(100deg, #b8860b 0%, #f7d774 35%, #fff6c9 45%, #f7d774 55%, #b8860b 100%);
	}
	.silver {
		background-image: linear-gradient(100deg, #7d8790 0%, #d9dfe5 35%, #ffffff 45%, #d9dfe5 55%, #7d8790 100%);
	}
	.bronze {
		background-image: linear-gradient(100deg, #8a4b22 0%, #d8935a 35%, #f6c79a 45%, #d8935a 55%, #8a4b22 100%);
	}
	/* Medals: a struck coin of the metal (rim, face, the number pressed in), the gleam passing over it; big ones hang
	   from a ribbon (red for gold, blue for silver, green for bronze). */
	.medal {
		position: relative;
		display: inline-flex;
		flex-direction: column;
		align-items: center;
	}
	.medal.big {
		padding-top: calc(var(--s) * 0.42);
	}
	.ribbon {
		position: absolute;
		top: 0;
		width: calc(var(--s) * 0.62);
		height: calc(var(--s) * 0.62);
		background:
			linear-gradient(90deg, transparent 0 46%, rgb(255 255 255 / 0.35) 46% 54%, transparent 54%),
			var(--rib);
		clip-path: polygon(0 0, 100% 0, 100% 100%, 50% 72%, 0 100%);
		filter: drop-shadow(0 1px 1px rgb(0 0 0 / 0.3));
	}
	.medal.gold {
		--rib: #c6303a;
	}
	.medal.silver {
		--rib: #2f6fbf;
	}
	.medal.bronze {
		--rib: #2f8f4e;
	}
	.coin {
		position: relative;
		width: var(--s);
		height: var(--s);
		border-radius: 50%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font-size: calc(var(--s) * 0.48);
		color: rgb(40 28 6 / 0.85);
		text-shadow: 0 1px 0 rgb(255 255 255 / 0.45);
		background-size: 250% 100%;
		animation: gleam 5s ease-in-out infinite;
		box-shadow:
			inset 0 0 0 calc(var(--s) * 0.08) rgb(0 0 0 / 0.12),
			inset 0 calc(var(--s) * -0.08) 0 rgb(0 0 0 / 0.2),
			inset 0 calc(var(--s) * 0.06) 0 rgb(255 255 255 / 0.5),
			0 calc(var(--s) * 0.08) calc(var(--s) * 0.22) rgb(0 0 0 / 0.35);
	}
	.coin.c-gold {
		background-image: linear-gradient(100deg, #b8860b 0%, #f7d774 35%, #fff6c9 45%, #f7d774 55%, #b8860b 100%);
	}
	.coin.c-silver {
		background-image: linear-gradient(100deg, #7d8790 0%, #e1e6eb 35%, #ffffff 45%, #e1e6eb 55%, #7d8790 100%);
		color: rgb(30 36 42 / 0.85);
	}
	.coin.c-bronze {
		background-image: linear-gradient(100deg, #8a4b22 0%, #dc9a62 35%, #f6c79a 45%, #dc9a62 55%, #8a4b22 100%);
		color: rgb(46 23 8 / 0.85);
	}
	.coin span {
		transform: translateY(0.5px);
	}
	.medal.up .coin {
		animation:
			rise 900ms cubic-bezier(0.2, 0.9, 0.3, 1.3),
			gleam 5s ease-in-out infinite;
	}
	.medal.down .coin {
		animation: sink 1100ms ease-out;
	}
	@keyframes gleam {
		0%,
		60% {
			background-position: 100% 0;
		}
		100% {
			background-position: 0% 0;
		}
	}

	/* Up: a soft rise with a warm glow. Down: a dull sink, colour drained for a moment. */
	.up {
		animation: rise 900ms cubic-bezier(0.2, 0.9, 0.3, 1.3);
	}
	.up.gold,
	.up.silver,
	.up.bronze {
		animation:
			rise 900ms cubic-bezier(0.2, 0.9, 0.3, 1.3),
			gleam 5s ease-in-out infinite;
	}
	@keyframes rise {
		0% {
			transform: translateY(10px) scale(0.9);
			opacity: 0.3;
			text-shadow: none;
		}
		55% {
			transform: translateY(-3px) scale(1.12);
			opacity: 1;
			text-shadow: 0 0 14px color-mix(in srgb, var(--hp-ok) 70%, transparent);
		}
		100% {
			transform: none;
			text-shadow: none;
		}
	}
	.down {
		animation: sink 1100ms ease-out;
	}
	@keyframes sink {
		0% {
			transform: translateY(-4px);
			filter: grayscale(1) brightness(0.7);
			opacity: 0.6;
		}
		60% {
			transform: translateY(2px);
			filter: grayscale(1) brightness(0.7);
		}
		100% {
			transform: none;
			filter: none;
			opacity: 1;
		}
	}
	.delta {
		font-size: 11px;
		font-weight: 700;
		animation: delta 2200ms ease-out forwards;
	}
	.delta.up {
		color: var(--hp-ok);
	}
	.delta.down {
		color: var(--muted);
	}
	@keyframes delta {
		0% {
			opacity: 0;
			transform: translateY(4px);
		}
		15%,
		75% {
			opacity: 1;
			transform: none;
		}
		100% {
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.n,
		.coin,
		.medal .coin,
		.delta {
			animation: none !important;
		}
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
</style>
