package com.piotrek.trimaranyacht.entity;

import com.piotrek.trimaranyacht.TrimaranSportYachtMod;
import com.piotrek.trimaranyacht.geometry.TrimaranGeometry;
import com.piotrek.trimaranyacht.geometry.TrimaranLayout;
import com.piotrek.pwwindnsails.physics.MooringConstraint;
import com.piotrek.pwwindnsails.physics.SailingPhysics;
import com.piotrek.pwwindnsails.wind.WindManager;
import com.piotrek.pwwindnsails.wind.WindVector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Peterwolf's Trimaran Sport Yacht Entity.
 * Dimensions: 16 blocks long x 11.25 blocks wide; three wave-piercing hulls.
 * Features:
 *  - Three wave-piercing hulls, twin nets, low racing shelter and working cockpit
 *  - Three independent T-rudders, fixed modeled foils and auxiliary saildrive
 *  - Six mooring cleats and Wind & Sails integration
 *  - Geometry-derived deck support and three-hull SAT collision
 */
public class TrimaranSportYachtEntity extends Entity implements Leashable {

	public static final int CLEAT_COUNT = 6;
	public static final int CLEAT_BOW_PORT = 0;
	public static final int CLEAT_MID_PORT = 1;
	public static final int CLEAT_STERN_PORT = 2;
	public static final int CLEAT_BOW_STARBOARD = 3;
	public static final int CLEAT_MID_STARBOARD = 4;
	public static final int CLEAT_STERN_STARBOARD = 5;

	/**
	 * Cleat offsets in entity local coordinates.
	 * 0..2 = port (-X), 3..5 = starboard (+X).
	 */
	public static final Vec3[] CLEAT_OFFSETS = java.util.stream.IntStream.range(0, 6)
		.mapToObj(i -> { float[] c = TrimaranGeometry.cleat(i); return new Vec3(c[0] / 16.0, -c[1] / 16.0, -c[2] / 16.0); })
		.toArray(Vec3[]::new);

