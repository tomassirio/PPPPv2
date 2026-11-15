<!-- Settings (account menu): the player's key bindings. Two ready-made layouts, then any key per pad button: click a
     button's key, press the new one. A key already in use swaps over. Escape cancels a change, then closes. -->
<script lang="ts">
	import { BUTTONS, keyName, PRESETS, rebind, RESERVED_KEYS, type Bindings, type Button } from '#lib/play.ts';
	import { keys, loadKeys, setBindings } from '#lib/keys.svelte.ts';

	let { open = $bindable(false) }: { open?: boolean } = $props();

	const LABELS: Record<Button, string> = { up: 'Up', down: 'Down', left: 'Left', right: 'Right', a: 'A', b: 'B', start: 'Start', select: 'Select' };
	const LAYOUTS: { id: keyof typeof PRESETS; label: string }[] = [
		{ id: 'arrows', label: 'Arrows + Z / X' },
		{ id: 'wasd', label: 'WASD + K / J' }
	];

	let dialog = $state<HTMLDialogElement>();
	let listening = $state<Button | null>(null);
	let note = $state('');

	$effect(() => {
		if (open) void loadKeys();
		if (open && !dialog?.open) dialog?.showModal();
		if (!open && dialog?.open) dialog.close();
	});

	const same = (a: Bindings, b: Bindings) => BUTTONS.every((k) => a[k] === b[k]);
	const layout = $derived(LAYOUTS.find((l) => same(keys.bindings, PRESETS[l.id]))?.id ?? null);

	function usePreset(id: keyof typeof PRESETS) {
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
		listening = null;
		note = swapped
			? `${LABELS[b]} is now ${keyName(bindings[b])}; ${LABELS[swapped]} took ${keyName(bindings[swapped])}.`
			: `${LABELS[b]} is now ${keyName(bindings[b])}.`;
	}
</script>

<dialog
	bind:this={dialog}
	class="panel"
	aria-labelledby="settings-h"
	{onkeydown}
	oncancel={(e) => listening && e.preventDefault()}
	onclose={() => {
		open = false;
		listening = null;
		note = '';
	}}
	onclick={(e) => e.target === dialog && dialog.close()}
>
	<div class="body">
		<header>
			<h2 id="settings-h">Settings</h2>
			<button type="button" class="close" aria-label="Close settings" onclick={() => dialog?.close()}>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</header>

		<section aria-labelledby="keys-h">
			<h3 id="keys-h">Keyboard</h3>
			<div class="layouts" role="group" aria-label="Ready-made layouts">
				{#each LAYOUTS as l (l.id)}
					<button type="button" aria-pressed={layout === l.id} onclick={() => usePreset(l.id)}>{l.label}</button>
				{/each}
			</div>
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
			<p class="note" role="status" aria-live="polite">{keys.error || note || 'Click a key to change it. Saved to your account.'}</p>
		</section>
	</div>
</dialog>

<style>
	dialog {
		width: min(420px, calc(100vw - 32px));
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
	h2 {
		margin: 0;
		font-size: 18px;
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
	.layouts {
		display: grid;
		grid-template-columns: 1fr 1fr;
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
	@media (max-width: 400px) {
		.binds {
			grid-template-columns: 1fr;
		}
	}
	@media (prefers-reduced-motion: no-preference) {
		.key {
			transition: border-color 0.15s;
		}
	}
</style>
