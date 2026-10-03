package com.piotrek.trimaranyacht.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.piotrek.trimaranyacht.entity.TrimaranMooringAim;
import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Renders authentic black wireframe bounding boxes ("czarny drut"):
 *  1) Around the Trimaran's steering wheel (koło sterowe)
 *  2) Around targeted mooring cleats and dock posts
 */
public final class TrimaranWheelHighlightRenderer {
	private TrimaranWheelHighlightRenderer() {}

	public static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return;
		}

		Vec3 camPos = context.levelState().cameraRenderState.pos;
		if (camPos == null) {
			return;
		}

		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		PoseStack poseStack = context.poseStack();

		// 1. Steering wheel highlight
		boolean isHelmsman = player.getVehicle() instanceof TrimaranSportYachtEntity yacht && yacht.isHelmsman(player);
		if (!isHelmsman) {
			AABB searchBox = player.getBoundingBox().inflate(8.0);
			List<TrimaranSportYachtEntity> yachts = level.getEntitiesOfClass(TrimaranSportYachtEntity.class, searchBox);
			for (TrimaranSportYachtEntity yacht : yachts) {
				boolean wheel = yacht.isAimingAtWheel(player, partialTick);
				if (!wheel) {
					continue;
				}
				context.submitNodeCollector().submitCustomGeometry(
					poseStack,
					RenderTypes.lines(),
					(pose, consumer) -> {
						if (wheel) {
							drawLocalBox(pose, consumer, yacht, yacht.getWheelLocalBox(), partialTick, camPos);
						}
					}
				);
				break;
			}
		}

		// 2. Mooring Cleats & Fence Posts
		boolean holdsLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
		boolean holdsThrownAnchor = com.piotrek.pwwindnsails.entity.AnchorEntity.findHeldBy(player) != null;
		AABB cleatSearch = player.getBoundingBox().inflate(TrimaranMooringAim.REACH + 3.0);
		List<TrimaranSportYachtEntity> nearbyYachts = level.getEntitiesOfClass(TrimaranSportYachtEntity.class, cleatSearch);

		boolean hasLineFromYacht = false;
		for (TrimaranSportYachtEntity y : nearbyYachts) {
			for (int i = 0; i < TrimaranSportYachtEntity.CLEAT_COUNT; i++) {
				if (y.isCleatAttachedTo(i, player)) {
					hasLineFromYacht = true;
					break;
				}
			}
			if (hasLineFromYacht) break;
		}

		if (holdsLead || hasLineFromYacht || holdsThrownAnchor) {
			TrimaranMooringAim.Target aim = TrimaranMooringAim.find(player, level, partialTick);
			if (aim != null) {
				context.submitNodeCollector().submitCustomGeometry(
					poseStack,
					RenderTypes.lines(),
					(pose, consumer) -> {
						if (aim.cleat()) {
							if (level.getEntity(aim.yachtId()) instanceof TrimaranSportYachtEntity yacht) {
								drawCleatWire(pose, consumer, yacht, aim.cleatIndex(), partialTick, camPos);
							}
							return;
						}
						if (aim.fence() != null && !holdsThrownAnchor) {
							drawAabbEdges(pose, consumer, TrimaranMooringAim.postColumn(aim.fence()).move(-camPos.x, -camPos.y, -camPos.z), 220);
							LeashFenceKnotEntity.getKnot(level, aim.fence()).ifPresent(knot ->
								drawAabbEdges(pose, consumer, knot.getBoundingBox().move(-camPos.x, -camPos.y, -camPos.z), 220)
							);
						}
					}
				);
			}
		}
	}

	private static void drawLocalBox(PoseStack.Pose pose, VertexConsumer consumer, TrimaranSportYachtEntity yacht, AABB box, float partialTick, Vec3 camPos) {
		float boatYaw = Mth.rotLerp(partialTick, yacht.yRotO, yacht.getYRot());
		Vec3 boatPos = yacht.getPosition(partialTick);
		float yawRad = boatYaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		Vec3[] localCorners = new Vec3[]{
			new Vec3(box.minX, box.minY, box.minZ),
			new Vec3(box.maxX, box.minY, box.minZ),
			new Vec3(box.maxX, box.minY, box.maxZ),
			new Vec3(box.minX, box.minY, box.maxZ),
			new Vec3(box.minX, box.maxY, box.minZ),
			new Vec3(box.maxX, box.maxY, box.minZ),
			new Vec3(box.maxX, box.maxY, box.maxZ),
			new Vec3(box.minX, box.maxY, box.maxZ)
		};

		float[] rx = new float[8];
		float[] ry = new float[8];
		float[] rz = new float[8];

		for (int k = 0; k < 8; k++) {
			rx[k] = (float) (boatPos.x + rightX * localCorners[k].x + fwdX * localCorners[k].z - camPos.x);
			ry[k] = (float) (boatPos.y + localCorners[k].y - camPos.y);
			rz[k] = (float) (boatPos.z + rightZ * localCorners[k].x + fwdZ * localCorners[k].z - camPos.z);
		}

		drawBoxLines(pose, consumer, rx, ry, rz, 230);
	}

	private static void drawCleatWire(PoseStack.Pose pose, VertexConsumer consumer, TrimaranSportYachtEntity yacht, int cleat, float partialTick, Vec3 camPos) {
		float boatYaw = Mth.rotLerp(partialTick, yacht.yRotO, yacht.getYRot());
		Vec3 boatPos = yacht.getPosition(partialTick);
		float yawRad = boatYaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		AABB local = TrimaranMooringAim.cleatLocalBox(yacht.getCleatLocalOffset(cleat), 0.0);
		Vec3[] corners = new Vec3[]{
			new Vec3(local.minX, local.minY, local.minZ),
			new Vec3(local.maxX, local.minY, local.minZ),
			new Vec3(local.maxX, local.minY, local.maxZ),
			new Vec3(local.minX, local.minY, local.maxZ),
			new Vec3(local.minX, local.maxY, local.minZ),
			new Vec3(local.maxX, local.maxY, local.minZ),
			new Vec3(local.maxX, local.maxY, local.maxZ),
			new Vec3(local.minX, local.maxY, local.maxZ)
		};

		float[] rx = new float[8];
		float[] ry = new float[8];
		float[] rz = new float[8];

		for (int k = 0; k < 8; k++) {
			rx[k] = (float) (boatPos.x + rightX * corners[k].x + fwdX * corners[k].z - camPos.x);
			ry[k] = (float) (boatPos.y + corners[k].y - camPos.y);
			rz[k] = (float) (boatPos.z + rightZ * corners[k].x + fwdZ * corners[k].z - camPos.z);
		}

		drawBoxLines(pose, consumer, rx, ry, rz, 230);
	}

	private static void drawBoxLines(PoseStack.Pose pose, VertexConsumer consumer, float[] rx, float[] ry, float[] rz, int alpha) {
		drawLine(pose, consumer, rx[0], ry[0], rz[0], rx[1], ry[1], rz[1], alpha);
		drawLine(pose, consumer, rx[1], ry[1], rz[1], rx[2], ry[2], rz[2], alpha);
		drawLine(pose, consumer, rx[2], ry[2], rz[2], rx[3], ry[3], rz[3], alpha);
		drawLine(pose, consumer, rx[3], ry[3], rz[3], rx[0], ry[0], rz[0], alpha);

		drawLine(pose, consumer, rx[4], ry[4], rz[4], rx[5], ry[5], rz[5], alpha);
		drawLine(pose, consumer, rx[5], ry[5], rz[5], rx[6], ry[6], rz[6], alpha);
		drawLine(pose, consumer, rx[6], ry[6], rz[6], rx[7], ry[7], rz[7], alpha);
		drawLine(pose, consumer, rx[7], ry[7], rz[7], rx[4], ry[4], rz[4], alpha);

		drawLine(pose, consumer, rx[0], ry[0], rz[0], rx[4], ry[4], rz[4], alpha);
		drawLine(pose, consumer, rx[1], ry[1], rz[1], rx[5], ry[5], rz[5], alpha);
		drawLine(pose, consumer, rx[2], ry[2], rz[2], rx[6], ry[6], rz[6], alpha);
		drawLine(pose, consumer, rx[3], ry[3], rz[3], rx[7], ry[7], rz[7], alpha);
	}

	private static void drawAabbEdges(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int a) {
		float x0 = (float) box.minX;
		float y0 = (float) box.minY;
		float z0 = (float) box.minZ;
		float x1 = (float) box.maxX;
		float y1 = (float) box.maxY;
		float z1 = (float) box.maxZ;

		drawLine(pose, consumer, x0, y0, z0, x1, y0, z0, a);
		drawLine(pose, consumer, x1, y0, z0, x1, y0, z1, a);
		drawLine(pose, consumer, x1, y0, z1, x0, y0, z1, a);
		drawLine(pose, consumer, x0, y0, z1, x0, y0, z0, a);

		drawLine(pose, consumer, x0, y1, z0, x1, y1, z0, a);
		drawLine(pose, consumer, x1, y1, z0, x1, y1, z1, a);
		drawLine(pose, consumer, x1, y1, z1, x0, y1, z1, a);
		drawLine(pose, consumer, x0, y1, z1, x0, y1, z0, a);

		drawLine(pose, consumer, x0, y0, z0, x0, y1, z0, a);
		drawLine(pose, consumer, x1, y0, z0, x1, y1, z0, a);
		drawLine(pose, consumer, x1, y0, z1, x1, y1, z1, a);
		drawLine(pose, consumer, x0, y0, z1, x0, y1, z1, a);
	}

	private static void drawLine(PoseStack.Pose pose, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int a) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = (len > 1e-5F) ? dx / len : 0.0F;
		float ny = (len > 1e-5F) ? dy / len : 1.0F;
		float nz = (len > 1e-5F) ? dz / len : 0.0F;

		consumer.addVertex(pose, x1, y1, z1).setColor(0, 0, 0, a).setNormal(pose, nx, ny, nz).setLineWidth(2.5F);
		consumer.addVertex(pose, x2, y2, z2).setColor(0, 0, 0, a).setNormal(pose, nx, ny, nz).setLineWidth(2.5F);
	}
}
