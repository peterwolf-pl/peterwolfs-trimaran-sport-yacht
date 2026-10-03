package com.piotrek.trimaranyacht.geometry;

import java.util.ArrayList;
import java.util.List;

/** Physical footprint in entity-local blocks. Open water between the three hulls stays open. */
public final class TrimaranLayout {
    public record HullSection(double minX, double minZ, double maxX, double maxZ, boolean cockpit, boolean ama) {}
    public static final List<HullSection> HULLS = sections();
    public static final double MIN_Z = -7.5, MAX_Z = 9.875, HALF_BEAM = 5.625;
    public static final double COCKPIT_HEIGHT = .5;
    public static final double HELM_Z = -5.0;

    private static List<HullSection> sections() {
        List<HullSection> result = new ArrayList<>();
        for (int side = -1; side <= 1; side++) {
            for (var station : TrimaranGeometry.stations(side != 0)) {
                double center = side * TrimaranGeometry.AMA_X / 16;
                double width = station.halfWidth() / 16;
                result.add(new HullSection(center - width, -(station.z() + station.length()) / 16,
                    center + width, -station.z() / 16, station.cockpit(), side != 0));
            }
        }
        return List.copyOf(result);
    }

    public static double deckHeight(double x, double z) {
        double mx = x * 16, mz = -z * 16;
        if (Math.abs(mx) <= 7 && mz >= 112 && mz <= 120) return 10.0 / 16;
        // Pod roof: same eight six-unit stations as the visual yellow shelter.
        if (mz >= -24 && mz < 24) {
            int i = (int)((mz + 24) / 6);
            double w = i < 3 ? 8 + i * 2 : 14 - (i - 3) * .5;
            double top = i < 3 ? -26 - i * 4 : -38 + (i - 3) * 1.2;
            if (Math.abs(mx) <= w) return -top / 16;
        }
        for (HullSection s : HULLS) {
            if (x >= s.minX && x <= s.maxX && z >= s.minZ && z <= s.maxZ) {
                if (s.ama) return 15.0 / 16;
                double edge = Math.min(x - s.minX, s.maxX - x) * 16;
                if (s.cockpit && edge > 3.4) {
                    if (mz >= 36 && mz <= 60 && Math.abs(mx) >= 9 && Math.abs(mx) <= 13) return 13.0 / 16;
                    return COCKPIT_HEIGHT;
                }
                return 21.0 / 16;
            }
        }
        double ax = Math.abs(mx);
        if (ax >= 20 && ax <= 70 && mz >= -32 && mz <= 56) return 15.0 / 16;
        // Each arched aka has a thin walkable upper edge, not a rectangular invisible bridgedeck.
        if (ax >= 16 && ax <= 80) {
            double[] xs = {16, 32, 50, 66, 80}, ys = {-22, -29, -27, -20, -14}, zs = {0, -4, -5, -2, 0};
            for (double base : new double[]{-40, 64}) {
                for (int i = 1; i < xs.length; i++) {
                    if (ax >= xs[i - 1] && ax <= xs[i]) {
                        double t = (ax - xs[i - 1]) / (xs[i] - xs[i - 1]);
                        double beamZ = base + zs[i - 1] + t * (zs[i] - zs[i - 1]);
                        if (Math.abs(mz - beamZ) <= 4) return -(ys[i - 1] + t * (ys[i] - ys[i - 1]) - 2) / 16;
                    }
                }
            }
        }
        return Double.NaN;
    }

    /** Four-axis SAT for a local rectangle versus an axis-aligned world block shape. */
    public static boolean intersects(HullSection s, double boatX, double boatZ, double yaw,
                                     double minX, double minZ, double maxX, double maxZ) {
        double c = Math.cos(Math.toRadians(yaw)), n = Math.sin(Math.toRadians(yaw));
        double localX = (s.minX + s.maxX) / 2, localZ = (s.minZ + s.maxZ) / 2;
        double dx = (minX + maxX) / 2 - (boatX + c * localX - n * localZ);
        double dz = (minZ + maxZ) / 2 - (boatZ + n * localX + c * localZ);
        double hx = (s.maxX - s.minX) / 2, hz = (s.maxZ - s.minZ) / 2;
        double bx = (maxX - minX) / 2, bz = (maxZ - minZ) / 2;
        double ac = Math.abs(c), an = Math.abs(n);
        return Math.abs(dx) < bx + hx * ac + hz * an
            && Math.abs(dz) < bz + hx * an + hz * ac
            && Math.abs(dx * c + dz * n) < hx + bx * ac + bz * an
            && Math.abs(-dx * n + dz * c) < hz + bx * an + bz * ac;
    }

    public static boolean hullIntersects(double boatX, double boatY, double boatZ, double yaw,
                                         double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxY <= boatY - .25 || minY >= boatY + 1.3125) return false;
        for (HullSection s : HULLS) {
            if (s.ama && minY >= boatY + .9375) continue;
            if (intersects(s, boatX, boatZ, yaw, minX, minZ, maxX, maxZ)) return true;
        }
        return false;
    }
    private TrimaranLayout() {}
}
