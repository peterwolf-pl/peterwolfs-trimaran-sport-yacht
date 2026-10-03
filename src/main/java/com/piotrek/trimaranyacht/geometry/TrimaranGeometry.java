package com.piotrek.trimaranyacht.geometry;

import java.util.ArrayList;
import java.util.List;

/** Hand-authored model coordinates: +X starboard, -Z bow, -Y up; 16 units = one block.
 * No Minecraft types: the renderer, asset exporter and unit tests consume the same geometry. */
public final class TrimaranGeometry {
    public static final int ATLAS_SIZE = 2048;
    public static final float AMA_X = 80, MAST_Y = -448, MAST_Z = -28, BOOM_Y = -62;
    public static final float JIB_TOP_Y = -430, TACK_Y = -20, TACK_Z = -146;
    public static final float STAY_ANGLE = (float) Math.atan2(TACK_Z - MAST_Z, TACK_Y - JIB_TOP_Y);
    public static final float STAY_LENGTH = (float) Math.hypot(TACK_Z - MAST_Z, TACK_Y - JIB_TOP_Y);
    public static final int SAIL_BANDS = 48;
    public static final int NAVY = 0, CARBON = 1, YELLOW = 2, GLASS = 3,
        ICE = 4, METAL = 5, NET = 6, ROPE = 7;

    public record Box(float x, float y, float z, float w, float h, float d, int u, int v, int material) {}
    public static final class Part {
        public final String name;
        public final float x, y, z, rx, ry, rz;
        public final List<Box> boxes = new ArrayList<>();
        public final List<Part> children = new ArrayList<>();
        Part(String name, float x, float y, float z, float rx, float ry, float rz) {
            this.name = name; this.x = x; this.y = y; this.z = z;
            this.rx = rx; this.ry = ry; this.rz = rz;
        }
        public Part child(String name, float x, float y, float z) { return child(name, x, y, z, 0, 0, 0); }
        public Part child(String name, float x, float y, float z, float rx, float ry, float rz) {
            Part p = new Part(name, x, y, z, rx, ry, rz); children.add(p); return p;
        }
        public Part box(int material, float x, float y, float z, float w, float h, float d) {
            return uvBox(material, x, y, z, w, h, d, (material % 4) * 256, (material / 4) * 256);
        }
        public Part uvBox(int material, float x, float y, float z, float w, float h, float d, int u, int v) {
            boxes.add(new Box(x, y, z, w, h, d, u, v, material)); return this;
        }
    }

    public record Station(float z, float length, float halfWidth, boolean cockpit) {}
    public static List<Station> stations(boolean ama) {
        List<Station> result = new ArrayList<>();
        int start = ama ? -136 : -144, end = ama ? 104 : 112;
        for (int z = start; z < end; z += 8) {
            float width = halfWidth(ama, z + 4);
            result.add(new Station(z, 8, width, !ama && z >= 24 && z < 88));
        }
        return List.copyOf(result);
    }

    private static float halfWidth(boolean ama, float z) {
        float[][] points = ama
            ? new float[][]{{-136, 1}, {-120, 4}, {-88, 7}, {-32, 10}, {40, 10}, {80, 8}, {104, 5}}
            : new float[][]{{-144, 1}, {-128, 5}, {-96, 11}, {-48, 16}, {0, 18}, {64, 17}, {96, 12}, {112, 8}};
        for (int i = 1; i < points.length; i++) {
            if (z <= points[i][0]) {
                float t = (z - points[i - 1][0]) / (points[i][0] - points[i - 1][0]);
                return points[i - 1][1] + t * (points[i][1] - points[i - 1][1]);
            }
        }
        return points[points.length - 1][1];
    }

    /** Centers of the visible cleat horns; consumed directly by the entity. */
    public static float[] cleat(int index) {
        if (index < 0 || index >= 6) throw new IllegalArgumentException("cleat index");
        return new float[]{index < 3 ? -AMA_X : AMA_X, -16.5f, new float[]{-96, 0, 80}[index % 3]};
    }

    public static Part body() {
        Part root = new Part("root", 0, 0, 0, 0, 0, 0);
        Part hull = root.child("hull", 0, 24, 0);
        hull(hull.child("vaka", 0, 0, 0), false);
        hull(hull.child("ama_port", -AMA_X, 0, 0), true);
        hull(hull.child("ama_starboard", AMA_X, 0, 0), true);
        crossbeams(hull);
        cockpit(hull);
        rig(hull);
        fittings(hull);
        foils(hull);
        return root;
    }

