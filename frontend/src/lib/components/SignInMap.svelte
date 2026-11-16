<!-- Behind the sign-in device: the Pokégear town map, Kanto and Johto side by side as the regions lie, drawn from
     Crystal's ROM (TownMapGFX, KantoMap/JohtoMap, the Pokégear palettes; each with its outer frame cropped). Every
     player is a pin where they last were in their own game, in their version's colour: online ones pulse, offline
     ones fade. The pins are anonymous (GET /public/teaser: region, x, y, version, online). -->
<script lang="ts">
	import map from '#lib/assets/town-map.png';
	import type { Teaser } from '#lib/teaser.js';

	let { teaser }: { teaser: Teaser | null } = $props();

	const W = 288; // the joined map: Kanto 0-143, Johto 144-287 (160x144 each, less an 8px frame all round)
	const H = 128;
	const FRAME = 8;
	const COLORS: Record<string, string> = { POKEMON_GLD: '#e6b422', POKEMON_SLV: '#c4ccd6', PM_CRYSTAL: '#4fc3f0' };

	// Pins on one spot fan out round it, so three players in Goldenrod read as three.
	const pins = $derived.by(() => {
		const seen = new Map<string, number>();
		return (teaser?.pins ?? []).map((p) => {
			const key = `${p.region}:${p.x}:${p.y}`;
			const k = seen.get(key) ?? 0;
			seen.set(key, k + 1);
			const x = (p.region === 'kanto' ? 0 : W / 2) + p.x - FRAME;
			const y = p.y - FRAME;
			const angle = k * 2.4; // golden-angle spread
			const r = k === 0 ? 0 : 3 + k;
			return {
				...p,
				left: ((x + Math.cos(angle) * r) / W) * 100,
				top: ((y + Math.sin(angle) * r) / H) * 100,
				color: COLORS[p.version] ?? '#f2c14e',
				delay: (k * 0.37 + x / W) % 1.6
			};
		});
	});
</script>

<div class="backdrop" aria-hidden="true">
	<div class="map">
		<img src={map} alt="" />
		{#each pins as p, i (i)}
			<span class="pin" class:online={p.online} style:left="{p.left}%" style:top="{p.top}%" style:--c={p.color} style:--d="{p.delay}s"
				><i></i></span
			>
		{/each}
	</div>
</div>

<style>
	.backdrop {
		position: fixed;
		inset: 0;
		z-index: 0;
		overflow: hidden;
		pointer-events: none;
		background: #102010;
	}
	/* The map covers the window, centred; pins are placed in % of it, so they stay on their towns at any size. */
	.map {
		position: absolute;
		left: 50%;
		top: 50%;
		width: max(100vw, calc(100vh * 288 / 128));
		aspect-ratio: 288 / 128;
		translate: -50% -50%;
	}
	/* Muted, so the map reads as a backdrop and the pins stand out; darker in the dark theme. */
	img {
		filter: saturate(0.55) contrast(0.85) brightness(1.05);
	}
	:global(:root[data-theme='dark']) img {
		filter: saturate(0.5) brightness(0.42);
	}
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) img {
			filter: saturate(0.5) brightness(0.42);
		}
	}
	/* A soft vignette toward the middle, where the device and the card sit. */
	.backdrop::after {
		content: '';
		position: absolute;
		inset: 0;
		background: radial-gradient(ellipse 55% 60% at 50% 50%, rgb(0 0 0 / 0.28), transparent 75%);
	}
	.map img {
		display: block;
		width: 100%;
		height: 100%;
		image-rendering: pixelated;
	}
	.pin {
		position: absolute;
		width: clamp(14px, 1.15vw, 24px);
		aspect-ratio: 1;
		translate: -50% -50%;
		border-radius: 50%;
		background: var(--c);
		box-shadow:
			0 0 0 2px #fff,
			0 0 0 4px #101018,
			0 3px 8px rgb(0 0 0 / 0.5);
		opacity: 0.6;
		z-index: 1;
	}
	.pin.online {
		z-index: 2;
		opacity: 1;
	}
	/* Online: a ring keeps going out from the pin, like the Pokégear's blinking cursor. */
	.pin i {
		position: absolute;
		inset: 0;
		border-radius: 50%;
		border: 2px solid var(--c);
		opacity: 0;
	}
	@media (prefers-reduced-motion: no-preference) {
		.pin.online i {
			animation: ping 1.6s ease-out var(--d) infinite;
		}
	}
	@keyframes ping {
		from {
			opacity: 0.9;
			scale: 1;
		}
		to {
			opacity: 0;
			scale: 3.2;
		}
	}
</style>
