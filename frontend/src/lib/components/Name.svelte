<!-- A player's shown name (their nickname, see pictures.svelte.ts), with their @username on hover. Text only.
     link: a link to their profile; label: show this instead of the name (e.g. "You"). -->
<script lang="ts">
	import { nameOf, needPicture, usernameOf } from '#lib/pictures.svelte.ts';

	let { user, link = false, label }: { user: string; link?: boolean; label?: string } = $props();
	$effect(() => {
		needPicture(user);
	});
</script>

{#if link}
	<a class="pib-name link" href="/pib/{encodeURIComponent(user)}" title={usernameOf(user)}>{label ?? nameOf(user)}</a>
{:else}
	<span class="pib-name" title={usernameOf(user)}>{label ?? nameOf(user)}</span>
{/if}

<style>
	.link {
		color: inherit;
		text-decoration: none;
	}
	.link:hover {
		text-decoration: underline;
	}
	.link:focus-visible {
		outline: 2px solid var(--accent-text);
		outline-offset: 2px;
		border-radius: 3px;
	}
</style>
