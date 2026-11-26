// The sign-in page's public teaser (GET /api/v1/public/teaser, anonymous) and its sound.
import { api } from './api.js';

/**
 * Totals about the group, never who: players (everyone who picked a game), online, versions (players per ROM title,
 * "POKEMON_GLD"), caught (Pokémon caught, every player's added up), achievements unlocked, link trades and link battles played.
 */
export type Teaser = {
	online: number;
	players: number;
	versions?: Record<string, number>;
	caught?: number;
	achievements?: number;
	trades?: number;
	battles?: number;
	/**
	 * Everyone on the town map: first name, cartridge (for /public/sprites/{cartridge}/...), version (ROM title),
	 * online, the spot (x, y in that region's 160x144 Pokégear screen; region null before their first reading) and
	 * the party's species in order.
	 */
	trainers?: {
		name: string;
		cartridge: string;
		version: string;
		online: boolean;
		region: 'johto' | 'kanto' | null;
		x: number;
		y: number;
		party: number[];
		/** Where they stand: the pret map constant, x/y in 16px steps from its top-left, and facing. */
		spot: { map: string; x: number; y: number; facing: 'down' | 'up' | 'left' | 'right' } | null;
		/** For the card over their name: Trainer Title, badges, Pokémon caught, "H:MM" played, the landmark. */
		title?: string | null;
		badges?: number;
		caught?: number;
		playTime?: string | null;
		location?: string | null;
		/** The trainer they walk as (a PlayerSprites key), null for the game's own. */
		sprite?: string | null;
	}[];
	/** Latest catches, badges, achievements and Hall of Fame entries, newest first; trainer: index in trainers. */
	moments?: { trainer: number; type: 'CAUGHT' | 'BADGE' | 'ACHIEVEMENT' | 'HALL_OF_FAME'; detail: string; at: number }[];
};

