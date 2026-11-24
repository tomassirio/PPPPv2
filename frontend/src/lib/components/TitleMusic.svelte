<!-- The sign-in page's music switch, in the corner: Pokémon Crystal's title theme (recorded from the ROM's own sound
     engine), looping, fading in. Off until the visitor turns it on; remembered per browser. A browser that refuses to
     play before a gesture gets it on the next key or tap. -->
<script lang="ts">
	import theme from '#lib/assets/music/crystal-title.mp3';

	const KEY = 'pppp.titleMusic';
	const VOLUME = 0.35;
	let on = $state(false);
	try {
		on = localStorage.getItem(KEY) === 'on';
	} catch {
		// No storage: off.
	}
	let audio: HTMLAudioElement | undefined;
	let fader: ReturnType<typeof setInterval> | undefined;

	function start() {
		audio ??= Object.assign(new Audio(theme), { loop: true, volume: 0 });
		audio.play().then(
			() => {
				clearInterval(fader);
				fader = setInterval(() => {
					if (!audio) return;
					audio.volume = Math.min(VOLUME, audio.volume + 0.02);
					if (audio.volume >= VOLUME) clearInterval(fader);
				}, 60);
			},
			() => {} // not allowed yet: the next gesture tries again
		);
	}
	function stop() {
		clearInterval(fader);
		audio?.pause();
		if (audio) audio.volume = 0;
	}

	$effect(() => {
		if (on) start();
		else stop();
		return stop;
	});

	function toggle() {
		on = !on;
		try {
			localStorage.setItem(KEY, on ? 'on' : 'off');
		} catch {
			// Not remembered.
		}
	}
</script>

<svelte:window
	onpointerdown={() => on && audio?.paused && start()}
	onkeydown={() => on && audio?.paused && start()}
/>

<button
	type="button"
	class="music"
	aria-pressed={on}
	aria-label="Title music"
	title={on ? 'Music on' : 'Music off'}
	onpointerdown={(e) => e.stopPropagation()}
	onkeydown={(e) => e.stopPropagation()}
	onclick={toggle}
>
	<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
		<path d="M4 9h4l5-4v14l-5-4H4z" fill="currentColor" />
		{#if on}
			<path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12" />
		{:else}
			<path d="M17 9l5 6M22 9l-5 6" />
		{/if}
	</svg>
</button>

<style>
	.music {
		position: fixed;
		top: max(12px, env(safe-area-inset-top));
		right: max(12px, env(safe-area-inset-right));
		z-index: 6; /* above the intro */
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border: 1px solid rgba(255, 255, 255, 0.25);
		border-radius: 50%;
		background: rgba(20, 16, 30, 0.6);
		color: #fff;
		cursor: pointer;
		backdrop-filter: blur(4px);
		transition: background 120ms;
	}
	.music:hover {
		background: rgba(20, 16, 30, 0.8);
	}
	.music[aria-pressed='false'] {
		color: rgba(255, 255, 255, 0.6);
	}
	.music:focus-visible {
		outline: 2px solid #fff;
		outline-offset: 2px;
	}
</style>
