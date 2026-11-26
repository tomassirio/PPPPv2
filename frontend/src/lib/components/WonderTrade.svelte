<!-- Wonder Trade (backend WonderTrade), once a day, once the Cable Club is open: pick one of your party, confirm you are
     letting it go (the warning: whatever comes back is a surprise, and there is no undo), and send it. If another Pib's
     Pokémon waits in the pool, you get it at once (the reveal) and yours goes to them; else yours waits for the next Pib
     and theirs comes to you then. Steps: pick, confirm, sending (a Poké Ball across), result; or the locked / done-today
     cards. Party from GET /players/{me}/profile; the pool from lib/wonder.svelte.ts; POST /wonder-trade {slot, species}.
     Nothing changes until the result: a 409 says why. -->
<script lang="ts">
	import { api, ApiError } from '#lib/api.ts';
	import Name from '#lib/components/Name.svelte';
	import { session } from '#lib/session.svelte.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import type { ProfileMon } from '#lib/play.ts';
	import { loadPool, untilTomorrow, wonder } from '#lib/wonder.svelte.ts';

	type Mon = { species: number; name: string; nickname: string | null; ot: string; level: number; shiny: boolean; evolvedFrom: number | null };
	type Traded = { gave: Mon; got: Mon | null; from: string | null };

	let { open = $bindable(false), locked = false }: { open?: boolean; locked?: boolean } = $props();
	let dialog = $state<HTMLDialogElement>();
	let party = $state<ProfileMon[] | null>(null);
	let picked = $state<number | null>(null);
	let step = $state<'pick' | 'confirm' | 'sending' | 'done'>('pick');
	let result = $state<Traded | null>(null);
	let error = $state<string | null>(null);
	const still = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	function load() {
		const me = session.me?.id;
		if (!me) return;
		api<{ party: ProfileMon[] }>('GET', `/players/${encodeURIComponent(me)}/profile`).then((p) => (party = p.party), () => (party = []));
		loadPool();
	}
	$effect(() => {
		if (open && !dialog?.open) {
			step = 'pick';
			picked = null;
			result = null;
			error = null;
			load();
			dialog?.showModal();
			dialog?.focus();
		}
		if (!open && dialog?.open) dialog.close();
	});

	const pool = $derived(wonder.pool);
	const fighters = $derived((party ?? []).filter((m) => !m.egg && (m.hp ?? 1) > 0).length);
	const blocked = (m: ProfileMon) => (m.egg ? 'Eggs stay home' : fighters <= 1 && (m.hp ?? 1) > 0 ? 'Your last fighter' : null);
	const label = (m: { name: string; nickname: string | null }) => m.nickname ?? m.name;
	const icon = (species: number) => `/api/v1/game/icons/${species}.png`;
	const chosen = $derived(picked != null && party ? party[picked] : null);

	async function send() {
		if (picked == null || !chosen) return;
		step = 'sending';
		error = null;
		const minimum = new Promise((r) => setTimeout(r, still ? 0 : 2200)); // long enough to watch the ball go
		try {
			const [r] = await Promise.all([api<Traded>('POST', '/wonder-trade', { slot: picked, species: chosen.species }), minimum]);
			result = r;
			step = 'done';
			navigator.vibrate?.([20, 40, 60]);
		} catch (e) {
			error = e instanceof ApiError ? e.detail : 'The trade didn’t go through.';
			step = 'pick';
			load();
		}
	}
</script>

<dialog
	bind:this={dialog}
	tabindex="-1"
	aria-labelledby="wt-h"
	onclose={() => (open = false)}
	onclick={(e) => e.target === dialog && step !== 'sending' && dialog?.close()}
