<!-- D-pad, A/B, Start/Select. Keys and fingers hold buttons down on the player's own console over /ws/input
     (#lib/input.ts) until let go; a click without a pointer, or a closed socket, sends one press (POST /me/inputs). Keys:
     with the player's own key bindings (Settings in the account menu; arrows + Z/X by default). -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { keyToButton, type Button } from '#lib/play.ts';
	import { sendKey, useInput } from '#lib/input.ts';
	import { SvelteSet } from 'svelte/reactivity';
	import { keys, loadKeys } from '#lib/keys.svelte.ts';
	$effect(() => void loadKeys());
	// aria-keyshortcuts names (KeyboardEvent.key, Space for " ").
	const shortcut = (b: Button) => (keys.bindings[b] === ' ' ? 'Space' : keys.bindings[b]);
	import { shellVars } from '#lib/shells.ts';
	import { currentShell } from '#lib/shell.svelte.ts';
	import ShellPicker from './ShellPicker.svelte';
	import { konamiPress } from '#lib/konami.svelte.ts';
	import { controls, setControlsLow } from '#lib/controls.svelte.ts';

	let { talk }: { talk: () => boolean } = $props();

	// The player's controller shell (colours), remembered in this browser.
	const shell = $derived(currentShell.value);
	const vars = $derived(shellVars(shell));

	let status = $state('');

	let statusError = $state(false);
	let statusTimer: ReturnType<typeof setTimeout> | undefined;
	function say(text: string, error = true) {
		status = text;
		statusError = error;
		clearTimeout(statusTimer);
		statusTimer = setTimeout(() => (status = ''), 3000);
	}

	/** One press (held a few frames, then released): the fallback when the input socket is not open. */
	async function send(command: string) {
		try {
			await api('POST', '/me/inputs', { command, repeat: 1 });
		} catch (e) {
			if (!(e instanceof ApiError)) return say('Network error, try again');
			say(e.status === 400 ? e.detail : `Not sent: ${e.detail}`);
		}
	}

	/*
	 * Held buttons: a key or a finger stays down on the Game Boy until it lets go (sendKey over /ws/input), so
	 * holding a direction walks smoothly. Taps that come as clicks without a pointer (Enter or Space on a focused
	 * pad button) go out as a single press instead.
	 */
	const down = new SvelteSet<Button>();
	$effect(() => useInput());

	function press(b: Button) {
		if (down.has(b)) return;
		if (b === 'a' && talk()) {
			releaseAll();
			return;
		}
		down.add(b);
		konamiPress(b);
		if (!sendKey(b, true)) send(b); // socket not open yet: at least a tap
	}

	function release(b: Button) {
		if (!down.delete(b)) return;
		sendKey(b, false);
	}

	function releaseAll() {
		for (const b of [...down]) release(b);
	}
	$effect(() => releaseAll); // leaving the page

	function onkeydown(e: KeyboardEvent) {
		const b = keyToButton({ key: e.key, ctrlKey: e.ctrlKey, metaKey: e.metaKey, altKey: e.altKey, target: e.target }, keys.bindings);
		if (!b) return;
		e.preventDefault(); // no page scroll, also for the browser's auto-repeat of a held key
		if (!e.repeat) press(b);
	}

	function onkeyup(e: KeyboardEvent) {
		// Let go even if focus moved into a text field meanwhile.
		const b = keyToButton({ key: e.key }, keys.bindings);
		if (b) release(b);
	}

	/** A short buzz for touch presses (Android; ignored where unsupported, e.g. iPhone). */
	function buzz(e: Event) {
		if ((e as PointerEvent).pointerType === 'touch') navigator.vibrate?.(12);
	}

	function pointerDown(e: PointerEvent, b: Button) {
		if (e.button !== 0) return;
		buzz(e);
		press(b);
	}

	const dpad: { b: Button; label: string; path: string; area: string; key: string }[] = [
		{ b: 'up', label: 'Up', path: 'M6 15l6-6 6 6', area: 'up', key: 'ArrowUp' },
		{ b: 'left', label: 'Left', path: 'M15 6l-6 6 6 6', area: 'left', key: 'ArrowLeft' },
		{ b: 'right', label: 'Right', path: 'M9 6l6 6-6 6', area: 'right', key: 'ArrowRight' },
		{ b: 'down', label: 'Down', path: 'M6 9l6 6 6-6', area: 'down', key: 'ArrowDown' }
	];
</script>

<svelte:window {onkeydown} {onkeyup} onblur={releaseAll} />

<div class="shell" style={vars}>
	<div class="picker-corner"><ShellPicker /></div>
	<p class="pib" aria-hidden="true">Pibtendo</p>
	<div class="row">
		<div class="dpad">
			{#each dpad as d (d.b)}
				<button
					type="button"
					class="dir"
					class:held={down.has(d.b)}
					style:grid-area={d.area}
					aria-label={d.label}
					aria-keyshortcuts={shortcut(d.b)}
					onpointerdown={(e) => pointerDown(e, d.b)}
					onpointerup={() => release(d.b)}
					onpointercancel={() => release(d.b)}
					onpointerleave={() => release(d.b)}
					oncontextmenu={(e) => e.preventDefault()}
					onclick={(e) => e.detail === 0 && send(d.b)}
				>
					<svg
						width="22"
						height="22"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2.5"
						stroke-linecap="round"
						stroke-linejoin="round"
						aria-hidden="true"><path d={d.path}></path></svg
					>
				</button>
			{/each}
			<div class="hub"></div>
		</div>
		<div class="ab">
			<button type="button" class="round pixel b" class:held={down.has('b')} aria-label="B button" aria-keyshortcuts={shortcut('b')} onpointerdown={(e) => pointerDown(e, 'b')}
			onpointerup={() => release('b')}
			onpointercancel={() => release('b')}
			onpointerleave={() => release('b')}
			oncontextmenu={(e) => e.preventDefault()}
			onclick={(e) => e.detail === 0 && send('b')}
				>B</button
			>
			<button type="button" class="round pixel" class:held={down.has('a')} aria-label="A button" aria-keyshortcuts={shortcut('a')} onpointerdown={(e) => pointerDown(e, 'a')}
			onpointerup={() => release('a')}
			onpointercancel={() => release('a')}
			onpointerleave={() => release('a')}
			oncontextmenu={(e) => e.preventDefault()}
			onclick={(e) => e.detail === 0 && !talk() && send('a')}
				>A</button
			>
		</div>
	</div>
	<div class="meta">
		<button type="button" class="pill" class:held={down.has('select')} aria-label="Select" aria-keyshortcuts={shortcut('select')} onpointerdown={(e) => pointerDown(e, 'select')}
			onpointerup={() => release('select')}
			onpointercancel={() => release('select')}
			onpointerleave={() => release('select')}
			oncontextmenu={(e) => e.preventDefault()}
			onclick={(e) => e.detail === 0 && send('select')}
			>SELECT</button
		>
		<button type="button" class="pill" class:held={down.has('start')} aria-label="Start" aria-keyshortcuts={shortcut('start')} onpointerdown={(e) => pointerDown(e, 'start')}
			onpointerup={() => release('start')}
			onpointercancel={() => release('start')}
			onpointerleave={() => release('start')}
			oncontextmenu={(e) => e.preventDefault()}
			onclick={(e) => e.detail === 0 && send('start')}
			>START</button
		>
	</div>
	<!-- Upright phones: a triangle in the corner moves the controller down to the bottom edge, or back up. -->
	<button
		type="button"
		class="move"
		aria-label={controls.low ? 'Move the controller up' : 'Move the controller down'}
		title={controls.low ? 'Move the controller up' : 'Move the controller down'}
		onclick={() => setControlsLow(!controls.low)}
	>
		<svg viewBox="0 0 12 12" width="12" height="12" fill="currentColor" aria-hidden="true"><path d={controls.low ? 'M6 2l5 8H1z' : 'M6 10L1 2h10z'} /></svg>
	</button>
</div>

<!-- Only a failed press says anything here. Key bindings live in Settings (account menu). -->
<p class="last" role="status" aria-live="polite">
	{#if status}<span class:error={statusError} class:muted={!statusError}>{status}</span>{/if}
</p>

<style>
	.shell {
		position: relative;
		background: var(--shell);
		box-shadow: inset 0 0 0 1px var(--pad-edge);
		border-radius: 16px 16px 48px 16px;
		padding: 24px;
		display: flex;
		flex-direction: column;
		gap: 24px;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		padding: 0 8px;
	}
	.dpad {
		display: grid;
		grid-template-columns: repeat(3, 52px);
		grid-template-rows: repeat(3, 52px);
		grid-template-areas: '. up .' 'left hub right' '. down .';
	}
	.hub {
		grid-area: hub;
		background: var(--dpad);
	}
	button {
		border: none;
		touch-action: manipulation;
		-webkit-tap-highlight-color: transparent;
		user-select: none;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.dir {
		-webkit-touch-callout: none; /* no long-press menu while holding a direction */
		background: var(--dpad);
		color: var(--dpad-ink);
		border-radius: 6px;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0;
	}
	.dir:active,
	.round:active,
	.pill:active,
	.held {
		filter: brightness(0.8);
	}
	.ab {
		display: flex;
		gap: 16px;
		align-items: center;
		transform: rotate(-20deg);
	}
	.round {
		width: 64px;
		height: 64px;
		border-radius: 50%;
		background: var(--ab);
		color: var(--ab-ink);
		font-size: 14px;
	}
	.round.b {
		margin-top: 28px;
	}
	.meta {
		display: flex;
		gap: 16px;
		justify-content: center;
	}
	.pill {
		min-width: 88px;
		height: 44px;
		border-radius: 22px;
		background: var(--pill);
		color: var(--pill-ink);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.08em;
	}
	/* The Pibtendo oval from the sign-in handheld, embossed in the shell's own colour. */
	/* Floats in the shell's top padding, between the d-pad and A/B: adds no height to the pad. */
	.pib {
		position: absolute;
		top: 8px;
		left: 50%;
		translate: -50% 0;
		margin: 0;
		padding: 2px 10px;
		border-radius: 999px;
		--emboss: color-mix(in oklab, var(--shell), black 28%);
		--emboss-hi: color-mix(in oklab, var(--shell), white 40%);
		border: 2px solid var(--emboss);
		box-shadow:
			0 1px 0 0 var(--emboss-hi),
			inset 0 1px 0 0 var(--emboss-hi);
		font: italic 800 11px/1 var(--body-font);
		letter-spacing: 0.01em;
		color: var(--emboss);
		text-shadow: 0 1px 0 var(--emboss-hi);
		user-select: none;
	}
	@media (orientation: landscape) and (max-height: 520px) {
		.pib {
			display: none; /* the pad is split around the screen there */
		}
	}
	.move {
		display: none;
		position: absolute;
		left: 10px;
		bottom: 10px;
		width: 32px;
		height: 32px;
		align-items: center;
		justify-content: center;
		padding: 0;
		border-radius: 50%;
		background: transparent;
		color: color-mix(in oklab, var(--shell), black 32%);
	}
	@media (max-width: 760px) and (orientation: portrait) {
		.move {
			display: flex;
		}
	}
	.picker-corner {
		position: absolute;
		top: 10px;
		right: 12px;
		z-index: 5;
	}
	.last {
		margin: 0;
		min-height: 0;
		text-align: center;
		font-size: 12px;
	}
	.last:empty {
		display: none;
	}
	.muted {
		color: var(--muted);
	}
	.error {
		color: var(--danger);
		font-weight: 600;
	}
	@media (max-width: 400px) {
		.shell {
			padding: 16px 12px;
		}
		.row {
			padding: 0;
			gap: 8px;
		}
		.dpad {
			grid-template-columns: repeat(3, 46px);
			grid-template-rows: repeat(3, 46px);
		}
		.round {
			width: 56px;
			height: 56px;
		}
		.ab {
			gap: 10px;
		}
	}
	/* Short phones: a tighter pad so it fits under the screen without scrolling. */
	@media (max-width: 760px) and (max-height: 760px) {
		.shell {
			padding: 14px 12px;
			gap: 12px;
		}
		.dpad {
			grid-template-columns: repeat(3, 44px);
			grid-template-rows: repeat(3, 44px);
		}
		.round {
			width: 52px;
			height: 52px;
		}
		.pill {
			height: 36px;
		}
	}
</style>
