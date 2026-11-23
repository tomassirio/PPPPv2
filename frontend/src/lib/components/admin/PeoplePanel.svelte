<!-- Everyone in one table: the allowlist (GET /admin/users: who may sign in, their role, mute and ban) joined with
     the scoreboard (GET /scoreboard, SCOREBOARD_CHANGED: their game, points, online). Bots are scoreboard players
     with no allowlist entry (seeded test runs): they cannot sign in, so they have no role, mute, ban or remove.
     Filter All / People / Bots; Manage opens the player's admin page; Reset wipes a run. Emails show in full. -->
<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import { on } from '#lib/events.ts';
	import type { Cartridge } from '#lib/cartridges.ts';
	import type { ScoreRow } from '#lib/play.ts';
	import { nameOf, needPicture } from '#lib/pictures.svelte.ts';
	import { act } from './admin.svelte';
	import Pager from './Pager.svelte';

	type Role = 'PLAYER' | 'ADMIN';
	type User = { id: string; role: Role; banned: boolean; mutedUntil: number | null };
	type Person = { id: string; user: User | null; row: ScoreRow | null };
	type Filter = 'all' | 'people' | 'bots';

	let users = $state<User[]>([]);
	let rows = $state<ScoreRow[]>([]);
	let names = $state<Record<string, string>>({});
	let email = $state('');
	let role = $state<Role>('PLAYER');
	let muteMinutes = $state(10);
	let filter = $state<Filter>('all');
	const PER_PAGE = 15;
	let page = $state(0);

	// People first (admins, then by points), then bots: online ones first, then by points.
	const everyone = $derived.by(() => {
		const byId = new Map<string, Person>();
		for (const u of users) byId.set(u.id, { id: u.id, user: u, row: null });
		for (const r of rows) byId.set(r.userId, { id: r.userId, user: byId.get(r.userId)?.user ?? null, row: r });
		const pts = (p: Person) => p.row?.points ?? -1;
		return [...byId.values()].sort(
			(a, b) =>
				Number(!a.user) - Number(!b.user) ||
				Number(b.user?.role === 'ADMIN') - Number(a.user?.role === 'ADMIN') ||
				(!a.user ? Number(!!b.row?.online) - Number(!!a.row?.online) : 0) ||
				pts(b) - pts(a)
		);
	});
	const counts = $derived({ all: everyone.length, people: everyone.filter((p) => p.user).length, bots: everyone.filter((p) => !p.user).length });
	const listed = $derived(filter === 'all' ? everyone : everyone.filter((p) => (filter === 'bots' ? !p.user : !!p.user)));
	const shown = $derived(listed.slice(page * PER_PAGE, (page + 1) * PER_PAGE));
	$effect(() => shown.forEach((p) => needPicture(p.id))); // their nicknames

	async function loadUsers() {
		const list = await act('Load users', () => api<User[]>('GET', '/admin/users'), undefined, true);
		if (list) users = list;
	}
	onMount(async () => {
		loadUsers();
		rows = (await act('Load players', () => api<ScoreRow[]>('GET', '/scoreboard'), undefined, true)) ?? [];
		const carts = await act('Load cartridges', () => api<Cartridge[]>('GET', '/admin/cartridges'), undefined, true);
		names = Object.fromEntries((carts ?? []).map((c) => [c.id, c.name]));
	});
	// Someone let in from Access requests joins the list; scores and who is online stay live.
	$effect(() => on('ACCESS_REQUESTS', loadUsers));
	$effect(() => on('SCOREBOARD_CHANGED', (r: ScoreRow[]) => (rows = r)));

	const enc = encodeURIComponent;
	const until = (ms: number) => new Date(ms).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' });

	// Runs an action that returns no body, then reloads the allowlist.
	async function change(label: string, fn: () => Promise<unknown>, confirmText?: string) {
		if (await act(label, () => fn().then(() => true), confirmText)) await loadUsers();
	}
	async function add(e: SubmitEvent) {
		e.preventDefault();
		await change(`Add ${email}`, () => api('POST', '/admin/allowlist', { email, role }));
		email = '';
	}
	async function reset(id: string) {
		await act(
			`Reset ${id}`,
			() => api('DELETE', `/admin/players/${enc(id)}`),
			`Reset ${id}? This deletes their game choice, save, points and activity. They pick a game again. It cannot be undone.`
		);
	}
	function pick(f: Filter) {
		filter = f;
		page = 0;
	}
