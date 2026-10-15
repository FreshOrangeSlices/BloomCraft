package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class VenomousTrait extends BaseTrait {

	private double seconds = 4;
	private int amplifier = 1;

	public VenomousTrait() {
		super("venomous", TraitCategory.OFFENSE, "Venomous", "Venomous");
	}

	@Override
	public void load(Cfg s) {
		seconds = s.getDouble("effect-seconds", 4);
		amplifier = Math.max(0, s.getInt("amplifier", 1));
	}

	@Override
	public String description() {
		return "Hits inflict Poison " + Text.roman(amplifier + 1) + " for " + Text.num(seconds) + "s.";
	}

	@Override
	public void afterAttack(Boss boss, LivingEntity victim, double finalDamage, DamageContext ctx) {
		if (ctx.secondary() || finalDamage <= 0) {
			return;
		}
		int ticks = (int) Math.round(seconds * 20 * boss.power());
		victim.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amplifier), boss.entity());
	}
}
