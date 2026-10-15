package com.bloomcraft.core;

import com.bloomcraft.bosses.BossKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Mobs the mod spawns for effects (curse hauntings, flocks): tagged as minions (no drops, never a boss), never saved
 * with the world, and optionally visible to one player only.
 */
public final class Spawns {

	/** Saved-data flag: the entity is never written to disk (it simply isn't there after a reload). */
	public static final String TRANSIENT = "transient";
	/** Saved-data key: the UUID of the only player who can see the entity. */
	public static final String VISIBLE_TO = "visible_to";

	private Spawns() {
	}

	/**
	 * Spawns an effect mob. {@code tag} is stored as its minion tag (what made it), {@code setup} runs before it's
	 * added to the world. Returns null if it couldn't be added.
	 */
	public static <T extends Entity> @Nullable T effectMob(ServerLevel level, EntityType<T> type, Vec3 at, float yaw,
														   String tag, @Nullable Consumer<T> setup) {
		T entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (entity == null) {
			return null;
		}
		entity.snapTo(at.x, at.y, at.z, yaw, 0);
		Data.of(entity).putString(BossKeys.MINION, tag);
		Data.flag(entity, TRANSIENT, true);
		if (entity instanceof Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
			mob.setCanPickUpLoot(false);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				mob.setDropChance(slot, 0f);
			}
		}
		if (setup != null) {
			setup.accept(entity);
		}
		if (!level.addFreshEntity(entity)) {
			return null;
		}
		return entity;
	}

	/** Only this player will see the entity (set before it's added to the world). */
	public static void onlyVisibleTo(Entity entity, ServerPlayer player) {
		Data.of(entity).putString(VISIBLE_TO, player.getUUID().toString());
	}

	public static boolean isTransient(Entity entity) {
		return Data.flag(entity, TRANSIENT);
	}

	/** False if the entity is hidden from this player (see {@link #onlyVisibleTo}). */
	public static boolean visibleTo(Entity entity, ServerPlayer player) {
		String only = Data.str(entity, VISIBLE_TO);
		return only == null || only.equals(player.getUUID().toString());
	}

	public static boolean tagged(Entity entity, String tag) {
		return tag.equals(Data.str(entity, BossKeys.MINION));
	}

	public static @Nullable Entity find(ServerLevel level, @Nullable UUID id) {
		return id == null ? null : level.getEntity(id);
	}
}
