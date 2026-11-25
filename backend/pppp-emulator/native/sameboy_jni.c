// JNI glue between dev.pppp.emulator.SameBoy and SameBoy's core (Core/gb.h). One Console per Game Boy, its address
// as the Java handle. Everything here runs on the console's own emulator thread (EmulatorHost), never concurrently.
#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "Core/gb.h"

#define WIDTH 160
#define HEIGHT 144
#define AUDIO_CAPACITY 8192 /* mono samples between two drains (one frame is ~369 at 22050 Hz) */
#define TICKS_PER_FRAME 140448 /* 70224 cycles of the 4 MHz clock, in GB_run's 8 MHz ticks */

typedef struct Console {
    GB_gameboy_t *gb;
    uint32_t pixels[256 * 224]; /* sized for an SGB border; borders are off, so 160x144 is used */
    int16_t audio[AUDIO_CAPACITY];
    int audioCount;
    bool vblank;
    struct Console *peer; /* the other end of the link cable, or NULL */
    bool bitOut;          /* the bit this console put on the cable */
    long long ticks;      /* 8 MHz ticks run, for lockstep */
    long long linkedAt;   /* ticks when the cable went in */
    long long bits;       /* link cable bits this console clocked, for diagnostics */
    int sampleRate;
} Console;

static Console *of(jlong handle) { return (Console *) (intptr_t) handle; }

static uint32_t rgb(GB_gameboy_t *gb, uint8_t r, uint8_t g, uint8_t b) { return (uint32_t) r << 16 | g << 8 | b; }

static void onVblank(GB_gameboy_t *gb, GB_vblank_type_t type) { ((Console *) GB_get_user_data(gb))->vblank = true; }

static void onSample(GB_gameboy_t *gb, GB_sample_t *s) {
    Console *c = GB_get_user_data(gb);
    if (c->audioCount < AUDIO_CAPACITY) c->audio[c->audioCount++] = (int16_t) ((s->left + s->right) / 2);
}

// The link cable, as SameBoy's own frontends wire two consoles: a console starting a bit puts it on the cable; at
// the bit's end it takes the peer's bit and hands over its own.
static void bitStart(GB_gameboy_t *gb, bool bit) { ((Console *) GB_get_user_data(gb))->bitOut = bit; }

static bool bitEnd(GB_gameboy_t *gb) {
    Console *c = GB_get_user_data(gb);
    c->bits++;
    if (!c->peer) return true; /* no cable: the line idles high */
    bool in = GB_serial_get_data_bit(c->peer->gb);
    GB_serial_set_data_bit(c->peer->gb, c->bitOut);
    return in;
}

