package com.bloomcraft.core;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Setting an entity's velocity so it really moves (players are told right away). */
public final class Motion {

	private Motion() {
	}

	public static void set(Entity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		entity.needsSync = true;
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), velocity));
		}
	}

	/** Flat direction from {@code from} to {@code to}, normalized (or a small default if they overlap). */
	public static Vec3 flatDirection(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
		return d.lengthSqr() < 1.0E-4 ? new Vec3(0.1, 0, 0) : d.normalize();
	}
}
