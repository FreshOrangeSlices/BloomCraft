package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.bosses.relic.RelicManager;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Spawns;
import com.bloomcraft.core.Text;
import com.bloomcraft.mixin.AgeableMobInvoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** More curses: atmospheric scares (Terror, Echoes), Reduction, Matador and Mother Hen. */
public final class MoreCurses {

	private MoreCurses() {
	}

	public static final class Terror extends BaseRelic {
		private double chance = 2;
		private double cooldown = 90;
		private double seconds = 5;

		public Terror() {
			super("terror", "Terror", true);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance-per-second", 2);
			cooldown = s.getDouble("cooldown-seconds", 90);
			seconds = s.getDouble("darkness-seconds", 5);
		}

		@Override
		public String description() {
			return "Now and then the world goes dark and something roars nearby. Only you hear it.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (Rng.chance(chance) && ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				trigger(p, ctx.manager());
			}
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			RelicUtil.darkness(p, (int) (seconds * 20));
			Vec3 at = RelicUtil.behind(p, 6);
			Fx.playToAt(p, "entity.warden.heartbeat", at, 1.0f, 1.0f);
			if (Rng.chance(50)) {
				Fx.playToAt(p, "entity.warden.roar", at, 0.8f, 1.0f);
			} else {
				Fx.playToAt(p, "entity.warden.nearby_closer", at, 1.0f, 1.0f);
			}
			return true;
		}
	}

	public static final class Echoes extends BaseRelic {
		private static final String[] SOUNDS = {
			"entity.creeper.primed", "block.stone.step", "entity.zombie.ambient", "block.wooden_door.open",
			"entity.skeleton.ambient", "entity.tnt.primed", "ambient.cave", "entity.spider.ambient",
			"entity.enderman.stare", "block.chest.open"};
		private double chance = 3;
		private double cooldown = 45;

		public Echoes() {
			super("echoes", "Echoes", true);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance-per-second", 3);
			cooldown = s.getDouble("cooldown-seconds", 45);
		}

		@Override
		public String description() {
			return "You keep hearing things that aren't there. Only you.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (Rng.chance(chance) && ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				trigger(p, ctx.manager());
			}
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			String sound = SOUNDS[Rng.between(0, SOUNDS.length - 1)];
			Fx.playToAt(p, sound, RelicUtil.behind(p, Rng.between(3.0, 7.0)), 1.0f, (float) Rng.between(0.9, 1.1));
			return true;
		}
	}

	public static final class Reduction extends BaseRelic {
		private double size = 0.55;
		private double reachPenalty = 1.0;

		public Reduction() {
			super("reduction", "Reduction", true);
		}

		@Override
		public void load(Cfg s) {
			size = Math.max(0.1, Math.min(1.0, s.getDouble("size", 0.55)));
			reachPenalty = s.getDouble("reach-penalty", 1.0);
		}

		@Override
		public String description() {
			return "Shrinks you to " + Text.num(size * 100) + "% size, and your reach is " + Text.num(reachPenalty)
				+ " block shorter.";
		}

		@Override
		public List<AttributeBonus> attributeBonuses() {
			return List.of(
				new AttributeBonus(Attributes.SCALE, size - 1.0, AttributeModifier.Operation.ADD_VALUE),
				new AttributeBonus(Attributes.ENTITY_INTERACTION_RANGE, -reachPenalty, AttributeModifier.Operation.ADD_VALUE),
				new AttributeBonus(Attributes.BLOCK_INTERACTION_RANGE, -reachPenalty, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	public static final class Matador extends BaseRelic {
		private static final Set<EntityType<?>> ANGERED = Set.of(EntityTypes.HOGLIN, EntityTypes.ZOGLIN,
			EntityTypes.POLAR_BEAR, EntityTypes.GOAT, EntityTypes.RAVAGER);
		private double radius = 16;
		private double knockback = 1.2;

		public Matador() {
			super("matador", "Matador", true);
		}

		@Override
		public void load(Cfg s) {
			radius = s.getDouble("radius", 16);
			knockback = s.getDouble("knockback", 1.2);
		}

		@Override
		public String description() {
			return "Hoglins, Zoglins, goats and bears charge at you, and their hits send you flying.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (p.isCreative() || p.isSpectator()) {
				return;
			}
			AABB box = p.getBoundingBox().inflate(radius, radius / 2, radius);
			for (Mob mob : p.level().getEntitiesOfClass(Mob.class, box, m -> ANGERED.contains(m.getType()))) {
				if (BossManager.isPet(mob) || mob.getTarget() != null) {
					continue;
				}
				if (mob instanceof Goat goat) {
					// goats don't fight, they ram: point the ram at the player and skip its cooldown
					goat.getBrain().eraseMemory(MemoryModuleType.RAM_COOLDOWN_TICKS);
					goat.getBrain().setMemory(MemoryModuleType.RAM_TARGET, p.position());
					continue;
				}
				mob.setTarget(p);
				mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, (LivingEntity) p); // hoglins and zoglins think with a brain
			}
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			LivingEntity attacker = damage.attacker();
			if (attacker == null || attacker instanceof Player || !ANGERED.contains(attacker.getType())) {
				return;
			}
			ServerPlayer p = ctx.player();
			Vec3 push = new Vec3(p.getX() - attacker.getX(), 0, p.getZ() - attacker.getZ());
			if (push.lengthSqr() < 0.01) {
				return;
			}
			Vec3 v = push.normalize().scale(knockback);
			Scheduler.later(1, () -> Motion.set(p, new Vec3(v.x, 0.5, v.z)));
		}
	}

	public static final class MotherHen extends BaseRelic {
		static final String TAG = "mother-hen";
		private final Map<UUID, List<UUID>> chicks = new HashMap<>();
		private int maxChicks = 5;

		public MotherHen() {
			super(TAG, "Mother Hen", true);
		}

		@Override
		public void load(Cfg s) {
			maxChicks = Math.max(1, Math.min(15, s.getInt("chicks", 5)));
		}

		@Override
		public String description() {
			return "A flock of " + maxChicks + " chicks follows you everywhere. They never grow up.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			ServerLevel level = p.level();
			List<UUID> flock = chicks.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
			Iterator<UUID> it = flock.iterator();
			while (it.hasNext()) {
				Entity chick = level.getEntity(it.next());
				if (chick == null || !chick.isAlive()) {
					// gone, or left behind in another dimension (never saved, so it vanishes there): a new one hatches here
					it.remove();
					continue;
				}
				double distSq = chick.distanceToSqr(p);
				if (distSq > 24 * 24) {
					SafeSpots.Spot spot = SafeSpots.behind(p, 2);
					if (spot != null) {
						chick.teleportTo(spot.pos().x, spot.pos().y, spot.pos().z);
					}
				} else if (distSq > 9 && chick instanceof Mob mob) {
					mob.getNavigation().moveTo(p, 1.3);
				}
			}
			if (flock.size() < maxChicks) {
				SafeSpots.Spot spot = SafeSpots.behind(p, 1.5);
				Vec3 at = spot != null ? spot.pos() : p.position();
				Chicken chick = Spawns.effectMob(level, EntityTypes.CHICKEN, at, Rng.r().nextFloat() * 360f, TAG, c -> {
					c.setBaby(true);
					((AgeableMobInvoker) c).bloomcraft$setAgeLocked(true);
				});
				if (chick != null) {
					flock.add(chick.getUUID());
				}
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player) {
			List<UUID> flock = chicks.remove(player.getUUID());
			if (flock == null) {
				return;
			}
			for (ServerLevel level : player.level().getServer().getAllLevels()) {
				for (UUID id : flock) {
					Entity chick = level.getEntity(id);
					if (chick != null) {
						chick.discard();
					}
				}
			}
		}
	}
}
