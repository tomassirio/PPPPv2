<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';

	// Ranges mirror backend Settings.Input validation.
	type Input = { holdFrames: number; releaseFrames: number };

	let input = $state<Input | null>(null);

	onMount(async () => {
		input = (await act('Load input settings', () => api<Input>('GET', '/admin/settings/input'), undefined, true)) ?? null;
	});

	function saveInput(e: SubmitEvent) {
		e.preventDefault();
		act('Save input settings', () => api('PUT', '/admin/settings/input', input));
	}

	function clearSystem(e: SubmitEvent) {
		e.preventDefault();
		act(
			'Clear system messages',
			() => api('DELETE', '/admin/chat/system-messages'),
			'Remove every System and Admin message from chat for everyone? Players’ own messages stay. This cannot be undone.'
		);
	}
</script>

<section class="panel" aria-labelledby="settings-h">
	<h2 id="settings-h" class="pixel">Settings</h2>

	{#if input}
		<form onsubmit={saveInput}>
			<h3>Input</h3>
			<div class="grid">
				<label>
					Hold frames
					<input type="number" min="1" max="60" required bind:value={input.holdFrames} />
				</label>
				<label>
					Release frames
					<input type="number" min="1" max="60" required bind:value={input.releaseFrames} />
				</label>
			</div>
			<button class="primary">Save input</button>
		</form>
	{/if}

	<form onsubmit={clearSystem}>
		<h3>Chat</h3>
		<p class="hint">Fresh start: removes all System and Admin messages. Players’ messages stay.</p>
		<button class="danger">Clear system messages</button>
	</form>
</section>
