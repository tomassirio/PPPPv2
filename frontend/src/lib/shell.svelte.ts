// The player's chosen controller shell, shared so the page can echo its colour (phones: the selected tab, the
// bezel's chat toggle). Remembered per player in this browser: the colour, and Solid or Atomic (see-through) where it
// has both. Until they pick one, the pad wears their version's (VERSION_SHELL).
import { SHELLS, VERSION_SHELL, saveShell, savedShell, variant, type Shell } from './shells';

let owner: string | undefined;
let version: keyof typeof VERSION_SHELL | null = null;

/** value: the shell as worn (the atomic plastic when on); value.id is the colour's id either way. */
export const currentShell = $state<{ value: Shell; atomic: boolean; chosen: boolean }>({ value: SHELLS[0], atomic: false, chosen: false });

/** What to wear: the owner's pick, else their version's (atomic; not saved, so it follows the game), else Original. */
function refresh() {
	const saved = savedShell(owner);
	const own = !saved.chosen && version ? SHELLS.find((s) => s.id === VERSION_SHELL[version!]) : undefined;
	currentShell.chosen = saved.chosen;
	currentShell.atomic = own ? true : saved.atomic;
	currentShell.value = variant(own ?? saved.base, currentShell.atomic);
}
refresh();

/** Whose pad this is (the signed-in player). */
export function shellOwner(user: string | undefined) {
	if (user === owner) return;
	owner = user;
	refresh();
}

/** The player's version (Gold, Silver, Crystal), for the pad they never picked a colour for. */
export function wearVersion(v: keyof typeof VERSION_SHELL | null) {
	if (v === version) return;
	version = v;
	refresh();
}

export function pickShell(id: string) {
	const base = SHELLS.find((s) => s.id === id) ?? SHELLS[0];
	currentShell.value = variant(base, currentShell.atomic);
	currentShell.chosen = true;
	saveShell(base.id, currentShell.atomic, owner);
}

/** The Solid / Atomic switch: the same colour in the other plastic. */
export function setAtomic(on: boolean) {
	currentShell.atomic = on;
	pickShell(currentShell.value.id);
}
