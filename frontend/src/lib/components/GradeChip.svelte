<!-- The grade from the capped Trainer rating (0–1000): S+ S A B C D E F, each with its own colour. S+ shimmers. -->
<script lang="ts">
	let { grade, rating, size = 9 }: { grade: string; rating?: number; size?: number } = $props();
	const tier = $derived(({ 'S+': 'splus', S: 's', A: 'a', B: 'b', C: 'c', D: 'd', E: 'e', F: 'f' } as Record<string, string>)[grade] ?? 'f');
</script>

<span
	class="grade pixel g-{tier}"
	style:font-size="{size}px"
	title="Grade {grade}{rating != null ? ` · Trainer rating ${rating} of 1000` : ''}">{grade}</span
>

<style>
	.grade {
		display: inline-block;
		flex: none;
		padding: 0.45em 0.6em;
		border-radius: 4px;
		line-height: 1;
		color: var(--g-ink);
		background: var(--g-bg);
		box-shadow: inset 0 0 0 1px var(--g-edge);
		--g-ink: var(--muted);
		--g-bg: var(--sunk);
		--g-edge: var(--border);
	}
	.g-f {
		--g-ink: #8a8f8c;
		--g-bg: rgb(138 143 140 / 0.14);
		--g-edge: rgb(138 143 140 / 0.35);
	}
	.g-e {
		--g-ink: #c8673f;
		--g-bg: rgb(200 103 63 / 0.14);
		--g-edge: rgb(200 103 63 / 0.4);
	}
	.g-d {
		--g-ink: #b8890f;
		--g-bg: rgb(212 160 23 / 0.15);
		--g-edge: rgb(212 160 23 / 0.45);
	}
	.g-c {
		--g-ink: #3e9a4a;
		--g-bg: rgb(63 174 74 / 0.14);
		--g-edge: rgb(63 174 74 / 0.45);
	}
	.g-b {
		--g-ink: #2f7fd6;
		--g-bg: rgb(47 127 214 / 0.14);
		--g-edge: rgb(47 127 214 / 0.45);
	}
	.g-a {
		--g-ink: #8b55d9;
		--g-bg: rgb(139 85 217 / 0.15);
		--g-edge: rgb(139 85 217 / 0.5);
	}
	.g-s {
		--g-ink: #1d1f1c;
		--g-bg: linear-gradient(135deg, #f7d774, #e0a923);
		--g-edge: rgb(150 105 0 / 0.5);
	}
	.g-splus {
		--g-ink: #1d1f1c;
		--g-bg: linear-gradient(110deg, #f7d774 0%, #fff6d0 35%, #e0a923 50%, #f29bd1 75%, #9bd3f2 100%);
		--g-edge: rgb(150 105 0 / 0.6);
		background-size: 220% 100%;
	}
	/* Darker theme: the light hues read better a step brighter. */
	:global(:root[data-theme='dark']) .g-f { --g-ink: #a9aeab; }
	:global(:root[data-theme='dark']) .g-e { --g-ink: #e98a63; }
	:global(:root[data-theme='dark']) .g-d { --g-ink: #e6b93a; }
	:global(:root[data-theme='dark']) .g-c { --g-ink: #6fcf7a; }
	:global(:root[data-theme='dark']) .g-b { --g-ink: #6ea8ff; }
	:global(:root[data-theme='dark']) .g-a { --g-ink: #b48cf2; }
	@media (prefers-color-scheme: dark) {
		:global(:root:not([data-theme='light'])) .g-f { --g-ink: #a9aeab; }
		:global(:root:not([data-theme='light'])) .g-e { --g-ink: #e98a63; }
		:global(:root:not([data-theme='light'])) .g-d { --g-ink: #e6b93a; }
		:global(:root:not([data-theme='light'])) .g-c { --g-ink: #6fcf7a; }
		:global(:root:not([data-theme='light'])) .g-b { --g-ink: #6ea8ff; }
		:global(:root:not([data-theme='light'])) .g-a { --g-ink: #b48cf2; }
	}
	@media (prefers-reduced-motion: no-preference) {
		.g-splus {
			animation: shimmer 3s linear infinite;
		}
		@keyframes shimmer {
			to {
				background-position: -220% 0;
			}
		}
	}
</style>
