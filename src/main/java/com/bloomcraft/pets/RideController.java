package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Riding pets. The pet doesn't need a saddle and nobody else steers it: every tick it moves the way its rider is
 * pressing (WASD, jump, sprint), at its category's speed.
 *
 * <ul>
 *     <li>Small pets: the pet either grows big enough to carry you, or you shrink to its size (picked once per pet).</li>
 *     <li>Bees, Allays and Bats hover: hold jump to rise (a few blocks at most), let go to drift down.</li>
 * </ul>
 */
public final class RideController {

	public static final Identifier MOD_RIDE_SCALE = BloomCraft.id("ride_scale");

	private static final class Ride {
		final PetManager.Active active;
		final UUID rider;
		@Nullable Vec3 last;
		double travelled;

		Ride(PetManager.Active active, UUID rider) {
			this.active = active;
			this.rider = rider;
		}
	}

	private final Pets mod;
	private final Map<UUID, Ride> rides = new HashMap<>();
	/** Players who just got off a pet: their landing is forgiving for a moment. */
	private final Map<UUID, Long> softLanding = new HashMap<>();
	private boolean mounting;

	RideController(Pets mod) {
		this.mod = mod;
	}

	/** True while we are seating the owner (the only way onto a pet). */
	public boolean mounting() {
		return mounting;
	}

	public boolean isRidden(PetManager.Active a) {
		Ride r = rides.get(a.owner);
		return r != null && r.active == a;
	}

	public void tryMount(ServerPlayer p, PetManager.Active a) {
		Pet pet = a.pet;
		PetSettings s = mod.settings();
		if (!pet.species.rideable()) {
			PetMsg.error(p, pet.species.plural() + " can't be ridden.");
			return;
		}
		int rideLevel = s.rideLevel(pet.species);
		if (pet.level < rideLevel) {
			PetMsg.error(p, pet.name + " can be ridden from level " + rideLevel + " (now level " + pet.level + ").");
			return;
		}
		Mob m = a.entity;
		if (p.isPassenger() || m.isVehicle() || a.fainting) {
			return;
		}
		double scale = pet.species.rideScale();
		if (pet.rideStyle == Pet.RideStyle.GROW && scale > 0) {
			setScale(m, scale);
		} else if (pet.rideStyle == Pet.RideStyle.SHRINK && scale > 0) {
			setScale(p, Math.max(s.minRiderScale, 1 / scale));
		}
		boolean seated;
		mounting = true;
		try {
			seated = p.startRiding(m, true, true);
		} finally {
			mounting = false;
		}
		if (!seated) {
			setScale(m, 1);
			setScale(p, 1);
			return;
		}
		Ride ride = new Ride(a, p.getUUID());
		ride.last = m.position();
		rides.put(p.getUUID(), ride);
		a.target = null;
		PetMsg.bar(p, Component.empty().append(Text.colored("Riding " + pet.name + "  ", pet.species.category().color()))
			.append(PetMsg.text(pet.species.hovers() ? "hold jump to rise · sneak to get off" : "sneak to get off",
				ChatFormatting.GRAY)));
	}

	/** Takes the rider off this pet (it's being put away) and puts everyone back to normal size. */
	public void eject(PetManager.Active a) {
		Ride r = rides.get(a.owner);
		if (r != null && r.active == a) {
			end(r, true);
		}
	}

	/** The player left the game. */
	public void forget(UUID player) {
		Ride r = rides.get(player);
		if (r != null) {
			end(r, true);
		}
		softLanding.remove(player);
	}

	private void end(Ride r, boolean dismount) {
		rides.remove(r.rider);
		softLanding.put(r.rider, System.currentTimeMillis() + 1200); // just the hop down, not a long fall
		Mob m = r.active.entity;
		ServerPlayer p = PetManager.player(r.rider);
		if (p != null) {
			setScale(p, 1);
			if (dismount && p.getVehicle() == m) {
				p.stopRiding();
			}
			p.resetFallDistance();
		}
		if (!m.isRemoved()) {
			setScale(m, 1);
			m.setDeltaMovement(new Vec3(0, m.getDeltaMovement().y, 0));
		}
	}

	void tick() {
		if (rides.isEmpty()) {
			return;
		}
		for (Ride r : List.copyOf(rides.values())) {
			ServerPlayer p = PetManager.player(r.rider);
			Mob m = r.active.entity;
			if (p == null || !m.isAlive() || m.isRemoved() || p.getVehicle() != m) {
				end(r, true);
				continue;
			}
			steer(p, r);
		}
	}

