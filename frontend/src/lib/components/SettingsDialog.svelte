<!-- Settings (account menu). Upright phones: the strips under the controller on /play (pick up to three, put them in
     order; per browser). Keyboards: the player's key bindings (not on touch screens). Three choices: Arrows, WASD or Custom (any key per pad button:
     click a button's key, press the new one; a key already in use swaps over). A little keyboard shows which keys do
     what. Escape cancels a change, then closes. -->
<script lang="ts">
	import { BUTTONS, keyName, PRESETS, rebind, RESERVED_KEYS, type Bindings, type Button } from '#lib/play.ts';
	import { keys, loadKeys, setBindings } from '#lib/keys.svelte.ts';
	import { controls, setStrips, STRIP_IDS, STRIP_NAMES, stripsThatFit, type StripId } from '#lib/controls.svelte.ts';
	// As many as this screen has room for (at most three).
	const limit = $derived(stripsThatFit());

	// Strips: the picked ones in their order, then the rest.
	const stripRows = $derived([...controls.strips, ...STRIP_IDS.filter((id) => !controls.strips.includes(id))]);
	function toggleStrip(id: StripId, on: boolean) {
		setStrips(on ? [...controls.strips, id] : controls.strips.filter((s) => s !== id));
	}
	function moveStrip(id: StripId, to: number) {
		const list = controls.strips.filter((s) => s !== id);
		list.splice(Math.max(0, Math.min(to, list.length)), 0, id);
		setStrips(list);
	}
	// Dragging a picked strip by its grip (pointer events: fingers and mice alike). The row follows the pointer; the
	// list reorders as it crosses the middle of a neighbour.
	let dragging = $state<{ id: StripId; startY: number; startIndex: number; rowH: number; dy: number } | null>(null);
	function grab(e: PointerEvent, id: StripId) {
		const row = (e.currentTarget as HTMLElement).closest('li')!;
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
		e.preventDefault();
		dragging = { id, startY: e.clientY, startIndex: controls.strips.indexOf(id), rowH: row.offsetHeight + 6, dy: 0 };
	}
	function drag(e: PointerEvent) {
		if (!dragging) return;
		const dy = e.clientY - dragging.startY;
		const to = Math.max(0, Math.min(controls.strips.length - 1, dragging.startIndex + Math.round(dy / dragging.rowH)));
		if (to !== controls.strips.indexOf(dragging.id)) moveStrip(dragging.id, to);
		dragging.dy = dy;
	}
	const dragShift = (id: StripId) =>
		dragging?.id === id ? dragging.dy - (controls.strips.indexOf(id) - dragging.startIndex) * dragging.rowH : 0;

	let { open = $bindable(false) }: { open?: boolean } = $props();

	const LABELS: Record<Button, string> = { up: 'Up', down: 'Down', left: 'Left', right: 'Right', a: 'A', b: 'B', start: 'Start', select: 'Select' };
	const LAYOUTS: { id: keyof typeof PRESETS; label: string }[] = [
		{ id: 'arrows', label: 'Arrows' },
		{ id: 'wasd', label: 'WASD' }
	];
	/** What each pad button is called on its key in the drawing. */
	const TAGS: Record<Button, string> = { up: '▲', down: '▼', left: '◀', right: '▶', a: 'A', b: 'B', start: 'START', select: 'SEL' };
	/** The keyboard drawn: rows of [key as stored, label, width in key units]. */
	type Cap = [string, string, number];
	const k = (keys: string): Cap[] => [...keys].map((c) => [c, c.toUpperCase(), 1]);
	const ROWS: Cap[][] = [
		k('1234567890'),
		[['Tab', 'Tab', 1.5], ...k('qwertyuiop')],
		[['CapsLock', 'Caps', 1.75], ...k('asdfghjkl'), ['Enter', 'Enter', 1.75]],
		[['Shift', 'Shift', 2.25], ...k('zxcvbnm,.'), ['/', '/', 1]],
		[['Control', 'Ctrl', 1.5], ['Alt', 'Alt', 1.25], [' ', 'Space', 5.5], ['Backspace', '⌫', 1.75]]
	];
	const ARROWS: [string, string, number, number][] = [
		['ArrowUp', '↑', 2, 1],
		['ArrowLeft', '←', 1, 2],
		['ArrowDown', '↓', 2, 2],
		['ArrowRight', '→', 3, 2]
	];
	const byKey = $derived(Object.fromEntries(BUTTONS.map((b) => [keys.bindings[b], b])) as Record<string, Button>);
	const drawn = new Set([...ROWS.flat().map((c) => c[0]), ...ARROWS.map((a) => a[0])]);
	const offKeyboard = $derived(BUTTONS.filter((b) => !drawn.has(keys.bindings[b])));
	let custom = $state(false);

	let dialog = $state<HTMLDialogElement>();
	let listening = $state<Button | null>(null);
	let note = $state('');

	$effect(() => {
		if (open) void loadKeys();
		if (open && !dialog?.open) dialog?.showModal();
		if (!open && dialog?.open) dialog.close();
	});

	const same = (a: Bindings, b: Bindings) => BUTTONS.every((k) => a[k] === b[k]);
	const preset = $derived(LAYOUTS.find((l) => same(keys.bindings, PRESETS[l.id]))?.id ?? null);
	const layout = $derived(custom || !preset ? 'custom' : preset);

	function usePreset(id: keyof typeof PRESETS) {
		custom = false;
		setBindings({ ...PRESETS[id] });
		listening = null;
		note = '';
	}

	function listen(b: Button) {
		listening = listening === b ? null : b;
		note = listening ? `Press the key for ${LABELS[b]}. Escape cancels.` : '';
	}

	function onkeydown(e: KeyboardEvent) {
		if (!listening) return;
		if (e.key === 'Escape') {
			e.preventDefault(); // cancel the change, not the dialog
			listening = null;
			note = '';
			return;
		}
		if (RESERVED_KEYS.includes(e.key) || e.ctrlKey || e.metaKey || e.altKey) {
			note = `${e.key} can't be used. Press another key.`;
			e.preventDefault();
			return;
		}
		e.preventDefault();
		const b = listening;
		const { bindings, swapped } = rebind(keys.bindings, b, e.key);
		void setBindings(bindings);
		custom = true;
		listening = null;
		note = swapped
			? `${LABELS[b]} is now ${keyName(bindings[b])}; ${LABELS[swapped]} took ${keyName(bindings[swapped])}.`
			: `${LABELS[b]} is now ${keyName(bindings[b])}.`;
	}
