<!-- The sign-in page's opening, over the map: a Game Boy Color's power-on flash out of black, the title dropping in
     from the top with a bounce, then glowing in each cartridge's colour while sparkles twinkle round it and the
     cartridge's legendary swoops across behind it. PRESS START blinks under it. The page ends it (a key, a tap, or
     its own timer) by setting leaving: the title flies back up and the layer fades. Decorative: hidden from AT. -->
<script lang="ts">
	import pibs from '#lib/assets/banner-pibs.webp';
	import play from '#lib/assets/banner-play.webp';
	import hoOh from '#lib/assets/legends/ho-oh.webp';
	import lugia from '#lib/assets/legends/lugia.webp';
	import suicune from '#lib/assets/legends/suicune.webp';

	let { legend, leaving = false }: { legend: 'gold' | 'silver' | 'crystal'; leaving?: boolean } = $props();
	const LEGENDS = { gold: hoOh, silver: lugia, crystal: suicune };
	// Where the sparkles sit round the title (% of its box) and when each first twinkles.
	const SPARKLES = [
		[-4, 8, 1.3], [104, 14, 1.5], [12, -10, 1.75], [88, -6, 1.95], [-2, 78, 2.15], [100, 86, 1.4], [50, 108, 1.85], [64, -14, 2.3]
	];
</script>

<div class="intro" class:leaving aria-hidden="true">
	<span class="flash"></span>
	<img class="legend" src={LEGENDS[legend]} alt="" />
	<div class="title">
		<div class="drop">
			<img src={pibs} alt="" width="720" height="131" />
			<img src={play} alt="" width="720" height="171" />
			{#each SPARKLES as [x, y, at], i (i)}
				<span class="sparkle" style:left="{x}%" style:top="{y}%" style:--at="{at}s">✦</span>
			{/each}
		</div>
		<p class="press">PRESS START</p>
	</div>
</div>

<style>
	.intro {
		position: fixed;
		inset: 0;
		z-index: 5;
		display: grid;
		place-items: center;
		overflow: hidden;
		/* Black at first, gone once the console has "powered on": the map shows through. */
		animation: from-black 0.7s 0.2s both;
		transition: opacity 0.45s ease 0.15s;
	}
	.leaving {
		opacity: 0;
		pointer-events: none;
	}
	@keyframes from-black {
		from {
			background: #000;
		}
		to {
			background: color-mix(in srgb, #000 35%, transparent);
		}
	}
	.flash {
		position: absolute;
		inset: 0;
		background: #fff;
		opacity: 0;
		animation: flash 0.5s 0.15s both;
	}
	@keyframes flash {
		20% {
			opacity: 0.85;
		}
		to {
			opacity: 0;
		}
	}

	.title {
		position: relative;
		width: min(640px, 88vw);
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: clamp(16px, 4vh, 40px);
		transition:
			translate 0.45s cubic-bezier(0.5, 0, 0.9, 0.4),
			scale 0.45s ease-in;
	}
	.leaving .title {
		translate: 0 -70vh;
		scale: 0.6;
	}
	/* Falls from above the window, overshoots, bounces twice, settles. */
	.drop {
		position: relative;
		width: 100%;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1.2%;
		animation:
			drop 0.95s 0.45s both,
			glow 4.5s 1.4s ease-in-out infinite;
	}
	.drop img {
		display: block;
		width: 100%;
		height: auto;
		image-rendering: pixelated;
	}
	@keyframes drop {
		from {
			translate: 0 -100vh;
			animation-timing-function: cubic-bezier(0.55, 0, 1, 0.45);
		}
		55% {
			translate: 0 0;
			animation-timing-function: cubic-bezier(0, 0.55, 0.45, 1);
		}
		72% {
			translate: 0 -7%;
			animation-timing-function: cubic-bezier(0.55, 0, 1, 0.45);
		}
		86% {
			translate: 0 0;
			animation-timing-function: cubic-bezier(0, 0.55, 0.45, 1);
		}
		93% {
			translate: 0 -2%;
		}
		to {
			translate: 0 0;
		}
	}
	/* Gold, Crystal, Silver: the cartridges' plastic, as a halo round the letters. */
	@keyframes glow {
		0%,
		100% {
			filter: drop-shadow(0 0 6px #f2c14e) drop-shadow(0 0 22px rgba(242, 193, 78, 0.75));
		}
		33% {
			filter: drop-shadow(0 0 6px #5ac8f0) drop-shadow(0 0 22px rgba(90, 200, 240, 0.75));
		}
		66% {
			filter: drop-shadow(0 0 6px #dfe6ee) drop-shadow(0 0 22px rgba(223, 230, 238, 0.7));
		}
	}

	.sparkle {
		position: absolute;
		translate: -50% -50%;
		font-size: clamp(14px, 2.6vw, 26px);
		color: #fff;
		text-shadow: 0 0 8px #fff, 0 0 16px #f2c14e;
		opacity: 0;
		animation: twinkle 1.6s var(--at) ease-in-out infinite;
	}
	@keyframes twinkle {
		0%,
		100% {
			opacity: 0;
			scale: 0.3;
			rotate: 0deg;
		}
		50% {
			opacity: 1;
			scale: 1;
			rotate: 45deg;
		}
	}

	/* The cartridge's legendary, as a shadow, sweeps across behind the title once. */
	.legend {
		position: absolute;
		top: 18%;
		left: 0;
		width: min(420px, 60vw);
		opacity: 0;
		filter: brightness(0) drop-shadow(0 0 12px rgba(255, 255, 255, 0.35));
		animation: swoop 2.2s 1.3s cubic-bezier(0.4, 0.1, 0.6, 0.9) both;
	}
	@keyframes swoop {
		from {
			translate: 110vw 10vh;
			opacity: 0.55;
		}
		to {
			translate: -110% -6vh;
			opacity: 0.55;
		}
	}

	.press {
		margin: 0;
		font: clamp(12px, 2.2vw, 20px) / 1 var(--pixel-font);
		color: #fff;
		letter-spacing: 0.1em;
		text-shadow: 0 2px 0 #000, 0 0 10px rgba(0, 0, 0, 0.8);
		animation:
			appear 0s 1.9s both,
			blink 1.1s steps(1) 1.9s infinite;
	}
	@keyframes appear {
		from {
			visibility: hidden;
		}
	}
	@keyframes blink {
		50% {
			opacity: 0;
		}
	}
</style>