	private void steer(ServerPlayer p, Ride r) {
		Mob m = r.active.entity;
		Pet pet = r.active.pet;
		Species sp = pet.species;
		Category cat = sp.category();
		Input in = p.getLastClientInput();

		double forward = (in.forward() ? 1 : 0) - (in.backward() ? 0.5 : 0);
		double strafe = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		double yaw = Math.toRadians(p.getYRot());
		// forward is (-sin, cos); right is (-cos, -sin)
		double mx = -Math.sin(yaw) * forward - Math.cos(yaw) * strafe;
		double mz = Math.cos(yaw) * forward - Math.sin(yaw) * strafe;

		double speed = cat.rideSpeed() * (1 + 0.02 * (pet.level - 1));
		if (in.sprint() && forward > 0) {
			speed *= cat == Category.SPEEDSTER ? 1.25 : 1.15;
		}
		boolean swimming = sp.swims() && m.isInWater();
		boolean water = m.isInWater() && !swimming;
		if (water) {
			speed *= 0.6;
		}
		double pitch = Math.toRadians(p.getXRot());
		if (swimming) {
			// as quick as a boat, and you steer up and down by looking
			speed = (sp == Species.TURTLE ? 0.42 : 0.38) * (1 + 0.02 * (pet.level - 1)) * (in.sprint() ? 1.15 : 1);
			if (forward > 0) {
				mx *= Math.cos(pitch);
				mz *= Math.cos(pitch);
			}
		}

		double len = Math.sqrt(mx * mx + mz * mz);
		Vec3 v = m.getDeltaMovement();
		double vx;
		double vz;
		if (len > 0.01) {
			double scale = Math.min(1, len) / len * speed;
			vx = mx * scale;
			vz = mz * scale;
		} else {
			vx = v.x * 0.5;
			vz = v.z * 0.5;
		}

		double vy = v.y;
		if (sp.hovers()) {
			double hover = mod.settings().hoverHeight;
			double height = m.getY() - groundBelow(m, hover + 3);
			if (in.jump()) {
				vy = height < hover ? 0.2 : Math.max(-0.12, Math.min(0, hover - height)); // hold at the ceiling
			} else {
				vy = m.onGround() ? 0 : -0.12; // drift down gently
			}
		} else if (swimming) {
			vy = forward > 0 ? -Math.sin(pitch) * speed * forward : 0.03; // look down to dive; idle: drift up
			if (in.jump()) {
				vy = Math.max(vy, 0.16);
			}
		} else if (m.isInWater() || (sp == Species.STRIDER && m.isInLava())) {
			vy = in.jump() ? 0.14 : (water ? 0.04 : vy);
		} else if (in.jump() && m.onGround()) {
			vy = cat.jumpVelocity() * (1 + 0.01 * (pet.level - 1));
			if (sp == Species.RABBIT) {
				r.active.level().broadcastEntityEvent(m, (byte) 1);
			}
		} else if (sp == Species.RABBIT && m.onGround() && len > 0.01) {
			vy = 0.25; // bunnies hop along
			r.active.level().broadcastEntityEvent(m, (byte) 1);
		}

		Motion.set(m, new Vec3(vx, vy, vz));
		float facing = len > 0.01 ? PetManager.yaw(mx, mz) : p.getYRot();
		PetManager.face(m, facing);

		// bond XP for distance travelled
		Vec3 now = m.position();
		if (r.last != null) {
			double dx = now.x - r.last.x;
			double dz = now.z - r.last.z;
			double step = Math.sqrt(dx * dx + dz * dz);
			if (step < 2) {
				r.travelled += step;
			}
			int per = mod.settings().rideBlocksPerXp;
			if (r.travelled >= per) {
				int xp = (int) (r.travelled / per);
				r.travelled -= xp * per;
				mod.pets().addXp(pet, xp);
			}
		}
		r.last = now;
	}

	/** The y of the ground under the pet, or far below if there's none within {@code range} blocks. */
	private static double groundBelow(LivingEntity e, double range) {
		Level level = e.level();
		BlockPos b = e.blockPosition();
		for (int i = 0; i <= range; i++) {
			BlockPos below = b.below(i);
			BlockState state = level.getBlockState(below);
			VoxelShape shape = state.getCollisionShape(level, below);
			boolean liquid = !level.getFluidState(below).isEmpty();
			if (!shape.isEmpty() || liquid) {
				double top = shape.isEmpty() ? 1 : shape.max(net.minecraft.core.Direction.Axis.Y);
				return below.getY() + (liquid ? 1 : top);
			}
		}
		return e.getY() - range - 10;
	}

	static void setScale(LivingEntity e, double factor) {
		Attrs.setTransient(e, Attributes.SCALE, MOD_RIDE_SCALE, factor - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/**
	 * Riders land like they're on a horse: half the fall height counts, so ordinary jumps never hurt and only big
	 * drops do (also for the hop down right after getting off). The damage to deal, or -1 for none.
	 */
	float riderFall(ServerPlayer p, DamageSource source, float amount) {
		if (!source.is(DamageTypes.FALL)) {
			return amount;
		}
		boolean riding = p.getVehicle() != null && Pets.isPet(p.getVehicle());
		if (!riding && System.currentTimeMillis() > softLanding.getOrDefault(p.getUUID(), 0L)) {
			return amount;
		}
		double damage = Math.ceil(amount * 0.5 - 1.5);
		return damage <= 0 ? -1 : (float) damage;
	}

	/** Leftover size changes (a crash mid-ride) don't survive: transient modifiers are never saved. */
	void shutdown() {
		for (Ride r : List.copyOf(rides.values())) {
			end(r, true);
		}
		softLanding.clear();
	}
}