</script>

<section class="panel" aria-labelledby="people-h">
	<div class="people-head">
		<h2 id="people-h" class="pixel">People</h2>
		<div class="filters" role="group" aria-label="Show">
			{#each [['all', 'All'], ['people', 'People'], ['bots', 'Bots']] as const as [f, label] (f)}
				<button type="button" aria-pressed={filter === f} onclick={() => pick(f)}>{label} <span class="n">{counts[f]}</span></button>
			{/each}
		</div>
	</div>

	<div class="people-tools">
		<form class="add" onsubmit={add}>
			<input type="email" required bind:value={email} autocomplete="off" placeholder="Email to add" aria-label="Email to add" />
			<select bind:value={role} aria-label="Role for new person">
				<option value="PLAYER">Player</option>
				<option value="ADMIN">Admin</option>
			</select>
			<button class="primary">Add</button>
		</form>
		<label class="mute-for">
			<span class="muted">Mute for</span>
			<input type="number" min="1" max="525600" bind:value={muteMinutes} aria-label="Mute duration in minutes" />
			<span class="muted">min</span>
		</label>
	</div>

	<div class="scroll">
		<table class="people-table">
			<thead><tr><th>Person</th><th>Role</th><th>Game</th><th class="num">Points</th><th>Online</th><th></th></tr></thead>
			<tbody>
				{#each shown as p (p.id)}
					<tr class:bot={!p.user}>
						<td class="who">
							<b>{nameOf(p.id)}</b>
							<span class="email">{p.id}</span>
						</td>
						<td class="role-cell">
							{#if p.user}
								<select
									aria-label={`Role of ${p.id}`}
									value={p.user.role}
									onchange={(e) => change(`Set role of ${p.id}`, () => api('PUT', `/admin/allowlist/${enc(p.id)}`, { role: e.currentTarget.value }))}
								>
									<option value="PLAYER">Player</option>
									<option value="ADMIN">Admin</option>
								</select>
								{#if p.user.banned}<span class="tag danger-tag">Banned</span>{/if}
								{#if p.user.mutedUntil}<span class="tag" title="Muted until {until(p.user.mutedUntil)}">Muted</span>{/if}
							{:else}
								<span class="tag bot-tag" title="On the scoreboard but not on the allowlist: cannot sign in">
									<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"
										><rect x="5" y="8" width="14" height="11" rx="2" /><path d="M12 4v4M9 13h.01M15 13h.01M3 13v2M21 13v2" /></svg
									>Bot</span
								>
							{/if}
						</td>
						<td class="game-cell">{p.row?.game ? (names[p.row.game] ?? p.row.game) : '–'}</td>
						<td class="num pts-cell">{p.row ? p.row.points.toLocaleString() : '–'}</td>
						<td class="online-cell" class:yes={p.row?.online}>{p.row?.online ? 'Yes' : 'No'}</td>
						<td class="actions">
							<a class="manage" href="/admin/players/{enc(p.id)}">Manage</a>
							{#if p.user}
								{#if p.user.mutedUntil}
									<button onclick={() => change(`Unmute ${p.id}`, () => api('DELETE', `/admin/users/${enc(p.id)}/mute`))}>Unmute</button>
								{:else}
									<button onclick={() => change(`Mute ${p.id}`, () => api('POST', `/admin/users/${enc(p.id)}/mute`, { minutes: muteMinutes }))}>Mute</button>
								{/if}
								{#if p.user.banned}
									<button onclick={() => change(`Unban ${p.id}`, () => api('DELETE', `/admin/users/${enc(p.id)}/ban`))}>Unban</button>
								{:else}
									<button class="danger" onclick={() => change(`Ban ${p.id}`, () => api('POST', `/admin/users/${enc(p.id)}/ban`), `Ban ${p.id}?`)}>Ban</button>
								{/if}
							{/if}
							{#if p.row}<button class="danger" onclick={() => reset(p.id)}>Reset</button>{/if}
							{#if p.user}
								{#if p.user.role === 'ADMIN'}
									<button class="danger" disabled title="Demote to player first" aria-label="Remove {p.id} (demote to player first)">Remove</button>
								{:else}
									<button
										class="danger"
										onclick={() =>
											change(`Remove ${p.id}`, () => api('DELETE', `/admin/allowlist/${enc(p.id)}`), `Remove ${p.id}? They lose access, and their game choice, save, points and activity are deleted. It cannot be undone.`)}
										>Remove</button
									>
								{/if}
							{/if}
						</td>
					</tr>
				{:else}
					<tr><td colspan="6" class="muted">{filter === 'bots' ? 'No bots.' : 'Nobody yet.'}</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
	<Pager bind:page total={listed.length} perPage={PER_PAGE} label="People pages" noun="people" />
</section>

<style>
	.people-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
	}
	.filters {
		display: flex;
		gap: 4px;
	}
	.filters button {
		height: 30px;
		padding: 0 12px;
		border-radius: 999px;
		border: 1px solid var(--border);
		background: none;
		color: var(--muted);
		font-weight: 600;
	}
	.filters button[aria-pressed='true'] {
		border-color: var(--accent-text);
		color: var(--accent-text);
		background: var(--accent-tint);
	}
	.n {
		font-weight: 400;
		opacity: 0.8;
	}
	.people-tools {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
	}
	.add {
		display: flex;
		gap: 8px;
		flex: 1 1 360px;
	}
	.add input {
		flex: 1;
		min-width: 0;
	}
	.mute-for {
		display: flex;
		align-items: center;
		gap: 6px;
	}
	.mute-for input {
		width: 80px;
	}
	.who {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 220px;
	}
	/* Full addresses, wrapping at any character if they must. */
	.email {
		font-size: 12px;
		color: var(--muted);
		overflow-wrap: anywhere;
	}
	.num {
		text-align: right;
	}
	.actions {
		display: flex;
		flex-wrap: wrap;
		justify-content: flex-end;
		gap: 6px;
	}
	.actions button {
		height: 32px;
		padding: 0 10px;
	}
	.tag {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		margin-left: 6px;
		padding: 2px 8px;
		border-radius: 999px;
		border: 1px solid var(--border-strong);
		font-size: 11px;
		font-weight: 700;
		color: var(--muted);
		white-space: nowrap;
	}
	.danger-tag {
		border-color: var(--danger);
		color: var(--danger);
	}
	.bot-tag {
		margin-left: 0;
		border-style: dashed;
	}
	tr.bot .who b {
		color: var(--muted);
	}
	/* Phones: each person a small card: name and e-mail with their role on the right; game, points and online on one
	   line; then the actions sharing the width. Adding someone and "Mute for" stack above the list. */
	@media (max-width: 760px) {
		.people-tools {
			flex-direction: column;
			align-items: stretch;
		}
		.add {
			flex: none;
		}
		.mute-for {
			flex-direction: row;
			align-items: center;
		}
		.people-table tr {
			display: grid;
			grid-template-columns: minmax(0, 1fr) auto auto;
			grid-template-areas:
				'who who role'
				'game pts online'
				'actions actions actions';
			gap: 8px 12px;
			align-items: center;
			padding: 12px 0;
		}
		.people-table tr:has(td[colspan]) {
			display: block;
		}
		.who {
			grid-area: who;
			min-width: 0;
		}
		.role-cell {
			grid-area: role;
			justify-self: end;
			display: flex !important;
			flex-wrap: wrap;
			justify-content: flex-end;
			gap: 4px;
		}
		.game-cell {
			grid-area: game;
			font-size: 13px;
			color: var(--muted);
			white-space: nowrap;
			overflow: hidden;
			text-overflow: ellipsis;
		}
		.pts-cell {
			grid-area: pts;
			font-size: 13px;
		}
		.pts-cell::after {
			content: ' pts';
			color: var(--muted);
		}
		.online-cell {
			grid-area: online;
			font-size: 0; /* "Yes"/"No" become a dot and Online / Offline */
		}
		.online-cell::before {
			content: '● Offline';
			font-size: 12px;
			color: var(--muted);
		}
		.online-cell.yes::before {
			content: '● Online';
			color: var(--grass, #3fae4a);
		}
		/* Equal buttons, as many per line as fit (a second line rather than a clipped label). */
		.actions {
			grid-area: actions;
			margin-left: 0 !important;
			display: grid !important;
			grid-template-columns: repeat(auto-fit, minmax(76px, 1fr));
			gap: 6px;
		}
		.actions > :global(*) {
			justify-content: center;
		}
	}
</style>
