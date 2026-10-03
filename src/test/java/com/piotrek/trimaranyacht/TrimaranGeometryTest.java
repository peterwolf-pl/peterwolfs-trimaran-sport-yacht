package com.piotrek.trimaranyacht;

import com.piotrek.trimaranyacht.geometry.TrimaranGeometry;
import com.piotrek.trimaranyacht.geometry.TrimaranGeometry.Part;
import com.piotrek.trimaranyacht.geometry.TrimaranLayout;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TrimaranGeometryTest {
    private Stream<Part> parts(Part p) {
        return Stream.concat(Stream.of(p), p.children.stream().flatMap(this::parts));
    }
    private Part named(Part root, String name) {
        return parts(root).filter(p -> p.name.equals(name)).findFirst().orElseThrow();
    }

    @Test void dimensionsExceedCatamaranInBothAxes() {
        var center = TrimaranGeometry.stations(false);
        assertEquals(256, center.getLast().z() + center.getLast().length() - center.getFirst().z());
        assertEquals(240, TrimaranGeometry.stations(true).stream().mapToDouble(s -> s.length()).sum());
        assertEquals(11.25, TrimaranLayout.HALF_BEAM * 2);
        assertTrue(256 / 16.0 > 8 && TrimaranLayout.HALF_BEAM * 2 > 5);
        assertEquals(28, -TrimaranGeometry.MAST_Y / 16);
    }

    @Test void allBoxesHavePositiveFiniteDimensionsAndFitTheirUvIslands() {
        parts(TrimaranGeometry.body()).flatMap(p -> p.boxes.stream()).forEach(b -> {
            assertTrue(b.w() > 0 && b.h() > 0 && b.d() > 0);
            assertTrue(Float.isFinite(b.x() + b.y() + b.z() + b.w() + b.h() + b.d()));
            assertTrue(b.u() >= 0 && b.v() >= 0);
            assertTrue(b.u() + 2 * (b.w() + b.d()) <= 2048, "atlas U overflow: " + b);
            assertTrue(b.v() + b.d() + b.h() <= 2048, "atlas V overflow: " + b);
            if (b.u() < 1024) {
                assertTrue(2 * (b.w() + b.d()) <= 256, "material tile U overflow: " + b);
                assertTrue(b.d() + b.h() <= 256, "material tile V overflow: " + b);
            } else {
                assertTrue(b.d() + b.h() < 160, "sail island overlaps next row");
            }
        });
    }

    @Test void noDuplicateBoxesOrSiblingNames() {
        parts(TrimaranGeometry.body()).forEach(p -> {
            assertEquals(p.boxes.size(), new HashSet<>(p.boxes).size(), p.name);
            assertEquals(p.children.size(), p.children.stream().map(c -> c.name).distinct().count(), p.name);
        });
    }

    @Test void shellStationsMeetWithoutGapsOrOverlappingOuterSkins() {
        for (boolean ama : new boolean[]{false, true}) {
            var stations = TrimaranGeometry.stations(ama);
            for (int i = 1; i < stations.size(); i++) {
                assertEquals(stations.get(i - 1).z() + stations.get(i - 1).length(), stations.get(i).z(), .0001);
            }
            assertTrue(stations.getFirst().halfWidth() < stations.get(10).halfWidth());
        }
    }

    @Test void mainsailTapersFromFullFootToSquareHead() {
        float previous = Float.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            float chord = TrimaranGeometry.mainChord(i);
            assertTrue(chord <= previous);
            previous = chord;
        }
        assertEquals(120, TrimaranGeometry.mainChord(0));
        assertEquals(28, TrimaranGeometry.mainChord(47));
        assertEquals(-446, TrimaranGeometry.BOOM_Y - 48 * 8);
    }

    @Test void jibLuffRemainsOnStayForEitherTack() {
        Part tree = TrimaranGeometry.body();
        Part stay = named(tree, "stay_frame"), jib = named(tree, "jib");
        assertEquals(TrimaranGeometry.STAY_ANGLE, stay.rx);
        double c = Math.cos(stay.rx), s = Math.sin(stay.rx);
        assertEquals(TrimaranGeometry.TACK_Y, stay.y + c * TrimaranGeometry.STAY_LENGTH, .0001);
        assertEquals(TrimaranGeometry.TACK_Z, stay.z + s * TrimaranGeometry.STAY_LENGTH, .0001);
        var panels = jib.boxes.stream().filter(b -> b.material() == TrimaranGeometry.NAVY).toList();
        double previous = 0;
        for (var b : panels) {
            assertEquals(0, b.z());
            assertTrue(b.d() >= previous);
            previous = b.d();
            for (double angle : new double[]{-.6, 0, .6}) {
                // Point on the luff (local 0,y,0), rotated around local Y, remains unchanged.
                double x = 0 * Math.cos(angle) + b.z() * Math.sin(angle);
                double z = -0 * Math.sin(angle) + b.z() * Math.cos(angle);
                assertEquals(0, x, .000001);
                assertEquals(stay.z + s * b.y(), stay.z + s * b.y() + c * z, .000001);
            }
        }
    }

    @Test void threeRuddersHaveIndependentPivots() {
        Part root = TrimaranGeometry.body();
        assertEquals(-80, named(root, "rudder_port").x);
        assertEquals(0, named(root, "rudder_center").x);
        assertEquals(80, named(root, "rudder_starboard").x);
        assertEquals(102, named(root, "rudder_port").z);
        assertEquals(110, named(root, "rudder_center").z);
    }

    @Test void cleatCentersMatchVisibleHornGeometry() {
        Part hull = named(TrimaranGeometry.body(), "hull");
        for (int i = 0; i < 6; i++) {
            float[] c = TrimaranGeometry.cleat(i);
            assertTrue(hull.boxes.stream().anyMatch(b -> b.material() == TrimaranGeometry.METAL
                && b.w() == 2 && b.h() == 1 && b.d() == 6
                && b.x() + b.w() / 2 == c[0] && b.y() + b.h() / 2 == c[1] && b.z() + b.d() / 2 == c[2]));
            float[] opposite = TrimaranGeometry.cleat((i + 3) % 6);
            assertEquals(-c[0], opposite[0]);
            assertEquals(c[2], opposite[2]);
        }
    }

    @Test void maskMatchesThreeHullSectionsAndLeavesWaterBetweenThem() {
        Part patch = named(TrimaranGeometry.waterPatch(), "patch");
        assertEquals(TrimaranLayout.HULLS.size(), patch.boxes.size());
        for (var b : patch.boxes) {
            double minX = b.x() / 16.0, maxX = (b.x() + b.w()) / 16.0;
            double minZ = -(b.z() + b.d()) / 16.0, maxZ = -b.z() / 16.0;
            assertTrue(TrimaranLayout.HULLS.stream().anyMatch(s -> minX >= s.minX() && maxX <= s.maxX()
                && minZ >= s.minZ() && maxZ <= s.maxZ()));
            assertTrue(maxX < 2.0 || minX > 4.0, "mask must never span starboard open water");
            assertTrue(-b.y() / 16 >= .33 && -(b.y() + b.h()) / 16 < .33, "mask at equilibrium waterline");
        }
    }

    @Test void workingDeckSupportsHelmHikeNetBowAndBoardingStep() {
        assertEquals(.5, TrimaranLayout.deckHeight(0, TrimaranLayout.HELM_Z));
        assertEquals(21.0 / 16, TrimaranLayout.deckHeight(.86, TrimaranLayout.HELM_Z));
        assertEquals(15.0 / 16, TrimaranLayout.deckHeight(3, 0));
        assertEquals(21.0 / 16, TrimaranLayout.deckHeight(0, 8.8));
        assertEquals(10.0 / 16, TrimaranLayout.deckHeight(0, -7.3));
        assertTrue(TrimaranLayout.deckHeight(0, 0) > 2, "pod roof");
        assertTrue(Double.isNaN(TrimaranLayout.deckHeight(3, 6)), "open fore triangle is water, not invisible deck");
        assertTrue(Double.isNaN(TrimaranLayout.deckHeight(7, 0)));
    }

    @Test void satRejectsGapAndAcceptsAllThreeHullsAtEveryHeading() {
        for (int yaw = 0; yaw < 360; yaw += 15) {
            double c = Math.cos(Math.toRadians(yaw)), n = Math.sin(Math.toRadians(yaw));
            for (double x : new double[]{-5, 0, 5}) {
                double wx = c * x, wz = n * x;
                assertTrue(TrimaranLayout.hullIntersects(0, 0, 0, yaw, wx - .1, 0, wz - .1, wx + .1, .2, wz + .1));
            }
            double wx = c * 3, wz = n * 3;
            assertFalse(TrimaranLayout.hullIntersects(0, 0, 0, yaw, wx - .1, 0, wz - .1, wx + .1, .2, wz + .1));
        }
    }

    @Test void underwaterAndOverheadBlocksDoNotHitTopsides() {
        assertFalse(TrimaranLayout.hullIntersects(0, 0, 0, 0, -.1, 3, -.1, .1, 4, .1));
        assertFalse(TrimaranLayout.hullIntersects(0, 0, 0, 0, -.1, -3, -.1, .1, -2, .1));
    }
}
