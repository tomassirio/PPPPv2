package dev.pppp.service.game;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Draws a map of Pokémon Crystal the way the game does, for the sign-in page's camera: the map's blocks from its
 * attributes, each block four by four tiles of its tileset (Tilesets: GFX, Meta, PalMap), the town's roof
 * (MapGroupRoofs, Roofs, RoofPals), and the environment's palettes for the time of day (EnvironmentColorsPointers,
 * TilesetBGPalette); round it, the map's border block, as the game draws past the edges. Addresses from
 * pokecrystal11.sym; formats from pret's home/map.asm and engine/gfx/color.asm. Special palettes (LoadSpecialMapPalette:
 * Ice Path, Radio Tower and the like) are not drawn.
 */
final class Gen2MapArt {
    static final int BLOCK = 32, MORN = 0, DAY = 1, NITE = 2;
    private static final int MAP_GROUPS = rom(0x25, 0x4000), TILESETS = rom(0x13, 0x5596),
            ENVIRONMENT_COLORS = rom(0x02, 0x7279), BG_PALETTES = rom(0x02, 0x7319), ROOF_PALS = rom(0x02, 0x7569),
            MAP_GROUP_ROOFS = rom(0x07, 0x4021), ROOFS = rom(0x07, 0x403c), PALMAP_BANK = 0x13,
            TILESET_ENTRY = 15, HEADER = 9, ROOF_LENGTH = 9, ROOF_TILE = 0x0A, TILESET_JOHTO = 1,
            TILESET_JOHTO_MODERN = 2, TILESET_BATTLE_TOWER_OUTSIDE = 4, TOWN = 1, ROUTE = 2,
            OVERWORLD_SPRITES = rom(0x05, 0x4736), MAP_OBJECT_PALS = rom(0x02, 0x7469),
            JOHTO_GRASS = rom(0x0A, 0x65E9), KANTO_GRASS = rom(0x0A, 0x7274), GRASS_ENTRY = 47, GRASS_SLOTS = 7,
            NUM_OVERWORLD_SPRITES = 0x66, WALKING_SPRITE = 1, STILL_SPRITE = 3, OBJECT = 13,
            COLL_LONG_GRASS = 0x14, COLL_TALL_GRASS = 0x18;

    private final byte[] rom;
    private final String[][] mapIds;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Map<String, Info> infos = new ConcurrentHashMap<>();

    Gen2MapArt(byte[] rom) {
        this.rom = rom;
        this.mapIds = PokemonGen2.mapConstants(true);
    }

    /** Whether the ROM is Crystal (the addresses above are Crystal 1.1's). */
    static boolean fits(byte[] rom) {
        return rom.length >= 0x200000 && new String(rom, 0x134, 15, java.nio.charset.StandardCharsets.US_ASCII)
                .equals("PM_CRYSTAL\0BYTE");
    }

    /** The map (a pret map constant) as a PNG with margin blocks of border all round; null for an unknown map. */
    byte[] png(String map, int time, int margin) {
        int[] at = find(map);
        if (at == null || time < MORN || time > NITE) return null;
        return cache.computeIfAbsent(map + ":" + time + ":" + margin, k -> draw(at[0], at[1], time, margin));
    }

    /**
     * What the sign-in page puts on a map besides its picture, at a time of day.
     * neighbours: connected maps drawn into the picture, by where their top-left block sits relative to this map's
     * (in blocks; 2 steps each). npcs: the people and things standing on it. grass: steps of tall grass, as {x, y}.
     * wild: the Pokédex numbers that can jump out of that grass at that time.
     */
    record Info(int width, int height, java.util.List<Neighbour> neighbours, java.util.List<Npc> npcs,
                java.util.List<int[]> grass, java.util.List<Integer> wild) {}

    record Neighbour(String map, int x, int y) {}

    /**
     * sprite: a SPRITE_* id (sheet: {@link #npcSheet}); palette 0-7 (MapObjectPals); x, y: their step; move: still,
     * wander, spin, updown or leftright; rx, ry: how far they roam; facing: where they look first; frames: in the
     * sheet (6 walking, 3 standing, 1 still).
     */
    record Npc(int sprite, int palette, int x, int y, String move, int rx, int ry, String facing, int frames) {}

    Info info(String map, int time, int margin) {
        int[] at = find(map);
        if (at == null || time < MORN || time > NITE) return null;
        return infos.computeIfAbsent(map + ":" + time + ":" + margin, k -> describe(at[0], at[1], time, margin));
    }