    private static void hull(Part p, boolean ama) {
        for (Station s : stations(ama)) {
            float w = s.halfWidth(), top = ama ? -14 : -20;
            // Closed chine and shell sections meet at their end planes, not overlapping skins.
            p.box(CARBON, -w * .68f, -1, s.z(), w * 1.36f, 5, s.length());
            if (s.cockpit()) {
                p.box(NAVY, -w, top, s.z(), 2, 19, 8);
                p.box(NAVY, w - 2, top, s.z(), 2, 19, 8);
                p.box(CARBON, -w + 1.8f, -8, s.z(), 2 * w - 3.6f, 2, 8);
                p.box(CARBON, -w + .6f, -21, s.z(), 2.8f, 1.2f, 8);
                p.box(CARBON, w - 3.4f, -21, s.z(), 2.8f, 1.2f, 8);
            } else {
                p.box(NAVY, -w, top, s.z(), 2 * w, -1 - top, 8);
                p.box(CARBON, -w + .6f, top - 1, s.z(), 2 * w - 1.2f, 1.2f, 8);
            }
            // A pale boot stripe inset vertically but proud of each topside.
            for (int side : new int[]{-1, 1}) {
                p.box(ICE, side < 0 ? -w - .12f : w, -4, s.z() + .2f, .12f, .7f, 7.6f);
            }
        }
        if (!ama) {
            p.box(NAVY, -16, -20, 22, 32, 13, 2);
            p.box(NAVY, -13.5f, -20, 88, 27, 13, 2);
            p.box(CARBON, -7, -10, 112, 14, 2, 8); // boarding step outside transom
            p.box(METAL, -5, -8, 118, 1, 12, 1);
            p.box(METAL, 4, -8, 118, 1, 12, 1);
            p.box(METAL, -4, 0, 118, 8, 1, 1);
        }
    }

    private static void crossbeams(Part hull) {
        for (int side : new int[]{-1, 1}) {
            for (int aft = 0; aft < 2; aft++) {
                float z = aft == 0 ? -40 : 64;
                // Curved, swept carbon aka. Each segment has its own orientation.
                float[][] points = {{16, -22, z}, {32, -29, z - 4}, {50, -27, z - 5}, {66, -20, z - 2}, {80, -14, z}};
                for (int j = 1; j < points.length; j++) {
                    float[] a = points[j - 1], b = points[j];
                    spar(hull, "aka_" + side + "_" + aft + "_" + j, CARBON,
                        side * a[0], a[1], a[2], side * b[0], b[1], b[2], 4, 8);
                }
            }
            float x = side < 0 ? -70 : 20;
            hull.box(NET, x, -15, -32, 50, .4f, 44);
            hull.box(NET, x, -15, 12, 50, .4f, 44);
            // Raised bolt ropes and individual lashings, kept clear of the transparent net skin.
            spar(hull, "net_in_" + side, ROPE, side * 20, -15.8f, -32, side * 20, -15.8f, 56, .6f, .6f);
            spar(hull, "net_out_" + side, ROPE, side * 70, -15.8f, -32, side * 70, -15.8f, 56, .6f, .6f);
            for (int z = -28; z <= 52; z += 8) {
                hull.box(ROPE, side < 0 ? -73 : 70, -16.2f, z, 3, .5f, .7f);
                hull.box(METAL, side < 0 ? -74 : 73, -16.5f, z, 1, .6f, 1);
            }
        }
    }

