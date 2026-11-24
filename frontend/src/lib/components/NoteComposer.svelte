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
	const preview = $derived(words ? words.phrases[phrase].replace('____', word ?? '____') : '');

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
			<h2 id="note-h" class="pixel">Leave a note</h2>
			<button type="button" class="close" aria-label="Close" onclick={() => dialog?.close()}>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
					><path d="M6 6l12 12M18 6L6 18" /></svg
				>
			</button>
		</header>
		<p class="muted small">Others who walk here will read it{composer.spot ? ` (${composer.spot.map.replaceAll('_', ' ').toLowerCase()})` : ''}.</p>

		<p class="preview" aria-live="polite">“{preview}”</p>

		{#if words}
			<fieldset class="phrases">
				<legend>Phrase</legend>
				{#each words.phrases as p, i (i)}
					<button type="button" aria-pressed={phrase === i} onclick={() => (phrase = i)}>{p.replace('____', '___')}</button>
				{/each}
			</fieldset>

			<fieldset class="words">
				<legend>Word</legend>
				<div class="cats" role="group" aria-label="Kind of word">
					{#each Object.keys(words.words) as c (c)}
						<button type="button" aria-pressed={category === c} onclick={() => ((category = c), (search = ''))}>{c}</button>
					{/each}
				</div>
				<input type="search" placeholder="Search {category}" aria-label="Search {category}" bind:value={search} />
				<div class="grid">
					{#each list as w (w)}
						<button type="button" aria-pressed={word === w} onclick={() => (word = w)}>{w}</button>
					{:else}
						<p class="muted small">Nothing matches.</p>
					{/each}
				</div>
			</fieldset>
		{/if}

		{#if error}<p class="error" role="alert">{error}</p>{/if}
		<button type="button" class="leave" disabled={!word || saving} onclick={leave}>{saving ? 'Leaving…' : 'Leave note here'}</button>
	</div>
</dialog>

<style>
	.composer {
		width: min(480px, calc(100vw - 24px));
		max-height: min(720px, calc(100dvh - 24px));
		padding: 0;
		border: 2px solid #ffb347;
		border-radius: 16px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 0 30px rgb(255 157 46 / 0.35);
	}
	.composer::backdrop {
		background: rgb(0 0 0 / 0.6);
	}
	.body {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 16px;
	}
	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	h2 {
		margin: 0;
		font-size: 13px;
		color: #ffb347;
	}
	.close {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border: none;
		border-radius: 50%;
		background: transparent;
		color: var(--text);
		cursor: pointer;
	}
	.small {
		margin: 0;
		font-size: 12px;
	}
	.preview {
		margin: 0;
		padding: 14px 12px;
		border-radius: 10px;
		background: rgb(20 14 6 / 0.9);
		border: 1px solid #ffb347;
		color: #fff3dc;
		text-align: center;
		font: 12px/1.6 var(--pixel-font);
		text-shadow: 0 0 8px #ff9d2e;
	}
	fieldset {
		margin: 0;
		padding: 0;
		border: none;
		min-width: 0;
	}
	legend {
		margin-bottom: 6px;
		font-size: 12px;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		opacity: 0.7;
	}
	.phrases,
	.cats,
	.grid {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}
	.grid {
		max-height: 168px;
		overflow-y: auto;
		margin-top: 8px;
	}
	.cats {
		margin-bottom: 8px;
	}
	fieldset button,
	.cats button {
		min-height: 32px;
		padding: 0 10px;
		border: 1px solid var(--border);
		border-radius: 999px;
		background: transparent;
		color: var(--text);
		font: 600 13px var(--body-font);
		cursor: pointer;
	}
	button[aria-pressed='true'] {
		border-color: #ffb347;
		background: #ffb347;
		color: #1d1408;
	}
	input[type='search'] {
		width: 100%;
		box-sizing: border-box;
		min-height: 36px;
		padding: 0 10px;
		border: 1px solid var(--border);
		border-radius: 8px;
		background: transparent;
		color: var(--text);
		font: 14px var(--body-font);
	}
	.error {
		margin: 0;
		color: #ff7a7a;
		font-size: 13px;
	}
	.leave {
		min-height: 44px;
		border: none;
		border-radius: 12px;
		background: #ffb347;
		color: #1d1408;
		font: 700 15px var(--body-font);
		cursor: pointer;
	}
	.leave:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}
	button:focus-visible,
	input:focus-visible {
		outline: 2px solid #ffb347;
		outline-offset: 2px;
	}
</style>
