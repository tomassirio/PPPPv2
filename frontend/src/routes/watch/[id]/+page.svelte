<!-- /watch/{userId}: older links to one session; the Live tab shows it. -->
<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';

	$effect(() => {
		goto(`/watch?u=${encodeURIComponent(decodeURIComponent(page.params.id ?? '').toLowerCase())}`, { replaceState: true });
	});
</script>
