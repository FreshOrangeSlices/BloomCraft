package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.Presentation;
import com.bloomcraft.bosses.combat.CombatGuard;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** The good relics. */
public final class Relics {

	private Relics() {
	}

	public static final class BloodPact extends BaseRelic {
		private double healPercent = 12;

		public BloodPact() {
			super("blood-pact", "Blood Pact", false);
		}

		@Override
		public void load(Cfg s) {
			healPercent = s.getDouble("heal-percent", 12);
		}

		@Override
		public String description() {
			return "Your attacks heal you for " + Text.num(healPercent) + "% of the damage dealt.";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			RelicUtil.heal(ctx.player(), finalDamage * healPercent / 100.0);
		}
	}

	public static final class SoulHarvest extends BaseRelic {
		private double heal = 3;
		private double bossHeal = 12;

		public SoulHarvest() {
			super("soul-harvest", "Soul Harvest", false);
		}

		@Override
		public void load(Cfg s) {
			heal = s.getDouble("heal", 3);
			bossHeal = s.getDouble("boss-heal", 12);
		}

		@Override
		public String description() {
			return "Kills restore " + Text.num(heal / 2) + " hearts (" + Text.num(bossHeal / 2) + " for bosses).";
		}

		@Override
		public void onKill(RelicContext ctx, LivingEntity victim, boolean victimWasBoss) {
			RelicUtil.heal(ctx.player(), victimWasBoss ? bossHeal : heal);
			Fx.particle(ctx.player().level(), ParticleTypes.SOUL, Fx.center(victim), 6, 0.3, 0.03);
		}
	}

	public static final class SecondDawn extends BaseRelic {
		private double cooldownMinutes = 10;

		public SecondDawn() {
			super("second-dawn", "Second Dawn", false);
		}

		@Override
		public void load(Cfg s) {
			cooldownMinutes = s.getDouble("cooldown-minutes", 10);
		}

		@Override
		public String description() {
			return "Saves you from a killing blow like a Totem (every " + Text.num(cooldownMinutes) + " min).";
		}

		@Override
		public boolean onLethal(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			long now = p.level().getGameTime();
			if (now < Data.longValue(p, BossKeys.SECOND_DAWN, 0L)) {
				return false;
			}
			Data.of(p).putLong(BossKeys.SECOND_DAWN, now + Math.round(cooldownMinutes * 60 * 20));
			// like a Totem of Undying
			p.setHealth(1.0f);
			p.removeAllEffects();
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
			p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
			p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
			Fx.particle(p.level(), ParticleTypes.TOTEM_OF_UNDYING, Fx.center(p), 60, 0.6, 0.4);
			Fx.play(p, "item.totem.use", 1.0f, 1.0f);
			p.sendSystemMessage(Bosses.get().settings().messages.prefixed("second-dawn", java.util.Map.of()));
			return true;
		}
	}

	public static final class BrambleHeart extends BaseRelic {
		private double reflectPercent = 25;

		public BrambleHeart() {
			super("bramble-heart", "Bramble Heart", false);
		}

		@Override
		public void load(Cfg s) {
			reflectPercent = s.getDouble("reflect-percent", 25);
		}

		@Override
		public String description() {
			return "Melee attackers take " + Text.num(reflectPercent) + "% of their damage back.";
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			LivingEntity attacker = damage.attacker();
			if (damage.melee() && !damage.secondary() && attacker != null && attacker != ctx.player()) {
				double amount = finalDamage * reflectPercent / 100.0;
				if (amount >= 0.5) {
					CombatGuard.damage(attacker, amount, ctx.player(), DamageTypes.THORNS);
				}
			}
		}
	}

	public static final class Emberheart extends BaseRelic {
		private double seconds = 4;

		public Emberheart() {
			super("emberheart", "Emberheart", false);
		}

		@Override
		public void load(Cfg s) {
			seconds = s.getDouble("ignite-seconds", 4);
		}

