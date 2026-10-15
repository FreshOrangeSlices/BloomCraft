package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.bosses.relic.RelicManager;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Curses that a corrupted Relic carries on top of its good effect. Some are dangerous, some are just weird. */
public final class Curses {

	private Curses() {
	}

	public static final class Dread extends BaseRelic {
		private double chance = 20;
		private double seconds = 3;
		private double lightningChance = 1;
		private double lightningCooldown = 60;

		public Dread() {
			super("dread", "Dread", true);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance", 20);
			seconds = s.getDouble("duration-seconds", 3);
			lightningChance = s.getDouble("lightning-chance-per-second", 1);
			lightningCooldown = s.getDouble("lightning-cooldown-seconds", 60);
		}

		@Override
		public String description() {
			return "When hurt, " + Text.num(chance) + "% chance of Darkness. Harmless lightning strikes near you.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		/** Ominous, harmless lightning (no damage, no fire) a short distance away. */
		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (p.level().dimension() != Level.OVERWORLD || !Rng.chance(lightningChance)
				|| !ctx.manager().ready(p, id() + "-lightning", (int) Math.round(lightningCooldown * 20))) {
				return;
			}
			RelicUtil.strikeNear(p);
		}

		/** Test: the lightning plus the Darkness you'd get when hurt. */
		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			RelicUtil.strikeNear(p);
			darken(p);
			return true;
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			if (finalDamage > 0 && Rng.chance(chance)) {
				darken(ctx.player());
			}
		}

