<!-- The activity feed, newest first: badges, catches, levels (pictures: FeedIcon). limit: how many (the scoreboard's "Latest" shows a few);
     events: show these instead of everyone's (a profile's recent feed). -->
<script lang="ts">
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import FeedIcon from '#lib/components/FeedIcon.svelte';
	import { watchGame } from '#lib/game.svelte.ts';
	import { activityText, ago, type ScoreEvent } from '#lib/play.ts';
	import { nameOf } from '#lib/pictures.svelte.ts';
	import { session } from '#lib/session.svelte.ts';

	let { limit = Infinity, label, events }: { limit?: number; label?: string; events?: ScoreEvent[] } = $props();
	$effect(() => watchBoard());
	$effect(() => watchGame()); // species and badge names for the icons

	let now = $state(Date.now());
	$effect(() => {
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(tick);
	});

	const shown = $derived((events ?? board.activity).slice(0, limit));
</script>

{#if shown.length}
	<ul class="feed" aria-label={label}>
		{#each shown as e, i (i + ':' + e.at + e.user)}
			{@const you = e.user === session.me?.id}
			<li>
				<FeedIcon {e} />
				<span><b>{you ? 'You' : nameOf(e.user)}</b> {activityText(e, '').trim()} <span class="muted">· {ago(now - e.at)} · +{e.points}</span></span>
			</li>
		{/each}
	</ul>
{:else}
	<p class="muted">Nothing yet. Badges, catches and levels show up here.</p>
{/if}

<style>
	.feed {
		margin: 0;
		padding: 0;
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	li {
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
