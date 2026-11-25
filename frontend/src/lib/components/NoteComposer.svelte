<!-- Leave a note on the tile the player stands on (lib/notes.svelte.ts): a phrase and one word from the lists, Dark Souls
     style, previewed as it will read. Opened by holding A in the overworld. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { composer, loadWords, type NoteWords } from '#lib/notes.svelte.ts';

	let dialog = $state<HTMLDialogElement>();
	let downOnBackdrop = false;
	let words = $state<NoteWords | null>(null);
	let phrase = $state(0);
	let category = $state('');
	let word = $state<string | null>(null);
	let search = $state('');
	let error = $state('');
	let saving = $state(false);

	$effect(() => {
		if (composer.open && !dialog?.open) {
			error = '';
			word = null;
			search = '';
			dialog?.showModal();
			loadWords().then(
				(w) => {
					words = w;
					category ||= Object.keys(w.words)[0];
				},
				() => (error = 'Could not load the words')
			);
		}
		if (!composer.open && dialog?.open) dialog.close();
	});

	const list = $derived.by(() => {
		const all = words?.words[category] ?? [];
		const q = search.trim().toLowerCase();
		return q ? all.filter((w) => w.toLowerCase().includes(q)) : all;
	});
	// The phrase around its blank, for the preview: the word lit, or an empty slot.
	const parts = $derived(words ? words.phrases[phrase].split('____') : ['', '']);
	// "POKECENTER_2F" -> "Pokémon Center 2F".
	const place = $derived(
		composer.spot
			? composer.spot.map
					.split('_')
					.map((w) => (/\d/.test(w) ? w : w[0] + w.slice(1).toLowerCase()))
					.join(' ')
					.replace('Pokecenter', 'Pokémon Center')
			: ''
	);
	// A Pokémon word's sprite: its place in the list is its Pokédex number.
	const dex = (w: string) => (category === 'Pokémon' ? (words?.words['Pokémon'].indexOf(w) ?? -1) + 1 : 0);

	async function leave() {
		if (!word) return;
		saving = true;
		error = '';
		try {
			await api('POST', '/notes', { phrase, word });
			dialog?.close();
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not leave the note';
		} finally {
			saving = false;
		}
	}
</script>

<dialog
	bind:this={dialog}
	class="composer"
	aria-labelledby="note-h"
	onclose={() => (composer.open = false)}
	onpointerdown={(e) => (downOnBackdrop = e.target === dialog)}
	onclick={(e) => e.target === dialog && downOnBackdrop && dialog.close()}