JNIEXPORT jlong JNICALL Java_dev_pppp_emulator_SameBoy_create(JNIEnv *env, jclass cls, jbyteArray bootRom, jbyteArray rom,
                                                             jbyteArray battery, jint sampleRate) {
    Console *c = calloc(1, sizeof(Console));
    if (!c) return 0;
    c->gb = GB_init(GB_alloc(), GB_MODEL_CGB_E);
    GB_set_user_data(c->gb, c);
    jsize n = (*env)->GetArrayLength(env, bootRom);
    jbyte *b = (*env)->GetByteArrayElements(env, bootRom, NULL);
    GB_load_boot_rom_from_buffer(c->gb, (const unsigned char *) b, n);
    (*env)->ReleaseByteArrayElements(env, bootRom, b, JNI_ABORT);
    n = (*env)->GetArrayLength(env, rom);
    b = (*env)->GetByteArrayElements(env, rom, NULL);
    GB_load_rom_from_buffer(c->gb, (const uint8_t *) b, n);
    (*env)->ReleaseByteArrayElements(env, rom, b, JNI_ABORT);
    if (battery) {
        n = (*env)->GetArrayLength(env, battery);
        b = (*env)->GetByteArrayElements(env, battery, NULL);
        GB_load_battery_from_buffer(c->gb, (const uint8_t *) b, n);
        (*env)->ReleaseByteArrayElements(env, battery, b, JNI_ABORT);
    }
    GB_set_rgb_encode_callback(c->gb, rgb);
    GB_set_pixels_output(c->gb, c->pixels);
    GB_set_border_mode(c->gb, GB_BORDER_NEVER);
    GB_set_color_correction_mode(c->gb, GB_COLOR_CORRECTION_DISABLED);
    GB_set_vblank_callback(c->gb, onVblank);
    c->sampleRate = sampleRate;
    GB_set_sample_rate(c->gb, sampleRate);
    GB_apu_set_sample_callback(c->gb, onSample);
    GB_set_serial_transfer_bit_start_callback(c->gb, bitStart);
    GB_set_serial_transfer_bit_end_callback(c->gb, bitEnd);
    GB_set_rtc_mode(c->gb, GB_RTC_MODE_SYNC_TO_HOST);
    GB_set_turbo_mode(c->gb, true, true); /* no pacing of its own: EmulatorHost paces */
    GB_set_emulate_joypad_bouncing(c->gb, false); /* a tap is one press, as the game expects from a clean pad */
    return (jlong) (intptr_t) c;
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_destroy(JNIEnv *env, jclass cls, jlong h) {
    Console *c = of(h);
    if (c->peer) c->peer->peer = NULL;
    GB_free(c->gb);
    free(c->gb);
    free(c);
}

/** Runs until the next vblank (one frame). */
JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_runFrame(JNIEnv *env, jclass cls, jlong h) {
    Console *c = of(h);
    c->vblank = false;
    long long start = c->ticks;
    // A frame ends at vblank; with the LCD off there is none, so stop after a frame's worth of ticks.
    while (!c->vblank && c->ticks - start < TICKS_PER_FRAME) c->ticks += GB_run(c->gb);
}

/*
 * Two real Game Boys never tick at exactly the same rate (their crystals differ by a few hundred ppm), so their frames
 * slide past each other. The Gen 2 link handshake (pret's WaitForLinkedFriend) counts on that: each side listens
 * (external clock) for a few microseconds per frame, then sends (internal clock), and they connect once one side's
 * send lands in the other's listening window. Two emulated consoles in perfect lockstep keep the same phase forever
 * and may never connect. So b runs DRIFT_PPM slower than a: about 140 ticks per frame, shorter than the window, so
 * the phase walks through it instead of jumping over it (worst case a full turn, ~17 s at 1000 ppm).
 */
#define DRIFT_PPM 1000

/** Runs a linked pair in lockstep (whoever is behind runs next, b a little slower) until both have reached a vblank. */
JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_runLinkedFrame(JNIEnv *env, jclass cls, jlong ha, jlong hb) {
    Console *a = of(ha), *b = of(hb);
    a->vblank = b->vblank = false;
    long long startA = a->ticks, startB = b->ticks;
    while (!(a->vblank || a->ticks - startA >= TICKS_PER_FRAME) || !(b->vblank || b->ticks - startB >= TICKS_PER_FRAME)) {
        if ((a->ticks - a->linkedAt) * 1000000 <= (b->ticks - b->linkedAt) * (1000000 + DRIFT_PPM)) a->ticks += GB_run(a->gb);
        else b->ticks += GB_run(b->gb);
    }
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_link(JNIEnv *env, jclass cls, jlong ha, jlong hb) {
    Console *a = of(ha), *b = of(hb);
    a->peer = b;
    b->peer = a;
    a->linkedAt = a->ticks; /* lockstep starts level, counted from now */
    b->linkedAt = b->ticks;
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_unlink(JNIEnv *env, jclass cls, jlong h) {
    Console *c = of(h);
    if (c->peer) c->peer->peer = NULL;
    c->peer = NULL;
}

/** Off: the PPU still runs, but draws no pixels (a frame nobody will see). */
JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_render(JNIEnv *env, jclass cls, jlong h, jboolean on) {
    GB_set_rendering_disabled(of(h)->gb, !on);
}

/** Off: the APU still runs, but makes no samples. */
JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_sound(JNIEnv *env, jclass cls, jlong h, jboolean on) {
    Console *c = of(h);
    GB_set_sample_rate(c->gb, on ? c->sampleRate : 0);
    if (!on) c->audioCount = 0;
}

/** Link cable bits this console has clocked since it was created (a plain read: a diagnostic, not synchronised). */
JNIEXPORT jlong JNICALL Java_dev_pppp_emulator_SameBoy_serialBits(JNIEnv *env, jclass cls, jlong h) {
    return of(h)->bits;
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_pixels(JNIEnv *env, jclass cls, jlong h, jintArray out) {
    (*env)->SetIntArrayRegion(env, out, 0, WIDTH * HEIGHT, (const jint *) of(h)->pixels);
}

/** Copies the samples since the last call into out (mono); returns how many. */
JNIEXPORT jint JNICALL Java_dev_pppp_emulator_SameBoy_drainAudio(JNIEnv *env, jclass cls, jlong h, jshortArray out) {
    Console *c = of(h);
    int n = c->audioCount;
    jsize cap = (*env)->GetArrayLength(env, out);
    if (n > cap) n = cap;
    (*env)->SetShortArrayRegion(env, out, 0, n, c->audio);
    c->audioCount = 0;
    return n;
}

JNIEXPORT jint JNICALL Java_dev_pppp_emulator_SameBoy_read(JNIEnv *env, jclass cls, jlong h, jint address) {
    return GB_safe_read_memory(of(h)->gb, (uint16_t) address); /* no side effects, unlike the CPU's read */
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_write(JNIEnv *env, jclass cls, jlong h, jint address, jint value) {
    GB_write_memory(of(h)->gb, (uint16_t) address, (uint8_t) value);
}

/** keys: bit i set = SameBoy's GB_key_t i held (right, left, up, down, A, B, select, start). */
JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_keys(JNIEnv *env, jclass cls, jlong h, jint keys) {
    for (int k = 0; k < 8; k++) GB_set_key_state(of(h)->gb, (GB_key_t) k, (keys >> k) & 1);
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_patchRom(JNIEnv *env, jclass cls, jlong h, jint offset, jint value) {
    size_t size;
    uint16_t bank;
    uint8_t *rom = GB_get_direct_access(of(h)->gb, GB_DIRECT_ACCESS_ROM, &size, &bank);
    if (offset >= 0 && (size_t) offset < size) rom[offset] = (uint8_t) value;
}

JNIEXPORT void JNICALL Java_dev_pppp_emulator_SameBoy_reset(JNIEnv *env, jclass cls, jlong h) {
    GB_reset(of(h)->gb);
}

JNIEXPORT jbyteArray JNICALL Java_dev_pppp_emulator_SameBoy_battery(JNIEnv *env, jclass cls, jlong h) {
    GB_gameboy_t *gb = of(h)->gb;
    int size = GB_save_battery_size(gb);
    if (size <= 0) return NULL;
    uint8_t *buf = malloc(size);
    GB_save_battery_to_buffer(gb, buf, size);
    jbyteArray out = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, out, 0, size, (const jbyte *) buf);
    free(buf);
    return out;
}

JNIEXPORT jbyteArray JNICALL Java_dev_pppp_emulator_SameBoy_saveState(JNIEnv *env, jclass cls, jlong h) {
    GB_gameboy_t *gb = of(h)->gb;
    size_t size = GB_get_save_state_size(gb);
    uint8_t *buf = malloc(size);
    GB_save_state_to_buffer(gb, buf);
    jbyteArray out = (*env)->NewByteArray(env, (jsize) size);
    (*env)->SetByteArrayRegion(env, out, 0, (jsize) size, (const jbyte *) buf);
    free(buf);
    return out;
}

/** 0 when loaded (SameBoy's own format or BESS, e.g. Coffee GB's), an error code otherwise. */
JNIEXPORT jint JNICALL Java_dev_pppp_emulator_SameBoy_loadState(JNIEnv *env, jclass cls, jlong h, jbyteArray state) {
    jsize n = (*env)->GetArrayLength(env, state);
    jbyte *b = (*env)->GetByteArrayElements(env, state, NULL);
    int r = GB_load_state_from_buffer(of(h)->gb, (const uint8_t *) b, n);
    (*env)->ReleaseByteArrayElements(env, state, b, JNI_ABORT);
    return r;
}
