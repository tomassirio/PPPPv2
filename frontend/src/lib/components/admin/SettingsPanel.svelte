<script lang="ts">
	import { api } from '#lib/api.ts';
	import { act } from './admin.svelte';

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

	<form onsubmit={clearSystem}>
		<h3>Chat</h3>
		<p class="hint">Fresh start: removes all System and Admin messages. Players’ messages stay.</p>
		<button class="danger">Clear system messages</button>
	</form>
</section>
