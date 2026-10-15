package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Text;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class VolleyTrait extends BaseTrait {

	private static final Set<EntityType<?>> ARCHERS = Set.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED,
		EntityTypes.PARCHED, EntityTypes.PILLAGER, EntityTypes.PIGLIN, EntityTypes.ILLUSIONER);

	/** Guard so the extra arrows we launch don't trigger another volley. */
	private static boolean spawning;
	private int extraArrows = 2;
	private double spread = 8;

	public VolleyTrait() {
		super("volley", TraitCategory.OFFENSE, "Volley", "Volleying");
	}

	@Override
	public void load(Cfg s) {
		extraArrows = Math.max(0, s.getInt("extra-arrows", 2));
		spread = s.getDouble("spread", 8);
	}

	@Override
	public String description() {
		return "Archers only. Every shot fires " + extraArrows + " extra arrows in a " + Text.num(spread) + "° spread.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return ARCHERS.contains(entity.getType());
	}

	public static boolean isSpawning() {
		return spawning;
	}

	@Override
	public void afterProjectileLaunch(Boss boss, Projectile projectile) {
		if (spawning || !(projectile instanceof AbstractArrow original)) {
			return;
		}
		Vec3 base = projectile.getDeltaMovement();
		LivingEntity e = boss.entity();
		spawning = true;
		try {
			for (int i = 1; i <= extraArrows; i++) {
				double side = (i % 2 == 0) ? 1 : -1;
				double angle = Math.toRadians(spread * side * Math.ceil(i / 2.0));
				AbstractArrow extra = ProjectileUtil.getMobArrow(e, new ItemStack(Items.ARROW), 1.6f, e.getMainHandItem());
				extra.setPos(original.position());
				extra.setDeltaMovement(base.yRot((float) angle));
				extra.pickup = AbstractArrow.Pickup.DISALLOWED;
				extra.setCritArrow(original.isCritArrow());
				boss.level().addFreshEntity(extra);
			}
		} finally {
			spawning = false;
		}
	}
}