>
	<div class="body">
		<header>
			<div>
				<h2 id="note-h" class="pixel">Leave a note</h2>
				<p class="where">{place ? `On this spot in ${place}` : 'On this spot'}: whoever walks here reads it.</p>
			</div>
			<button type="button" class="close" aria-label="Close" onclick={() => dialog?.close()}>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</header>

		<p class="preview" aria-live="polite">
			<span class="sr-only">Your note: </span>“{parts[0]}<span class="blank" class:filled={!!word}>{word ?? '…'}</span>{parts[1] ?? ''}”
		</p>

		{#if words}
			<div class="scroll">
				<section aria-labelledby="phrase-h">
					<h3 id="phrase-h"><span class="step">1</span>Phrase</h3>
					<div class="phrases" role="radiogroup" aria-labelledby="phrase-h">
						{#each words.phrases as p, i (i)}
							<button type="button" role="radio" aria-checked={phrase === i} onclick={() => (phrase = i)}>{p.replace('____', '___')}</button>
						{/each}
					</div>
				</section>

				<section aria-labelledby="word-h">
					<h3 id="word-h"><span class="step">2</span>Word</h3>
					<div class="word-tools">
						<div class="cats" role="tablist" aria-label="Kind of word">
							{#each Object.keys(words.words) as c (c)}
								<button type="button" role="tab" aria-selected={category === c} onclick={() => ((category = c), (search = ''))}>{c}</button>
							{/each}
						</div>
						<input type="search" placeholder="Search" aria-label="Search {category}" bind:value={search} />
					</div>
					<div class="grid" role="radiogroup" aria-labelledby="word-h">
						{#each list as w (w)}
							<button type="button" role="radio" aria-checked={word === w} onclick={() => (word = w)}>
								{#if dex(w)}<img src="/api/v1/game/icons/{dex(w)}.png" alt="" />{/if}
								<span>{w}</span>
							</button>
						{:else}
							<p class="empty">Nothing matches “{search}”.</p>
						{/each}
					</div>
				</section>
			</div>
		{/if}

		<footer>
			{#if error}<p class="error" role="alert">{error}</p>{/if}
			<button type="button" class="leave" disabled={!word || saving} onclick={leave}>{saving ? 'Leaving…' : word ? 'Leave note here' : 'Pick a word'}</button>
		</footer>
	</div>
</dialog>

<style>
	.composer {
		width: min(520px, calc(100vw - 24px));
		max-height: min(760px, calc(100dvh - 24px));
		padding: 0;
		border: 2px solid #ffb347;
		border-radius: 18px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 0 32px rgb(255 157 46 / 0.3);
		overflow: hidden;
	}
	.composer[open] {
		display: flex;
	}
	.composer::backdrop {
		background: rgb(0 0 0 / 0.6);
	}
	.body {
		flex: 1;
		min-height: 0;
		display: flex;
		flex-direction: column;
		width: 100%;
	}
	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 12px;
		padding: 16px 16px 0;
	}
	h2 {
		margin: 0;
		font-size: 13px;
		color: #ffb347;
	}
	.where {
		margin: 6px 0 0;
		font-size: 12px;
		color: var(--muted);
	}
	.close {
		flex: none;
		display: grid;
		place-items: center;
		width: 34px;
		height: 34px;
		border: none;
		border-radius: 50%;
		background: color-mix(in srgb, var(--muted) 14%, transparent);
		color: var(--text);
		cursor: pointer;
	}
	/* The note as it will read on the ground: an ember-lit plaque. */
	.preview {
		margin: 14px 16px 4px;
		padding: 16px 12px;
		border-radius: 12px;
		background: radial-gradient(ellipse at 50% 120%, rgb(255 140 40 / 0.25), transparent 60%), rgb(22 15 6 / 0.94);
		border: 1px solid rgb(255 179 71 / 0.6);
		color: #fff3dc;
		text-align: center;
		font: 12px/1.7 var(--pixel-font);
		text-shadow: 0 0 8px #ff9d2e;
	}
	.blank {
		display: inline-block;
		min-width: 3ch;
		padding: 0 4px;
		border-bottom: 2px dashed rgb(255 179 71 / 0.7);
		color: rgb(255 243 220 / 0.5);
	}
	.blank.filled {
		border-bottom-style: solid;
		color: #ffd27a;
	}
	.scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: 0 16px;
	}
	section {
		margin-top: 14px;
	}
	h3 {
		display: flex;
		align-items: center;
		gap: 8px;
		margin: 0 0 8px;
		font-size: 12px;
		font-weight: 800;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--muted);
	}
	.step {
		display: grid;
		place-items: center;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		background: #ffb347;
		color: #1d1408;
		font-size: 11px;
	}
	button[role='radio'],
	.cats button {
		border: 1.5px solid var(--border);
		background: color-mix(in srgb, var(--panel) 70%, transparent);
		color: var(--text);
		font: 600 13px var(--body-font);
		cursor: pointer;
	}
	button[aria-checked='true'] {
		border-color: #ffb347;
		background: color-mix(in srgb, #ffb347 20%, var(--panel));
		box-shadow: 0 0 0 2px rgb(255 179 71 / 0.35);
	}
	.phrases {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
		gap: 6px;
	}
	.phrases button {
		min-height: 38px;
		padding: 6px 10px;
		border-radius: 10px;
		text-align: left;
	}
	.word-tools {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
		align-items: center;
		margin-bottom: 8px;
	}
	.cats {
		display: inline-flex;
		padding: 3px;
		gap: 2px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--muted) 14%, transparent);
	}
	.cats button {
		height: 30px;
		padding: 0 12px;
		border: none;
		border-radius: 999px;
		background: transparent;
		color: var(--muted);
		font-weight: 700;
	}
	.cats button[aria-selected='true'] {
		background: #ffb347;
		color: #1d1408;
	}
	input[type='search'] {
		flex: 1 1 140px;
		min-width: 0;
		height: 36px;
		padding: 0 12px;
		border: 1px solid var(--border);
		border-radius: 999px;
		background: transparent;
		color: var(--text);
		font: 14px var(--body-font);
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(112px, 1fr));
		gap: 6px;
		padding-bottom: 12px;
	}
	.grid button {
		display: flex;
		align-items: center;
		gap: 6px;
		min-height: 40px;
		padding: 4px 8px;
		border-radius: 10px;
		text-align: left;
		line-height: 1.2;
	}
	.grid span {
		min-width: 0;
		overflow-wrap: anywhere;
	}
	.grid img {
		flex: none;
		width: 28px;
		height: 28px;
		margin: -2px 0 -2px -4px;
		image-rendering: pixelated;
	}
	.empty {
		grid-column: 1 / -1;
		margin: 8px 0;
		font-size: 13px;
		color: var(--muted);
	}
	footer {
		padding: 12px 16px 16px;
		border-top: 1px solid var(--border);
	}
	.error {
		margin: 0 0 8px;
		color: #ff7a7a;
		font-size: 13px;
	}
	.leave {
		width: 100%;
		min-height: 46px;
		border: none;
		border-radius: 12px;
		background: #ffb347;
		color: #1d1408;
		font: 800 15px var(--body-font);
		cursor: pointer;
	}
	.leave:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
	button:focus-visible,
	input:focus-visible {
		outline: 2px solid #ffb347;
		outline-offset: 2px;
	}
</style>