    private static void cockpit(Part hull) {
        // Low aerodynamic yellow pod with a faceted smoked windscreen (not a cruising saloon).
        for (int i = 0; i < 8; i++) {
            float z = -24 + i * 6, w = i < 3 ? 8 + i * 2 : 14 - (i - 3) * .5f;
            float top = i < 3 ? -26 - i * 4 : -38 + (i - 3) * 1.2f;
            hull.box(YELLOW, -w, top, z, 2 * w, -20 - top, 6);
            if (i >= 1 && i <= 5) {
                hull.box(GLASS, -w - .15f, top + 2, z + .5f, .2f, 5, 5);
                hull.box(GLASS, w - .05f, top + 2, z + .5f, .2f, 5, 5);
            }
        }
        hull.box(GLASS, -9, -31, -18.2f, 18, 5, .4f);
        hull.box(CARBON, -7, -30, 23.8f, 14, 10, .5f); // companionway hatch
        hull.box(METAL, 4, -26, 24.5f, 1, 3, .7f);
        hull.box(CARBON, -10, -38.3f, -4, 20, .5f, 12); // solar/instrument roof inset
        for (int x = -8; x < 10; x += 4) hull.box(ICE, x, -38.85f, -3, .25f, .25f, 10);
        hull.box(CARBON, -3, -24, 66, 6, 16, 5); // helm pedestal
        hull.box(GLASS, -4, -25, 65, 8, 3, 2);
        hull.box(ICE, -2.5f, -24.5f, 64.8f, 2, 1, .2f);
        Part wheel = hull.child("wheel", 0, -24, 72);
        wheel.box(METAL, -1.4f, -1.4f, -.7f, 2.8f, 2.8f, 1.4f);
        for (int i = 0; i < 12; i++) {
            float a = (float)(i * Math.PI / 6);
            wheel.child("rim_" + i, (float)Math.cos(a) * 6, (float)Math.sin(a) * 6, 0, 0, 0, a)
                .box(CARBON, -.65f, -1.85f, -.65f, 1.3f, 3.7f, 1.3f);
        }
        for (int i = 0; i < 3; i++) wheel.child("spoke_" + i, 0, 0, 0, 0, 0, (float)(i * Math.PI * 2 / 3))
            .box(METAL, -.4f, -6, -.35f, .8f, 6, .7f);
        for (int side : new int[]{-1, 1}) {
            hull.box(CARBON, side < 0 ? -13 : 9, -13, 36, 4, 5, 24); // narrow working benches
            for (int z : new int[]{30, 54}) {
                Part winch = hull.child("winch_" + side + "_" + z, side * 12, -22, z);
                winch.box(METAL, -2.5f, -1, -2.5f, 5, 1, 5);
                winch.box(CARBON, -1.8f, -4, -1.8f, 3.6f, 3, 3.6f);
                winch.box(METAL, -2.2f, -4.6f, -2.2f, 4.4f, .6f, 4.4f);
            }
        }
    }

    private static void rig(Part hull) {
        for (int i = 0; i < 6; i++) {
            float bottom = -20 - i * 70, width = 5.5f - i * .45f;
            hull.box(CARBON, -width / 2, bottom - 70, MAST_Z - 2, width, 70, 6);
        }
        hull.box(METAL, -1.2f, MAST_Y, MAST_Z - 1, 2.4f, 8, 8);
        hull.box(YELLOW, -.8f, MAST_Y - 4, MAST_Z + 4, 1.6f, 2, 12);
        for (int y : new int[]{-170, -300}) {
            hull.box(CARBON, -22, y, MAST_Z, 44, 1.5f, 3);
        }
        for (int side : new int[]{-1, 1}) {
            spar(hull, "shroud_" + side, METAL, 0, -436, MAST_Z, side * 80, -16, 0, .45f, .45f);
            spar(hull, "bobstay_" + side, METAL, side * 16, -1, -56, 0, -18, -156, .5f, .5f);
        }
        spar(hull, "bowsprit", CARBON, 0, -18, -124, 0, -18, -158, 2, 2);
        Part boom = hull.child("boom", 0, BOOM_Y, MAST_Z);
        boom.box(CARBON, -1.6f, -.3f, 0, 3.2f, 3, 124);
        boom.box(METAL, -2.2f, -1, -2, 4.4f, 4, 5);
        Part open = boom.child("sail_open", 0, 0, 0);
        for (int i = 0; i < SAIL_BANDS; i++) {
            float chord = mainChord(i);
            // Each eight-unit band has a dedicated UV island. Artwork is continuous across the bands.
            open.uvBox(NAVY, -.5f, -8 * (i + 1), 2, 1, 8, chord,
                1024 + (i % 4) * 256, (i / 4) * 160);
            if (i % 6 == 0) {
                open.box(ICE, -.65f, -8 * (i + 1) + .3f, 2, .15f, .45f, chord);
                open.box(ICE, .5f, -8 * (i + 1) + .3f, 2, .15f, .45f, chord);
            }
        }
        Part furled = boom.child("sail_furled", 0, 0, 0);
        furled.box(NAVY, -2.7f, -4.5f, 3, 5.4f, 4.5f, 117);
        for (int z = 12; z < 120; z += 18) furled.box(ROPE, -3, -5, z, 6, 5.4f, 1);
        spar(hull, "vang", METAL, 0, -24, MAST_Z, 0, BOOM_Y + 3, 8, 1, 1);
        // Stay frame aligns local +Y to the forestay. Local yaw keeps every point of the luff fixed.
        Part stay = hull.child("stay_frame", 0, JIB_TOP_Y, MAST_Z, STAY_ANGLE, 0, 0);
        for (float y = 0; y < STAY_LENGTH; y += 100) {
            stay.box(METAL, -.3f, y, -.3f, .6f, Math.min(100, STAY_LENGTH - y), .6f);
        }
        Part jib = stay.child("jib", 0, 0, 0);
        int bands = 48;
        float step = (STAY_LENGTH - 46) / bands;
        for (int i = 0; i < bands; i++) {
            float chord = 3 + i * 1.9f;
            jib.box(NAVY, -.4f, i * step, 0, .8f, step, chord);
            if (i % 8 == 0) jib.box(ICE, -.55f, i * step + .3f, .4f, .15f, .5f, chord - .4f);
        }
        stay.box(CARBON, -1.3f, STAY_LENGTH - 7, -1.3f, 2.6f, 7, 2.6f);
        Part rolled = stay.child("jib_furled", 0, 0, 0);
        for (float y = 0; y < STAY_LENGTH - 5; y += 100) rolled.box(NAVY, -.9f, y, -.9f, 1.8f, Math.min(100, STAY_LENGTH - 5 - y), 1.8f);
    }

