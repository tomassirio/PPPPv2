<!-- A profile's heading: the player's shown name (nickname) and @username under it. On your own profile a pencil turns
     it into a field (PUT /me/nickname; Enter saves, Esc cancels; empty goes back to your Google name). Others see the
     change at once (NAMES_CHANGED). -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import { nameOf, needPicture, pictures, usernameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	let { user, editable = false, headingId }: { user: string; editable?: boolean; headingId?: string } = $props();
	const MAX = 24;

	$effect(() => {
		needPicture(user);
	});

	let editing = $state(false);
	let value = $state('');
	let saving = $state(false);
	let error = $state('');
	let input = $state<HTMLInputElement>();

	function start() {
		value = nameOf(user);
		error = '';
		editing = true;
		queueMicrotask(() => input?.select());
	}

	async function save() {
		saving = true;
		error = '';
		try {
			const r = await api<{ nickname: string }>('PUT', '/me/nickname', { nickname: value });
			pictures.names = { ...pictures.names, [user]: r.nickname };
			if (session.me) session.me = { ...session.me, nickname: r.nickname };
			editing = false;
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not save your nickname';
		}
		saving = false;
	}
</script>

<div class="nick">
	{#if editing}
		<form
			class="edit"
			onsubmit={(e) => {
				e.preventDefault();
				save();
			}}
		>
			<label class="sr" for="nickname">Nickname</label>
			<input
				id="nickname"
				bind:this={input}
				bind:value
				maxlength={MAX}
				autocomplete="nickname"
				placeholder="Your Google name"
				onkeydown={(e) => {
					if (e.key === 'Escape') editing = false;
				}}
			/>
			<button type="submit" class="btn primary" disabled={saving}>Save</button>
			<button type="button" class="btn" onclick={() => (editing = false)}>Cancel</button>
		</form>
		<span class="muted xs">Up to {MAX} characters. Empty goes back to your Google name.</span>
		{#if error}<span class="error xs" role="alert">{error}</span>{/if}
	{:else}
		<div class="show">
			<h1 id={headingId} class="pixel">{nameOf(user)}</h1>
			{#if editable}
				<button type="button" class="pencil" onclick={start} aria-label="Change your nickname" title="Change your nickname">
					<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
						><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" /></svg
					>
				</button>
			{/if}
		</div>
	{/if}
	<span class="muted small username">{usernameOf(user)}</span>
</div>

<style>
	.nick {
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.show {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}
	h1 {
		margin: 0;
		font-size: 22px;
		overflow-wrap: anywhere;
	}
	.pencil {
		flex: none;
		width: 32px;
		height: 32px;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: 8px;
		border: 1px solid var(--border);
		background: transparent;
		color: var(--muted);
		cursor: pointer;
	}
	.pencil:hover {
		color: var(--text);
		border-color: var(--border-strong);
	}
	.edit {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	input {
		flex: 1;
		min-width: 180px;
		height: 38px;
		padding: 0 12px;
		border-radius: 10px;
		border: 1px solid var(--border-strong);
		background: var(--sunk);
		color: var(--text);
		font: inherit;
		font-size: 16px;
	}
	.btn {
		height: 38px;
		padding: 0 14px;
		border-radius: 10px;
		border: 1px solid var(--border-strong);
		background: var(--panel);
		color: var(--text);
		font: inherit;
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
	}
	.btn.primary {
		background: var(--accent);
		border-color: var(--accent);
		color: var(--ink);
	}
	.btn:disabled {
		opacity: 0.6;
		cursor: wait;
	}
	input:focus-visible,
	.btn:focus-visible,
	.pencil:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.muted {
		color: var(--muted);
	}
	.small {
		font-size: 13px;
	}
	.xs {
		font-size: 12px;
	}
	.error {
		color: var(--danger);
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
</style>
