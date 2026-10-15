package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class FrostboundTrait extends BaseTrait {

	private double seconds = 3;
	private int amplifier = 1;

	public FrostboundTrait() {
		super("frostbound", TraitCategory.CONTROL, "Frostbound", "Frostbound");
	}

	@Override
	public void load(Cfg s) {
		seconds = s.getDouble("effect-seconds", 3);
		amplifier = Math.max(0, s.getInt("amplifier", 1));
	}

	@Override
	public String description() {
		return "Hits inflict Slowness " + Text.roman(amplifier + 1) + " for " + Text.num(seconds) + "s.";
	}

	@Override
	public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
		if (ctx.secondary() || finalDamage <= 0) {
			return;
		}
		int ticks = (int) Math.round(seconds * 20 * boss.power());
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier), boss.entity());
		// visual frost only: stay below the maximum so no freeze damage is dealt
		victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), Math.max(0, victim.getTicksRequiredToFreeze() - 20)));
		Fx.particle(boss.level(), ParticleTypes.SNOWFLAKE, Fx.center(victim), 12, 0.4, 0.02);
	}
}
