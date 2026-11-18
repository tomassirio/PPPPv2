<!-- The trainer the player walks as in their game (GET/PUT /me/sprite): the current one walking in place, and "Change"
     opens every walking trainer their game has, each drawn from their own ROM (/me/sprites/{key}.png, a 16x96 sheet:
     down, up, left, then each walking). Picking one rebuilds their Game Boy from where it is (the backend refuses
     while a link cable is in); the game draws the new trainer from the next map it loads. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';

	type Choice = { key: string; name: string; id: number };
	type State = { current: string; choices: Choice[] };

	let picked = $state<State | null>(null);
	let open = $state(false);
	let busy = $state('');
	let message = $state('');
	let error = $state('');

	$effect(() => {
		api<State>('GET', '/me/sprite')
			.then((s) => (picked = s))
			.catch(() => {});
	});

	const current = $derived(picked?.choices.find((c) => c.key === picked?.current));
	const sheet = (key: string) => `/api/v1/me/sprites/${encodeURIComponent(key)}.png`;

	async function pick(c: Choice) {
		if (!picked || c.key === picked.current) return;
		busy = c.key;
		error = '';
		message = '';
		try {
			picked = await api<State>('PUT', '/me/sprite', { key: c.key });
			message = `You walk as ${c.name} from the next door you go through.`;
			open = false;
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'Could not change your trainer';
		}
		busy = '';
	}
</script>

{#if picked?.choices.length}
	<section class="card trainer" aria-labelledby="trainer-h">
		<div class="between">
			<h2 id="trainer-h">Your trainer</h2>
			<button type="button" class="change" aria-expanded={open} onclick={() => (open = !open)}>{open ? 'Close' : 'Change'}</button>
		</div>
		<div class="now">
			{#if current}<span class="walk big" style:background-image="url({sheet(current.key)})" role="img" aria-label={current.name}></span>{/if}
			<div>
				<b>{current?.name ?? picked.current}</b>
				<p class="muted">Who you walk around as in your game. Others see it too, on their screen and on the town map.</p>
			</div>
		</div>
		{#if message}<p class="ok" role="status">{message}</p>{/if}
		{#if error}<p class="error" role="alert">{error}</p>{/if}
		{#if open}
			<ul class="choices" aria-label="Trainers">
				{#each picked.choices as c (c.key)}
					<li>
						<button type="button" class:on={c.key === picked.current} aria-pressed={c.key === picked.current} disabled={!!busy} onclick={() => pick(c)} title={c.name}>
							<span class="walk" style:background-image="url({sheet(c.key)})" aria-hidden="true"></span>
							<span class="name">{c.name}</span>
						</button>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
{/if}

<style>
	.card {
		background: var(--panel);
		border: 1px solid var(--border);
		border-radius: 12px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		min-width: 0;
	}
	.between {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}
	h2 {
		margin: 0;
	}
	.change {
		height: 32px;
		padding: 0 12px;
		border-radius: 8px;
		border: 1px solid var(--border-strong);
		background: var(--panel);
		color: var(--text);
		font: inherit;
		font-size: 13px;
		font-weight: 600;
		cursor: pointer;
	}
	.change:hover {
		border-color: var(--accent-text);
	}
	.now {
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.now p {
		margin: 2px 0 0;
		font-size: 13px;
	}
	.muted {
		color: var(--muted);
	}
	/* One 16x16 frame of the 16x96 sheet, scaled with hard pixels; walking down in place. */
	.walk {
		--s: 32px;
		display: block;
		flex: none;
		width: var(--s);
		height: var(--s);
		background-size: var(--s) calc(var(--s) * 6);
		background-repeat: no-repeat;
		background-position: 0 0;
		image-rendering: pixelated;
	}
	.walk.big {
		--s: 48px;
		animation: walk 0.6s steps(1) infinite;
	}
	button:hover .walk,
	button.on .walk {
		animation: walk 0.6s steps(1) infinite;
	}
	@keyframes walk {
		0% {
			background-position: 0 0;
		}
		50% {
			background-position: 0 calc(var(--s) * -3);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.walk.big,
		button:hover .walk,
		button.on .walk {
			animation: none;
		}
	}
	.choices {
		list-style: none;
		margin: 4px 0 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(84px, 1fr));
		gap: 6px;
	}
	.choices button {
		width: 100%;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
		padding: 8px 4px;
		border-radius: 10px;
		border: 1px solid var(--border);
		background: var(--sunk);
		color: var(--text);
		font: inherit;
		cursor: pointer;
	}
	.choices button:hover {
		border-color: var(--border-strong);
	}
	.choices button.on {
		border-color: var(--accent-text);
		background: var(--accent-tint);
	}
	.choices button:disabled {
		cursor: wait;
	}
	.choices button:focus-visible,
	.change:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.name {
		font-size: 11px;
		text-align: center;
		line-height: 1.2;
	}
	.ok {
		margin: 0;
		font-size: 13px;
		color: var(--hp-ok);
	}
	.error {
		margin: 0;
		font-size: 13px;
		color: var(--danger);
	}
</style>
