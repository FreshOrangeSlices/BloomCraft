package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class BerserkTrait extends BaseTrait {

	private static final String FLAG = "berserk";
	private double threshold = 50;
	private double damageBonus = 35;
	private double speedBonus = 8;

	public BerserkTrait() {
		super("berserk", TraitCategory.OFFENSE, "Berserk", "Raging");
	}

	@Override
	public void load(Cfg s) {
		threshold = s.getDouble("health-threshold", 50);
		damageBonus = s.getDouble("damage-bonus", 35);
		speedBonus = s.getDouble("speed-bonus", 8);
	}

	@Override
	public String description() {
		return "Below " + Text.num(threshold) + "% health it enrages: +" + Text.num(damageBonus) + "% damage and +"
			+ Text.num(speedBonus) + "% speed.";
	}

	@Override
	public void onApply(Boss boss, boolean fresh) {
		if (Attrs.has(boss.entity(), Attributes.MOVEMENT_SPEED, BossKeys.MOD_TRAIT_BERSERK)) {
			boss.setFlag(FLAG, true); // already enraged before it was unloaded
		}
	}

	@Override
	public void onTick(Boss boss, int now) {
		if (!boss.flag(FLAG) && boss.healthRatio() * 100.0 <= threshold) {
			enrage(boss);
		}
	}

	@Override
	public void afterDamaged(Boss boss, double finalDamage, DamageContext ctx) {
		if (!boss.flag(FLAG) && boss.health() > 0 && boss.healthRatio() * 100.0 <= threshold) {
			enrage(boss);
		}
	}

	@Override
	public void onAttack(Boss boss, LivingEntity victim, DamageContext ctx) {
		if (boss.flag(FLAG)) {
			ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
		}
	}

	private void enrage(Boss boss) {
		boss.setFlag(FLAG, true);
		LivingEntity e = boss.entity();
		// a flat bonus: rank power makes its rage hit harder, not run faster
		Attrs.set(e, Attributes.MOVEMENT_SPEED, BossKeys.MOD_TRAIT_BERSERK, speedBonus / 100.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Fx.play(e, "entity.ravager.roar", 1.0f, 1.2f);
		Fx.particle(boss.level(), ParticleTypes.ANGRY_VILLAGER, Fx.center(e), 8, 0.6, 0);
		Bosses.get().presentation().messageNearby(boss, "enraged", 32);
	}
}