    private Info describe(int group, int number, int time, int margin) {
        var maps = world(group, number, time, margin);
        Layer home = maps.getFirst();
        var neighbours = new java.util.ArrayList<Neighbour>();
        for (Layer m : maps.subList(1, maps.size())) neighbours.add(new Neighbour(mapIds[m.group()][m.number()], m.x(), m.y()));
        int h = header(group, number), attr = attributes(h);
        // Tall grass, from the tileset's collision: four bytes a block, one per step (top left, top right, ...).
        int ts = TILESETS + (rom[h + 1] & 0xFF) * TILESET_ENTRY, coll = rom(rom[ts + 6] & 0xFF, u16(ts + 7));
        var grass = new java.util.ArrayList<int[]>();
        for (int by = 0; by < home.height(); by++) {
            for (int bx = 0; bx < home.width(); bx++) {
                int b = rom[home.blocks() + by * home.width() + bx] & 0xFF;
                for (int k = 0; k < 4; k++) {
                    int c = rom[coll + b * 4 + k] & 0xFF;
                    if (c == COLL_TALL_GRASS || c == COLL_LONG_GRASS) grass.add(new int[] {bx * 2 + k % 2, by * 2 + k / 2});
                }
            }
        }
        return new Info(home.width(), home.height(), neighbours, npcs(attr, time), grass, wild(group, number, time));
    }

    /**
     * The map's object events (macros/scripts/maps.asm): after two filler bytes, the warps (5 bytes each), coord
     * events (8), bg events (5), then objects of 13 bytes: sprite, y + 4, x + 4, movement, radius (y, x nybbles),
     * hours h1, h2, palette and type nybbles, sight, script, event flag. Kept: overworld sprites that always appear
     * (no event flag) at this time of day; the Pokémon and variable sprites (the rival, the Pokémon walking) are not.
     */
    private java.util.List<Npc> npcs(int attr, int time) {
        int at = rom(rom[attr + 6] & 0xFF, u16(attr + 9)) + 2;
        at += 1 + (rom[at] & 0xFF) * 5;
        at += 1 + (rom[at] & 0xFF) * 8;
        at += 1 + (rom[at] & 0xFF) * 5;
        int count = rom[at++] & 0xFF;
        var out = new java.util.ArrayList<Npc>();
        int hour = time == MORN ? 7 : time == DAY ? 14 : 22;
        for (int i = 0; i < count; i++, at += OBJECT) {
            int sprite = rom[at] & 0xFF, h1 = rom[at + 5] & 0xFF, h2 = rom[at + 6] & 0xFF, flag = u16(at + 11);
            if (sprite < 1 || sprite > NUM_OVERWORLD_SPRITES || flag != 0xFFFF) continue;
            boolean shown = h1 == 0xFF ? h2 == 0xFF || (h2 & 1 << time) != 0
                    : h1 == h2 || (h1 < h2 ? hour >= h1 && hour < h2 : hour >= h1 || hour < h2);
            if (!shown) continue;
            int pal = (rom[at + 7] & 0xFF) >> 4, radius = rom[at + 4] & 0xFF;
            int palette = pal != 0 ? pal & 7 : rom[OVERWORLD_SPRITES + (sprite - 1) * 6 + 5] & 7;
            int movement = rom[at + 3] & 0xFF;
            String move = switch (movement) {
                case 0x02, 0x24 -> "wander";
                case 0x03, 0x0A -> "spin";
                case 0x04 -> "updown";
                case 0x05 -> "leftright";
                default -> "still";
            };
            String facing = switch (movement) {
                case 0x07 -> "up";
                case 0x08 -> "left";
                case 0x09 -> "right";
                default -> "down";
            };
            int type = rom[OVERWORLD_SPRITES + (sprite - 1) * 6 + 4] & 0xFF;
            int frames = type == WALKING_SPRITE ? 6 : type == STILL_SPRITE ? 1 : 3;
            out.add(new Npc(sprite, palette, (rom[at + 2] & 0xFF) - 4, (rom[at + 1] & 0xFF) - 4,
                    frames == 6 ? move : "still", radius & 0xF, radius >> 4, facing, frames));
        }
        return out;
    }

