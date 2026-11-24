<!-- The controller colour picker: a swatch opening a grid of shells. up: the grid opens upward (landscape rail). The
     grid is a popover (the top layer), so the controller's rounded, clipped box cannot cut it off; it is placed next
     to the swatch when it opens, kept inside the window. -->
<script lang="ts">
	import { SHELLS, variant } from '#lib/shells.ts';
	import { currentShell, pickShell, setAtomic } from '#lib/shell.svelte.ts';

	let { up = false }: { up?: boolean } = $props();
	const shell = $derived(currentShell.value);
	let button = $state<HTMLButtonElement>();
	let panel = $state<HTMLElement>();
	let open = $state(false);
	const uid = $props.id(); // two pickers on the page (pad corner, landscape rail): separate radio groups

	// Beside the swatch: below it and right-aligned to it (up: above it, left-aligned), inside the window. Lengths
	// are divided by the page's zoom (the big-screen layout zooms /play), which also applies to the popover.
	function place() {
		if (!button || !panel) return;
		const zoom = (button as HTMLElement & { currentCSSZoom?: number }).currentCSSZoom ?? 1;
		const r = button.getBoundingClientRect(), w = panel.offsetWidth * zoom, h = panel.offsetHeight * zoom, m = 8;
		let left = up ? r.left : r.right - w;
		let top = up ? r.top - h - 8 : r.bottom + 8;
		left = Math.max(m, Math.min(left, innerWidth - w - m));
		top = Math.max(m, Math.min(top, innerHeight - h - m));
		panel.style.left = `${left / zoom}px`;
		panel.style.top = `${top / zoom}px`;
	}
</script>

<svelte:window onresize={() => open && place()} />

<span class="shells">
	<button
		type="button"
		class="trigger"
		bind:this={button}
		popovertarget="shells-{uid}"
		aria-expanded={open}
		aria-label="Controller colour: {shell.name}"
		title="Controller colour: {shell.name}"
	>
		<span class="swatch" style:background={shell.shell}></span>
	</button>
	<div
		id="shells-{uid}"
		class="panel"
		popover="auto"
		bind:this={panel}
		ontoggle={(e) => {
			open = (e as ToggleEvent).newState === 'open';
			if (open) place();
		}}
	>
		<fieldset>
			<legend>Controller colour</legend>
			<!-- Solid or Atomic (see-through) plastic, for the colours that come in both. -->
			<div class="plastic" role="group" aria-label="Plastic">
				<button type="button" aria-pressed={!currentShell.atomic} onclick={() => setAtomic(false)}>Solid</button>
				<button type="button" aria-pressed={currentShell.atomic} onclick={() => setAtomic(true)}>Atomic</button>
			</div>
			<div class="grid">
				{#each SHELLS.map((b) => variant(b, currentShell.atomic)) as s (s.id)}
					<label title={s.name}>
						<input
							type="radio"
							name="shell-{uid}"
							value={s.id}
							checked={s.id === shell.id}
							onchange={() => {
								pickShell(s.id);
								panel?.hidePopover();
							}}
						/>
						<span class="swatch" style:background={s.shell}><span class="dot" style:background={s.ab}></span></span>
						<span class="sr-only">{s.name}</span>
					</label>
				{/each}
			</div>
			<p class="current">{shell.name}</p>
		</fieldset>
	</div>
</span>

<style>
	.shells {
		position: relative;
		display: inline-block;
	}
	.trigger {
		display: block;
		padding: 0;
		border: none;
		background: none;
		cursor: pointer;
		border-radius: 50%;
	}
	.trigger:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.swatch {
		display: block;
		position: relative;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		box-shadow:
			inset 0 0 0 1px rgb(0 0 0 / 0.25),
			0 0 0 2px rgb(255 255 255 / 0.6);
	}
	.dot {
		position: absolute;
		right: 3px;
		bottom: 3px;
		width: 7px;
		height: 7px;
		border-radius: 50%;
	}
	.panel {
		position: fixed;
		inset: auto;
		margin: 0;
		padding: 12px;
		border: 1px solid var(--border);
		border-radius: 10px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 8px 24px rgb(0 0 0 / 0.35);
	}
	fieldset {
		margin: 0;
		padding: 0;
		border: none;
	}
	legend {
		float: left;
		width: 100%;
		margin-bottom: 8px;
		font-size: 11px;
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.plastic {
		clear: both;
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 2px;
		margin-bottom: 10px;
		padding: 2px;
		border-radius: 8px;
		background: rgb(127 127 127 / 0.15);
	}
	.plastic button {
		min-height: 28px;
		border: none;
		border-radius: 6px;
		background: transparent;
		color: var(--text);
		font: 600 12px var(--body-font);
		cursor: pointer;
	}
	.plastic button[aria-pressed='true'] {
		background: var(--accent);
		color: #1d1f1c;
	}
	.plastic button:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 1px;
	}
	.grid {
		clear: both;
		display: grid;
		grid-template-columns: repeat(5, 30px);
		gap: 8px;
	}
	.grid label {
		position: relative;
		cursor: pointer;
	}
	.grid input {
		position: absolute;
		opacity: 0;
		inset: 0;
		margin: 0;
	}
	.grid .swatch {
		width: 30px;
		height: 30px;
	}
	.grid input:checked + .swatch {
		box-shadow:
			inset 0 0 0 1px rgb(0 0 0 / 0.25),
			0 0 0 3px var(--accent-text);
	}
	.grid input:focus-visible + .swatch {
		outline: 3px solid var(--accent-text);
		outline-offset: 3px;
	}
	.current {
		margin: 10px 0 0;
		font-size: 12px;
		color: var(--text);
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
</style>
