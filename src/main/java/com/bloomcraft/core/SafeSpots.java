package com.bloomcraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Finding a safe place to stand (for blinks, teleports and summons). */
public final class SafeSpots {

	/** A place to stand, and which way to face. */
	public record Spot(Vec3 pos, float yaw) {
	}

	private SafeSpots() {
	}

	/** A safe spot about {@code distance} blocks behind the target, trying several angles; facing the target. */
	public static @Nullable Spot behind(Entity target, double distance) {
		ServerLevel level = (ServerLevel) target.level();
		Vec3 origin = target.position();
		Vec3 look = target.getLookAngle();
		Vec3 back = new Vec3(look.x, 0, look.z);
		if (back.lengthSqr() < 0.01) {
			back = new Vec3(1, 0, 0);
		}
		back = back.normalize().scale(-distance);
		double[] angles = {0, 45, -45, 90, -90, 135, -135, 180};
		for (double deg : angles) {
			Vec3 offset = back.yRot((float) Math.toRadians(deg));
			Vec3 spot = standable(level, origin.add(offset), 3);
			if (spot != null) {
				return new Spot(spot, yawTowards(spot, origin));
			}
		}
		return null;
	}

	/** A safe spot in a ring around {@code center} between min and max distance (random angles). */
	public static @Nullable Vec3 around(ServerLevel level, Vec3 center, double min, double max, int attempts) {
		for (int i = 0; i < attempts; i++) {
			double angle = Math.toRadians(Rng.between(0.0, 360.0));
			double dist = Rng.between(min, max);
			Vec3 spot = standable(level, center.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist), 6);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Searches up and down from the candidate for a block you can stand on (feet position, block-centred). */
	public static @Nullable Vec3 standable(ServerLevel level, Vec3 candidate, int verticalSearch) {
		int x = (int) Math.floor(candidate.x);
		int z = (int) Math.floor(candidate.z);
		int baseY = (int) Math.floor(candidate.y);
		for (int i = 0; i <= verticalSearch * 2; i++) {
			int dy = (i % 2 == 0) ? i / 2 : -(i / 2 + 1);
			int y = baseY + dy;
			if (y <= level.getMinY() || y >= level.getMaxY() - 2) {
				continue;
			}
			BlockPos feetPos = new BlockPos(x, y, z);
			if (open(level, feetPos) && open(level, feetPos.above())
				&& level.getBlockState(feetPos.below()).isFaceSturdy(level, feetPos.below(), Direction.UP)) {
				return new Vec3(x + 0.5, y, z + 0.5);
			}
		}
		return null;
	}

	/** Air-like: nothing to collide with and no liquid. */
	public static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.blocksMotion() && state.getFluidState().isEmpty();
	}

	public static float yawTowards(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
	}
}
