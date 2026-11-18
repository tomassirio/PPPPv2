<!-- A player's shown name (their nickname, see pictures.svelte.ts), with their @username on hover. Text only. -->
<script lang="ts">
	import { nameOf, needPicture, usernameOf } from '#lib/pictures.svelte.ts';

	let { user }: { user: string } = $props();
	$effect(() => {
		needPicture(user);
	});
</script>

<span class="pib-name" title={usernameOf(user)}>{nameOf(user)}</span>