>
	<header class="banner">
		<span class="orb" aria-hidden="true"><span class="spark s1">✦</span><span class="spark s2">✦</span></span>
		<div>
			<h2 id="wt-h" class="pixel">Wonder Trade</h2>
			<p>One Pokémon a day, swapped sight unseen with another Pib.</p>
		</div>
		<button type="button" class="close" aria-label="Close" disabled={step === 'sending'} onclick={() => dialog?.close()}>
			<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
				><path d="M6 6l12 12M18 6L6 18" /></svg
			>
		</button>
	</header>

	<div class="body">
		{#if pool && !locked && step !== 'sending' && step !== 'done'}
			<div class="status" aria-label="The pool">
				<div class="stat">
					<b>{pool.waiting}</b>
					<span>waiting from other Pibs</span>
				</div>
				{#if pool.mine.length}
					<div class="stat mine">
						<span class="minis">{#each pool.mine as w (w.since)}<img src={icon(w.species)} alt="" />{/each}</span>
						<span>yours in the pool</span>
					</div>
				{/if}
				{#if pool.incoming}
					<div class="stat coming">
						<b>{pool.incoming}</b>
						<span>on the way to you</span>
					</div>
				{/if}
			</div>
		{/if}

		{#if locked}
			<div class="state locked">
				<svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"
					><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg
				>
				<b>Opens with your Cable Club</b>
				<p>Bring Mr. Pokémon's Mystery Egg back to Prof. Elm, and Wonder Trade opens along with link trades.</p>
			</div>
		{:else if step === 'pick' && pool?.tradedToday}
			<div class="state">
				<svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
					><path d="M6 2h12M6 22h12M7 2v4l5 6-5 6v4M17 2v4l-5 6 5 6v4" /></svg
				>
				<b>Today's Wonder Trade is done</b>
				<p>The next one opens in <b>{untilTomorrow()}</b>.</p>
			</div>
		{:else if step === 'pick'}
			{#if error}<p class="error" role="alert">{error}</p>{/if}
			{#if party === null}
				<p class="muted small">Loading your party…</p>
			{:else if !party.length}
				<p class="muted small">No party to trade from yet.</p>
			{:else}
				<p class="label">Pick who to send</p>
				<ul class="party" role="radiogroup" aria-label="Pick a Pokémon to send">
					{#each party as m, i (i)}
						{@const why = blocked(m)}
						<li>
							<button type="button" role="radio" aria-checked={picked === i} disabled={!!why} class:on={picked === i} onclick={() => (picked = i)}>
								<img src={icon(m.egg ? 253 : m.species)} alt="" />
								<span class="col">
									<b>{m.egg ? 'Egg' : label(m)}{#if m.shiny}<span class="shiny" title="Shiny"> ✦</span>{/if}</b>
									<span class="xs muted">{why ?? `Lv ${m.level}`}</span>
									{#if m.item && !why}<span class="item">{m.item}</span>{/if}
								</span>
							</button>
						</li>
					{/each}
				</ul>
				<button type="button" class="go" disabled={!chosen} onclick={() => (step = 'confirm')}>
					{chosen ? `Send ${label(chosen)}…` : 'Pick a Pokémon'}
				</button>
			{/if}
		{:else if step === 'confirm' && chosen}
			<div class="confirm">
				<img class="big-mon" src={icon(chosen.species)} alt="" />
				<h3>Let {label(chosen)} go?</h3>
				<div class="warn" role="note">
					<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
						><path d="M12 3l10 18H2zM12 10v5M12 18h.01" /></svg
					>
					<ul>
						<li><b>{label(chosen)} leaves your party for good.</b> There's no getting it back.</li>
						<li>What you get is a surprise: any Pokémon another Pib sent, maybe one you don't like.</li>
						{#if pool && !pool.waiting}<li>Nobody's waiting right now: yours goes into the pool, and you get theirs when the next Pib trades.</li>{/if}
						<li>You can Wonder Trade once a day.</li>
					</ul>
				</div>
				<div class="actions">
					<button type="button" class="ghost" onclick={() => (step = 'pick')}>Keep {label(chosen)}</button>
					<button type="button" class="go danger" onclick={send}>Let {label(chosen)} go</button>
				</div>
			</div>
		{:else if step === 'sending'}
			<div class="stage" role="status">
				<span class="ball" aria-hidden="true"></span>
				<p class="pixel">Off it goes…</p>
				<p class="muted small">Looking for a trade partner</p>
			</div>
		{:else if result && !result.got}
			<div class="stage" role="status">
				<img class="got" src={icon(result.gave.species)} alt="" />
				<p class="pixel big">{label(result.gave)} is in the pool</p>
				<p class="muted small">When the next Pib sends a Pokémon, they get yours and theirs comes to you. You'll get a notification.</p>
				<p class="save">Your game was saved.</p>
				<div class="actions"><button type="button" class="go" onclick={() => dialog?.close()}>Done</button></div>
			</div>
		{:else if result?.got}
			<div class="stage reveal" role="status">
				<span class="glow" class:shiny={result.got.shiny} aria-hidden="true"></span>
				<img class="got" src={icon(result.got.species)} alt="" />
				<p class="pixel big">{label(result.got)}{#if result.got.shiny} ✦{/if}</p>
				<p>
					Lv {result.got.level}
					{#if result.got.nickname}· {result.got.name}{/if}
					· from <b>{nameOf(result.from ?? '')}</b>
					{#if result.got.ot}<span class="muted"> (OT {result.got.ot})</span>{/if}
				</p>
				{#if result.got.evolvedFrom}<p class="evolved">It evolved as it arrived!</p>{/if}
				<p class="muted small">{label(result.gave)} is on its way to <Name user={result.from ?? ''} />.</p>
				<p class="save">Your game was saved.</p>
				<div class="actions"><button type="button" class="go" onclick={() => dialog?.close()}>Done</button></div>
			</div>
		{/if}
	</div>
</dialog>

<style>
	dialog {
		--wt: var(--r-epic);
		position: fixed;
		inset: 0;
		margin: auto;
		width: min(540px, calc(100vw - 16px));
		max-height: calc(100dvh - 32px);
		padding: 0;
		border: 1px solid color-mix(in srgb, var(--wt) 60%, var(--border));
		border-radius: 18px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 20px 60px rgb(0 0 0 / 0.45);
		text-align: left;
		overflow: auto;
	}
	dialog:focus {
		outline: none;
	}
	dialog:not([open]) {
		display: none;
	}
	dialog::backdrop {
		background: rgb(8 11 13 / 0.6);
	}
	/* The banner: purple into gold, a Poké Ball orb with sparkles. */
	.banner {
		position: relative;
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 18px 56px 18px 18px;
		background:
			radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--wt) 55%, transparent), transparent 60%),
			radial-gradient(90% 120% at 100% 100%, color-mix(in srgb, var(--r-legendary) 30%, transparent), transparent 60%),
			var(--sunk);
		border-bottom: 1px solid color-mix(in srgb, var(--wt) 35%, var(--border));
	}
	.banner h2 {
		margin: 0 0 6px;
		font-size: 16px;
		color: var(--text);
	}
	.banner p {
		margin: 0;
		font-size: 13px;
		color: var(--muted);
	}
	.orb {
		position: relative;
		flex: none;
		width: 48px;
		height: 48px;
		border-radius: 50%;
		border: 3px solid #1d1f1c;
		background: linear-gradient(var(--wt) 0 46%, #1d1f1c 46% 54%, #f4f1e8 54%);
		box-shadow: 0 0 18px color-mix(in srgb, var(--wt) 70%, transparent);
	}
	.orb::after {
		content: '';
		position: absolute;
		inset: 0;
		margin: auto;
		width: 12px;
		height: 12px;
		border-radius: 50%;
		background: #f4f1e8;
		border: 3px solid #1d1f1c;
	}
	.spark {
		position: absolute;
		color: var(--r-legendary);
		font-size: 12px;
		animation: twinkle 2.2s ease-in-out infinite;
	}
	.s1 {
		top: -8px;
		right: -6px;
	}
	.s2 {
		bottom: -6px;
		left: -8px;
		animation-delay: 1.1s;
	}
	@keyframes twinkle {
		50% {
			opacity: 0.2;
			scale: 0.6;
		}
	}
	.close {
		position: absolute;
		top: 12px;
		right: 12px;
		width: 32px;
		height: 32px;
		display: grid;
		place-items: center;
		border: none;
		border-radius: 8px;
		background: none;
		color: var(--muted);
		cursor: pointer;
	}
	.close:hover {
		background: color-mix(in srgb, var(--text) 10%, transparent);
		color: var(--text);
	}
	.body {
		padding: 16px 18px 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	p {
		margin: 0;
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
	.label {
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
	}
	.error {
		padding: 10px 12px;
		border-radius: 10px;
		background: color-mix(in srgb, var(--danger) 14%, transparent);
		color: var(--danger);
		font-size: 13px;
		font-weight: 600;
	}
	.pixel {
		font-family: var(--pixel-font);
		font-size: 12px;
	}
	/* The pool at a glance. */
	.status {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.stat {
		flex: 1 1 120px;
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 8px 12px;
		border-radius: 12px;
		background: var(--sunk);
		border: 1px solid var(--border);
		font-size: 12px;
		color: var(--muted);
	}
	.stat b {
		font-size: 20px;
		color: var(--wt);
	}
	.stat.coming b {
		color: var(--r-legendary);
	}
	.minis {
		display: flex;
	}
	.minis img {
		width: 32px;
		height: 32px;
		image-rendering: pixelated;
		margin-right: -6px;
	}
	/* Party cards. */
	.party {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 8px;
	}
	.party button {
		position: relative;
		width: 100%;
		height: 100%;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
		padding: 8px 6px 10px;
		border: 1px solid var(--border);
		border-radius: 12px;
		background: var(--sunk);
		color: var(--text);
		font: inherit;
		text-align: center;
		cursor: pointer;
		transition:
			border-color 0.15s,
			transform 0.15s;
	}
	.party button:hover:not(:disabled) {
		border-color: color-mix(in srgb, var(--wt) 60%, var(--border));
		transform: translateY(-2px);
	}
	.party button.on {
		border-color: var(--wt);
		background: color-mix(in srgb, var(--wt) 16%, var(--sunk));
		box-shadow:
			0 0 0 2px color-mix(in srgb, var(--wt) 40%, transparent),
			0 6px 18px color-mix(in srgb, var(--wt) 30%, transparent);
	}
	.party button:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}
	.party img {
		width: 56px;
		height: 56px;
		image-rendering: pixelated;
	}
	.col {
		display: flex;
		flex-direction: column;
		align-items: center;
		min-width: 0;
		max-width: 100%;
	}
	.col b {
		font-size: 13px;
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.item {
		margin-top: 2px;
		padding: 1px 6px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--text) 8%, transparent);
		font-size: 10px;
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.shiny {
		color: var(--r-legendary);
	}
	.go,
	.ghost {
		height: 44px;
		padding: 0 18px;
		border-radius: 12px;
		font: 700 14px var(--body-font);
		cursor: pointer;
	}
	.go {
		border: none;
		background: linear-gradient(135deg, var(--wt), color-mix(in srgb, var(--wt) 60%, var(--r-legendary)));
		color: #fff;
		box-shadow: 0 6px 18px color-mix(in srgb, var(--wt) 35%, transparent);
	}
	.go:disabled {
		opacity: 0.45;
		cursor: not-allowed;
		box-shadow: none;
	}
	.go.danger {
		background: linear-gradient(135deg, var(--danger), var(--wt));
	}
	.ghost {
		border: 1px solid var(--border-strong);
		background: none;
		color: var(--text);
	}
	.actions {
		display: flex;
		gap: 8px;
		justify-content: center;
		flex-wrap: wrap;
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	/* Locked / done today. */
	.state {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		padding: 22px 16px;
		border-radius: 14px;
		background: var(--sunk);
		border: 1px dashed var(--border-strong);
		text-align: center;
		color: var(--muted);
	}
	.state svg {
		color: var(--wt);
	}
	.state > b {
		font-size: 16px;
		color: var(--text);
	}
	.state.locked svg {
		color: var(--muted);
	}
	/* Confirm: the Pokémon, the warning, two clear choices. */
	.confirm {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12px;
		text-align: center;
	}
	.confirm h3 {
		margin: 0;
		font-size: 18px;
	}
	.big-mon {
		width: 96px;
		height: 96px;
		image-rendering: pixelated;
	}
	.warn {
		display: flex;
		gap: 10px;
		padding: 12px 14px;
		border-radius: 12px;
		border: 1px solid color-mix(in srgb, var(--r-legendary) 60%, var(--border));
		background: color-mix(in srgb, var(--r-legendary) 12%, transparent);
		text-align: left;
		font-size: 13px;
		line-height: 1.45;
	}
	.warn svg {
		flex: none;
		color: var(--r-legendary);
		margin-top: 1px;
	}
	.warn ul {
		margin: 0;
		padding-left: 16px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.save {
		padding: 6px 12px;
		border-radius: 999px;
		background: color-mix(in srgb, var(--r-legendary) 16%, transparent);
		color: var(--accent-text);
		font-size: 13px;
		font-weight: 700;
	}
	.stage {
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
		padding: 16px 0 4px;
		text-align: center;
	}
	/* A Poké Ball, drawn in CSS, spinning off across the stage. */
	.ball {
		width: 44px;
		height: 44px;
		border-radius: 50%;
		border: 3px solid #1d1f1c;
		background: linear-gradient(#e04848 0 46%, #1d1f1c 46% 54%, #f4f1e8 54%);
		position: relative;
		animation: toss 1.1s ease-in-out infinite;
	}
	.ball::after {
		content: '';
		position: absolute;
		inset: 0;
		margin: auto;
		width: 12px;
		height: 12px;
		border-radius: 50%;
		background: #f4f1e8;
		border: 3px solid #1d1f1c;
	}
	@keyframes toss {
		0% {
			translate: -90px 10px;
			rotate: 0deg;
			opacity: 0;
		}
		20% {
			opacity: 1;
		}
		50% {
			translate: 0 -18px;
		}
		80% {
			opacity: 1;
		}
		100% {
			translate: 90px 10px;
			rotate: 720deg;
			opacity: 0;
		}
	}
	.got {
		width: 96px;
		height: 96px;
		image-rendering: pixelated;
		position: relative;
		animation: pop 0.6s cubic-bezier(0.3, 1.6, 0.5, 1);
	}
	.glow {
		position: absolute;
		top: 8px;
		width: 140px;
		height: 140px;
		border-radius: 50%;
		background: radial-gradient(closest-side, color-mix(in srgb, var(--wt) 45%, transparent), transparent);
		animation: pulse 2s ease-in-out infinite;
	}
	.glow.shiny {
		background: radial-gradient(closest-side, color-mix(in srgb, var(--r-legendary) 60%, transparent), transparent);
	}
	.big {
		font-size: 14px;
	}
	.evolved {
		color: var(--r-legendary);
		font-weight: 700;
	}
	@keyframes pop {
		from {
			scale: 0.2;
			opacity: 0;
		}
	}
	@keyframes pulse {
		50% {
			scale: 0.85;
			opacity: 0.6;
		}
	}
	@media (max-width: 420px) {
		.party img {
			width: 44px;
			height: 44px;
		}
		.banner {
			padding: 14px 48px 14px 14px;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.ball,
		.got,
		.glow,
		.spark {
			animation: none;
		}
	}
</style>
