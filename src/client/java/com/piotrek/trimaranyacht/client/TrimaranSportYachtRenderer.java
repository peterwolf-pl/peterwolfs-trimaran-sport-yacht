package com.piotrek.trimaranyacht.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.trimaranyacht.TrimaranSportYachtMod;
import com.piotrek.trimaranyacht.entity.TrimaranSportYachtEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Optional;

public class TrimaranSportYachtRenderer extends EntityRenderer<TrimaranSportYachtEntity, TrimaranSportYachtRenderState> {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
		TrimaranSportYachtMod.MOD_ID, "textures/entity/trimaran_sport_yacht.png"
	);

	private final TrimaranSportYachtModel model;
	private final Model.Simple waterMask;

	public TrimaranSportYachtRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new TrimaranSportYachtModel(context.bakeLayer(TrimaranSportYachtClient.TRIMARAN_SPORT_YACHT_LAYER));
		this.waterMask = new Model.Simple(
			context.bakeLayer(TrimaranSportYachtClient.TRIMARAN_WATER_PATCH),
			id -> RenderTypes.waterMask()
		);
		this.shadowRadius = 4.8F;
	}

	@Override
	protected AABB getBoundingBoxForCulling(TrimaranSportYachtEntity entity, float partialTick) {
		return entity.getBoundingBox().inflate(4.0, 2.0, 6.0).expandTowards(0.0, 30.0, 0.0);
	}

	@Override
	protected boolean affectedByCulling(TrimaranSportYachtEntity entity) {
		return false;
	}

	@Override
	public TrimaranSportYachtRenderState createRenderState() {
		return new TrimaranSportYachtRenderState();
	}

	@Override
	public void extractRenderState(TrimaranSportYachtEntity entity, TrimaranSportYachtRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
		state.heel = entity.getHeelAngle();
		state.boomAngle = entity.getBoomAngle();
		state.rudderAngle = entity.getRudderAngle();
		state.engineActive = entity.isEngineActive();
		state.sailFurled = entity.isSailFurled();
		state.grounded = entity.isGrounded();
		state.speed = (float) entity.getDeltaMovement().horizontalDistance();
		state.hurtTime = entity.getHurtTime();
		state.damage = entity.getDamage();

		if (state.leashStates == null) {
			state.leashStates = new ArrayList<>();
		} else {
			state.leashStates.clear();
		}

		Vec3 boatPos = entity.getPosition(partialTick);
		float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());

		for (int i = 0; i < TrimaranSportYachtEntity.CLEAT_COUNT; i++) {
			state.cleatTied[i] = entity.isCleatAttached(i);
			if (!state.cleatTied[i]) {
				continue;
			}

			Optional<BlockPos> knotPos = entity.getCleatFencePos(i);
			Entity holder = entity.getCleatHolderEntity(i);

			if (knotPos.isPresent() || (holder != null && holder.isAlive())) {
				EntityRenderState.LeashState leashState = new EntityRenderState.LeashState();
				Vec3 cleatWorld = entity.getCleatWorldPos(i, boatPos, yaw);

				leashState.offset = cleatWorld.subtract(boatPos);
				leashState.start = cleatWorld;

				if (knotPos.isPresent()) {
					BlockPos kp = knotPos.get();
					leashState.end = new Vec3(kp.getX() + 0.5, kp.getY() + 0.5 + 0.2, kp.getZ() + 0.5);
				} else {
					leashState.end = holder.getRopeHoldPosition(partialTick);
				}

				BlockPos startPos = BlockPos.containing(cleatWorld);
				BlockPos endPos = BlockPos.containing(leashState.end);

				leashState.startBlockLight = entity.level().getBrightness(LightLayer.BLOCK, startPos);
				leashState.startSkyLight = entity.level().getBrightness(LightLayer.SKY, startPos);
				leashState.endBlockLight = entity.level().getBrightness(LightLayer.BLOCK, endPos);
				leashState.endSkyLight = holder != null
					? holder.level().getBrightness(LightLayer.SKY, endPos)
					: entity.level().getBrightness(LightLayer.SKY, endPos);

				double dist = cleatWorld.distanceTo(leashState.end);
				leashState.slack = dist < 3.5;

				state.leashStates.add(leashState);
			}
		}
	}

	@Override
	public void submit(TrimaranSportYachtRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, stack, collector, camera);

		stack.pushPose();
		stack.rotateDegrees(Axis.YP, 180.0F - state.yaw);

		// Water mask (level with horizon, NO heel tilt)
		if (camera.fogType != FogType.WATER) {
			stack.pushPose();
			stack.scale(-1.0F, -1.0F, 1.0F);
			stack.translate(0.0F, -1.5F, 0.0F);
			collector.submitModel(
				this.waterMask,
				Unit.INSTANCE,
				stack,
				TEXTURE,
				state.lightCoords,
				OverlayTexture.NO_OVERLAY,
				0
			);
			stack.popPose();
		}

		// Aerodynamic heel roll
		stack.rotateDegrees(Axis.ZP, state.heel);

		// Hurt shake
		if (state.hurtTime > 0) {
			float wobble = Mth.sin(state.hurtTime) * 2.0F;
			stack.rotateDegrees(Axis.ZP, wobble);
		}

		stack.scale(-1.0F, -1.0F, 1.0F);
		stack.translate(0.0F, -1.5F, 0.0F);

		this.model.setupAnim(state);
		collector.submitModel(
			this.model,
			state,
			stack,
			RenderTypes.entityCutout(TEXTURE),
			state.lightCoords,
			OverlayTexture.NO_OVERLAY,
			state.outlineColor
		);
		stack.popPose();
	}
}
