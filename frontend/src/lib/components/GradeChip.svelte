<!-- The grade from the capped Trainer rating (0–1000): S+ S A B C D E F, each with its own colour. S+ shimmers.
     When the grade goes up while shown (a live SCOREBOARD_CHANGED), the chip celebrates, more the higher the new
     grade: E pops; D and C spring with rings; B and A flip and glow, A with sparkles; S bounces big in gold with
     sparkles and "RANK UP!"; S+ does all of it in rainbow. Never on first render or a drop; none under reduced motion. -->
<script lang="ts">
	let { grade, rating, size = 9 }: { grade: string; rating?: number; size?: number } = $props();
	const TIERS: Record<string, string> = { 'S+': 'splus', S: 's', A: 'a', B: 'b', C: 'c', D: 'd', E: 'e', F: 'f' };
	const ORDER = ['F', 'E', 'D', 'C', 'B', 'A', 'S', 'S+'];
	const RINGS: Record<string, number> = { d: 1, c: 2, b: 2, a: 3, s: 3, splus: 4 };
	const SPARKS: Record<string, number> = { a: 6, s: 10, splus: 14 };
	const tier = $derived(TIERS[grade] ?? 'f');

	let ups = $state(0); // how many upgrades this chip has seen; keys the celebration so each one replays
	let before: string | undefined;
	$effect(() => {
		if (before !== undefined && ORDER.indexOf(grade) > ORDER.indexOf(before)) ups++;
		before = grade;
	});
</script>

