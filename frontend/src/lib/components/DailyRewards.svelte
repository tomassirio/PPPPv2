<!-- Daily rewards: "Day N", today's reward (on its way to the PC, or the party), the next days and the next big one,
     and the whole 90-day calendar folded under it. Opens by itself the first time each day (lib/daily.svelte.ts:
     maybeShow), when a day counts while they play (DAY_PLAYED), or from Your run's "Day N" button. -->
<script lang="ts">
	import { on } from '#lib/events.ts';
	import { session } from '#lib/session.svelte.ts';
	import { daily, giftArt, grantShort, loadDays, maybeShow, rewardText, type Grant, type Reward } from '#lib/daily.svelte.ts';

	let dialog = $state<HTMLDialogElement>();
	let all = $state(false);
	const me = $derived(session.me?.id);

	$effect(() => {
		const user = me;
		if (!user) return;
		const check = () => loadDays().then(() => maybeShow(user));
		check();
		// The day counts when the console opens, which can land just after this page asked.
		const later = setTimeout(check, 4000);
		const off = on('DAY_PLAYED', (e: { user: string; day: number; reward: Reward }) => {
			if (e.user !== user) return;
			daily.sent = e.reward;
			check();
		});
		return () => {
			clearTimeout(later);
			off();
		};
	});
	$effect(() => {
		if (daily.open && !dialog?.open) {
			dialog?.showModal();
			dialog?.focus(); // not the × (a ring around it on phones)
		}
		if (!daily.open && dialog?.open) dialog.close();
	});

	const d = $derived(daily.data);
	const n = $derived(d?.days ?? 0);
	const today = $derived(daily.sent?.day === n ? daily.sent : (d?.calendar[n - 1] ?? null));
	// The week the next day to earn is in (today's, once it counted): a row, as the calendar shows them.
	const weekStart = $derived(Math.floor(Math.max(0, (d?.today ? n : n + 1) - 1) / 7) * 7);
	const next = $derived(d?.calendar.slice(weekStart, weekStart + 7) ?? []);
	const tone = (r: Reward) => r.tier ?? 'common';
	// A day tapped (phones have no hover titles): its whole reward under the row it is in.
	let picked = $state<{ r: Reward; in: 'week' | 'all' } | null>(null);
	const describe = (r: Reward) => `Day ${r.day}${r.title ? ` · ${r.title}` : ''}: ${rewardText(r) || 'Welcome!'}`;
	const arrives = (r: Reward) =>
		r.grants.some((g) => g.pokemon) ? 'Joins your party when there is room.' : 'Waiting in your PC soon.';
</script>

