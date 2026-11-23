<!-- "Find someone online": opens the Find someone to link with dialog (LinkFinder). One look everywhere it appears
     (the Link cable card, /watch, Pib profiles): link-cable blue with a people icon, as wide as its slot up to 360px. -->
<script lang="ts">
	let { onclick, label = 'Find someone online' }: { onclick: () => void; label?: string } = $props();
</script>

<button type="button" class="find" {onclick}>
	<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
		><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></svg
	>
	<span>{label}</span>
</button>

<style>
	.find {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		width: 100%;
		max-width: 360px;
		height: 42px;
		box-sizing: border-box;
		margin: 0 auto;
		padding: 0 16px;
		border: 1px solid var(--link);
		border-radius: 10px;
		background: var(--link-tint, transparent);
		color: var(--link);
		font: inherit;
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
	}
	.find span {
		min-width: 0;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.find svg {
		flex: none;
	}
	.find:hover {
		background: color-mix(in oklab, var(--link) 18%, transparent);
	}
	.find:focus-visible {
		outline: 3px solid var(--accent-text);
		outline-offset: 2px;
	}
</style>
