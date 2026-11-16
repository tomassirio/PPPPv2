package dev.pppp.service.game;

/**
 * Synthetic Gold/Silver or Crystal RAM and ROM for tests. The addresses are restated here from pokegold.sym and
 * pokecrystal11.sym (bag counts included) rather than taken from PokemonGen2, so a slip there shows up here.
 */
final class Gen2Ram {
    record At(String header, int id, int hours, int coins, int badges, int tmsHms, int numItems, int numKeyItems,
              int numBalls, int numPcItems, int hof, int events, int phone, int party, int caught, int unown,
              int romNames, int romBase, int minutes, int mapGroup, int nicknames, int itemNames, int moveNames,
              int landmarks, int mapGroups, int curBox) {}

    static final At GOLD = new At("POKEMON_GLDAAUE", 0xD1A1, 0xD1EB, 0xD57A, 0xD57C, 0xD57E, 0xD5B7, 0xD5E1, 0xD5FC,
            0xD616, 0xD683, 0xD7B7, 0xD9C6, 0xDA22, 0xDBE4, 0xDC24, 0x6C * 0x4000 + 0x0B74, 0x14 * 0x4000 + 0x1B0B,
            0xD1ED, 0xDA00, 0xDB8C, 0x6C * 0x4000, 0x6C * 0x4000 + 0x1574, 0x24 * 0x4000 + 0x2382,
            0x25 * 0x4000 + 0x00ED, 0xD8BC);
    static final At CRYSTAL = new At("PM_CRYSTAL\0BYTE", 0xD47B, 0xD4C4, 0xD855, 0xD857, 0xD859, 0xD892, 0xD8BC,
            0xD8D7, 0xD8F1, 0xD95E, 0xDA72, 0xDC7C, 0xDCD7, 0xDE99, 0xDED9, 0x14 * 0x4000 + 0x3384,
            0x14 * 0x4000 + 0x1424, 0xD4C6, 0xDCB5, 0xDE41, 0x72 * 0x4000, 0x72 * 0x4000 + 0x1F29,
            0x72 * 0x4000 + 0x28C3, 0x25 * 0x4000, 0xDB72);
    static final int PLAYER_ID = 0x1234;
    /** pret type ids (constants/type_constants.asm). */
    static final int NORMAL = 0x00, FLYING = 0x02, WATER = 0x15, FIRE = 0x14, GRASS = 0x16, PSYCHIC = 0x18, DRAGON = 0x1A;

    final At at;
    final byte[] rom;
    byte[] mem;

    Gen2Ram(At at) {
        this.at = at;
        rom = new byte[0x200000]; // 2 MB, as the real ones
        System.arraycopy(at.header.getBytes(), 0, rom, 0x134, 15);
        for (int dex = 1; dex <= 251; dex++) {
            rom[at.romBase + (dex - 1) * 0x20] = (byte) dex; // BASE_DEX_NO
            type(dex, NORMAL, NORMAL);
        }
        newGame();
    }

    /** A new game in play: a named player, nothing else (empty lists end in -1). */
    void newGame() {
        mem = new byte[at.unown + 27 - at.id]; // through wUnlockedUnowns
        put(at.id, (byte) (PLAYER_ID >> 8), (byte) PLAYER_ID);
        byte[] name = PokemonRedTest.encode("KRIS");
        System.arraycopy(name, 0, mem, at.id + 2 - at.id, name.length);
        set(at.party + 1, 0xFF);
        for (int list : new int[] {at.numItems, at.numKeyItems, at.numBalls, at.numPcItems}) set(list + 1, 0xFF);
    }

    void type(int dex, int t1, int t2) {
        rom[at.romBase + (dex - 1) * 0x20 + 7] = (byte) t1;
        rom[at.romBase + (dex - 1) * 0x20 + 8] = (byte) t2;
    }

    PokemonGen2 profile() {
        return PokemonGen2.forRom(rom);
    }

    Ram ram() {
        return new Ram(at.id, mem.clone());
    }

    /** Puts a party member in slot (0-5); ot = its original trainer id; dvs = {atk/def, spd/spc}. */
    void mon(int slot, int species, int level, int ot, int... dvs) {
        int count = Math.max(u8(at.party), slot + 1);
        set(at.party, count);
        set(at.party + 1 + slot, species);
        set(at.party + 1 + count, 0xFF);
        int base = at.party + 8 + slot * 0x30;
        set(base, species);
        put(base + 6, (byte) (ot >> 8), (byte) ot);
        if (dvs.length == 2) put(base + 0x15, (byte) dvs[0], (byte) dvs[1]);
        set(base + 0x1F, level);
        hp(slot, 2 * level + 10, 2 * level + 10);
    }

    /** party_struct HP and max HP, big-endian. */
    void hp(int slot, int hp, int max) {
        int base = at.party + 8 + slot * 0x30;
        put(base + 0x22, (byte) (hp >> 8), (byte) hp, (byte) (max >> 8), (byte) max);
    }

    void status(int slot, int status) {
        set(at.party + 8 + slot * 0x30 + 0x20, status);
    }

    void item(int slot, int item) {
        set(at.party + 8 + slot * 0x30 + 1, item);
    }

    void moves(int slot, int... moves) {
        for (int i = 0; i < moves.length; i++) set(at.party + 8 + slot * 0x30 + 2 + i, moves[i]);
    }

