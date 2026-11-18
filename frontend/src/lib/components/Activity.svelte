<!-- The activity feed, newest first (each player's name links to their profile): badges, catches, levels (pictures: FeedIcon). A new entry arrives with its tier's
     animation (tierOf, arriveTier). limit: how many (the scoreboard's "Latest" shows a few);
     events: show these instead of everyone's (a profile's recent feed). A link battle is one entry, the winner's, told
     the same to everyone ("Ash beat Misty in a link battle", never "You"); older LINK_LOSS entries are left out. -->
<script lang="ts">
	import Name from '#lib/components/Name.svelte';
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import FeedIcon from '#lib/components/FeedIcon.svelte';
	import { watchGame } from '#lib/game.svelte.ts';
	import { activityText, ago, tierOf, type ScoreEvent } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';
	import { arriveTier } from '#lib/motion.ts';

	let { limit = Infinity, label, events }: { limit?: number; label?: string; events?: ScoreEvent[] } = $props();
	$effect(() => watchBoard());
	$effect(() => watchGame()); // species and badge names for the icons

	let now = $state(Date.now());
	$effect(() => {
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(tick);
	});

	// Keyed by what happened, not by position, so a new entry animates in instead of relabelling the list.
	const shown = $derived.by(() => {
		const seen: Record<string, number> = {};
		return (events ?? board.activity).filter((e) => e.type !== 'LINK_LOSS').slice(0, limit).map((e) => {
			const k = `${e.at}:${e.user}:${e.type}:${e.detail}`;
			seen[k] = (seen[k] ?? 0) + 1;
			return { e, key: `${k}:${seen[k]}` };
		});
	});
</script>

{#if shown.length}
	<ul class="feed" aria-label={label}>
		{#each shown as { e, key } (key)}
			{@const you = e.user === session.me?.id && e.type !== 'LINK_WIN'}
			<li in:arriveTier={{ tier: tierOf(e) }}>
				<FeedIcon {e} />
				<span
					><b><Name user={e.user} link colored label={you ? 'You' : undefined} /></b>
					{#if e.type === 'LINK_WIN' && e.detail}beat <b><Name user={e.detail} link colored /></b> in a link battle{:else if e.type === 'ACHIEVEMENT' && e.detail}unlocked
						<a class="ach" href="/pib/{encodeURIComponent(e.user)}?ach={encodeURIComponent(e.detail)}">{e.detail}</a>{:else}{activityText(e, '', (u) => (u === session.me?.id && e.type !== 'LINK_WIN' ? 'you' : nameOf(u))).trim()}{/if}
					<span class="muted">· {ago(now - e.at)} · +{e.points}</span></span
				>
			</li>
		{/each}
	</ul>
{:else}
	<p class="muted">Nothing yet. Badges, catches and levels show up here.</p>
{/if}

<style>
	.ach {
		color: inherit;
		font-weight: 600;
		text-decoration: underline;
		text-decoration-color: color-mix(in srgb, currentColor 35%, transparent);
		text-underline-offset: 2px;
	}
	.ach:hover {
		text-decoration-color: currentColor;
	}
	.ach:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
		border-radius: 3px;
	}
	.feed {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	li {
		border-radius: 6px;
		display: flex;
		gap: 10px;
		font-size: 13px;
		line-height: 19px;
		min-width: 0;
		overflow-wrap: anywhere;
	}
	p {
		margin: 0;
	}
	.muted {
		font-size: 13px;
		color: var(--muted);
	}
</style>