/** What stands on a map image (GET /public/maps/{MAP}.json): see Gen2MapArt.Info. */
export type MapInfo = {
	width: number;
	height: number;
	/** Connected maps in the image: their top-left block relative to this map's. */
	neighbours: { map: string; x: number; y: number }[];
	npcs: { sprite: number; palette: number; x: number; y: number; move: 'still' | 'wander' | 'spin' | 'updown' | 'leftright'; rx: number; ry: number; facing: 'down' | 'up' | 'left' | 'right'; frames: number }[];
	/** Tall grass steps as [x, y]. */
	grass: [number, number][];
	/** Pokédex numbers in that grass at this time of day. */
	wild: number[];
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

/** Konami: a rising arpeggio. */
export const fanfare = () =>
	chime([[523.3, 0.1], [659.3, 0.1], [784, 0.1], [1046.5, 0.1], [784, 0.1], [1046.5, 0.4]]);
/** Konami on /play: the ball shakes three times, then the catch. */
export const gotcha = () => chime([[330, 0.12], [262, 0.12], [330, 0.12], [262, 0.12], [330, 0.12], [1318.5, 0.08], [1568, 0.5]]);

/**
 * Konami on /play: the opera's aria. melody and bass are [MIDI note, beats][] (0 = rest); the melody sings on a square
 * wave with a soprano's vibrato, the bass goes oom-pah-pah on a triangle. drums: [1 kick | 2 snare | 3 boom | 0 rest,
 * beats][]. From fryAt seconds on, everything is deep fried: bass boosted and clipped, at about the same loudness.
 * Returns stop(): fades it out at once.
 */
export function aria(
	beat: number,
	melody: [number, number][],
	bass: [number, number][],
	drums: [number, number][] = [],
	fryAt = Infinity,
	volume = 0.05
) {
	try {
		audio ??= new AudioContext();
		const ctx = audio;
		const out = ctx.createGain();
		const hz = (n: number) => 440 * 2 ** ((n - 69) / 12);
		const start = ctx.currentTime + 0.05;
		// Two ways out: clean, and fried (driven into a bass shelf and a tanh clipper, turned down after).
		const clean = ctx.createGain();
		const drive = ctx.createGain();
		const shelf = ctx.createBiquadFilter();
		const clip = ctx.createWaveShaper();
		const fried = ctx.createGain();
		drive.gain.value = 8;
		shelf.type = 'lowshelf';
		shelf.frequency.value = 120;
		shelf.gain.value = 14;
		clip.curve = Float32Array.from({ length: 1024 }, (_, i) => Math.tanh(3 * (i / 511.5 - 1)));
		fried.gain.value = 0;
		if (Number.isFinite(fryAt)) {
			clean.gain.setValueAtTime(0, start + fryAt);
			fried.gain.setValueAtTime(0.18, start + fryAt);
		}
		out.connect(clean).connect(ctx.destination);
		out.connect(drive).connect(shelf).connect(clip).connect(fried).connect(ctx.destination);
		const voice = (notes: [number, number][], type: OscillatorType, vol: number, vibrato: boolean) => {
			let t = start;
			for (const [n, beats] of notes) {
				const len = beats * beat;
				if (n) {
					const osc = ctx.createOscillator();
					const gain = ctx.createGain();
					osc.type = type;
					osc.frequency.value = hz(n);
					if (vibrato && len > beat) {
						// The vibrato swells in after the note's attack, as a singer's does.
						const lfo = ctx.createOscillator();
						const depth = ctx.createGain();
						lfo.frequency.value = 5.5;
						depth.gain.setValueAtTime(0, t);
						depth.gain.linearRampToValueAtTime(hz(n) * 0.015, t + Math.min(len, 0.6));
						lfo.connect(depth).connect(osc.frequency);
						lfo.start(t);
						lfo.stop(t + len);
					}
					gain.gain.setValueAtTime(0.0001, t);
					gain.gain.exponentialRampToValueAtTime(vol, t + 0.02);
					gain.gain.setValueAtTime(vol, t + len * 0.85);
					gain.gain.exponentialRampToValueAtTime(0.0001, t + len * 0.98);
					osc.connect(gain).connect(out);
					osc.start(t);
					osc.stop(t + len);
				}
				t += len;
			}
		};
		voice(melody, 'square', volume, true);
		voice(bass, 'triangle', volume * 2.4, false);
		// Kick: a sine dropping in pitch. Snare: a burst of high-passed noise.
		const noise = ctx.createBuffer(1, ctx.sampleRate * 0.1, ctx.sampleRate);
		noise.getChannelData(0).forEach((_, i, d) => (d[i] = Math.random() * 2 - 1));
		let t = start;
		for (const [hit, beats] of drums) {
			const gain = ctx.createGain();
			gain.connect(out);
			if (hit === 1 || hit === 3) {
				// The boom is a kick that takes its time.
				const len = hit === 3 ? 1 : 0.15;
				const osc = ctx.createOscillator();
				osc.frequency.setValueAtTime(hit === 3 ? 110 : 150, t);
				osc.frequency.exponentialRampToValueAtTime(hit === 3 ? 32 : 40, t + len * 0.8);
				gain.gain.setValueAtTime(volume * (hit === 3 ? 8 : 5), t);
				gain.gain.exponentialRampToValueAtTime(0.0001, t + len);
				osc.connect(gain);
				osc.start(t);
				osc.stop(t + len);
			} else if (hit === 2) {
				const src = ctx.createBufferSource();
				const hp = ctx.createBiquadFilter();
				src.buffer = noise;
				hp.type = 'highpass';
				hp.frequency.value = 1500;
				gain.gain.setValueAtTime(volume * 2.5, t);
				gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.09);
				src.connect(hp).connect(gain);
				src.start(t);
			}
			t += beats * beat;
		}
		return () => {
			out.gain.setTargetAtTime(0, ctx.currentTime, 0.05);
			setTimeout(() => (clean.disconnect(), fried.disconnect()), 400);
		};
	} catch {
		return () => {};
	}
}
