package com.piotrek.trimaranyacht.client;

import com.piotrek.trimaranyacht.geometry.TrimaranGeometry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Java LayerDefinition; no Blockbench/entity JSON and no runtime asset generation. */
public final class TrimaranSportYachtModel extends EntityModel<TrimaranSportYachtRenderState> {
    private final ModelPart wheel, boom, open, furled, jib, jibFurled, propeller;
    private final ModelPart[] rudders = new ModelPart[3];
    private final ModelPart[] knots = new ModelPart[6];

    public TrimaranSportYachtModel(ModelPart root) {
        super(root);
        ModelPart hull = root.getChild("hull");
        wheel = hull.getChild("wheel");
        boom = hull.getChild("boom");
        open = boom.getChild("sail_open");
        furled = boom.getChild("sail_furled");
        jib = hull.getChild("stay_frame").getChild("jib");
        jibFurled = hull.getChild("stay_frame").getChild("jib_furled");
        propeller = hull.getChild("propeller");
        rudders[0] = hull.getChild("rudder_port");
        rudders[1] = hull.getChild("rudder_center");
        rudders[2] = hull.getChild("rudder_starboard");
        for (int i = 0; i < knots.length; i++) knots[i] = hull.getChild("cleat_knot_" + i);
    }

    public static LayerDefinition createBodyLayer() { return bake(TrimaranGeometry.body()); }
    public static LayerDefinition createWaterPatch() { return bake(TrimaranGeometry.waterPatch()); }

    private static LayerDefinition bake(TrimaranGeometry.Part geometry) {
        MeshDefinition mesh = new MeshDefinition();
        for (var child : geometry.children) append(mesh.getRoot(), child);
        return LayerDefinition.create(mesh, TrimaranGeometry.ATLAS_SIZE, TrimaranGeometry.ATLAS_SIZE);
    }

    private static void append(PartDefinition parent, TrimaranGeometry.Part source) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (var b : source.boxes) cubes.texOffs(b.u(), b.v()).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d());
        PartDefinition part = parent.addOrReplaceChild(source.name, cubes,
            PartPose.offsetAndRotation(source.x, source.y, source.z, source.rx, source.ry, source.rz));
        for (var child : source.children) append(part, child);
    }

    @Override
    public void setupAnim(TrimaranSportYachtRenderState state) {
        super.setupAnim(state);
        for (ModelPart rudder : rudders) rudder.yRot = -state.rudderAngle * Mth.DEG_TO_RAD;
        wheel.zRot = -state.rudderAngle * 2.5f * Mth.DEG_TO_RAD;
        boom.yRot = -state.boomAngle * Mth.DEG_TO_RAD;
        open.visible = !state.sailFurled;
        furled.visible = state.sailFurled;
        jib.visible = !state.sailFurled;
        jibFurled.visible = state.sailFurled;
        // The parent is inclined along the forestay; yaw here rotates around the actual stay, not the mast.
        jib.yRot = state.sailFurled ? 0 : -state.boomAngle * .4f * Mth.DEG_TO_RAD;
        propeller.zRot = state.engineActive ? state.ageInTicks * .8f : 0;
        for (int i = 0; i < knots.length; i++) knots[i].visible = state.cleatTied[i];
    }
}