    public static float mainChord(int band) {
        float t = band / (float)(SAIL_BANDS - 1);
        return (float)Math.floor(120 - 92 * Math.pow(t, 1.35));
    }

    private static void fittings(Part hull) {
        for (int i = 0; i < 6; i++) {
            float[] c = cleat(i);
            hull.box(METAL, c[0] - 1, c[1] + .5f, c[2] - 1, 2, 1, 2);
            hull.box(METAL, c[0] - 1, c[1] - .5f, c[2] - 3, 2, 1, 6);
            hull.child("cleat_knot_" + i, c[0], c[1], c[2])
                .box(ROPE, -1.6f, -1.1f, -2.2f, 3.2f, 1.8f, 4.4f);
        }
        for (int side : new int[]{-1, 1}) {
            hull.box(YELLOW, side * 80 - 2, -16.2f, -118, 4, 1, 12);
            hull.box(GLASS, side * 80 - 3, -15.3f, 20, 6, .4f, 9);
            for (int z : new int[]{-64, 48}) hull.box(METAL, side * 80 - 1, -17, z, 2, 2, 2);
        }
        hull.box(YELLOW, -3, -21.4f, -108, 6, .5f, 18);
    }

    private static void foils(Part hull) {
        for (int side : new int[]{-1, 1}) {
            Part foil = hull.child("foil_" + side, side * 80, -9, -16, 0, 0, side * -.30f);
            foil.box(CARBON, -1.1f, 0, -5, 2.2f, 34, 10);
            foil.child("lifting_tip", 0, 33, 0, 0, 0, side * 1.20f)
                .box(CARBON, -1, 0, -4, 2, 22, 8);
            foil.box(YELLOW, -1.25f, 0, -5.1f, 2.5f, 3, 10.2f);
        }
        for (int i = -1; i <= 1; i++) {
            Part rudder = hull.child(i < 0 ? "rudder_port" : i > 0 ? "rudder_starboard" : "rudder_center", i * AMA_X, -8, i == 0 ? 110 : 102);
            rudder.box(METAL, -1, -5, -1, 2, 10, 2);
            rudder.box(CARBON, -.9f, 5, -1, 1.8f, 24, 9);
            rudder.box(CARBON, -10, 27, -1.5f, 20, 1.4f, 7);
        }
        hull.box(CARBON, -1.4f, -1, -8, 2.8f, 31, 14); // daggerboard
        hull.box(METAL, -1.5f, 2, 78, 3, 9, 3); // auxiliary saildrive
        Part prop = hull.child("propeller", 0, 10, 83);
        prop.box(METAL, -1, -1, -1, 2, 2, 2);
        for (int i = 0; i < 3; i++) prop.child("blade_" + i, 0, 0, 0, 0, 0, (float)(i * Math.PI * 2 / 3))
            .box(METAL, -.8f, -5, -.3f, 1.6f, 4, .6f);
    }

    /** Rectangular spar with its local +Y axis exactly between two endpoints. */
    private static void spar(Part parent, String name, int material, float ax, float ay, float az,
                             float bx, float by, float bz, float width, float depth) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float length = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        float rx = (float)Math.atan2(dz, Math.hypot(dx, dy));
        float rz = (float)Math.atan2(-dx, dy);
        Part p = parent.child(name, ax, ay, az, rx, 0, rz);
        for (float y = 0; y < length; y += 100) p.box(material, -width / 2, y, -depth / 2, width, Math.min(100, length - y), depth);
    }

    public static Part waterPatch() {
        Part root = new Part("root", 0, 0, 0, 0, 0, 0);
        Part patch = root.child("patch", 0, 24, 0);
        // Only hull interiors; never remove water from the open spaces beneath the trampolines.
        for (int side = -1; side <= 1; side++) {
            for (Station s : stations(side != 0)) {
                float w = s.halfWidth() - .4f;
                patch.box(NAVY, side * AMA_X - w, -5.3f, s.z(), 2 * w, .6f, s.length());
            }
        }
        return root;
    }

    private TrimaranGeometry() {}
}
