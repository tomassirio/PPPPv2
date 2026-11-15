<script lang="ts">
	import { onMount } from 'svelte';
	import { api } from '#lib/api.ts';
	import type { Cartridge } from '#lib/cartridges.ts';
	import { act } from './admin.svelte';

	let cartridges = $state<Cartridge[]>([]);
	let file = $state<FileList | null>(null);

	const load = async () => {
		const list = await act('Load cartridges', () => api<Cartridge[]>('GET', '/admin/cartridges'), undefined, true);
		if (list) cartridges = list;
	};
	onMount(load);

	async function upload(e: SubmitEvent) {
		e.preventDefault();
		const form = e.currentTarget as HTMLFormElement; // currentTarget is null once we await
		const f = file?.[0];
		if (!f) return;
		const body = new FormData();
		body.append('file', f);
		if (await act(`Upload ${f.name}`, () => api('POST', '/admin/cartridges', body))) {
			form.reset();
			await load();
		}
	}

	async function remove(id: string) {
		const ok = await act(`Delete ${id}`, () => api('DELETE', `/admin/cartridges/${encodeURIComponent(id)}`).then(() => true), `Delete cartridge ${id}? Players who picked it cannot play until it is uploaded again; their saves stay.`);
		if (ok) await load();
	}
</script>

<section class="panel" aria-labelledby="carts-h">
	<h2 id="carts-h" class="pixel">Cartridges</h2>
	<div class="scroll">
		<table>
			<thead><tr><th>Game</th><th>ID</th><th>Size</th><th>Color</th><th></th></tr></thead>
			<tbody>
				{#each cartridges as cart (cart.id)}
					<tr>
						<td>{cart.name}</td>
						<td><code>{cart.id}</code></td>
						<td>{Math.round(cart.size / 1024)} KB</td>
						<td>{cart.cgb ? 'GBC' : 'GB'}</td>
						<td class="row">
							<button class="danger" onclick={() => remove(cart.id)}>Delete</button>
						</td>
					</tr>
				{:else}
					<tr><td colspan="5" class="muted">No cartridges uploaded.</td></tr>
				{/each}
			</tbody>
		</table>
	</div>
	<form class="row" onsubmit={upload}>
		<label>ROM file <input type="file" accept=".gb,.gbc" required bind:files={file} /></label>
		<button class="primary">Upload</button>
	</form>
</section>
