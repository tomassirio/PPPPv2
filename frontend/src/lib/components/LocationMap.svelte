<!-- A location you can hover (or focus): a window on the real map where the player stands, as Pokémon Crystal draws
     it (GET /public/maps/{MAP}.png, the map with MARGIN border blocks round it), two pixels per Game Boy pixel and a
     Game Boy screen across (160x120), centred on them with a marker. Without a spot, just the text. -->
<script lang="ts">
	import type { Snippet } from 'svelte';

	type Spot = { map: string; x: number; y: number; facing?: string };
	let { spot, label, children }: { spot: Spot | null | undefined; label: string; children: Snippet } = $props();

	const MARGIN = 20; // border blocks round each map image (PublicController.MAP_MARGIN)
	const BLOCK = 32; // px per block; a step is half a block
	const SCALE = 2;
	const W = 320;
	const H = 240;
	let open = $state(false);
	let failed = $state(false);
	// The player's middle on the map image, then the image shifted so that point sits in the window's middle.
	const at = $derived(spot ? { x: MARGIN * BLOCK + spot.x * 16 + 8, y: MARGIN * BLOCK + spot.y * 16 + 8 } : null);
	const hour = new Date().getHours();
	const time = hour >= 4 && hour < 10 ? 'morn' : hour >= 10 && hour < 18 ? 'day' : 'nite'; // the games' clock bands
</script>

{#if spot}
	<span
		class="where"
		role="button"
		tabindex="0"
		aria-label="{label}: show it on the map"
		aria-expanded={open}
		onpointerenter={() => (open = true)}
		onpointerleave={() => (open = false)}
		onfocus={() => (open = true)}
		onblur={() => (open = false)}
	>
		{@render children()}
		{#if open && at}
			<span class="pop" role="tooltip" style:width="{W}px">
				<span class="screen" style:height="{H}px">
					{#if failed}
						<span class="none">No map for this place</span>
					{:else}
						<img
							src="/api/v1/public/maps/{spot.map}.png?time={time}&m={MARGIN}"
							alt=""
							style:transform="translate({W / 2 - at.x * SCALE}px, {H / 2 - at.y * SCALE}px) scale({SCALE})"
							onerror={() => (failed = true)}
						/>
						<span class="marker" aria-hidden="true"></span>
					{/if}
				</span>
				<span class="cap">{label}</span>
			</span>
		{/if}
	</span>
{:else}
	{@render children()}
{/if}

<style>
	.where {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: 4px;
		cursor: help;
		border-radius: 6px;
	}
	.where:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.pop {
		position: absolute;
		z-index: 40;
		top: calc(100% + 8px);
		left: 0;
		display: flex;
		flex-direction: column;
		border-radius: 12px;
		border: 3px solid var(--border-strong);
		background: var(--panel);
		box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.55);
		overflow: hidden;
		pointer-events: none;
		animation: pop 120ms ease-out;
	}
	@keyframes pop {
		from {
			opacity: 0;
			scale: 0.96;
		}
	}
	.screen {
		position: relative;
		overflow: hidden;
		background: #000;
	}
	.screen img {
		position: absolute;
		top: 0;
		left: 0;
		max-width: none;
		transform-origin: 0 0;
		image-rendering: pixelated;
	}
	/* Where they stand: a ring pulsing round their tile. */
	.marker {
		position: absolute;
		left: 50%;
		top: 50%;
		width: 36px;
		height: 36px;
		translate: -50% -50%;
		border-radius: 50%;
		border: 3px solid #ff3b30;
		box-shadow: 0 0 0 2px rgb(255 255 255 / 0.85), 0 0 14px rgb(255 59 48 / 0.7);
		animation: ping 1.4s ease-out infinite;
	}
	@keyframes ping {
		0% {
			scale: 0.7;
			opacity: 1;
		}
		100% {
			scale: 1.25;
			opacity: 0.35;
		}
	}
	.none {
		position: absolute;
		inset: 0;
		display: grid;
		place-items: center;
		color: #ccc;
		font-size: 13px;
	}
	.cap {
		padding: 8px 12px;
		font-size: 13px;
		font-weight: 700;
		color: var(--text);
	}
	@media (prefers-reduced-motion: reduce) {
		.pop,
		.marker {
			animation: none;
		}
	}
</style>