    /** The species in the map's grass at a time of day (data/wild/*_grass.asm: map id, 3 rates, 7 slots a time). */
    private java.util.List<Integer> wild(int group, int number, int time) {
        var out = new java.util.LinkedHashSet<Integer>();
        for (int table : new int[] {JOHTO_GRASS, KANTO_GRASS}) {
            for (int at = table; (rom[at] & 0xFF) != 0xFF; at += GRASS_ENTRY) {
                if ((rom[at] & 0xFF) != group || (rom[at + 1] & 0xFF) != number) continue;
                for (int s = 0; s < GRASS_SLOTS; s++) out.add(rom[at + 5 + time * GRASS_SLOTS * 2 + s * 2 + 1] & 0xFF);
            }
        }
        return java.util.List.copyOf(out);
    }

    /**
     * An overworld sprite's sheet (data/sprites/sprites.asm OverworldSprites: pointer, length, bank, type, palette),
     * 16 px wide, a 2x2-tile frame under the other: down, up, left, then for walking sprites their stepping frames;
     * one frame for still ones. Colour 0 is see-through; the rest from MapObjectPals at the time of day.
     */
    byte[] npcSheet(int sprite, int palette, int time) {
        if (sprite < 1 || sprite > NUM_OVERWORLD_SPRITES || palette < 0 || palette > 7 || time < MORN || time > NITE) return null;
        return cache.computeIfAbsent("npc:" + sprite + ":" + palette + ":" + time, k -> {
            int e = OVERWORLD_SPRITES + (sprite - 1) * 6, type = rom[e + 4] & 0xFF;
            int gfx = rom(rom[e + 3] & 0xFF, u16(e));
            int tiles = type == STILL_SPRITE ? 4 : type == WALKING_SPRITE ? 24 : 12;
            int pal = MAP_OBJECT_PALS + time * 64 + palette * 8;
            int[] argb = {0, 0xFF000000 | Gen2Pic.rgb(rom, pal + 2), 0xFF000000 | Gen2Pic.rgb(rom, pal + 4),
                    0xFF000000 | Gen2Pic.rgb(rom, pal + 6)};
            var img = new BufferedImage(16, tiles / 4 * 16, BufferedImage.TYPE_INT_ARGB);
            for (int t = 0; t < tiles; t++) {
                for (int row = 0; row < 8; row++) {
                    int lo = rom[gfx + t * 16 + row * 2] & 0xFF, hi = rom[gfx + t * 16 + row * 2 + 1] & 0xFF;
                    for (int col = 0; col < 8; col++) {
                        int v = (lo >> 7 - col & 1) | (hi >> 7 - col & 1) << 1;
                        img.setRGB(t % 2 * 8 + col, t / 2 * 8 + row, argb[v]);
                    }
                }
            }
            var out = new ByteArrayOutputStream();
            try {
                ImageIO.write(img, "png", out);
            } catch (IOException ex) {
                return null;
            }
            return out.toByteArray();
        });
    }

    /** Whether the map is a town or a route (on the world map, not inside a building, cave or gate). */
    boolean outdoor(String map) {
        int[] at = find(map);
        if (at == null) return false;
        int env = rom[header(at[0], at[1]) + 2] & 0xFF;
        return env == TOWN || env == ROUTE;
    }

    /** Width and height in blocks of a map, or null. */
    int[] size(String map) {
        int[] at = find(map);
        if (at == null) return null;
        int attr = attributes(header(at[0], at[1]));
        return new int[] {rom[attr + 2] & 0xFF, rom[attr + 1] & 0xFF};
    }

    private int[] find(String map) {
        for (int g = 1; g < mapIds.length; g++) {
            for (int n = 1; n < mapIds[g].length; n++) if (mapIds[g][n].equals(map)) return new int[] {g, n};
        }
        return null;
    }

    private int header(int group, int number) {
        return rom(0x25, u16(MAP_GROUPS + (group - 1) * 2)) + (number - 1) * HEADER;
    }

    private int attributes(int header) {
        return rom(rom[header] & 0xFF, u16(header + 3));
    }

    /** One map ready to draw: its blocks and how to draw a block. */
    private record Layer(int group, int number, int x, int y, int width, int height, int blocks, int border,
                         byte[] tiles, int meta, int palmap, int[][] pals) {}