<span class="chip">
	{#key ups}
		<span
			class="grade pixel g-{tier}"
			class:up={ups > 0}
			style:font-size="{size}px"
			title="Grade {grade}{rating != null ? ` · Trainer rating ${rating} of 1000` : ''}">{grade}</span
		>
		{#if ups > 0}
			<span class="fx g-{tier}" aria-hidden="true">
				{#each { length: RINGS[tier] ?? 0 } as _, i (i)}<span class="ring" style:--i={i}></span>{/each}
				{#each { length: SPARKS[tier] ?? 0 } as _, i (i)}<i class="spark" style:--i={i} style:--a="{(360 / SPARKS[tier]) * i}deg"></i>{/each}
				{#if tier === 's' || tier === 'splus'}<b class="cheer pixel">RANK UP!</b>{/if}
			</span>
		{/if}
	{/key}
</span>

<style>
	.grade {
		display: inline-block;
		flex: none;
		padding: 0.45em 0.6em;
		border-radius: 4px;
		line-height: 1;
		color: var(--g-ink);
		background: var(--g-bg);
		box-shadow: inset 0 0 0 1px var(--g-edge);
		--g-ink: var(--muted);
		--g-bg: var(--sunk);
		--g-edge: var(--border);
	}
	/* The celebration's colour: the grade's ink, gold for S, pink (rainbow-shifted) for S+. */
	.grade,
	.fx {
		--g-fx: var(--g-ink);
	}
	.chip {
		position: relative;
		display: inline-flex;
		flex: none;
	}
	.fx {
		position: absolute;
		inset: 0;
		pointer-events: none;
		display: none;
	}
	.g-f {
		--g-ink: #8a8f8c;
		--g-bg: rgb(138 143 140 / 0.14);
		--g-edge: rgb(138 143 140 / 0.35);
	}
	.g-e {
		--g-ink: #c8673f;
		--g-bg: rgb(200 103 63 / 0.14);
		--g-edge: rgb(200 103 63 / 0.4);
	}
	.g-d {
		--g-ink: #b8890f;
		--g-bg: rgb(212 160 23 / 0.15);
		--g-edge: rgb(212 160 23 / 0.45);
	}
	.g-c {
		--g-ink: #3e9a4a;
		--g-bg: rgb(63 174 74 / 0.14);
		--g-edge: rgb(63 174 74 / 0.45);
	}
	.g-b {
		--g-ink: #2f7fd6;
		--g-bg: rgb(47 127 214 / 0.14);
		--g-edge: rgb(47 127 214 / 0.45);
	}
	.g-a {
		--g-ink: #8b55d9;
		--g-bg: rgb(139 85 217 / 0.15);
		--g-edge: rgb(139 85 217 / 0.5);
	}
	.g-s {
		--g-ink: #1d1f1c;
		--g-bg: linear-gradient(135deg, #f7d774, #e0a923);
		--g-edge: rgb(150 105 0 / 0.5);
		--g-fx: #f2b705;
	}
	.g-splus {
		--g-ink: #1d1f1c;
		--g-bg: linear-gradient(110deg, #f7d774 0%, #fff6d0 35%, #e0a923 50%, #f29bd1 75%, #9bd3f2 100%);
		--g-edge: rgb(150 105 0 / 0.6);
		--g-fx: #f29bd1;
		background-size: 220% 100%;
	}
	/* Darker theme: the light hues read better a step brighter. */
	:global(:root[data-theme='dark']) .g-f { --g-ink: #a9aeab; }
	:global(:root[data-theme='dark']) .g-e { --g-ink: #e98a63; }
	:global(:root[data-theme='dark']) .g-d { --g-ink: #e6b93a; }
	:global(:root[data-theme='dark']) .g-c { --g-ink: #6fcf7a; }
	:global(:root[data-theme='dark']) .g-b { --g-ink: #6ea8ff; }
	:global(:root[data-theme='dark']) .g-a { --g-ink: #b48cf2; }
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .g-f { --g-ink: #a9aeab; }
		:global(:root:not([data-theme='light'])) .g-e { --g-ink: #e98a63; }
		:global(:root:not([data-theme='light'])) .g-d { --g-ink: #e6b93a; }
		:global(:root:not([data-theme='light'])) .g-c { --g-ink: #6fcf7a; }
		:global(:root:not([data-theme='light'])) .g-b { --g-ink: #6ea8ff; }
		:global(:root:not([data-theme='light'])) .g-a { --g-ink: #b48cf2; }
	}
	@media (prefers-reduced-motion: no-preference) {
		.g-splus {
			animation: shimmer 3s linear infinite;
		}
		@keyframes shimmer {
			to {
				background-position: -220% 0;
			}
		}

		/* The chip itself. */
		.up.g-e {
			animation: pop 0.5s cubic-bezier(0.2, 0.9, 0.3, 1.4);
		}
		.up.g-d {
			animation: spring 0.8s ease-out;
		}
		.up.g-c {
			animation: spring 1s ease-out;
		}
		.up.g-b {
			animation: flip 1.1s ease-out, glow 1.1s ease-out;
		}
		.up.g-a {
			animation: flip 1.3s ease-out, glow 1.3s ease-out;
		}
		.up.g-s {
			animation: big 1.8s ease-out, glow 1.8s ease-out;
		}
		.up.g-splus {
			animation: big 2.4s ease-out, glow 2.4s ease-out, shimmer 3s linear infinite;
		}
		@keyframes pop {
			from {
				transform: scale(1.45);
			}
		}
		@keyframes spring {
			0% {
				transform: scale(0.6);
			}
			40% {
				transform: scale(1.5);
			}
			70% {
				transform: scale(0.92);
			}
		}
		@keyframes flip {
			0% {
				transform: perspective(60px) rotateY(0) scale(1.3);
			}
			60% {
				transform: perspective(60px) rotateY(360deg) scale(1.4);
			}
			100% {
				transform: perspective(60px) rotateY(360deg) scale(1);
			}
		}
		@keyframes big {
			0% {
				transform: scale(0.4);
			}
			28% {
				transform: scale(2.1);
			}
			46% {
				transform: scale(0.88);
			}
			64% {
				transform: scale(1.18);
			}
			82% {
				transform: scale(0.97);
			}
		}
		@keyframes glow {
			30% {
				box-shadow: inset 0 0 0 1px var(--g-edge), 0 0 14px 5px color-mix(in oklab, var(--g-fx) 70%, transparent);
			}
		}

		/* Around it. */
		.fx {
			display: block;
		}
		.ring {
			position: absolute;
			inset: 0;
			border-radius: 4px;
			border: 2px solid var(--g-fx);
			opacity: 0;
			animation: ring 0.9s ease-out calc(var(--i) * 0.22s) forwards;
		}
		.fx.g-splus .ring {
			filter: hue-rotate(calc(var(--i) * 90deg));
			animation-duration: 1.2s;
		}
		@keyframes ring {
			0% {
				opacity: 0.9;
				transform: scale(1);
			}
			100% {
				opacity: 0;
				transform: scale(2.6);
			}
		}
		.spark {
			--r: 22px;
			position: absolute;
			left: 50%;
			top: 50%;
			width: 4px;
			height: 4px;
			margin: -2px;
			border-radius: 1px;
			background: var(--g-fx);
			opacity: 0;
			animation: spark 0.9s ease-out calc(0.15s + mod(var(--i), 2) * 0.1s) forwards;
		}
		.fx.g-s .spark {
			--r: 32px;
		}
		.fx.g-splus .spark {
			--r: 40px;
			width: 5px;
			height: 5px;
			filter: hue-rotate(var(--a));
			animation-duration: 1.2s;
		}
		@keyframes spark {
			0% {
				opacity: 1;
				transform: rotate(var(--a)) translateY(0) scale(1);
			}
			100% {
				opacity: 0;
				transform: rotate(var(--a)) translateY(calc(-1 * var(--r))) scale(0.3);
			}
		}
		.cheer {
			position: absolute;
			left: 50%;
			bottom: 100%;
			font-size: 9px;
			font-weight: normal;
			white-space: nowrap;
			color: var(--g-fx);
			text-shadow: 0 1px 0 rgb(0 0 0 / 0.6);
			opacity: 0;
			animation: cheer 1.6s ease-out 0.25s forwards;
		}
		.fx.g-splus .cheer {
			animation: cheer 2.2s ease-out 0.25s forwards, rainbow 0.8s linear infinite;
		}
		@keyframes cheer {
			0% {
				opacity: 0;
				transform: translate(-50%, 6px) scale(0.6);
			}
			20% {
				opacity: 1;
				transform: translate(-50%, -2px) scale(1.15);
			}
			70% {
				opacity: 1;
				transform: translate(-50%, -8px) scale(1);
			}
			100% {
				opacity: 0;
				transform: translate(-50%, -14px);
			}
		}
		@keyframes rainbow {
			to {
				filter: hue-rotate(360deg);
			}
		}
	}
</style>
