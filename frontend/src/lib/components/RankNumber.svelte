<!-- A place on the ladder (rank from 1, of total). 1, 2 and 3 in gold, silver and bronze with a slow gleam; the rest
     coloured from green just under the podium to red at the bottom. animate: when the rank changes while shown, going up
     rises softly with a glow and "▲n", going down sinks dull with "▼n" (none with reduced motion). prefix: e.g. "#".
     medal: the podium as small metal coins (lists), else as metal lettering (a big number on its own). -->
<script lang="ts">
	let { rank, total, animate = false, prefix = '', medal = false }: { rank: number; total: number; animate?: boolean; prefix?: string; medal?: boolean } =
		$props();

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
			<span class="coin {podium} {move?.dir ?? ''}"><span class="pixel">{rank}</span></span>
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
	/* Medals: a coin of the metal with the number struck in dark, the same gleam over it. */
	.coin {
		position: relative;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font-size: 10px;
		color: #2b2111;
		background-size: 250% 100%;
		animation: gleam 5s ease-in-out infinite;
		box-shadow:
			inset 0 -2px 0 rgb(0 0 0 / 0.22),
			inset 0 1px 0 rgb(255 255 255 / 0.55),
			0 1px 2px rgb(0 0 0 / 0.3);
	}
	.coin.gold {
		background-image: linear-gradient(100deg, #c9971c 0%, #f7d774 35%, #fff6c9 45%, #f7d774 55%, #c9971c 100%);
		-webkit-background-clip: border-box;
		background-clip: border-box;
		filter: none;
	}
	.coin.silver {
		background-image: linear-gradient(100deg, #8d98a2 0%, #dfe5ea 35%, #ffffff 45%, #dfe5ea 55%, #8d98a2 100%);
		-webkit-background-clip: border-box;
		background-clip: border-box;
		color: #2a3036;
		filter: none;
	}
	.coin.bronze {
		background-image: linear-gradient(100deg, #9a5a2c 0%, #dc9a62 35%, #f6c79a 45%, #dc9a62 55%, #9a5a2c 100%);
		-webkit-background-clip: border-box;
		background-clip: border-box;
		color: #2e1708;
		filter: none;
	}
	.coin span {
		transform: translateY(0.5px);
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