    private Layer layer(int group, int number, int x, int y, int time) {
        int h = header(group, number), tileset = rom[h + 1] & 0xFF, env = rom[h + 2] & 0xFF, attr = attributes(h);
        int ts = TILESETS + tileset * TILESET_ENTRY;
        byte[] gfx = Gen2Pic.decompress(rom, rom(rom[ts] & 0xFF, u16(ts + 1)));
        byte[] tiles = java.util.Arrays.copyOf(gfx, Math.max(gfx.length, 0xC0 * 16));
        int roof = rom[MAP_GROUP_ROOFS + group] & 0xFF;
        if (roof != 0xFF && (tileset == TILESET_JOHTO || tileset == TILESET_JOHTO_MODERN
                || tileset == TILESET_BATTLE_TOWER_OUTSIDE)) {
            System.arraycopy(rom, ROOFS + roof * ROOF_LENGTH * 16, tiles, ROOF_TILE * 16, ROOF_LENGTH * 16);
        }
        return new Layer(group, number, x, y, rom[attr + 2] & 0xFF, rom[attr + 1] & 0xFF,
                rom(rom[attr + 3] & 0xFF, u16(attr + 4)), rom[attr] & 0xFF, tiles, rom(rom[ts + 3] & 0xFF, u16(ts + 4)),
                rom(PALMAP_BANK, u16(ts + 13)), palettes(group, env, time));
    }

    /**
     * The map and, outward through their connections (data/maps/attributes.asm: after the attributes, one 12-byte
     * entry per direction set in the connections byte, north, south, west, east), every map that reaches into the
     * frame. The entry's y/x adjustment holds -2 x the connection's offset, which places the neighbour.
     */
    private java.util.List<Layer> world(int group, int number, int time, int margin) {
        java.util.List<Layer> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Layer> todo = new java.util.ArrayDeque<>();
        Layer home = layer(group, number, 0, 0, time);
        todo.add(home);
        seen.add(group + "." + number);
        while (!todo.isEmpty()) {
            Layer m = todo.poll();
            out.add(m);
            int attr = attributes(header(m.group(), m.number())), flags = rom[attr + 11] & 0xFF, entry = attr + 12;
            for (int dir : new int[] {NORTH, SOUTH, WEST, EAST}) {
                if ((flags & dir) == 0) continue;
                int g = rom[entry] & 0xFF, n = rom[entry + 1] & 0xFF;
                int adjY = rom[entry + 8], adjX = rom[entry + 9]; // signed
                entry += CONNECTION;
                if (!seen.add(g + "." + n) || g < 1 || g >= mapIds.length || n < 1 || n >= mapIds[g].length) continue;
                int t = attributes(header(g, n)), tw = rom[t + 2] & 0xFF, th = rom[t + 1] & 0xFF;
                int x = m.x(), y = m.y();
                switch (dir) {
                    case NORTH -> { x += -adjX / 2; y -= th; }
                    case SOUTH -> { x += -adjX / 2; y += m.height(); }
                    case WEST -> { x -= tw; y += -adjY / 2; }
                    default -> { x += m.width(); y += -adjY / 2; }
                }
                boolean inFrame = x < home.width() + margin && x + tw > -margin && y < home.height() + margin
                        && y + th > -margin;
                if (inFrame) todo.add(layer(g, n, x, y, time));
            }
        }
        return out;
    }

    private static final int NORTH = 8, SOUTH = 4, WEST = 2, EAST = 1, CONNECTION = 12; // constants/map_data_constants.asm

