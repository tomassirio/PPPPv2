<!-- Everyone who picked a game, from the scoreboard (GET /api/v1/scoreboard, SCOREBOARD_CHANGED); Reset wipes a run. -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import type { Cartridge } from '#lib/cartridges.ts';
	import type { ScoreRow } from '#lib/play.ts';
	import { act } from './admin.svelte';

	let rows = $state<ScoreRow[]>([]);
	let names = $state<Record<string, string>>({});

	onMount(async () => {
		rows = (await act('Load players', () => api<ScoreRow[]>('GET', '/scoreboard'), undefined, true)) ?? [];
		const carts = await act('Load cartridges', () => api<Cartridge[]>('GET', '/admin/cartridges'), undefined, true);
		names = Object.fromEntries((carts ?? []).map((c) => [c.id, c.name]));
	});

	async function reset(userId: string) {
		await act(
			`Reset ${userId}`,
			() => api('DELETE', `/admin/players/${encodeURIComponent(userId)}`),
			`Reset ${userId}? This deletes their game choice, save, points and activity. They pick a game again. It cannot be undone.`
		);
	}
	$effect(() => on('SCOREBOARD_CHANGED', (r: ScoreRow[]) => (rows = r)));
</script>

<section class="panel" aria-labelledby="players-h">
	<h2 id="players-h" class="pixel">Players</h2>
	<div class="scroll">
		<table>
			<thead><tr><th>User</th><th>Game</th><th>Points</th><th>Online</th><th></th></tr></thead>
			<tbody>
				{#each rows as r (r.userId)}
					<tr>
						<td>{r.userId}</td>
						<td>{r.game ? (names[r.game] ?? r.game) : '–'}</td>
						<td>{r.points}</td>
						<td>{r.online ? 'Yes' : 'No'}</td>
						<td class="row"><button class="danger" onclick={() => reset(r.userId)}>Reset</button></td>
					</tr>
				{:else}
					<tr><td colspan="5" class="muted">Nobody has picked a game yet.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
</section>