    void nickname(int slot, String name) {
        byte[] b = PokemonRedTest.encode(name);
        System.arraycopy(b, 0, mem, at.nicknames + slot * 11 - at.id, b.length);
    }

    void map(int group, int number) {
        set(at.mapGroup, group);
        set(at.mapGroup + 1, number);
    }

    void curBox(int box) {
        set(at.curBox, box);
    }

    void minutes(int n) {
        set(at.minutes, n);
    }

    /** Writes a species name into the ROM's PokemonNames (10 bytes each, padded with the terminator). */
    void speciesName(int dex, String name) {
        byte[] b = PokemonRedTest.encode(name);
        System.arraycopy(b, 0, rom, at.romNames + (dex - 1) * 10, b.length);
    }

    /** Rewrites a ROM name list ("@"-terminated names, ids 1..count); ids not given are named "X". */
    void nameList(int offset, int count, java.util.Map<Integer, String> names) {
        for (int id = 1; id <= count; id++) {
            byte[] b = PokemonRedTest.encode(names.getOrDefault(id, "X")); // ends in 0x50
            System.arraycopy(b, 0, rom, offset, b.length);
            offset += b.length;
        }
    }

    /**
     * Puts the player on map group.number, whose header gives this environment (1 TOWN, 2 ROUTE, 3 INDOOR, ...)
     * and landmark; the landmark's name goes to the end of its bank (raw bytes, so 0x1F line breaks can be
     * written). Map headers go to the end of their bank.
     */
    void location(int group, int number, int env, int landmark, byte[] name) {
        set(at.mapGroup, group);
        set(at.mapGroup + 1, number);
        int bank = at.mapGroups / 0x4000 * 0x4000, headers = 0x7000;
        rom[at.mapGroups + (group - 1) * 2] = (byte) headers;
        rom[at.mapGroups + (group - 1) * 2 + 1] = (byte) (headers >> 8);
        rom[bank - 0x4000 + headers + (number - 1) * 9 + 2] = (byte) env;
        rom[bank - 0x4000 + headers + (number - 1) * 9 + 5] = (byte) landmark;
        int names = at.landmarks / 0x4000 * 0x4000, at7 = 0x7F00;
        System.arraycopy(name, 0, rom, names - 0x4000 + at7, name.length);
        rom[at.landmarks + landmark * 4 + 2] = (byte) at7;
        rom[at.landmarks + landmark * 4 + 3] = (byte) (at7 >> 8);
    }

    void egg(int slot, int species) {
        mon(slot, species, 1, PLAYER_ID);
        set(at.party + 1 + slot, 0xFD);
    }

    /** Leaves only the first n party members. */
    void partySize(int n) {
        set(at.party, n);
        set(at.party + 1 + n, 0xFF);
    }

    void caught(int... dex) {
        for (int d : dex) mem[at.caught + (d - 1) / 8 - at.id] |= (byte) (1 << (d - 1) % 8);
    }

    void seen(int... dex) {
        for (int d : dex) mem[at.caught + 32 + (d - 1) / 8 - at.id] |= (byte) (1 << (d - 1) % 8);
    }

    void event(int flag, boolean on) {
        int a = at.events + flag / 8 - at.id;
        mem[a] = (byte) (on ? mem[a] | 1 << flag % 8 : mem[a] & ~(1 << flag % 8));
    }

    void badges(int johto, int kanto) {
        put(at.badges, (byte) johto, (byte) kanto);
    }

    void hallOfFame(int n) {
        set(at.hof, n);
    }

    void coins(int n) {
        put(at.coins, (byte) (n >> 8), (byte) n);
    }

    void hours(int n) {
        put(at.hours, (byte) (n >> 8), (byte) n);
    }

    void hms(int n) {
        for (int i = 0; i < 7; i++) set(at.tmsHms + 50 + i, i < n ? 1 : 0);
    }

    /** Replaces the Items pocket: id, quantity pairs. */
    void items(int... ids) {
        pairs(at.numItems, ids);
    }

    void balls(int... ids) {
        pairs(at.numBalls, ids);
    }

    void pc(int... ids) {
        pairs(at.numPcItems, ids);
    }

    void keyItems(int... ids) {
        set(at.numKeyItems, ids.length);
        for (int i = 0; i < ids.length; i++) set(at.numKeyItems + 1 + i, ids[i]);
        set(at.numKeyItems + 1 + ids.length, 0xFF);
    }

    void unown(int... forms) {
        for (int i = 0; i < forms.length; i++) set(at.unown + i, forms[i]);
    }

    void phone(int... contacts) {
        for (int i = 0; i < contacts.length; i++) set(at.phone + i, contacts[i]);
    }

    private void pairs(int countAt, int[] ids) {
        set(countAt, ids.length);
        for (int i = 0; i < ids.length; i++) put(countAt + 1 + i * 2, (byte) ids[i], (byte) 1);
        set(countAt + 1 + ids.length * 2, 0xFF);
    }

    int u8(int address) {
        return mem[address - at.id] & 0xFF;
    }

    void set(int address, int value) {
        mem[address - at.id] = (byte) value;
    }

    void put(int address, byte... bytes) {
        System.arraycopy(bytes, 0, mem, address - at.id, bytes.length);
    }
}