		@Override
		public String description() {
			return "Held: hits set foes ablaze. Worn: melee attackers catch fire (" + Text.num(seconds) + "s).";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			if (ctx.inHand()) {
				victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), (int) (seconds * 20)));
			}
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			LivingEntity a = damage.attacker();
			if (!ctx.inHand() && damage.melee() && a != null && !damage.secondary()) {
				a.setRemainingFireTicks(Math.max(a.getRemainingFireTicks(), (int) (seconds * 20)));
			}
		}
	}

	public static final class Frostbite extends BaseRelic {
		private double seconds = 2;
		private int amplifier = 1;

		public Frostbite() {
			super("frostbite", "Frostbite", false);
		}

		@Override
		public void load(Cfg s) {
			seconds = s.getDouble("slow-seconds", 2);
			amplifier = Math.max(0, s.getInt("amplifier", 1));
		}

		@Override
		public String description() {
			return "Held: hits slow foes. Worn: melee attackers are slowed (Slowness " + Text.roman(amplifier + 1)
				+ ", " + Text.num(seconds) + "s).";
		}

		private void chill(LivingEntity target) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (seconds * 20), amplifier));
			if (target.level() instanceof ServerLevel level) {
				Fx.particle(level, ParticleTypes.SNOWFLAKE, Fx.center(target), 8, 0.3, 0.02);
			}
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			if (ctx.inHand()) {
				chill(victim);
			}
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			if (!ctx.inHand() && damage.melee() && damage.attacker() != null && !damage.secondary()) {
				chill(damage.attacker());
			}
		}
	}

	public static final class Stormcaller extends BaseRelic {
		private double chance = 10;
		private double bonusDamage = 4;

		public Stormcaller() {
			super("stormcaller", "Stormcaller", false);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance", 10);
			bonusDamage = s.getDouble("bonus-damage", 4);
		}

		@Override
		public String description() {
			return Text.num(chance) + "% chance to call lightning for +" + Text.num(bonusDamage)
				+ " damage (on hit if held, on attackers if worn).";
		}

		@Override
		public void onAttack(RelicContext ctx, LivingEntity victim, DamageContext damage, boolean victimIsBoss) {
			if (ctx.inHand() && Rng.chance(chance)) {
				damage.addFlat(bonusDamage);
				Presentation.lightningEffect(ctx.player().level(), victim.position());
			}
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			LivingEntity attacker = damage.attacker();
			if (!ctx.inHand() && damage.melee() && attacker != null && !damage.secondary() && Rng.chance(chance)) {
				Presentation.lightningEffect(ctx.player().level(), attacker.position());
				CombatGuard.damage(attacker, bonusDamage, ctx.player(), DamageTypes.LIGHTNING_BOLT);
			}
		}
	}

	public static final class Windstep extends BaseRelic {
		private double cooldown = 4;
		private double strength = 1.2;

		public Windstep() {
			super("windstep", "Windstep", false);
		}

		@Override
		public void load(Cfg s) {
			cooldown = s.getDouble("cooldown-seconds", 4);
			strength = s.getDouble("strength", 1.2);
		}

		@Override
		public String description() {
			return "Jump while sneaking to dash forward (every " + Text.num(cooldown) + "s).";
		}

		@Override
		public void onJump(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (!p.isShiftKeyDown() || !ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				return;
			}
			Vec3 look = p.getLookAngle();
			Vec3 dir = new Vec3(look.x, 0, look.z);
			if (dir.lengthSqr() < 0.01) {
				return;
			}
			Vec3 v = dir.normalize().scale(strength);
			Scheduler.later(1, () -> Motion.set(p, new Vec3(v.x, 0.38, v.z)));
			Fx.play(p, "entity.breeze.shoot", 0.8f, 1.4f);
			Fx.particle(p.level(), ParticleTypes.CLOUD, p.position(), 12, 0.3, 0.05);
		}
	}

	public static final class HuntersMark extends BaseRelic {
		private double rewardBonus = 25;
		private double bossDamageBonus = 10;

		public HuntersMark() {
			super("hunters-mark", "Hunter's Mark", false);
		}

		@Override
		public void load(Cfg s) {
			rewardBonus = s.getDouble("reward-chance-bonus", 25);
			bossDamageBonus = s.getDouble("boss-damage-bonus", 10);
		}

		@Override
		public String description() {
			return "+" + Text.num(bossDamageBonus) + "% damage to bosses, and their reward chances are "
				+ Text.num(rewardBonus) + "% higher for you.";
		}

		@Override
		public void onAttack(RelicContext ctx, LivingEntity victim, DamageContext damage, boolean victimIsBoss) {
			if (victimIsBoss) {
				damage.multiply(1.0 + bossDamageBonus / 100.0);
			}
		}

		@Override
		public double rewardChanceMultiplier() {
			return 1.0 + rewardBonus / 100.0;
		}
	}

	public static final class EchoStrike extends BaseRelic {
		private double chance = 15;
		private double echoPercent = 50;

		public EchoStrike() {
			super("echo-strike", "Echo Strike", false);
		}

		@Override
		public void load(Cfg s) {
			chance = s.getDouble("chance", 15);
			echoPercent = s.getDouble("echo-damage-percent", 50);
		}

		@Override
		public String description() {
			return Text.num(chance) + "% chance for a melee hit to strike again a moment later for "
				+ Text.num(echoPercent) + "% damage.";
		}

		@Override
		public void afterAttack(RelicContext ctx, LivingEntity victim, double finalDamage, DamageContext damage) {
			if (!damage.melee() || finalDamage <= 0 || !Rng.chance(chance)) {
				return;
			}
			ServerPlayer p = ctx.player();
			double amount = finalDamage * echoPercent / 100.0;
			// 12 ticks: just after the victim's hurt-immunity window ends
			Scheduler.later(12, () -> {
				if (victim.isAlive() && !p.hasDisconnected()) {
					CombatGuard.damage(victim, amount, p, DamageTypes.PLAYER_ATTACK);
					if (victim.level() instanceof ServerLevel level) {
						Fx.particle(level, ParticleTypes.SWEEP_ATTACK, Fx.center(victim), 1, 0, 0);
					}
					Fx.play(victim, "entity.player.attack.sweep", 0.8f, 1.6f);
				}
			});
		}
	}

	public static final class OwlsSight extends BaseRelic {
		public OwlsSight() {
			super("owls-sight", "Owl's Sight", false);
		}

		@Override
		public String description() {
			return "Grants Night Vision while equipped.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			RelicUtil.refreshEffect(ctx.player(), MobEffects.NIGHT_VISION, 0);
		}
	}

	public static final class Lodestone extends BaseRelic {
		private double radius = 6;

		public Lodestone() {
			super("lodestone", "Lodestone", false);
		}

		@Override
		public void load(Cfg s) {
			radius = s.getDouble("radius", 6);
		}

		@Override
		public String description() {
			return "Pulls nearby items and experience orbs (" + Text.num(radius) + " blocks) toward you.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			Vec3 target = p.position().add(0, 0.5, 0);
			for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(radius))) {
				if (e instanceof ItemEntity item) {
					if (item.getOwner() == p || item.hasPickUpDelay()) {
						continue;
					}
				} else if (!(e instanceof ExperienceOrb)) {
					continue;
				}
				Vec3 pull = target.subtract(e.position());
				if (pull.lengthSqr() > 1) {
					Motion.set(e, pull.normalize().scale(0.55));
				}
			}
		}
	}

	public static final class Aegis extends BaseRelic {
		private double threshold = 30;
		private int level = 2;
		private double duration = 10;
		private double cooldown = 60;

		public Aegis() {
			super("aegis", "Aegis", false);
		}

		@Override
		public void load(Cfg s) {
			threshold = s.getDouble("health-threshold", 30);
			level = Math.max(1, s.getInt("absorption-level", 2));
			duration = s.getDouble("duration-seconds", 10);
			cooldown = s.getDouble("cooldown-seconds", 60);
		}

		@Override
		public String description() {
			return "Dropping below " + Text.num(threshold) + "% health grants Absorption " + Text.roman(level)
				+ " (every " + Text.num(cooldown) + "s).";
		}

		@Override
		public void afterDamaged(RelicContext ctx, double finalDamage, DamageContext damage) {
			ServerPlayer p = ctx.player();
			double after = p.getHealth();
			if (after <= 0 || after / p.getMaxHealth() * 100.0 > threshold) {
				return;
			}
			if (!ctx.manager().ready(p, id(), (int) Math.round(cooldown * 20))) {
				return;
			}
			p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, (int) (duration * 20), level - 1));
			Fx.play(p, "block.beacon.power_select", 0.8f, 1.6f);
			Fx.particle(p.level(), ParticleTypes.ENCHANTED_HIT, Fx.center(p), 15, 0.4, 0.1);
		}
	}

	public static final class Featherweight extends BaseRelic {
		private double reduction = 75;

		public Featherweight() {
			super("featherweight", "Featherweight", false);
		}

		@Override
		public void load(Cfg s) {
			reduction = s.getDouble("fall-reduction", 75);
		}

		@Override
		public String description() {
			return "Take " + Text.num(reduction) + "% less fall damage.";
		}

		@Override
		public void onDamaged(RelicContext ctx, DamageContext damage) {
			if (damage.source().is(DamageTypeTags.IS_FALL)) {
				damage.multiply(Math.max(0, 1.0 - reduction / 100.0));
			}
		}
	}

	public static final class Prospector extends BaseRelic {
		private double xpBonus = 50;

		public Prospector() {
			super("prospector", "Prospector", false);
		}

		@Override
		public void load(Cfg s) {
			xpBonus = s.getDouble("xp-bonus", 50);
		}

		@Override
		public String description() {
			return "Mobs you kill drop " + Text.num(xpBonus) + "% more experience.";
		}

		@Override
		public void onKill(RelicContext ctx, LivingEntity victim, boolean victimWasBoss) {
			ServerLevel level = ctx.player().level();
			int base = victim.getExperienceReward(level, ctx.player());
			int extra = (int) Math.round(base * xpBonus / 100.0);
			if (extra > 0) {
				ExperienceOrb.award(level, victim.position(), extra);
			}
		}
	}
}