		private void darken(ServerPlayer p) {
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) (seconds * 20), 0));
			Fx.playTo(p, "ambient.cave", 1.0f, 0.8f);
		}
	}

	public static final class Butterfingers extends BaseRelic {
		private double chance = 2;

		public Butterfingers() {
			super("butterfingers", "Butterfingers", true);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance", 2);
		}

		@Override
		public String description() {
			return Text.num(chance) + "% chance to fumble your held item when you attack.";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			if (damage.melee() && !damage.secondary() && Rng.chance(chance)) {
				// after the hit has finished, so the swing still lands with the item
				ServerPlayer p = ctx.player();
				Scheduler.later(1, () -> fumble(p));
			}
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			fumble(p);
			return true;
		}

		private static void fumble(ServerPlayer p) {
			ItemStack held = p.getMainHandItem();
			if (held.isEmpty() || !p.isAlive()) {
				return;
			}
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			ServerLevel level = p.level();
			ItemEntity drop = new ItemEntity(level, p.getX(), p.getEyeY() - 0.4, p.getZ(), held);
			drop.setPickUpDelay(30);
			Vec3 look = p.getLookAngle();
			drop.setDeltaMovement(look.x * 0.25, 0.2, look.z * 0.25);
			level.addFreshEntity(drop);
			p.sendSystemMessage(Bosses.get().settings().messages.prefixed("butterfingers", Map.of()));
		}
	}

	public static final class FowlOmen extends BaseRelic {
		private double chance = 5;
		private double cooldown = 15;

		public FowlOmen() {
			super("fowl-omen", "Fowl Omen", true);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance", 5);
			cooldown = s.getDouble("cooldown-seconds", 15);
		}

		@Override
		public String description() {
			return "Your attacks sometimes summon a very confused chicken.";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			ServerPlayer p = ctx.player();
			if (damage.secondary() || !Rng.chance(chance) || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				return;
			}
			chicken(p.level(), victim.position());
		}

		@Override
		public boolean trigger(ServerPlayer p, RelicManager manager) {
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			Vec3 at = flat.lengthSqr() < 0.01 ? p.position() : p.position().add(flat.normalize().scale(2));
			chicken(p.level(), at);
			return true;
		}

		private static void chicken(ServerLevel level, Vec3 at) {
			Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (chicken == null) {
				return;
			}
			chicken.snapTo(at.x, at.y + 0.5, at.z, Rng.r().nextFloat() * 360f, 0);
			chicken.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
			chicken.setDeltaMovement(0, 0.4, 0);
			level.addFreshEntity(chicken);
			Fx.play(level, at, "entity.chicken.hurt", 1.0f, 1.0f);
		}
	}

	public static final class Insomnia extends BaseRelic {
		public Insomnia() {
			super("insomnia", "Insomnia", true);
		}

		@Override
		public String description() {
			return "You cannot sleep while this is equipped.";
		}

		@Override
		public boolean preventsSleep() {
			return true;
		}
	}

	public static final class Limelight extends BaseRelic {
		public Limelight() {
			super("limelight", "Limelight", true);
		}

		@Override
		public String description() {
			return "You glow, visible through walls to everyone.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			RelicUtil.refreshEffect(ctx.player(), MobEffects.GLOWING, 0);
		}

		@Override
		public void onDeactivate(ServerPlayer player) {
			MobEffectInstance glow = player.getEffect(MobEffects.GLOWING);
			if (glow != null && glow.isAmbient()) {
				player.removeEffect(MobEffects.GLOWING); // only the curse's own glow, not a Spectral Arrow's
			}
		}
	}

	public static final class Gluttony extends BaseRelic {
		private double exhaustion = 0.8;

		public Gluttony() {
			super("gluttony", "Gluttony", true);
		}

		@Override
		public void load(Cfg s) {
			exhaustion = s.getDouble("exhaustion", 0.8);
		}

		@Override
		public String description() {
			return "Every attack makes you noticeably hungrier.";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			if (!damage.secondary()) {
				ctx.player().causeFoodExhaustion((float) exhaustion);
			}
		}
	}

	public static final class Recoil extends BaseRelic {
		private double strength = 0.45;

		public Recoil() {
			super("recoil", "Recoil", true);
		}

		@Override
		public void load(Cfg s) {
			strength = s.getDouble("strength", 0.45);
		}

		@Override
		public String description() {
			return "Your melee hits knock you backwards too.";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			if (!damage.melee() || damage.secondary()) {
				return;
			}
			ServerPlayer p = ctx.player();
			Vec3 back = new Vec3(p.getX() - victim.getX(), 0, p.getZ() - victim.getZ());
			if (back.lengthSqr() < 0.01) {
				return;
			}
			Vec3 push = back.normalize().scale(strength);
			// a tick later: the attack itself slows a sprinting player down after the hit
			Scheduler.later(1, () -> Motion.set(p, new Vec3(push.x, 0.15, push.z)));
		}
	}

	public static final class GlassBones extends BaseRelic {
		private double multiplier = 2.0;

		public GlassBones() {
			super("glass-bones", "Glass Bones", true);
		}

		@Override
		public void load(Cfg s) {
			multiplier = s.getDouble("fall-multiplier", 2.0);
		}

		@Override
		public String description() {
			return "Fall damage is multiplied by " + Text.num(multiplier) + ".";
		}

		@Override
		public void onDamaged(RelicContext ctx, DamageContext damage) {
			if (damage.source().is(DamageTypeTags.IS_FALL)) {
				damage.multiply(multiplier);
			}
		}
	}

	public static final class BloodTithe extends BaseRelic {
		private double cost = 1.0;

		public BloodTithe() {
			super("blood-tithe", "Blood Tithe", true);
		}

		@Override
		public void load(Cfg s) {
			cost = s.getDouble("health-cost", 1.0);
		}

		@Override
		public String description() {
			return "Every kill costs you " + Text.num(cost / 2) + " heart (never fatal).";
		}

		@Override
		public void onKill(RelicContext ctx, LivingEntity victim, boolean victimWasBoss) {
			ServerPlayer p = ctx.player();
			if (p.getHealth() > 1.0f) {
				p.setHealth((float) Math.max(1.0, p.getHealth() - cost));
			}
		}
	}
}