    private byte[] draw(int group, int number, int time, int margin) {
        var maps = world(group, number, time, margin);
        Layer home = maps.getFirst();
        int w = home.width() + 2 * margin, ht = home.height() + 2 * margin;
        var img = new BufferedImage(w * BLOCK, ht * BLOCK, BufferedImage.TYPE_INT_RGB);
        for (int by = 0; by < ht; by++) {
            for (int bx = 0; bx < w; bx++) block(img, home, home.border(), bx, by); // the border, past every edge
        }
        for (int i = maps.size() - 1; i >= 0; i--) { // neighbours first, the map itself on top
            Layer m = maps.get(i);
            for (int my = 0; my < m.height(); my++) {
                for (int mx = 0; mx < m.width(); mx++) {
                    int bx = m.x() + mx + margin, by = m.y() + my + margin;
                    if (bx < 0 || by < 0 || bx >= w || by >= ht) continue;
                    block(img, m, rom[m.blocks() + my * m.width() + mx] & 0xFF, bx, by);
                }
            }
        }
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            return null;
        }
        return out.toByteArray();
    }

    private void block(BufferedImage img, Layer m, int b, int bx, int by) {
        for (int k = 0; k < 16; k++) {
            int t = rom[m.meta() + b * 16 + k] & 0xFF;
            int idx = t < 0x80 ? t : 0x60 + t - 0x80; // $80+: the second half, in VRAM bank 1
            int pb = rom[m.palmap() + t / 2] & 0xFF, pal = (t % 2 == 1 ? pb >> 4 : pb) & 7;
            tile(img, m.tiles(), idx, m.pals()[pal], bx * BLOCK + k % 4 * 8, by * BLOCK + k / 4 * 8);
        }
    }

    /** The eight background palettes of an environment at a time of day, and the town's roof over palette 6. */
    private int[][] palettes(int group, int env, int time) {
        int table = rom(0x02, u16(ENVIRONMENT_COLORS + (env & 7) * 2)) + time * 8;
        int[][] pals = new int[8][4];
        for (int p = 0; p < 8; p++) {
            for (int c = 0; c < 4; c++) pals[p][c] = Gen2Pic.rgb(rom, BG_PALETTES + (rom[table + p] & 0xFF) * 8 + c * 2);
        }
        if (env == TOWN || env == ROUTE) {
            int roof = ROOF_PALS + group * 8 + (time == NITE ? 4 : 0);
            pals[6][1] = Gen2Pic.rgb(rom, roof);
            pals[6][2] = Gen2Pic.rgb(rom, roof + 2);
        }
        return pals;
    }

    /**
     * The Pokégear's town map of Johto or Kanto as a 160x144 PNG, as Pokegear_LoadGFX and _TownMap draw it
     * (engine/pokegear/pokegear.asm): TownMapGFX at tile 0 and PokegearGFX at tile $30 (both LZ), the screen's tiles
     * from JohtoMap or KantoMap (20x18 ids, $ff-terminated; FillTownMap), each tile's palette from TownMapPals.PalMap
     * (a nybble per tile id, low nybble first; $60 and up palette 0), colours from MalePokegearPals. Addresses from
     * pokecrystal11.sym. Null when the bytes are not what pret has there.
     */
    byte[] townMap(boolean kanto) {
        int map = rom(0x24, kanto ? 0x6168 : 0x5FFF), palMap = rom(0x24, 0x5F4B), pals = rom(0x02, 0x7729);
        byte[] tiles = new byte[0x80 * 16];
        byte[] townMap = Gen2Pic.decompress(rom, rom(0x3E, 0x4BA0)), pokegear = Gen2Pic.decompress(rom, rom(0x77, 0x62E4));
        System.arraycopy(townMap, 0, tiles, 0, Math.min(townMap.length, 0x30 * 16));
        System.arraycopy(pokegear, 0, tiles, 0x30 * 16, Math.min(pokegear.length, tiles.length - 0x30 * 16));
        if ((rom[map + 20 * 18] & 0xFF) != 0xFF) return null; // FillTownMap's terminator right after the screen
        var img = new BufferedImage(160, 144, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 20 * 18; i++) {
            int t = rom[map + i] & 0xFF;
            int pal = t >= 0x60 ? 0 : (rom[palMap + t / 2] >> (t % 2 == 1 ? 4 : 0)) & 7;
            int[] colors = new int[4];
            for (int c = 0; c < 4; c++) colors[c] = Gen2Pic.rgb(rom, pals + pal * 8 + c * 2);
            tile(img, tiles, t, colors, i % 20 * 8, i / 20 * 8);
        }
        var out = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(img, "png", out);
        } catch (java.io.IOException e) {
            return null;
        }
        return out.toByteArray();
    }

    private static void tile(BufferedImage img, byte[] tiles, int idx, int[] pal, int x, int y) {
        if (idx * 16 + 16 > tiles.length) return;
        for (int row = 0; row < 8; row++) {
            int lo = tiles[idx * 16 + row * 2] & 0xFF, hi = tiles[idx * 16 + row * 2 + 1] & 0xFF;
            for (int col = 0; col < 8; col++) {
                int v = (lo >> 7 - col & 1) | (hi >> 7 - col & 1) << 1;
                img.setRGB(x + col, y + row, pal[v]);
            }
        }
    }

    private int u16(int at) {
        return (rom[at] & 0xFF) | (rom[at + 1] & 0xFF) << 8;
    }

    private static int rom(int bank, int address) {
        return bank * 0x4000 + address - 0x4000;
    }
}
