<!-- A location you can hover (or focus): the Pokégear town map of the region they are in (GET
     /public/townmap/{region}.png, drawn from Crystal), two pixels per Game Boy pixel, with a marker where they are.
     Without a pin (link rooms, the ship, before their first reading), just the text. user instead of pin: asks
     where that Pib is (GET /players/{id}/pin) each time it opens, for places that move (the Play screen's sign). -->
<script lang="ts">
	import type { Snippet } from 'svelte';
	import { api } from '#lib/api.ts';

	type Pin = { region: 'johto' | 'kanto'; x: number; y: number };
	let { pin: given, user, label, children }: { pin?: Pin | null; user?: string; label: string; children: Snippet } = $props();

	const SCALE = 2;
	let open = $state(false);
	let fetched = $state<Pin | null>(null);
	const pin = $derived(given ?? fetched);

	async function show() {
		open = true;
		if (user && !given) fetched = (await api<Pin | null>('GET', `/players/${encodeURIComponent(user)}/pin`).catch(() => null)) || null;
	}
</script>

{#if given || user}
	<span
		class="where"
		role="button"
		tabindex="0"
		aria-label="{label}: show it on the town map"
		aria-expanded={open}
		onpointerenter={show}
		onpointerleave={() => (open = false)}
		onfocus={show}
		onblur={() => (open = false)}
	>
		{@render children()}
		{#if open && pin}
			<span class="pop" role="tooltip">
				<span class="screen" style:width="{160 * SCALE}px" style:height="{144 * SCALE}px">
					<img src="/api/v1/public/townmap/{pin.region}.png" alt="" width={160 * SCALE} height={144 * SCALE} />
					<span class="marker" aria-hidden="true" style:left="{pin.x * SCALE}px" style:top="{pin.y * SCALE}px"></span>
				</span>
				<span class="cap">{label}<span class="region">{pin.region === 'kanto' ? 'Kanto' : 'Johto'}</span></span>
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
		display: block;
	}
	.screen img {
		display: block;
		image-rendering: pixelated;
	}
	/* Where they are: a red dot with a ring pulsing out of it. */
	.marker {
		position: absolute;
		width: 14px;
		height: 14px;
		translate: -50% -50%;
		border-radius: 50%;
		background: #ff3b30;
		box-shadow: 0 0 0 2px #fff;
	}
	.marker::after {
		content: '';
		position: absolute;
		inset: -6px;
		border-radius: 50%;
		border: 3px solid #ff3b30;
		animation: ping 1.4s ease-out infinite;
	}
	@keyframes ping {
		0% {
			scale: 0.5;
			opacity: 1;
		}
		100% {
			scale: 1.6;
			opacity: 0;
		}
	}
	.cap {
		display: flex;
		justify-content: space-between;
		gap: 12px;
		padding: 8px 12px;
		font-size: 13px;
		font-weight: 700;
		color: var(--text);
	}
	.region {
		font-weight: 600;
		color: var(--muted);
	}
	@media (prefers-reduced-motion: reduce) {
		.pop,
		.marker::after {
			animation: none;
		}
	}
</style>
