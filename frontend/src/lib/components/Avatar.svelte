<!-- A player's round avatar: their Google photo, or initials on a colour from their id. Decorative: the name is
     always written next to it. -->
<script lang="ts">
	import { hue, shortUser } from '#lib/play.ts';
	import { needPicture, pictures } from '#lib/pictures.svelte.ts';

	let { user, size = 32 }: { user: string; size?: number } = $props();
	let broken = $state(false);
	const picture = $derived(pictures.byUser[user]);
	$effect(() => {
		needPicture(user);
	});
</script>

<span class="av" style:--size="{size}px" style:--hue={hue(user)} aria-hidden="true">
	{#if picture && !broken}
		<img src={picture} alt="" referrerpolicy="no-referrer" onerror={() => (broken = true)} />
	{:else}
		{shortUser(user).slice(0, 2).toUpperCase()}
	{/if}
</span>

<style>
	/* Pastel initials with dark ink, like a sticker: the same in both themes. */
	.av {
		flex: none;
		width: var(--size);
		height: var(--size);
		border-radius: 50%;
		overflow: hidden;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		background: hsl(var(--hue) 62% 72%);
		color: #1d1f1c;
		font-size: calc(var(--size) * 0.34);
		font-weight: 700;
		line-height: 1;
	}
	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
