package com.piotrek.trimaranyacht.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.piotrek.trimaranyacht.TrimaranSportYachtMod;
import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import com.piotrek.trimaranyacht.network.TrimaranYachtInputPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TrimaranSportYachtClient implements ClientModInitializer {
	public static final ModelLayerLocation TRIMARAN_SPORT_YACHT_LAYER = new ModelLayerLocation(
		TrimaranSportYachtMod.id("trimaran_sport_yacht"), "main"
	);
	public static final ModelLayerLocation TRIMARAN_WATER_PATCH = new ModelLayerLocation(
		TrimaranSportYachtMod.id("trimaran_sport_yacht"), "water_patch"
	);

	public static final KeyMapping KEY_TRIMARAN_ENGINE = new KeyMapping(
		"key.peterwolfs_trimaran_sport_yacht.engine",
		InputConstants.Type.KEYBOARD,
		InputConstants.KEY_Z,
		KeyMapping.Category.GAMEPLAY
	);

	public static final KeyMapping KEY_TRIMARAN_SAIL = new KeyMapping(
		"key.peterwolfs_trimaran_sport_yacht.sail",
		InputConstants.Type.KEYBOARD,
		InputConstants.KEY_X,
		KeyMapping.Category.GAMEPLAY
	);

	private static float lastRudder = 0.0F;
	private static float lastThrottle = 0.0F;
	private static boolean wasJumpDown = false;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(TrimaranSportYachtMod.TRIMARAN_SPORT_YACHT, TrimaranSportYachtRenderer::new);
		ModelLayerRegistry.registerModelLayer(TRIMARAN_SPORT_YACHT_LAYER, TrimaranSportYachtModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(TRIMARAN_WATER_PATCH, TrimaranSportYachtModel::createWaterPatch);

		KeyMappingHelper.registerKeyMapping(KEY_TRIMARAN_ENGINE);
		KeyMappingHelper.registerKeyMapping(KEY_TRIMARAN_SAIL);

		HudElementRegistry.addLast(TrimaranSportYachtMod.id("trimaran_yacht_hud"), TrimaranSportYachtHudOverlay.INSTANCE);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(TrimaranWheelHighlightRenderer::render);

		// Client tick input polling & deck following
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) return;

			// Smooth client-side deck motion for standing players
			if (client.player.getVehicle() == null) {
				AABB search = client.player.getBoundingBox().inflate(1.5, 2.0, 1.5);
				for (TrimaranSportYachtEntity yacht : client.level.getEntitiesOfClass(TrimaranSportYachtEntity.class, search)) {
					if (yacht.isPlayerOnDeck(client.player)) {
						double dx = yacht.getX() - yacht.xOld;
						double dy = yacht.getY() - yacht.yOld;
						double dz = yacht.getZ() - yacht.zOld;
						float dYaw = yacht.getYRot() - yacht.yRotO;

						if (dx * dx + dy * dy + dz * dz > 1.0E-8 || Math.abs(dYaw) > 1.0E-4F) {
							double rad = Math.toRadians(dYaw);
							double cos = Math.cos(rad);
							double sin = Math.sin(rad);
							Vec3 oldBoatPos = new Vec3(yacht.xOld, yacht.yOld, yacht.zOld);
							Vec3 rel = client.player.position().subtract(oldBoatPos);
							double rotX = rel.x * cos - rel.z * sin;
							double rotZ = rel.x * sin + rel.z * cos;

							client.player.setPos(yacht.getX() + rotX, client.player.getY() + dy, yacht.getZ() + rotZ);
						}
						yacht.adjustPlayerDeckHeight(client.player);
						break;
					}
				}
			}

			if (client.player.getVehicle() instanceof TrimaranSportYachtEntity yacht && yacht.isHelmsman(client.player)) {
				boolean toggleEngine = KEY_TRIMARAN_ENGINE.consumeClick();
				boolean toggleSail = KEY_TRIMARAN_SAIL.consumeClick();

				int targetHikeMode = -1;
				boolean jumpDown = client.options.keyJump.isDown();
				if (jumpDown && !wasJumpDown) {
					targetHikeMode = (yacht.getHikeMode() == 0) ? 1 : 0;
					yacht.setHikeMode(targetHikeMode, client.player);
				}
				wasJumpDown = jumpDown;

				float rudder = 0.0F;
				if (client.options.keyLeft.isDown()) rudder -= 1.0F;
				if (client.options.keyRight.isDown()) rudder += 1.0F;

				float throttleOrSheet = 0.0F;
				if (client.options.keyUp.isDown()) throttleOrSheet += 1.0F;
				if (client.options.keyDown.isDown()) throttleOrSheet -= 1.0F;

				boolean inputChanged = Math.abs(rudder - lastRudder) > 0.01F || Math.abs(throttleOrSheet - lastThrottle) > 0.01F;

				if (toggleEngine || toggleSail || targetHikeMode >= 0 || inputChanged || client.player.tickCount % 5 == 0) {
					lastRudder = rudder;
					lastThrottle = throttleOrSheet;

					ClientPlayNetworking.send(new TrimaranYachtInputPayload(
						yacht.getId(),
						rudder,
						throttleOrSheet,
						toggleEngine,
						toggleSail,
						targetHikeMode
					));
				}
			} else {
				wasJumpDown = false;
				while (KEY_TRIMARAN_ENGINE.consumeClick()) { }
				while (KEY_TRIMARAN_SAIL.consumeClick()) { }
			}
		});
	}
}
