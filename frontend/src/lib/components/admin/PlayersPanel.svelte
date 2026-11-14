<!-- Read-only: everyone who picked a game, from the scoreboard (GET /api/v1/scoreboard, SCOREBOARD_CHANGED). -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import type { ScoreRow } from '#lib/play.ts';
	import { act } from './admin.svelte';

	let rows = $state<ScoreRow[]>([]);

	onMount(async () => {
		rows = (await act('Load players', () => api<ScoreRow[]>('GET', '/scoreboard'), undefined, true)) ?? [];
	});
	$effect(() => on('SCOREBOARD_CHANGED', (r: ScoreRow[]) => (rows = r)));
</script>

<section class="panel" aria-labelledby="players-h">
	<h2 id="players-h" class="pixel">Players</h2>
	<div class="scroll">
		<table>
			<thead><tr><th>User</th><th>Game</th><th>Points</th><th>Online</th></tr></thead>
			<tbody>
				{#each rows as r (r.userId)}
					<tr>
						<td>{r.userId}</td>
						<td><code>{r.game ?? '–'}</code></td>
						<td>{r.points}</td>
						<td>{r.online ? 'Yes' : 'No'}</td>
					</tr>
				{:else}
					<tr><td colspan="4" class="muted">Nobody has picked a game yet.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
</section>
