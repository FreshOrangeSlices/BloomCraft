package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class UndyingTrait extends BaseTrait {

	private double reviveHealth = 50;

	public UndyingTrait() {
		super("undying", TraitCategory.DEFENSE, "Undying", "Undying");
	}

	@Override
	public void load(Cfg s) {
		reviveHealth = s.getDouble("revive-health-percent", 50);
	}

	@Override
	public String description() {
		return "The first time it would die, it rises again with " + Text.num(reviveHealth) + "% health.";
	}

	@Override
	public boolean onLethalDamage(Boss boss) {
		if (boss.undyingUsed()) {
			return false;
		}
		boss.setUndyingUsed(true);
		LivingEntity e = boss.entity();
		Data.flag(e, BossKeys.UNDYING, true);
		e.setHealth((float) Math.max(1.0, boss.maxHealth() * Math.min(100.0, reviveHealth) / 100.0));
		e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 2));
		Fx.particle(boss.level(), ParticleTypes.TOTEM_OF_UNDYING, Fx.center(e), 40, 0.6, 0.3);
		Fx.play(e, "item.totem.use", 0.8f, 0.9f);
		Bosses.get().bossBars().updateHealth(boss);
		Bosses.get().presentation().messageNearby(boss, "undying", 32);
		return true;
	}
}
