<!-- The activity feed, newest first: badges, catches, levels. limit: how many (the scoreboard's "Latest" shows a few). -->
<script lang="ts">
	import { board, watchBoard } from '#lib/board.svelte.ts';
	import { activityText, ago, shortUser } from '#lib/play.ts';
	import { session } from '#lib/session.svelte.ts';

	let { limit = Infinity, label }: { limit?: number; label?: string } = $props();
	$effect(() => watchBoard());

	let now = $state(Date.now());
	$effect(() => {
		const tick = setInterval(() => (now = Date.now()), 30_000);
		return () => clearInterval(tick);
	});

	const ICONS: Record<string, string> = { BADGE: '★', CAUGHT: '●', SEEN: '◐', LEVEL: '▲', HALL_OF_FAME: '♛' };
	const shown = $derived(board.activity.slice(0, limit));
</script>

{#if shown.length}
	<ul class="feed" aria-label={label}>
		{#each shown as e, i (i + ':' + e.at + e.user)}
			{@const you = e.user === session.me?.id}
			<li>
				<span class="ico" aria-hidden="true">{ICONS[e.type] ?? '·'}</span>
				<span><b>{you ? 'You' : shortUser(e.user)}</b> {activityText(e, '').trim()} <span class="muted">· {ago(now - e.at)} · +{e.points}</span></span>
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
	.ico {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 6px;
		background: var(--border);
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 11px;
	}
	p {
		margin: 0;
	}
	.muted {
		font-size: 13px;
		color: var(--muted);
	}
</style>
