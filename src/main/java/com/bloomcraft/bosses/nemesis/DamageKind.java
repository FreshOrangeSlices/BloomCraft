package com.bloomcraft.bosses.nemesis;

import com.bloomcraft.bosses.BossManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** The kinds of damage a Nemesis learns to resist. Every pet counts as one kind. */
public enum DamageKind {
	MELEE("melee"),
	RANGED("arrows"),
	MAGIC("magic"),
	FIRE("fire"),
	EXPLOSION("explosions"),
	PET("pets");

	private final String plain;

	DamageKind(String plain) {
		this.plain = plain;
	}

	public String plain() {
		return plain;
	}

	/** What kind of damage this is, or null for kinds a Nemesis doesn't adapt to. */
	public static @Nullable DamageKind of(DamageSource source, @Nullable LivingEntity attacker) {
		if (attacker != null && BossManager.isPet(attacker)) {
			return PET;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return EXPLOSION;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return RANGED;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return FIRE;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)
			|| source.is(DamageTypes.DRAGON_BREATH)) {
			return MAGIC;
		}
		if (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
			|| source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
			return MELEE;
		}
		return null;
	}

	/** Damage it learns from: a player's, a pet's, or with no attacker (burning, potion clouds...). */
	public static boolean learnsFrom(@Nullable LivingEntity attacker) {
		return attacker == null || attacker instanceof ServerPlayer || BossManager.isPet(attacker);
	}

	public static @Nullable DamageKind parse(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