	private static final EntityDataAccessor<Integer> CLEAT_0_HOLDER = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_1_HOLDER = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_2_HOLDER = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_3_HOLDER = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_4_HOLDER = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CLEAT_5_HOLDER = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);

	@SuppressWarnings("unchecked")
	public static final EntityDataAccessor<Integer>[] CLEAT_HOLDERS = new EntityDataAccessor[]{
		CLEAT_0_HOLDER, CLEAT_1_HOLDER, CLEAT_2_HOLDER, CLEAT_3_HOLDER, CLEAT_4_HOLDER, CLEAT_5_HOLDER
	};

	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_0_POS = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_1_POS = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_2_POS = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_3_POS = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_4_POS = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Optional<BlockPos>> CLEAT_5_POS = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

	@SuppressWarnings("unchecked")
	public static final EntityDataAccessor<Optional<BlockPos>>[] CLEAT_POS = new EntityDataAccessor[]{
		CLEAT_0_POS, CLEAT_1_POS, CLEAT_2_POS, CLEAT_3_POS, CLEAT_4_POS, CLEAT_5_POS
	};

	private final UUID[] cleatHolderUuids = new UUID[CLEAT_COUNT];
	private final BlockPos[] cleatHolderPositions = new BlockPos[CLEAT_COUNT];
	private final Entity[] cleatHolderEntities = new Entity[CLEAT_COUNT];

	// Synced Navigation & Physics
	private static final EntityDataAccessor<Float> RUDDER_ANGLE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> MAINSHEET = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> BOOM_ANGLE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> HEEL_ANGLE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> SAIL_FURLED = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ENGINE_ACTIVE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> ENGINE_THROTTLE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> GROUNDED = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Byte> HIKE_MODE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> WINDWARD_SIGN = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> HELMSMAN_ID = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> HURT_TIME = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(TrimaranSportYachtEntity.class, EntityDataSerializers.FLOAT);

	@Nullable
	private Leashable.LeashData leashData;
	@Nullable
	private UUID helmsmanUuid;

	private float clientRudderInput;
	private float clientThrottleOrSheetInput;
	private int inputFreshTicks;
	private float yawVelocity;
	private int groundingAlertCooldown;
	private int engineSoundCooldown;

	public TrimaranSportYachtEntity(EntityType<? extends TrimaranSportYachtEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	public static void notifyPlayer(Player player, Component message) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(message, true);
		} else {
			player.sendSystemMessage(message);
		}
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		for (int i = 0; i < CLEAT_COUNT; i++) {
			builder.define(CLEAT_HOLDERS[i], -1);
			builder.define(CLEAT_POS[i], Optional.empty());
		}
		builder.define(RUDDER_ANGLE, 0.0F);
		builder.define(MAINSHEET, 0.5F);
		builder.define(BOOM_ANGLE, 0.0F);
		builder.define(HEEL_ANGLE, 0.0F);
		builder.define(SAIL_FURLED, true);
		builder.define(ENGINE_ACTIVE, false);
		builder.define(ENGINE_THROTTLE, 0.0F);
		builder.define(GROUNDED, false);
		builder.define(HIKE_MODE, (byte) 0);
		builder.define(WINDWARD_SIGN, -1.0F);
		builder.define(HELMSMAN_ID, -1);
		builder.define(HURT_TIME, 0);
		builder.define(DAMAGE, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setSailFurled(input.getBooleanOr("SailFurled", true));
		this.setEngineActive(input.getBooleanOr("EngineActive", false));
		this.setEngineThrottle(input.getFloatOr("EngineThrottle", 0.0F));
		this.setMainsheet(input.getFloatOr("Mainsheet", 0.5F));
		this.setBoomAngle(input.getFloatOr("BoomAngle", 0.0F));
		this.setRudderAngle(input.getFloatOr("RudderAngle", 0.0F));
		this.setHeelAngle(input.getFloatOr("HeelAngle", 0.0F));
		this.setGrounded(input.getBooleanOr("Grounded", false));
		this.setHikeMode(input.getByteOr("HikeMode", (byte) 0), null);
		this.entityData.set(DAMAGE, input.getFloatOr("Damage", 0.0F));

		for (int i = 0; i < CLEAT_COUNT; i++) {
			Optional<UUID> holderUuid = input.read("Cleat" + i + "Holder", net.minecraft.core.UUIDUtil.CODEC);
			this.cleatHolderUuids[i] = holderUuid.orElse(null);
			Optional<BlockPos> fencePos = input.read("Cleat" + i + "FencePos", BlockPos.CODEC);
			this.cleatHolderPositions[i] = fencePos.orElse(null);
			this.entityData.set(CLEAT_POS[i], fencePos);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putBoolean("SailFurled", this.isSailFurled());
		output.putBoolean("EngineActive", this.isEngineActive());
		output.putFloat("EngineThrottle", this.getEngineThrottle());
		output.putFloat("Mainsheet", this.getMainsheet());
		output.putFloat("BoomAngle", this.getBoomAngle());
		output.putFloat("RudderAngle", this.getRudderAngle());
		output.putFloat("HeelAngle", this.getHeelAngle());
		output.putBoolean("Grounded", this.isGrounded());
		output.putByte("HikeMode", this.getHikeMode());
		output.putFloat("Damage", this.getDamage());

		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (this.cleatHolderUuids[i] != null) {
				output.store("Cleat" + i + "Holder", net.minecraft.core.UUIDUtil.CODEC, this.cleatHolderUuids[i]);
			}
			if (this.cleatHolderPositions[i] != null) {
				output.store("Cleat" + i + "FencePos", BlockPos.CODEC, this.cleatHolderPositions[i]);
			}
		}
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		// Vanilla would turn the three hulls and open water into one solid rectangular platform.
		// Deck height support is handled explicitly on client and server.
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isClientAuthoritative() {
		return false;
	}

	@Override
	protected boolean isLocalClientAuthoritative() {
		return false;
	}

	public final boolean isHelmsman(@Nullable Entity entity) {
		return entity != null && entity.getId() == this.entityData.get(HELMSMAN_ID);
	}

	@Nullable
	public final Entity getHelmsman() {
		int id = this.entityData.get(HELMSMAN_ID);
		if (id < 0) return null;
		return this.level().getEntity(id);
	}

	public void setHelmsman(@Nullable LivingEntity captain) {
		if (captain == null) {
			this.helmsmanUuid = null;
			this.entityData.set(HELMSMAN_ID, -1);
			this.clientRudderInput = 0.0F;
			this.clientThrottleOrSheetInput = 0.0F;
			return;
		}
		this.helmsmanUuid = captain.getUUID();
		this.entityData.set(HELMSMAN_ID, captain.getId());
	}

	public boolean tryClaimHelm(LivingEntity captain) {
		if (captain == null || !captain.isAlive()) return false;
		Entity current = this.getHelmsman();
		if (current != null && current != captain) return false;
		if (captain.getVehicle() != null && captain.getVehicle() != this) return false;
		if (captain.getVehicle() != this) {
			if (!captain.startRiding(this)) return false;
		}
		this.setHelmsman(captain);
		return true;
	}

	public boolean releaseHelm(LivingEntity captain) {
		if (!this.isHelmsman(captain)) return false;
		this.setHelmsman(null);
		if (captain.getVehicle() == this) {
			captain.stopRiding();
		}
		return true;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof LivingEntity && this.getPassengers().isEmpty();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (this.helmsmanUuid != null && this.helmsmanUuid.equals(passenger.getUUID())) {
			this.setHelmsman(null);
		}
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		Entity helmsman = this.getHelmsman();
		if (helmsman instanceof LivingEntity living && this.hasPassenger(living)) {
			return living;
		}
		return null;
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		double radians = Math.toRadians(this.getYRot());
		double localY = (this.getHikeMode() == 1 ? 21.0 / 16 : TrimaranLayout.COCKPIT_HEIGHT) + .60;
		double localZ = TrimaranLayout.HELM_Z;
		double localX = this.getHikeMode() == 1 ? this.getWindwardSign() * .86 : 0.0;
		double worldX = Math.cos(radians) * localX - Math.sin(radians) * localZ;
		double worldZ = Math.sin(radians) * localX + Math.cos(radians) * localZ;
		return this.position().add(worldX, localY, worldZ);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return this.getPassengerRidingPosition(passenger).subtract(this.position());
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		double radians = Math.toRadians(this.getYRot());
		double localX = 0.35;
		double localY = TrimaranLayout.COCKPIT_HEIGHT;
		double localZ = TrimaranLayout.HELM_Z;
		double worldX = Math.cos(radians) * localX - Math.sin(radians) * localZ;
		double worldZ = Math.sin(radians) * localX + Math.cos(radians) * localZ;
		return this.position().add(worldX, localY, worldZ);
	}

	public void updateBoundingBox() {
		this.setBoundingBox(this.makeBoundingBox(this.position()));
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		// Tight broad phase from actual hull stations; SAT below handles gaps between hulls.
		java.util.List<double[]> hullContour = new java.util.ArrayList<>();
		for (var section : TrimaranLayout.HULLS) {
			for (double x : new double[]{section.minX(), section.maxX()}) {
				for (double z : new double[]{section.minZ(), section.maxZ()}) hullContour.add(new double[]{x, z});
			}
		}
		hullContour.add(new double[]{0, TrimaranLayout.MIN_Z});
		hullContour.add(new double[]{0, TrimaranLayout.MAX_Z});

		double minX = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;

		for (double[] pt : hullContour) {
			double wx = pos.x + rightX * pt[0] + fwdX * pt[1];
			double wz = pos.z + rightZ * pt[0] + fwdZ * pt[1];
			if (wx < minX) minX = wx;
			if (wx > maxX) maxX = wx;
			if (wz < minZ) minZ = wz;
			if (wz > maxZ) maxZ = wz;
		}

		return new AABB(minX, pos.y - .25, minZ, maxX, pos.y + 2.5, maxZ);
	}

	public boolean isHullColliding(double boatX, double boatY, double boatZ, float yaw) {
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);

		double maxFwd = TrimaranLayout.MAX_Z;
		double minFwd = TrimaranLayout.MIN_Z;
		double maxDim = Math.max(Math.abs(minFwd), Math.abs(maxFwd));

		double extX = Math.abs(fwdX) * maxDim + Math.abs(rightX) * TrimaranLayout.HALF_BEAM;
		double extZ = Math.abs(fwdZ) * maxDim + Math.abs(rightZ) * TrimaranLayout.HALF_BEAM;

		int minBlockX = Mth.floor(boatX - extX - 0.05);
		int maxBlockX = Mth.ceil(boatX + extX + 0.05);
		int minBlockZ = Mth.floor(boatZ - extZ - 0.05);
		int maxBlockZ = Mth.ceil(boatZ + extZ + 0.05);
		int minBlockY = Mth.floor(boatY - .25);
		int maxBlockY = Mth.ceil(boatY + 1.30);

		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
		CollisionContext context = CollisionContext.of(this);

		for (int x = minBlockX; x <= maxBlockX; x++) {
			for (int z = minBlockZ; z <= maxBlockZ; z++) {
				for (int y = minBlockY; y <= maxBlockY; y++) {
					mpos.set(x, y, z);
					BlockState state = this.level().getBlockState(mpos);
					if (state.isAir()) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(this.level(), mpos, context);
					if (shape.isEmpty()) {
						continue;
					}
					for (AABB box : shape.toAabbs()) {
						AABB worldBox = box.move(x, y, z);
						if (TrimaranLayout.hullIntersects(boatX, boatY, boatZ, yaw,
							worldBox.minX, worldBox.minY, worldBox.minZ, worldBox.maxX, worldBox.maxY, worldBox.maxZ)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public Vec3 resolveHullCollisions(Vec3 vel) {
		double targetX = this.getX() + vel.x;
		double targetY = this.getY() + vel.y;
		double targetZ = this.getZ() + vel.z;
		float yaw = this.getYRot();

		if (!this.isHullColliding(targetX, targetY, targetZ, yaw)) {
			return vel;
		}

		boolean canMoveX = (Math.abs(vel.x) > 1e-4) && !this.isHullColliding(targetX, targetY, this.getZ(), yaw);
		boolean canMoveZ = (Math.abs(vel.z) > 1e-4) && !this.isHullColliding(this.getX(), targetY, targetZ, yaw);

		if (canMoveX && !canMoveZ) return new Vec3(vel.x, vel.y, 0.0);
		if (canMoveZ && !canMoveX) return new Vec3(0.0, vel.y, vel.z);
		if (canMoveX && canMoveZ) {
			return Math.abs(vel.x) >= Math.abs(vel.z) ? new Vec3(vel.x, vel.y, 0.0) : new Vec3(0.0, vel.y, vel.z);
		}
		return new Vec3(0.0, vel.y, 0.0);
	}

	/**
	 * Returns local Y deck height above vessel root for (localX, localZ).
	 * Covers only modeled hulls, nets, curved beams, pod roof and boarding step.
	 */
	public double getDeckHeightAt(double localX, double localZ) {
		return TrimaranLayout.deckHeight(localX, localZ);
	}

	public void setControlInput(float rudder, float throttleOrSheet) {
		this.clientRudderInput = Mth.clamp(rudder, -1.0F, 1.0F);
		this.clientThrottleOrSheetInput = Mth.clamp(throttleOrSheet, -1.0F, 1.0F);
		this.inputFreshTicks = 8;
	}

	public static Component getCleatDisplayName(int cleat) {
		return switch (cleat) {
			case 0 -> Component.translatable("cleat.peterwolfs_trimaran_sport_yacht.bow_port");
			case 1 -> Component.translatable("cleat.peterwolfs_trimaran_sport_yacht.midship_port");
			case 2 -> Component.translatable("cleat.peterwolfs_trimaran_sport_yacht.stern_port");
			case 3 -> Component.translatable("cleat.peterwolfs_trimaran_sport_yacht.bow_starboard");
			case 4 -> Component.translatable("cleat.peterwolfs_trimaran_sport_yacht.midship_starboard");
			case 5 -> Component.translatable("cleat.peterwolfs_trimaran_sport_yacht.stern_starboard");
			default -> Component.literal("Cleat #" + (cleat + 1));
		};
	}

	public static String getCleatName(int cleat) {
		return switch (cleat) {
			case 0 -> "Bow Port";
			case 1 -> "Midship Port";
			case 2 -> "Stern Port";
			case 3 -> "Bow Starboard";
			case 4 -> "Midship Starboard";
			case 5 -> "Stern Starboard";
			default -> "Cleat #" + (cleat + 1);
		};
	}

	public Vec3 getCleatLocalOffset(int cleat) {
		if (cleat >= 0 && cleat < CLEAT_COUNT) {
			return CLEAT_OFFSETS[cleat];
		}
		return Vec3.ZERO;
	}

	public Vec3 getCleatWorldPos(int cleat, Vec3 vesselPos, float yaw) {
		Vec3 offset = this.getCleatLocalOffset(cleat);
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);
		return new Vec3(
			vesselPos.x + rightX * offset.x + fwdX * offset.z,
			vesselPos.y + offset.y,
			vesselPos.z + rightZ * offset.x + fwdZ * offset.z
		);
	}

	public Vec3 worldToLocal(Vec3 world) {
		Vec3 rel = world.subtract(this.position());
		float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
		double fwdX = -Mth.sin(yawRad);
		double fwdZ = Mth.cos(yawRad);
		double rightX = Mth.cos(yawRad);
		double rightZ = Mth.sin(yawRad);
		return new Vec3(
			rel.x * rightX + rel.z * rightZ,
			rel.y,
			rel.x * fwdX + rel.z * fwdZ
		);
	}

	public AABB getWheelLocalBox() {
		// Steering wheel on helm pedestal in cockpit
		return new AABB(-.48, 1.04, -4.60, .48, 1.96, -4.40);
	}

	public boolean isAimingAtWheel(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick).scale(5.0);
		Vec3 localEye = this.worldToLocal(eye);
		Vec3 localEnd = this.worldToLocal(eye.add(look));
		return this.getWheelLocalBox().clip(localEye, localEnd).isPresent();
	}

	public static boolean handleAimedControl(Player player) {
		AABB search = player.getBoundingBox().inflate(6.0);
		for (TrimaranSportYachtEntity yacht : player.level().getEntitiesOfClass(TrimaranSportYachtEntity.class, search)) {
			if (yacht.isAimingAtWheel(player, 1.0F)) {
				if (!player.level().isClientSide()) {
					if (yacht.isHelmsman(player)) {
						yacht.releaseHelm(player);
					} else {
						if (!yacht.tryClaimHelm(player)) {
							notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.helm_occupied"));
						} else {
							notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.helm_claimed"));
						}
					}
				}
				return true;
			}
		}
		return false;
	}



	public boolean isCleatAttached(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return false;
		return this.entityData.get(CLEAT_HOLDERS[cleat]) != -1 || this.entityData.get(CLEAT_POS[cleat]).isPresent();
	}

	public boolean isCleatAttachedTo(int cleat, Entity entity) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || entity == null) return false;
		return this.entityData.get(CLEAT_HOLDERS[cleat]) == entity.getId();
	}

	public boolean hasAnyCleatAttached() {
		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (this.isCleatAttached(i)) return true;
		}
		return false;
	}

	public void attachCleatToEntity(int cleat, Entity holder) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || holder == null) return;
		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (i != cleat && this.isCleatAttachedTo(i, holder)) {
				this.detachCleat(i);
			}
		}
		this.cleatHolderUuids[cleat] = holder.getUUID();
		this.cleatHolderEntities[cleat] = holder;
		this.entityData.set(CLEAT_HOLDERS[cleat], holder.getId());
		this.cleatHolderPositions[cleat] = null;
		this.entityData.set(CLEAT_POS[cleat], Optional.empty());
	}

	public void attachCleatToPos(int cleat, BlockPos pos) {
		if (cleat < 0 || cleat >= CLEAT_COUNT || pos == null) return;
		this.cleatHolderPositions[cleat] = pos.immutable();
		this.entityData.set(CLEAT_POS[cleat], Optional.of(pos.immutable()));
		this.cleatHolderUuids[cleat] = null;
		this.cleatHolderEntities[cleat] = null;
		this.entityData.set(CLEAT_HOLDERS[cleat], -1);
	}

	public void detachCleat(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return;
		this.cleatHolderUuids[cleat] = null;
		this.cleatHolderEntities[cleat] = null;
		this.cleatHolderPositions[cleat] = null;
		this.entityData.set(CLEAT_HOLDERS[cleat], -1);
		this.entityData.set(CLEAT_POS[cleat], Optional.empty());
	}

	@Nullable
	public Entity getCleatHolderEntity(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return null;
		int id = this.entityData.get(CLEAT_HOLDERS[cleat]);
		if (id != -1) {
			Entity e = this.level().getEntity(id);
			if (e != null) {
				this.cleatHolderEntities[cleat] = e;
				return e;
			}
		}
		if (this.cleatHolderUuids[cleat] != null && this.level() instanceof ServerLevel slevel) {
			Entity e = slevel.getEntity(this.cleatHolderUuids[cleat]);
			if (e != null) {
				this.cleatHolderEntities[cleat] = e;
				this.entityData.set(CLEAT_HOLDERS[cleat], e.getId());
				return e;
			}
		}
		return this.cleatHolderEntities[cleat];
	}

	public Optional<BlockPos> getCleatFencePos(int cleat) {
		if (cleat < 0 || cleat >= CLEAT_COUNT) return Optional.empty();
		return this.entityData.get(CLEAT_POS[cleat]);
	}

	@Nullable
	public Vec3 getCleatAnchorPos(int cleat) {
		Optional<BlockPos> pos = this.getCleatFencePos(cleat);
		if (pos.isPresent()) {
			BlockPos p = pos.get();
			return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
		}
		Entity holder = this.getCleatHolderEntity(cleat);
		if (holder != null && holder.isAlive()) {
			return holder.position();
		}
		return null;
	}

	public boolean isCleatMooringAnchor(int cleat) {
		if (this.getCleatFencePos(cleat).isPresent()) return true;
		Entity holder = this.getCleatHolderEntity(cleat);
		return holder instanceof com.piotrek.pwwindnsails.entity.AnchorEntity;
	}

	public static boolean holdsAnchor(Player player) {
		return player.getMainHandItem().is(com.piotrek.pwwindnsails.WindAndSailsMod.ANCHOR_ITEM)
			|| player.getOffhandItem().is(com.piotrek.pwwindnsails.WindAndSailsMod.ANCHOR_ITEM);
	}

	public static boolean handleAimedMooringClick(Player player) {
		TrimaranMooringAim.Target aim = TrimaranMooringAim.find(player, player.level(), 1.0F);
		if (aim == null) return false;

		boolean holdsLead = player.getMainHandItem().is(Items.LEAD) || player.getOffhandItem().is(Items.LEAD);
		com.piotrek.pwwindnsails.entity.AnchorEntity thrownAnchor = com.piotrek.pwwindnsails.entity.AnchorEntity.findHeldBy(player);

		if (aim.cleat()) {
			if (player.level().getEntity(aim.yachtId()) instanceof TrimaranSportYachtEntity yacht) {
				int cleat = aim.cleatIndex();
				// 1. Anchor attachment to cleat
				if (thrownAnchor != null) {
					if (!player.level().isClientSide()) {
						thrownAnchor.removeLeash();
						yacht.attachCleatToEntity(cleat, thrownAnchor);
						notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.anchor_attached", getCleatDisplayName(cleat)));
						player.level().playSound(null, yacht.getX(), yacht.getY(), yacht.getZ(),
							SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
					}
					return true;
				}
				// 2. Untie anchor or mooring from cleat
				if (yacht.isCleatAttached(cleat)) {
					if (!player.level().isClientSide()) {
						Entity holder = yacht.getCleatHolderEntity(cleat);
						if (holder instanceof com.piotrek.pwwindnsails.entity.AnchorEntity anchor) {
							yacht.detachCleat(cleat);
							anchor.discard();
							player.getInventory().add(new ItemStack(com.piotrek.pwwindnsails.WindAndSailsMod.ANCHOR_ITEM));
							notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.anchor_lifted", getCleatDisplayName(cleat)));
						} else {
							yacht.detachCleat(cleat);
							player.getInventory().add(new ItemStack(Items.LEAD));
							notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.mooring_untied_cleat", getCleatDisplayName(cleat)));
						}
						player.level().playSound(null, yacht.getX(), yacht.getY(), yacht.getZ(),
							SoundEvents.LEAD_UNTIED, SoundSource.PLAYERS, 1.0F, 1.0F);
					}
					return true;
				}
				// 3. Attach line to cleat
				if (holdsLead) {
					if (!player.level().isClientSide()) {
						yacht.attachCleatToEntity(cleat, player);
						if (!player.getAbilities().instabuild) {
							ItemStack lead = player.getMainHandItem().is(Items.LEAD) ? player.getMainHandItem() : player.getOffhandItem();
							lead.shrink(1);
						}
						notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.mooring_cleat_selected", getCleatDisplayName(cleat)));
					}
					return true;
				}
			}
		}

		if (aim.fence() != null && !aim.cleat()) {
			BlockPos fence = aim.fence();
			AABB search = player.getBoundingBox().inflate(TrimaranMooringAim.REACH + 2.0);
			for (TrimaranSportYachtEntity yacht : player.level().getEntitiesOfClass(TrimaranSportYachtEntity.class, search)) {
				for (int i = 0; i < CLEAT_COUNT; i++) {
					if (yacht.isCleatAttachedTo(i, player)) {
						if (!player.level().isClientSide()) {
							yacht.attachCleatToPos(i, fence);
							notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.mooring_cleat_to_post", getCleatDisplayName(i)));
							player.level().playSound(null, fence.getX(), fence.getY(), fence.getZ(),
								SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
						}
						return true;
					}
				}
			}
		}

		return false;
	}

	public static boolean handleEntityMooringClick(Player player, Entity entity) {
		AABB search = player.getBoundingBox().inflate(TrimaranMooringAim.REACH + 2.0);
		for (TrimaranSportYachtEntity yacht : player.level().getEntitiesOfClass(TrimaranSportYachtEntity.class, search)) {
			for (int i = 0; i < CLEAT_COUNT; i++) {
				if (yacht.isCleatAttachedTo(i, player)) {
					if (!player.level().isClientSide()) {
						yacht.attachCleatToEntity(i, entity);
						notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.mooring_entity_to_cleat", getCleatDisplayName(i)));
						player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
							SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
					}
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
		if (holdsAnchor(player) && com.piotrek.pwwindnsails.entity.AnchorEntity.findHeldBy(player) == null) {
			return InteractionResult.PASS;
		}
		if (handleAimedControl(player)) return InteractionResult.SUCCESS;
		if (handleAimedMooringClick(player)) return InteractionResult.SUCCESS;
		return InteractionResult.PASS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		if (this.isInvulnerableToBase(damageSource)) {
			return false;
		}
		if (damageSource.getEntity() instanceof Player player) {
			if (player.getAbilities().instabuild) {
				this.discard();
				return true;
			}
			this.setDamage(this.getDamage() + amount * 8.0F);
			this.entityData.set(HURT_TIME, 10);
			if (this.getDamage() > 50.0F) {
				this.spawnAtLocation(level, TrimaranSportYachtMod.TRIMARAN_SPORT_YACHT_ITEM);
				this.discard();
			}
			return true;
		}
		return false;
	}

	public float getRudderAngle() { return this.entityData.get(RUDDER_ANGLE); }
	public void setRudderAngle(float angle) { this.entityData.set(RUDDER_ANGLE, angle); }

	public float getMainsheet() { return this.entityData.get(MAINSHEET); }
	public void setMainsheet(float sheet) { this.entityData.set(MAINSHEET, sheet); }

	public float getBoomAngle() { return this.entityData.get(BOOM_ANGLE); }
	public void setBoomAngle(float angle) { this.entityData.set(BOOM_ANGLE, angle); }

	public float getHeelAngle() { return this.entityData.get(HEEL_ANGLE); }
	public void setHeelAngle(float angle) { this.entityData.set(HEEL_ANGLE, angle); }

	public boolean isSailFurled() { return this.entityData.get(SAIL_FURLED); }
	public void setSailFurled(boolean furled) { this.entityData.set(SAIL_FURLED, furled); }

	public boolean isEngineActive() { return this.entityData.get(ENGINE_ACTIVE); }
	public void setEngineActive(boolean active) { this.entityData.set(ENGINE_ACTIVE, active); }

	public float getEngineThrottle() { return this.entityData.get(ENGINE_THROTTLE); }
	public void setEngineThrottle(float throttle) { this.entityData.set(ENGINE_THROTTLE, throttle); }

	public boolean isGrounded() { return this.entityData.get(GROUNDED); }
	public void setGrounded(boolean grounded) { this.entityData.set(GROUNDED, grounded); }

	public byte getHikeMode() { return this.entityData.get(HIKE_MODE); }
	public void setHikeMode(int mode, @Nullable Player player) {
		this.entityData.set(HIKE_MODE, (byte) mode);
		if (player != null && !player.level().isClientSide()) {
			notifyPlayer(player, (mode == 1)
				? Component.translatable("message.peterwolfs_trimaran_sport_yacht.hike_windward")
				: Component.translatable("message.peterwolfs_trimaran_sport_yacht.hike_center"));
		}
	}

	public float getWindwardSign() { return this.entityData.get(WINDWARD_SIGN); }
	public void setWindwardSign(float sign) { this.entityData.set(WINDWARD_SIGN, sign); }

	public int getHurtTime() { return this.entityData.get(HURT_TIME); }
	public float getDamage() { return this.entityData.get(DAMAGE); }
	public void setDamage(float damage) { this.entityData.set(DAMAGE, damage); }

	public void toggleEngine(Player player) {
		boolean newActive = !this.isEngineActive();
		this.setEngineActive(newActive);
		if (newActive) {
			this.setSailFurled(true);
			this.setEngineThrottle(0.35F);
			notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.engine_on"));
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.8F, 0.7F);
		} else {
			this.setEngineThrottle(0.0F);
			notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.engine_off"));
		}
	}

	public void toggleSailOrCutEngine(Player player) {
		boolean newFurled = !this.isSailFurled();
		this.setSailFurled(newFurled);
		if (!newFurled) {
			if (this.isEngineActive()) {
				this.setEngineActive(false);
				this.setEngineThrottle(0.0F);
				notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.sails_unfurled_engine_cut"));
			} else {
				notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.sails_unfurled"));
			}
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1.0F, 0.8F);
		} else {
			notifyPlayer(player, Component.translatable("message.peterwolfs_trimaran_sport_yacht.sails_furled"));
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (this.getHurtTime() > 0) {
			this.entityData.set(HURT_TIME, this.getHurtTime() - 1);
		}

		if (this.inputFreshTicks > 0) {
			this.inputFreshTicks--;
		} else {
			this.clientRudderInput = 0.0F;
			this.clientThrottleOrSheetInput = 0.0F;
		}

		this.updateBoundingBox();
		if (this.level().isClientSide()) {
			return;
		}

		float boomSide = this.getBoomAngle();
		if (Math.abs(boomSide) > 5) this.setWindwardSign(boomSide < 0 ? -1 : 1);
		Entity helmsman = this.getHelmsman();
		if (helmsman != null && (!helmsman.isAlive() || !this.hasPassenger(helmsman))) {
			this.setHelmsman(null);
			helmsman = null;
		}

		// Water detection & surface finding
		double surface = this.findWaterSurface();
		boolean inWater = !Double.isNaN(surface);

		if (!inWater) {
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.65, 0.0, 0.65));
			return;
		}

		double targetY = surface - 0.33;
		double currentY = this.getY();
		double smoothedY = Double.isNaN(currentY) ? targetY : Mth.lerp(0.20, currentY, targetY);

		// Water support sampled under all three hulls.
		boolean isCurrentlyGrounded = this.checkIsGrounded(surface);
		this.setGrounded(isCurrentlyGrounded);

		if (isCurrentlyGrounded) {
			this.groundingAlertCooldown--;
			if (this.groundingAlertCooldown <= 0) {
				this.groundingAlertCooldown = 30;
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 1.0F, 0.6F);
				if (helmsman instanceof Player p) {
					notifyPlayer(p, Component.translatable("message.peterwolfs_trimaran_sport_yacht.grounded_warning"));
				}
			}
		}

		List<Player> deckStanders = this.collectDeckStanders();
		Vec3 oldPos = this.position();
		float oldYaw = this.getYRot();

		Vec3 currentVel = this.getDeltaMovement();
		Vec3 newVel;
		float newYaw = oldYaw;
		float targetRudder = this.clientRudderInput * 26.0F;
		float currentRudder = this.getRudderAngle();

		if (Math.abs(this.clientRudderInput) < 0.05F) {
			currentRudder = Mth.approach(currentRudder, 0.0F, 2.6F);
			this.setRudderAngle(currentRudder);
		} else {
			this.setRudderAngle(Mth.lerp(0.20F, currentRudder, targetRudder));
		}

		if (this.isEngineActive()) {
			// Auxiliary saildrive for docking
			if (this.clientThrottleOrSheetInput > 0.15F) {
				this.setEngineThrottle(Mth.clamp(this.getEngineThrottle() + 0.015F, -0.60F, 1.00F));
			} else if (this.clientThrottleOrSheetInput < -0.15F) {
				this.setEngineThrottle(Mth.clamp(this.getEngineThrottle() - 0.015F, -0.60F, 1.00F));
			}

			double headingRad = Math.toRadians(oldYaw);
			Vec3 fwd = new Vec3(-Math.sin(headingRad), 0.0, Math.cos(headingRad));

			double motorPower = (this.getEngineThrottle() >= 0)
				? this.getEngineThrottle() * 0.22
				: this.getEngineThrottle() * 0.12;

			if (isCurrentlyGrounded && motorPower > 0.0) {
				motorPower = 0.0;
			}

			float speedFactor = 0.45F + (float) Math.abs(motorPower) * 1.10F;
			float turnRate = (this.getRudderAngle() / 26.0F) * speedFactor;
			if (motorPower < -1.0E-4) {
				turnRate = -turnRate;
			}
			newYaw = Mth.wrapDegrees(oldYaw + turnRate);

			Vec3 targetVel = fwd.scale(motorPower);
			newVel = new Vec3(
				Mth.lerp(0.12, currentVel.x, targetVel.x),
				0.0,
				Mth.lerp(0.12, currentVel.z, targetVel.z)
			);
			this.setBoomAngle(0.0F);
			this.setHeelAngle(Mth.lerp(0.1F, this.getHeelAngle(), 0.0F));

			this.engineSoundCooldown--;
			if (this.engineSoundCooldown <= 0) {
				double power = Math.abs(this.getEngineThrottle());
				if (power < 0.04) {
					this.engineSoundCooldown = 18;
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.40F, 0.65F);
				} else {
					this.engineSoundCooldown = Math.max(5, 16 - (int)(power * 10));
					float pitch = 0.75F + (float) power * 0.35F;
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 0.45F + (float) power * 0.25F, pitch);
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.50F + (float) power * 0.3F, pitch * 0.9F);
				}
			}
		} else {
			// --- SAILING & DRIFT MODE ---
			WindVector wind = WindManager.getInstance().getWind(this.level(), this.position());
			Vec3 windDir = (wind != null && wind.strength() > 0.02F) ? wind.toVec3().normalize() : Vec3.ZERO;
			float windSpeed = (wind != null) ? wind.strength() : 0.0F;

			if (this.isSailFurled()) {
				// Sails furled: NO sail propulsion! The yacht coasts down and naturally drifts with the wind.
				// Realistic, visible wind drift (0.018 * windSpeed ≈ 0.35 m/s in fresh breeze)
				Vec3 targetDrift = windDir.scale(Math.min(.025, 0.018 * windSpeed));

				newVel = new Vec3(
					Mth.lerp(0.08, currentVel.x, targetDrift.x),
					currentVel.y,
					Mth.lerp(0.08, currentVel.z, targetDrift.z)
				);
				if (newVel.horizontalDistanceSqr() < 1.0E-6) {
					newVel = new Vec3(0.0, currentVel.y, 0.0);
				}

				// Gentle rudder influence when drifting
				double driftSpeed = newVel.horizontalDistance();
				if (driftSpeed > 0.005) {
					float driftTurn = (this.getRudderAngle() / 26.0F) * 0.15F * (float) Math.min(1.0, driftSpeed / 0.02);
					newYaw = Mth.wrapDegrees(oldYaw + driftTurn);
				}
				this.yawVelocity = Mth.lerp(0.20F, this.yawVelocity, 0.0F);
				this.setBoomAngle(Mth.approach(this.getBoomAngle(), 0.0F, 3.0F));
				this.setHeelAngle(Mth.lerp(0.15F, this.getHeelAngle(), 0.0F));
			} else {
				// Sails hoisted: true wind aerodynamics via pw_wind_n_sails
				float sheet = this.getMainsheet();
				if (this.clientThrottleOrSheetInput > 0.1F) {
					sheet = Math.max(0.0F, sheet - 0.02F);
				} else if (this.clientThrottleOrSheetInput < -0.1F) {
					sheet = Math.min(1.0F, sheet + 0.02F);
				}
				this.setMainsheet(sheet);

				SailingPhysics.PhysicsResult res = SailingPhysics.step(
					currentVel,
					oldYaw,
					this.yawVelocity,
					this.getRudderAngle(),
					this.getMainsheet(),
					wind,
					this.getHeelAngle(),
					false,
					inWater,
					0
				);

				Vec3 physicsVel = res.newVelocity();
				newVel = new Vec3(
					Mth.lerp(0.12, currentVel.x, physicsVel.x),
					currentVel.y,
					Mth.lerp(0.12, currentVel.z, physicsVel.z)
				);
				// Subtle leeway drift under sail
				newVel = newVel.add(windDir.x * 0.0012 * windSpeed, 0.0, windDir.z * 0.0012 * windSpeed);

				this.yawVelocity = Mth.lerp(0.20F, this.yawVelocity, res.newYawVelocity());
				newYaw = Mth.wrapDegrees(oldYaw + this.yawVelocity);

				float targetBoom = res.boomAngleDeg();
				this.setBoomAngle(Mth.approach(this.getBoomAngle(), targetBoom, 2.8F));

				float rawHeel = res.heelAngleDeg() * 0.18F;
				if (this.getHikeMode() > 0) {
					rawHeel = Math.signum(rawHeel) * Math.max(0.0F, Math.abs(rawHeel) - 1.5F);
				}
				float targetHeel = Mth.clamp(rawHeel, -7.0F, 7.0F);
				this.setHeelAngle(Mth.lerp(0.12F, this.getHeelAngle(), targetHeel));

				if (isCurrentlyGrounded) {
					double headingRad = Math.toRadians(oldYaw);
					Vec3 fwd = new Vec3(-Math.sin(headingRad), 0.0, Math.cos(headingRad));
					double forwardSpeed = newVel.dot(fwd);
					if (forwardSpeed > 0.0) {
						newVel = newVel.subtract(fwd.scale(forwardSpeed));
					}
				}
			}
		}

		// Wind strength for mooring line calculations
		float currentWindStrength = 0.0F;
		if (inWater && !isCurrentlyGrounded) {
			WindVector wind = WindManager.getInstance().getWind(this.level(), this.position());
			if (wind != null) {
				currentWindStrength = wind.strength();
			}
		}

		// Mooring line constraints across cleats
		newVel = this.applyMooringConstraints(newVel, newYaw, currentWindStrength);

		if (this.hasAnyCleatAttached()) newYaw = Mth.wrapDegrees(oldYaw + this.yawVelocity);
		if (this.isHullColliding(this.getX(), smoothedY, this.getZ(), newYaw)) newYaw = oldYaw;
		this.setYRot(newYaw);
		// Narrow-phase SAT replaces vanilla's solid rectangular AABB collision.
		newVel = this.resolveHullCollisions(newVel);
		this.setDeltaMovement(newVel);

		// Circular arc pivot when moored
		int activeCleat = this.getFirstActiveCleat();
		if (activeCleat >= 0 && Math.abs(newYaw - oldYaw) > 1.0E-4F) {
			Vec3 cleatBefore = this.getCleatWorldPos(activeCleat, oldPos, oldYaw);
			Vec3 offsetNew = this.getCleatWorldPos(activeCleat, Vec3.ZERO, newYaw);
			double arcX = cleatBefore.x - offsetNew.x;
			double arcZ = cleatBefore.z - offsetNew.z;
			double pivotX = Mth.lerp(0.85, this.getX(), arcX), pivotZ = Mth.lerp(0.85, this.getZ(), arcZ);
			if (!this.isHullColliding(pivotX, smoothedY, pivotZ, newYaw)) {
				this.setPos(pivotX, smoothedY, pivotZ);
			}
		} else {
			this.setPos(this.getX(), smoothedY, this.getZ());
		}

		this.setPos(this.getX() + newVel.x, this.getY(), this.getZ() + newVel.z);
		this.updateBoundingBox();

		// Carry deck standers
		Vec3 newPos = this.position();
		this.carryDeckStanders(deckStanders, oldPos, oldYaw, newPos, newYaw);
	}

	private int getFirstActiveCleat() {
		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (this.isCleatAttached(i) && this.isCleatMooringAnchor(i)) {
				return i;
			}
		}
		return -1;
	}

	private Vec3 applyMooringConstraints(Vec3 velocity, float yaw, float windStrength) {
		Vec3 adjusted = velocity;
		float sumError = 0.0F;
		float totalWeight = 0.0F;

		for (int i = 0; i < CLEAT_COUNT; i++) {
			if (!this.isCleatAttached(i) || !this.isCleatMooringAnchor(i)) continue;
			Vec3 cleatWorld = this.getCleatWorldPos(i, this.position(), yaw);
			Vec3 anchorPos = this.getCleatAnchorPos(i);
			if (anchorPos == null) continue;

			Entity holder = this.getCleatHolderEntity(i);
			boolean isAnchor = holder instanceof com.piotrek.pwwindnsails.entity.AnchorEntity;
			double maxLine = isAnchor ? 10.5 : 5.5;

			Vec3 diff = anchorPos.subtract(cleatWorld);
			double dist = diff.length();
			Vec3 local = this.getCleatLocalOffset(i);
			float w = MooringConstraint.lineYawWeight(dist, maxLine);
			float error = MooringConstraint.cleatYawError(
				yaw, this.getX(), this.getZ(), local.x, local.z, anchorPos.x, anchorPos.z
			);
			sumError += error * w;
			totalWeight += w;

			if (dist > maxLine) {
				Vec3 norm = diff.normalize();
				double tension = Math.min(1.0, (dist - maxLine) / 2.0);
				double pullStrength = tension * 0.14;
				adjusted = adjusted.add(norm.x * pullStrength, 0.0, norm.z * pullStrength);
				adjusted = MooringConstraint.cancelOutwardVelocity(adjusted, -norm.x, -norm.z);
			}
		}

		if (totalWeight > 1.0E-4F) {
			float avgError = sumError / totalWeight;
			if (Math.abs(avgError) > 0.5F) {
				float windFactor = Mth.clamp(windStrength / 0.35F, 0.4F, 1.8F);
				float turn = Mth.clamp(avgError * 0.02F * windFactor, -0.6F, 0.6F);
				this.yawVelocity = Mth.lerp(0.12F, this.yawVelocity, turn);
			} else {
				this.yawVelocity = Mth.lerp(0.20F, this.yawVelocity, 0.0F);
			}
		}

		return adjusted;
	}

	private boolean checkIsGrounded(double surfaceY) {
		int surfBlockY = Mth.floor(surfaceY);
		double radians = Math.toRadians(this.getYRot());
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);

		// Sample all three hulls, not the water in the open gaps.
		double[][] sampleOffsets = new double[][]{
			{-5, 7}, {0, 8}, {5, 7}, {-5, 0}, {0, 0}, {5, 0}, {-5, -6}, {0, -6}, {5, -6}
		};

		for (double[] offset : sampleOffsets) {
			double wx = this.getX() + cos * offset[0] - sin * offset[1];
			double wz = this.getZ() + sin * offset[0] + cos * offset[1];
			BlockPos p1 = new BlockPos(Mth.floor(wx), surfBlockY - 1, Mth.floor(wz));

			FluidState f1 = this.level().getFluidState(p1);
			if (!f1.is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	public boolean isPlayerOnDeck(Player player) {
		if (player == null || player.isSpectator() || player.getVehicle() == this || player.isPassenger()) {
			return false;
		}
		Vec3 local = this.worldToLocal(player.position());
		double deck = this.getDeckHeightAt(local.x, local.z);
		return Double.isFinite(deck) && local.y >= deck - .85 && local.y <= deck + 1.30;
	}

	private List<Player> collectDeckStanders() {
		AABB search = this.getBoundingBox().inflate(2.0, 3.0, 2.0);
		return this.level().getEntitiesOfClass(Player.class, search, this::isPlayerOnDeck);
	}

	private void carryDeckStanders(List<Player> standers, Vec3 oldPos, float oldYaw, Vec3 newPos, float newYaw) {
		double dYaw = newYaw - oldYaw;
		double rad = Math.toRadians(dYaw);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);

		Vec3 deltaMove = newPos.subtract(oldPos);
		boolean moved = deltaMove.lengthSqr() > 1.0E-8 || Math.abs(dYaw) > 1.0E-4;
		if (!moved) return;

		for (Player p : standers) {
			if (p.isRemoved() || p.getVehicle() == this || p.isPassenger()) continue;
			Vec3 rel = p.position().subtract(oldPos);
			double rotX = rel.x * cos - rel.z * sin;
			double rotZ = rel.x * sin + rel.z * cos;
			double targetX = newPos.x + rotX;
			double targetY = p.getY() + (newPos.y - oldPos.y);
			double targetZ = newPos.z + rotZ;

			p.setPos(targetX, targetY, targetZ);
			this.adjustPlayerDeckHeight(p);
		}
	}

	public void adjustPlayerDeckHeight(Player player) {
		if (player == null || player.isSpectator() || player.getVehicle() == this || player.isPassenger()) {
			return;
		}
		Vec3 local = this.worldToLocal(player.position());
		double deckH = this.getDeckHeightAt(local.x, local.z);
		if (Double.isNaN(deckH)) {
			return;
		}
		double targetY = this.getY() + deckH;
		double feetY = player.getY();
		Vec3 motion = player.getDeltaMovement();

		if (motion.y > 0.08) {
			return;
		}

		if (feetY <= targetY + 0.10 && feetY >= targetY - 0.28) {
			player.setPos(player.getX(), targetY, player.getZ());
			if (motion.y < 0.0) {
				player.setDeltaMovement(motion.x, 0.0, motion.z);
			}
			player.setOnGround(true);
			player.resetFallDistance();
			return;
		}

		if (feetY < targetY && targetY - feetY <= 0.85) {
			player.setPos(player.getX(), targetY, player.getZ());
			player.setOnGround(true);
			player.resetFallDistance();
		}
	}

	private double findWaterSurface() {
		AABB bb = this.getBoundingBox();
		int minX = Mth.floor(bb.minX);
		int maxX = Mth.ceil(bb.maxX);
		int minZ = Mth.floor(bb.minZ);
		int maxZ = Mth.ceil(bb.maxZ);
		int minY = Mth.floor(bb.minY - 0.5);
		int maxY = Mth.ceil(bb.maxY + 0.5);

		double highestSurface = Double.NaN;
		BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					mpos.set(x, y, z);
					FluidState fluid = this.level().getFluidState(mpos);
					if (fluid.is(FluidTags.WATER)) {
						double surfaceY = y + fluid.getHeight(this.level(), mpos);
						if (Double.isNaN(highestSurface) || surfaceY > highestSurface) {
							highestSurface = surfaceY;
						}
					}
				}
			}
		}
		return highestSurface;
	}

	@Override
	@Nullable
	public Leashable.LeashData getLeashData() {
		return this.leashData;
	}

	@Override
	public void setLeashData(@Nullable Leashable.LeashData leashData) {
		this.leashData = leashData;
	}
}
