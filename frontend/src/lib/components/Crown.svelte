<!-- The leader's crown (#1 on the podium, Your run, the profile): gold with a shine, a soft glow under it. -->
<script lang="ts">
	let { size = 34 }: { size?: number } = $props();
	const id = `crown-${Math.random().toString(36).slice(2, 8)}`;
</script>

<svg class="crown" viewBox="0 0 24 16" width={size} height={(size * 16) / 24} aria-hidden="true"
	><defs
		><linearGradient {id} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff2b0" /><stop offset="1" stop-color="#d9a522" /></linearGradient></defs
	><path d="M2 14 L3.5 4 L8.5 9 L12 2 L15.5 9 L20.5 4 L22 14 Z" fill="url(#{id})" stroke="#8a6508" stroke-width="1" stroke-linejoin="round" /><circle
		cx="12"
		cy="2.2"
		r="1.3"
		fill="#e8434f"
	/><circle cx="3.5" cy="4.2" r="1" fill="#4aa3df" /><circle cx="20.5" cy="4.2" r="1" fill="#4aa3df" /></svg
>

<style>
	.crown {
		display: block;
		margin-bottom: -4px;
		filter: drop-shadow(0 2px 4px rgb(247 200 80 / 0.55));
	}
</style>