{#snippet cell(r: Reward, size: number, where: 'week' | 'all')}
	{@const done = !!d && (r.day < n || (r.day === n && d.today))}
	<li>
		<button
			type="button"
			class="cell {tone(r)}"
			class:done
			class:now={r.day === n}
			class:on={picked?.r === r}
			aria-label={describe(r) + (done ? ', claimed' : '')}
			title={describe(r)}
			onclick={() => (picked = picked?.r === r ? null : { r, in: where })}
		>
			<span class="day"><span class="wide">Day&nbsp;</span>{r.day}</span>
			{@render art(r, size)}
			<span class="what">{r.grants.map(grantShort).join(' + ') || 'Welcome'}</span>
		</button>
	</li>
{/snippet}
{#snippet pickedLine(where: 'week' | 'all')}
	{#if picked?.in === where}<p class="picked {tone(picked.r)}" role="status">{describe(picked.r)}</p>{/if}
{/snippet}

{#snippet art(r: Reward, size: number)}
	<span class="art" style:--s="{size}px" aria-hidden="true">
		{#each r.grants as g (g.gift)}
			{@render one(g)}
		{:else}
			<span class="hi">Hi!</span>
		{/each}
	</span>
{/snippet}
{#snippet one(g: Grant)}
	{@const a = giftArt(g.gift)}
	<span class="pic">
		{#if a}<img src={a.src} alt="" class:mon={a.mon} />{:else}<span class="mystery">?</span>{/if}
		{#if !g.pokemon && g.quantity > 1}<span class="qty">×{g.quantity}</span>{/if}
	</span>
{/snippet}

<dialog bind:this={dialog} tabindex="-1" aria-labelledby="daily-h" onclose={() => (daily.open = false)} onclick={(e) => e.target === dialog && dialog?.close()}>
	{#if d}
		<div class="body">
			<header>
				<div>
					<p class="kicker">Daily rewards</p>
					<h2 id="daily-h" class="pixel">Day {n}</h2>
					<p class="muted small">Every day you play earns something. Missed days don't reset anything.</p>
				</div>
				<button type="button" class="close" aria-label="Close" onclick={() => dialog?.close()}>
					<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true"
						><path d="M6 6l12 12M18 6L6 18" /></svg
					>
				</button>
			</header>

			{#if today && d.today}
				<div class="today {tone(today)}">
					{#if today.grants.length}{@render art(today, 56)}{/if}
					<span class="label">Today{today.title ? ` · ${today.title}` : ''}</span>
					{#if today.grants.length}
						<b>{rewardText(today)}</b>
						<span class="muted xs">{arrives(today)} Save your game to keep it.</span>
					{:else}
						<b>Your first day!</b>
						<span class="muted xs">Rewards start tomorrow{d.calendar[n] ? `: ${rewardText(d.calendar[n])}` : ''}. Come back to claim it.</span>
					{/if}
				</div>
			{:else if !d.today}
				<p class="muted small">Play today to earn day {n + 1}'s reward: {d.calendar[n] ? rewardText(d.calendar[n]) : 'a surprise'}.</p>
			{/if}

			{#if next.length}
				<section aria-label="This week">
					<p class="label">Week {weekStart / 7 + 1}</p>
					<ol class="row">
						{#each next as r (r.day)}{@render cell(r, 32, 'week')}{/each}
					</ol>
					{@render pickedLine('week')}
				</section>
			{/if}

			<button type="button" class="more" aria-expanded={all} onclick={() => (all = !all)}>{all ? 'Hide' : 'Show'} the whole calendar</button>
			{#if all}
				<ol class="grid" aria-label="All {d.calendar.length} days">
					{#each d.calendar as r (r.day)}{@render cell(r, 28, 'all')}{/each}
				</ol>
				{@render pickedLine('all')}
			{/if}
		</div>
	{/if}
</dialog>

<style>
	dialog {
		position: fixed;
		inset: 0;
		margin: auto;
		width: min(680px, calc(100vw - 32px));
		max-height: calc(100dvh - 48px);
		padding: 0;
		border: 1px solid var(--r-legendary);
		border-radius: 16px;
		background: var(--panel);
		color: var(--text);
		box-shadow: 0 16px 48px rgb(0 0 0 / 0.35);
		text-align: left;
	}
	dialog:focus {
		outline: none;
	}
	dialog:not([open]) {
		display: none;
	}
	dialog::backdrop {
		background: rgb(8 11 13 / 0.55);
	}
	.body {
		padding: 18px 20px 20px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	header {
		display: flex;
		justify-content: space-between;
		gap: 12px;
	}
	h2 {
		margin: 4px 0 6px;
		font-size: 22px;
		color: var(--accent-text);
	}
	p {
		margin: 0;
	}
	.pixel {
		font-family: var(--pixel-font);
	}
	.kicker,
	.label {
		margin: 0;
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--muted);
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
	.common {
		--c: var(--r-common);
	}
	.rare {
		--c: var(--r-rare);
	}
	.epic {
		--c: var(--r-epic);
	}
	.legendary {
		--c: var(--r-legendary);
	}
	/* The gift's picture: items as their sprite, Pokémon as the ROM draws them (bigger box: their pics are 56x56). */
	.art {
		display: flex;
		gap: 4px;
		height: var(--s);
	}
	.art img {
		height: var(--s);
		width: var(--s);
		object-fit: contain;
		image-rendering: pixelated;
	}
	/* Day 1 has no gift: a hello in its place. */
	.hi {
		display: grid;
		place-items: center;
		height: var(--s);
		font: 10px var(--pixel-font);
		color: var(--c);
	}
	.pic {
		position: relative;
		display: inline-flex;
	}
	/* Phones: the cells are too narrow for words, so the sprite and its count say it (the title has the rest). */
	.qty {
		display: none;
		position: absolute;
		right: -6px;
		bottom: -4px;
		padding: 0 3px;
		border-radius: 4px;
		background: var(--panel);
		font: 700 10px var(--body-font);
	}
	.cell.on {
		outline: 2px solid var(--c);
		outline-offset: 1px;
	}
	.picked {
		margin-top: 6px;
		padding: 6px 10px;
		border-radius: 8px;
		border-left: 3px solid var(--c);
		background: var(--sunk);
		font-size: 13px;
	}
	/* Phones: a day is its number, its sprite and a count; tap it for the words. */
	@media (max-width: 560px) {
		dialog {
			width: calc(100vw - 16px);
			max-height: calc(100dvh - 16px);
		}
		.body {
			padding: 14px 12px 14px;
			gap: 12px;
		}
		h2 {
			font-size: 18px;
			margin: 2px 0 4px;
		}
		.today {
			padding: 10px 12px;
			column-gap: 10px;
		}
		.today b {
			font-size: 15px;
		}
		.row,
		.grid {
			gap: 4px;
		}
		.what,
		.wide {
			display: none;
		}
		.qty {
			display: block;
		}
		/* Day 7's bonus: the first gift's sprite only, with a mark; tapping the day lists them all. */
		.cell .art > .pic + .pic {
			display: none;
		}
		.cell .art:has(.pic + .pic)::after {
			content: '+';
			align-self: flex-start;
			margin-left: 4px;
			font-weight: 800;
			color: var(--c);
		}
		.cell {
			align-items: center;
			gap: 4px;
			padding: 5px 2px 7px;
			font-size: 11px;
		}
		.grid {
			max-height: 46dvh;
		}
	}
	.mystery {
		display: grid;
		place-items: center;
		width: var(--s);
		height: var(--s);
		border-radius: 50%;
		background: color-mix(in srgb, var(--c) 25%, var(--sunk));
		color: var(--c);
		font: calc(var(--s) * 0.5) / 1 var(--pixel-font);
	}
	.today {
		display: grid;
		grid-template-columns: auto 1fr;
		column-gap: 14px;
		row-gap: 4px;
		align-items: center;
		padding: 14px 16px;
		border-radius: 12px;
		border: 2px solid var(--c);
		background: color-mix(in srgb, var(--c) 14%, transparent);
	}
	.today b {
		font-size: 18px;
	}
	.today > :not(.art) {
		grid-column: 2;
	}
	.today > .art {
		grid-row: 1 / span 3;
	}
	ol {
		list-style: none;
		margin: 0;
		padding: 0;
	}
	/* A week a row, everywhere: days 6 and 7 of each week in their tier's frame. */
	.row,
	.grid {
		display: grid;
		grid-template-columns: repeat(7, minmax(0, 1fr));
		gap: 6px;
		margin-top: 6px;
	}
	.cell {
		width: 100%;
		height: 100%;
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 2px;
		padding: 6px 8px;
		color: var(--text);
		font: inherit;
		text-align: left;
		cursor: pointer;
		border-radius: 8px;
		border: 1px solid var(--c);
		background: color-mix(in srgb, var(--c) 8%, var(--sunk));
		font-size: 11px;
		line-height: 1.3;
		min-width: 0;
	}
	.day {
		font-weight: 700;
		color: var(--c);
	}
	.what {
		overflow-wrap: anywhere;
	}
	.more {
		align-self: flex-start;
		padding: 6px 12px;
		border: 1px solid var(--border-strong);
		border-radius: 999px;
		background: none;
		color: var(--text);
		font: 600 13px var(--body-font);
		cursor: pointer;
	}
	.grid {
		gap: 6px;
		max-height: 40dvh;
		overflow: auto;
	}
	.done {
		opacity: 0.5;
	}
	.done .day::after {
		content: ' ✓';
	}
	.now {
		opacity: 1;
		outline: 2px solid var(--accent-text);
	}
	button:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
	.close {
		flex: none;
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
		background: var(--sunk);
		color: var(--text);
	}
</style>
