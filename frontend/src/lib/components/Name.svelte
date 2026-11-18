<!-- A player's shown name (their nickname, see pictures.svelte.ts), with their @username on hover. Text only.
     link: a link to their profile; label: show this instead of the name (e.g. "You"); colored: in the player's own
     colour, the one chat gives them (nameColor, pulled toward the text colour on light themes by --name-dim). -->
<script lang="ts">
	import { nameColor } from '#lib/chat/messages.ts';
	import { nameOf, needPicture, usernameOf } from '#lib/pictures.svelte.ts';

	let { user, link = false, label, colored = false }: { user: string; link?: boolean; label?: string; colored?: boolean } = $props();
	const color = $derived(colored ? `color-mix(in oklab, ${nameColor(user)}, var(--text) var(--name-dim))` : undefined);
	$effect(() => {
		needPicture(user);
	});
</script>

{#if link}
	<a class="pib-name link" href="/pib/{encodeURIComponent(user)}" title={usernameOf(user)} style:color>{label ?? nameOf(user)}</a>
{:else}
	<span class="pib-name" title={usernameOf(user)} style:color>{label ?? nameOf(user)}</span>
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