</script>

<dialog
	bind:this={dialog}
	class="settings"
	aria-labelledby="settings-dialog-h"
	{onkeydown}
	oncancel={(e) => listening && e.preventDefault()}
	onclose={() => {
		open = false;
		listening = null;
		note = '';
		custom = false;
	}}
	onclick={(e) => e.target === dialog && dialog.close()}
>
	<div class="body">
		<header>
			<h2 id="settings-dialog-h">Settings</h2>
			<button type="button" class="close" aria-label="Close settings" onclick={() => dialog?.close()}>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</header>

		<section class="strips-section" aria-labelledby="strips-h">
			<div class="strips-head">
				<h3 id="strips-h">Under the controller</h3>
				<span class="count" class:full={Math.min(controls.strips.length, limit) >= limit}>{Math.min(controls.strips.length, limit)} / {limit}</span>
			</div>
			{#if limit === 0}
				<p class="hint">This screen is too short for anything under the Game Boy: the screen and controller take all of it.</p>
			{:else}
			<p class="hint">
				Pick what shows under your Game Boy on Play, and drag
				<span class="grip-chip" aria-hidden="true"
					><svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M5 8h14M5 12h14M5 16h14" /></svg></span
				><span class="sr">the grip</span> to put them in order.
			</p>
			<p class="fine">This screen has room for {limit === 1 ? 'one' : limit === 2 ? 'two' : 'three'}.</p>
			<ul class="strip-list">
				{#each stripRows as id (id)}
					{@const i = controls.strips.indexOf(id)}
					{@const on = i >= 0}
					<li class:on class:lifted={dragging?.id === id} style:translate={dragging?.id === id ? `0 ${dragShift(id)}px` : null}>
						<label>
							<input type="checkbox" checked={on} disabled={!on && controls.strips.length >= limit} onchange={(e) => toggleStrip(id, e.currentTarget.checked)} />
							<span>{STRIP_NAMES[id]}</span>
							{#if on && i >= limit}<span class="nofit">doesn't fit</span>{/if}
						</label>
						{#if on}
							<button
								type="button"
								class="grip"
								aria-label="Reorder {STRIP_NAMES[id]}, number {i + 1} of {controls.strips.length}: drag, or press the up and down arrows"
								onpointerdown={(e) => grab(e, id)}
								onpointermove={drag}
								onpointerup={() => (dragging = null)}
								onpointercancel={() => (dragging = null)}
								onkeydown={(e) => {
									if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
									e.preventDefault();
									moveStrip(id, i + (e.key === 'ArrowUp' ? -1 : 1));
									const el = e.currentTarget;
									queueMicrotask(() => el.focus()); // the row moved: keep the grip focused
								}}
							>
								<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
									><path d="M5 8h14M5 12h14M5 16h14" /></svg
								>
							</button>
						{/if}
					</li>
				{/each}
			</ul>
			{/if}
		</section>

		<section class="keys-section" aria-labelledby="keys-h">
			<h3 id="keys-h">Keyboard</h3>
			<div class="layouts" role="group" aria-label="Layout">
				{#each LAYOUTS as l (l.id)}
					<button type="button" aria-pressed={layout === l.id} onclick={() => usePreset(l.id)}>{l.label}</button>
				{/each}
				<button type="button" aria-pressed={layout === 'custom'} onclick={() => (custom = true)}>Custom</button>
			</div>

			<!-- The keyboard, the keys in use lit and tagged with their pad button. -->
			<div class="kb" aria-hidden="true">
				<div class="main">
					{#each ROWS as row, r (r)}
						<div class="row">
							{#each row as [key, label, w] (key)}
								{@const b = byKey[key]}
								<span class="cap" class:on={b} class:wait={b && listening === b} style:flex-grow={w}
									>{#if b}<i>{TAGS[b]}</i>{/if}<span>{label}</span></span
								>
							{/each}
						</div>
					{/each}
				</div>
				<div class="arrows">
					{#each ARROWS as [key, label, x, y] (key)}
						{@const b = byKey[key]}
						<span class="cap" class:on={b} class:wait={b && listening === b} style:grid-column={x} style:grid-row={y}
							>{#if b}<i>{TAGS[b]}</i>{/if}<span>{label}</span></span
						>
					{/each}
				</div>
			</div>
			{#if offKeyboard.length}
				<p class="off">Also: {offKeyboard.map((b) => `${TAGS[b]} ${keyName(keys.bindings[b])}`).join(' · ')}</p>
			{/if}

			{#if layout === 'custom'}
				<ul class="binds">
					{#each BUTTONS as b (b)}
						<li>
							<span>{LABELS[b]}</span>
							<button
								type="button"
								class="key"
								class:listening={listening === b}
								aria-label="{LABELS[b]}: {keyName(keys.bindings[b])}. Change"
								onclick={() => listen(b)}>{listening === b ? 'Press a key…' : keyName(keys.bindings[b])}</button
							>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="sr">
					{BUTTONS.map((b) => `${LABELS[b]}: ${keyName(keys.bindings[b])}`).join(', ')}
				</p>
			{/if}
			<p class="note" role="status" aria-live="polite">{keys.error || note || layout === 'custom' ? 'Click a key to change it. Saved to your account.' : 'Saved to your account.'}</p>
		</section>
	</div>
</dialog>

<style>
	/* Strips: upright phones only. Keyboard: not on touch screens. */
	.strips-section {
		display: none;
	}
	@media (max-width: 760px) {
		.strips-section {
			display: block;
		}
	}
	@media (hover: none) and (pointer: coarse) {
		.keys-section {
			display: none;
		}
	}
	.strips-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 8px;
	}
	.strips-head h3 {
		margin: 0;
	}
	.count {
		padding: 2px 8px;
		border-radius: 999px;
		border: 1px solid var(--border-strong);
		font-size: 12px;
		font-weight: 700;
		font-variant-numeric: tabular-nums;
		color: var(--muted);
	}
	.count.full {
		border-color: var(--accent);
		color: var(--accent-text);
	}
	.hint {
		margin: 0;
		font-size: 14px;
		line-height: 20px;
		color: var(--text);
	}
	.grip-chip {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 20px;
		height: 18px;
		margin: 0 2px;
		vertical-align: -3px;
		border: 1px solid var(--border-strong);
		border-radius: 5px;
		color: var(--muted);
	}
	.nofit {
		margin-left: auto;
		font-size: 12px;
		font-weight: 500;
		color: var(--muted);
	}
	.fine {
		margin: 4px 0 12px;
		font-size: 12px;
		color: var(--muted);
	}
	.strip-list {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.strip-list li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		min-height: 44px;
		padding: 0 6px 0 12px;
		border: 1px solid var(--border);
		border-radius: 10px;
	}
	.strip-list li.on {
		border-color: var(--accent);
		background: var(--accent-tint, var(--sunk));
	}
	.strip-list label {
		flex: 1;
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 44px;
		font-weight: 600;
		cursor: pointer;
	}
	.strip-list input {
		width: 18px;
		height: 18px;
		margin: 0;
		accent-color: var(--accent-text);
	}
	.strip-list input:disabled + span {
		color: var(--muted);
	}
	/* The grip: press and drag (touch-action none, so the finger drags the row, not the dialog). */
	.grip {
		width: 44px;
		height: 40px;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
		cursor: grab;
		touch-action: none;
	}
	.lifted {
		position: relative;
		z-index: 1;
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.35);
	}
	.lifted .grip {
		cursor: grabbing;
		color: var(--text);
	}
	/* Modal, so in the top layer and centred whatever page opens it. */
	dialog {
		position: fixed;
		inset: 0;
		margin: auto;
		width: min(560px, calc(100vw - 32px));
		max-height: calc(100dvh - 32px);
		padding: 0;
		border: 1px solid var(--border-strong);
		border-radius: 14px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 16px 48px rgb(0 0 0 / 0.35);
	}
	dialog::backdrop {
		background: rgb(8 11 13 / 0.55);
	}
	.body {
		padding: 18px 20px 20px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	/* .settings in front of each: the admin page's own h2/svg rules must not shrink these. */
	.settings h2 {
		margin: 0;
		font: 700 18px/1.3 var(--font, system-ui, sans-serif);
	}
	.settings .close {
		padding: 0;
		min-width: 0;
		height: 32px;
		border: none;
	}
	.settings .close svg {
		flex: none;
		width: 18px;
		height: 18px;
	}
	h3 {
		margin: 0 0 10px;
		font-size: 12px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	button {
		font: inherit;
		color: inherit;
		cursor: pointer;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.close {
		width: 32px;
		height: 32px;
		display: flex;
		align-items: center;
		justify-content: center;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
	}
	.close:hover {
		background: var(--sunk);
		color: var(--text);
	}
	dialog:not([open]) {
		display: none;
	}
	.layouts {
		display: grid;
		grid-template-columns: 1fr 1fr 1fr;
		gap: 8px;
	}
	.layouts button {
		height: 36px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		background: none;
		font-size: 13px;
		font-weight: 600;
	}
	.layouts button[aria-pressed='true'] {
		border-color: var(--accent-text);
		color: var(--accent-text);
		background: var(--accent-tint, var(--sunk));
	}
	/* The keyboard drawing. */
	.kb {
		margin-top: 14px;
		display: flex;
		gap: 10px;
		align-items: flex-end;
		padding: 10px;
		border-radius: 10px;
		background: var(--sunk);
		border: 1px solid var(--border);
	}
	.main {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 3px;
		min-width: 0;
	}
	.row {
		display: flex;
		gap: 3px;
	}
	.arrows {
		display: grid;
		grid-template-columns: repeat(3, 26px);
		grid-template-rows: repeat(2, 26px);
		gap: 3px;
	}
	.cap {
		position: relative;
		flex: 1 1 0;
		min-width: 0;
		height: 26px;
		display: flex;
		align-items: flex-end;
		justify-content: center;
		padding-bottom: 3px;
		box-sizing: border-box;
		border-radius: 4px;
		background: var(--panel);
		border: 1px solid var(--border);
		border-bottom-width: 2px;
		font-size: 9px;
		color: var(--muted);
		overflow: hidden;
	}
	.cap.on {
		background: color-mix(in oklab, var(--accent), transparent 25%);
		border-color: var(--accent-text);
		color: #1d1f1c;
		font-weight: 700;
	}
	.cap.wait {
		outline: 2px solid var(--accent-text);
		outline-offset: 1px;
	}
	.cap i {
		position: absolute;
		top: 1px;
		left: 2px;
		font: 700 7px/1 var(--pixel-font);
		font-style: normal;
	}
	.off {
		margin: 8px 0 0;
		font-size: 12px;
		color: var(--muted);
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
	}
	.binds {
		margin: 14px 0 0;
		padding: 0;
		list-style: none;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 6px 16px;
	}
	.binds li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		font-size: 14px;
	}
	.key {
		min-width: 64px;
		height: 32px;
		padding: 0 10px;
		border-radius: 6px;
		border: 1px solid var(--border-strong);
		border-bottom-width: 3px;
		background: var(--sunk);
		font-size: 13px;
		font-weight: 700;
	}
	.key.listening {
		border-color: var(--accent-text);
		color: var(--accent-text);
		font-weight: 600;
		font-size: 12px;
	}
	.note {
		margin: 12px 0 0;
		min-height: 18px;
		font-size: 12px;
		color: var(--muted);
	}
	@media (max-width: 480px) {
		.binds {
			grid-template-columns: 1fr;
		}
		.kb {
			flex-direction: column;
			align-items: stretch;
		}
		.arrows {
			align-self: center;
		}
		.cap {
			font-size: 7px;
		}
	}
	@media (prefers-reduced-motion: no-preference) {
		.key {
			transition: border-color 0.15s;
		}
	}
</style>
