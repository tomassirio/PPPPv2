<!-- The Link cable card before the Cable Club opens (Gold/Silver/Crystal: its receptionists turn you away until Prof.
     Elm has Mr. Pokémon's Mystery Egg, ScoreRow.cableClub < 3): the Mystery Egg, wobbling now and then, and the
     story steps with the ones done ticked. step: 0 nothing yet, 1 got a Pokémon from Elm, 2 got the egg. -->
<script lang="ts">
	let { step }: { step: number } = $props();

	// The egg as 14x14 pixels: o outline, w shell, s shade, g spots.
	const EGG = [
		'....oooooo....',
		'..oowwwwwwoo..',
		'.owwwwggwwwwo.',
		'.owwwggggwwso.',
		'owwwwwggwwwwso',
		'owgwwwwwwwwwso',
		'oggwwwwwwwggso',
		'owgwwwwwwgggso',
		'owwwwwwwwwggso',
		'owwwwggwwwwsso',
		'.owwggggwwsso.',
		'.owwwggwwssso.',
		'..oosssssssoo.',
		'....oooooo....'
	];
	const COLOR: Record<string, string> = { o: '#2b2b2b', w: '#f8f0d4', s: '#dccfa6', g: '#6ab04c' };
	const pixels = EGG.flatMap((row, y) => [...row].map((c, x) => ({ x, y, c })).filter((p) => p.c !== '.'));

	const steps = [
		{ text: 'Get your first Pokémon from Prof. Elm', where: 'New Bark Town' },
		{ text: "Pick up the Mystery Egg from Mr. Pokémon's house", where: 'Route 30' },
		{ text: 'Bring the egg back to Prof. Elm', where: 'New Bark Town' }
	];
</script>

<div class="locked">
	<div class="art" aria-hidden="true">
		<svg class="egg" viewBox="0 0 14 14" width="84" height="84" shape-rendering="crispEdges">
			{#each pixels as p, i (i)}<rect x={p.x} y={p.y} width="1" height="1" fill={COLOR[p.c]} />{/each}
		</svg>
		<span class="q pixel">?</span>
	</div>
	<div class="text">
		<h3>The Cable Club is closed</h3>
		<p class="muted">Every Pokémon Center's link receptionists turn you away until Prof. Elm has Mr. Pokémon's Mystery Egg.</p>
	</div>
	<ol class="steps">
		{#each steps as s, i (i)}
			{@const done = step > i}
			<li class:done class:next={step === i}>
				<span class="mark" aria-hidden="true">{done ? '✓' : i + 1}</span>
				<span><b>{s.text}</b><span class="muted xs">{s.where}</span></span>
				<span class="sr">{done ? ' (done)' : step === i ? ' (next)' : ''}</span>
			</li>
		{/each}
	</ol>
</div>

<style>
	.locked {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12px;
		text-align: center;
	}
	.art {
		position: relative;
		padding: 10px 0 4px;
	}
	.egg {
		display: block;
		image-rendering: pixelated;
		transform-origin: 50% 90%;
		animation: wobble 4s ease-in-out infinite;
		filter: drop-shadow(0 6px 0 color-mix(in srgb, var(--text) 10%, transparent));
	}
	@keyframes wobble {
		0%,
		70%,
		100% {
			transform: rotate(0);
		}
		76% {
			transform: rotate(-9deg);
		}
		82% {
			transform: rotate(8deg);
		}
		88% {
			transform: rotate(-5deg);
		}
		94% {
			transform: rotate(3deg);
		}
	}
	.q {
		position: absolute;
		top: 0;
		right: -18px;
		font-size: 16px;
		color: var(--accent-text);
	}
	@media (prefers-reduced-motion: reduce) {
		.egg {
			animation: none;
		}
	}
	h3 {
		margin: 0;
		font-size: 16px;
	}
	.text p {
		margin: 4px 0 0;
		font-size: 13px;
		max-width: 320px;
	}
	.muted {
		color: var(--muted);
	}
	.xs {
		font-size: 12px;
	}
	.steps {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
		width: 100%;
		text-align: left;
	}
	.steps li {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 8px 10px;
		border-radius: 10px;
		border: 1px solid var(--border);
		background: var(--sunk);
		font-size: 13px;
	}
	.steps li > span:nth-child(2) {
		display: flex;
		flex-direction: column;
	}
	.steps li.next {
		border-color: var(--accent-text);
		background: var(--accent-tint);
	}
	.steps li.done {
		opacity: 0.7;
	}
	.mark {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font-size: 12px;
		font-weight: 700;
		border: 1px solid var(--border-strong);
		color: var(--muted);
	}
	.done .mark {
		background: var(--hp-ok);
		border-color: var(--hp-ok);
		color: #fff;
	}
	.next .mark {
		border-color: var(--accent-text);
		color: var(--accent-text);
	}
	.sr {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
	}
</style>
