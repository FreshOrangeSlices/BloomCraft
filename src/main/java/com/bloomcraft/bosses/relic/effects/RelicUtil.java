package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.Presentation;
import com.bloomcraft.core.Rng;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Small helpers shared by relics and curses. */
public final class RelicUtil {

	private RelicUtil() {
	}

	public static void heal(LivingEntity entity, double amount) {
		if (amount > 0 && entity.isAlive()) {
			entity.setHealth((float) Math.min(entity.getMaxHealth(), entity.getHealth() + amount));
		}
	}

	/** A long-ish effect that's topped up before it runs out (no particles, icon shown). */
	public static void refreshEffect(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = entity.getEffect(effect);
		if (current == null || (!current.isInfiniteDuration() && current.getDuration() < 220)) {
			entity.addEffect(new MobEffectInstance(effect, 300, amplifier, true, false, true));
		}
	}

	/** Harmless lightning (no damage, no fire) 8-16 blocks from the player, on the surface. */
	public static void strikeNear(ServerPlayer p) {
		ServerLevel level = p.level();
		double angle = Math.toRadians(Rng.between(0.0, 360.0));
		double dist = Rng.between(8.0, 16.0);
		int x = (int) Math.floor(p.getX() + Math.cos(angle) * dist);
		int z = (int) Math.floor(p.getZ() + Math.sin(angle) * dist);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		Presentation.lightningEffect(level, new Vec3(x + 0.5, y, z + 0.5));
	}

	public static boolean overworld(Level level) {
		return level.dimension() == Level.OVERWORLD;
	}

	public static boolean isNight(Level level) {
		if (!overworld(level)) {
			return false;
		}
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 13000 && time < 23000;
	}

	public static int skyLight(Level level, BlockPos pos) {
		return level.getBrightness(LightLayer.SKY, pos);
	}

	public static int light(Level level, BlockPos pos) {
		return Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.isBrightOutside() ? skyLight(level, pos) : 0);
	}

	/** The items a mob just dropped (spawned this tick next to it). */
	public static List<ItemEntity> freshDrops(LivingEntity victim) {
		AABB box = victim.getBoundingBox().inflate(1.5);
		return victim.level().getEntitiesOfClass(ItemEntity.class, box, item -> item.getAge() == 0 && item.isAlive());
	}
}
