<!-- A number that counts to its new value when it changes (instant under reduced motion). -->
<script lang="ts">
	import { Tween } from 'svelte/motion';
	import { cubicOut } from 'svelte/easing';
	import { ms } from '#lib/motion.ts';

	let { value }: { value: number } = $props();
	const shown = new Tween(0, { easing: cubicOut });
	let first = true;
	$effect(() => {
		const v = value;
		if (first) {
			first = false;
			shown.set(v, { duration: 0 });
		} else shown.set(v, { duration: ms(700) });
	});
</script>

{Math.round(shown.current)}
