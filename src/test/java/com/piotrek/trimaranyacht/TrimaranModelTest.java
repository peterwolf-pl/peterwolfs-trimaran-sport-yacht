package com.piotrek.trimaranyacht;

import com.piotrek.trimaranyacht.client.TrimaranSportYachtModel;
import com.piotrek.trimaranyacht.client.TrimaranSportYachtRenderState;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Headless bake/animation tests; does not start Minecraft or a client GameTest. */
class TrimaranModelTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void bothRegisteredLayersBake() {
        var root = TrimaranSportYachtModel.createBodyLayer().bakeRoot();
        assertNotNull(root.getChild("hull").getChild("ama_port"));
        assertNotNull(root.getChild("hull").getChild("ama_starboard"));
        assertNotNull(root.getChild("hull").getChild("vaka"));
        assertNotNull(TrimaranSportYachtModel.createWaterPatch().bakeRoot().getChild("patch"));
    }

    @Test void animationsResetVisibilityAndRespectIndependentPivots() {
        var root = TrimaranSportYachtModel.createBodyLayer().bakeRoot();
        var hull = root.getChild("hull");
        var model = new TrimaranSportYachtModel(root);
        var state = new TrimaranSportYachtRenderState();
        state.rudderAngle = 20;
        state.boomAngle = 45;
        state.sailFurled = false;
        state.engineActive = true;
        state.ageInTicks = 30;
        state.cleatTied[2] = true;
        model.setupAnim(state);
        assertEquals(-80, hull.getChild("rudder_port").x);
        assertEquals(80, hull.getChild("rudder_starboard").x);
        assertEquals(hull.getChild("rudder_port").yRot, hull.getChild("rudder_starboard").yRot);
        assertTrue(hull.getChild("boom").yRot < 0);
        assertTrue(hull.getChild("stay_frame").getChild("jib").yRot < 0);
        assertTrue(hull.getChild("boom").getChild("sail_open").visible);
        assertFalse(hull.getChild("boom").getChild("sail_furled").visible);
        assertTrue(hull.getChild("cleat_knot_2").visible);
        assertNotEquals(0, hull.getChild("propeller").zRot);
        state.sailFurled = true;
        state.engineActive = false;
        state.cleatTied[2] = false;
        model.setupAnim(state);
        assertFalse(hull.getChild("boom").getChild("sail_open").visible);
        assertTrue(hull.getChild("boom").getChild("sail_furled").visible);
        assertFalse(hull.getChild("stay_frame").getChild("jib").visible);
        assertTrue(hull.getChild("stay_frame").getChild("jib_furled").visible);
        assertEquals(0, hull.getChild("stay_frame").getChild("jib").yRot);
        assertFalse(hull.getChild("cleat_knot_2").visible);
        assertEquals(0, hull.getChild("propeller").zRot);
    }
}
