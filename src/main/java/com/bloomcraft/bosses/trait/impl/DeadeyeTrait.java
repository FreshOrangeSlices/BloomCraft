package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Set;

public final class DeadeyeTrait extends BaseTrait {

	private static final Set<EntityType<?>> EXTRA_SHOOTERS = Set.of(EntityTypes.BLAZE, EntityTypes.GHAST, EntityTypes.BREEZE);
	private double speedBonus = 40;
	private double damageBonus = 25;

	public DeadeyeTrait() {
		super("deadeye", TraitCategory.OFFENSE, "Deadeye", "Deadeye");
	}

	@Override
	public void load(Cfg s) {
		speedBonus = s.getDouble("projectile-speed-bonus", 40);
		damageBonus = s.getDouble("damage-bonus", 25);
	}

	@Override
	public String description() {
		return "Ranged mobs only. Projectiles fly " + Text.num(speedBonus) + "% faster and deal +"
			+ Text.num(damageBonus) + "% damage.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return entity instanceof RangedAttackMob || EXTRA_SHOOTERS.contains(entity.getType());
	}

	@Override
	public void onProjectileLaunch(Boss boss, Projectile projectile) {
		if (VolleyTrait.isSpawning()) {
			return; // volley arrows copy the already-boosted velocity of the main arrow
		}
		projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1.0 + speedBonus / 100.0));
	}

	@Override
	public void onAttack(Boss boss, LivingEntity victim, DamageContext ctx) {
		if (ctx.projectile()) {
			ctx.multiply(1.0 + damageBonus * boss.power() / 100.0);
		}
	}
}
