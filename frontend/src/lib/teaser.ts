// The sign-in page's public teaser (GET /api/v1/public/teaser, anonymous) and its sound.
import { api } from './api.js';

/**
 * Totals about the group, never who: players (everyone who picked a game), online, versions (players per ROM title,
 * "POKEMON_GLD"), caughtTogether (species at least one Pib caught), achievements unlocked, link trades and battles.
 */
export type Teaser = {
	online: number;
	players: number;
	versions?: Record<string, number>;
	caughtTogether?: number;
	achievements?: number;
	trades?: number;
	battles?: number;
};

export const loadTeaser = () => api<Teaser>('GET', '/public/teaser');

let audio: AudioContext | null = null;

/**
 * Square-wave notes, like the console's sound chip. Browsers only allow sound after a tap or key press, so call
 * this from one. notes: [frequency Hz, length s][].
 */
export function chime(notes: [number, number][], volume = 0.06) {
	try {
		audio ??= new AudioContext();
		let t = audio.currentTime + 0.02;
		for (const [freq, len] of notes) {
			const osc = audio.createOscillator();
			const gain = audio.createGain();
			osc.type = 'square';
			osc.frequency.value = freq;
			gain.gain.setValueAtTime(volume, t);
			gain.gain.exponentialRampToValueAtTime(0.0001, t + len);
			osc.connect(gain).connect(audio.destination);
			osc.start(t);
			osc.stop(t + len);
			t += len * 0.9;
		}
	} catch {
		// No Web Audio: silent.
	}
}

/** Power-on: two quick rising notes. */
export const bootChime = () => chime([[1046.5, 0.09], [2093, 0.35]]);
/** Konami: a rising arpeggio. */
export const fanfare = () =>
	chime([[523.3, 0.1], [659.3, 0.1], [784, 0.1], [1046.5, 0.1], [784, 0.1], [1046.5, 0.4]]);
/** Konami on /play: the ball shakes three times, then the catch. */
export const gotcha = () => chime([[330, 0.12], [262, 0.12], [330, 0.12], [262, 0.12], [330, 0.12], [1318.5, 0.08], [1568, 0.5]]);
