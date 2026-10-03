package com.piotrek.trimaranyacht.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class TrimaranSportYachtRenderState extends EntityRenderState {
	public float yaw;
	public float heel;
	public float boomAngle;
	public float rudderAngle;
	public float wheelAngle;
	public boolean engineActive;
	public boolean sailFurled;
	public boolean grounded;
	public float speed;
	public int hurtTime;
	public float damage;
	public final boolean[] cleatTied = new boolean[6];
}
