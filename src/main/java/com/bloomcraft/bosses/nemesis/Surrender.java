package com.bloomcraft.bosses.nemesis;

import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.core.Rng;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A Nemesis's Terms of Surrender: an easy, common way to die. Die like that while carrying its Challenge and the feud
 * is over (no reward). Picked once per Nemesis and never changes. A Nemesis never asks you to die to its own kind,
 * and no boss ever counts as the one that killed you.
 */
public enum Surrender {
	DROWNING("Sink beneath the water and stay there. Then I will consider our feud ended.", "drown", Set.of()),
	BURNING("Let fire finish what I began.", "burn to death (fire or lava)", Set.of()),
	FALL("Throw yourself from somewhere high enough that the ground may judge you.", "die from a fall", Set.of()),
	CREEPER("Stand before the hissing one. Do not run.", "be blown up by a creeper", Set.of(EntityTypes.CREEPER)),
	ZOMBIE("Let the dead drag you down.", "be killed by a zombie",
		Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER)),
	SKELETON("Take an arrow meant for the grave.", "be killed by a skeleton",
		Set.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED)),
	SPIDER("Feed yourself to the crawling dark.", "be killed by a spider", Set.of(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER)),
	ENDERMAN("Look one in the eyes. Accept what follows.", "be killed by an enderman", Set.of(EntityTypes.ENDERMAN)),
	DROWNED("Return to the water and let its dead claim you.", "be killed by a drowned", Set.of(EntityTypes.DROWNED)),
	CACTUS("Embrace the thorns until there is nothing left to bleed.", "die to a cactus", Set.of()),
	SUFFOCATION("Bury yourself where even the air cannot follow.", "suffocate in blocks", Set.of()),
	COMMON_MOB("Let any common beast strike the final blow.", "be killed by an ordinary monster", Set.of());

	/** Not "common beasts": bosses of their own, and raiders. */
	private static final Set<EntityType<?>> NOT_COMMON = Set.of(EntityTypes.WARDEN, EntityTypes.WITHER,
		EntityTypes.ENDER_DRAGON, EntityTypes.ELDER_GUARDIAN, EntityTypes.RAVAGER, EntityTypes.EVOKER,
		EntityTypes.VINDICATOR, EntityTypes.PILLAGER, EntityTypes.ILLUSIONER, EntityTypes.PIGLIN_BRUTE, EntityTypes.VEX,
		EntityTypes.BREEZE, EntityTypes.CREAKING);

	/** Families: a Husk Nemesis counts zombies as its own kind, and so on. */
	private static final List<Set<EntityType<?>>> FAMILIES = List.of(
		Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.DROWNED,
			EntityTypes.ZOMBIFIED_PIGLIN),
		Set.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED,
			EntityTypes.WITHER_SKELETON),
		Set.of(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER));

	static boolean sameKind(EntityType<?> a, EntityType<?> b) {
		if (a == b) {
			return true;
		}
		for (Set<EntityType<?>> family : FAMILIES) {
			if (family.contains(a) && family.contains(b)) {
				return true;
			}
		}
		return false;
	}

	private final String words;
	private final String plain;
	private final Set<EntityType<?>> mobs;

	Surrender(String words, String plain, Set<EntityType<?>> mobs) {
		this.words = words;
		this.plain = plain;
		this.mobs = mobs;
	}

	/** What the Nemesis says. */
	public String words() {
		return words;
	}

	/** What it means, plainly. */
	public String plain() {
		return plain;
	}

	/** A random condition that doesn't involve the Nemesis's own kind (a Zombie Nemesis won't ask for a zombie). */
	public static Surrender pick(EntityType<?> nemesisType) {
		List<Surrender> options = new ArrayList<>();
		for (Surrender s : values()) {
			boolean ownKind = false;
			for (EntityType<?> mob : s.mobs) {
				ownKind |= sameKind(mob, nemesisType);
			}
			if (!ownKind) {
				options.add(s);
			}
		}
		return Rng.pick(options);
	}

	public static @Nullable Surrender parse(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Whether this death meets the terms. Never when a boss (or a boss's minion or a pet) had a hand in it, and never
	 * at the hands of the Nemesis's own kind.
	 */
	public boolean matches(DamageSource source, EntityType<?> nemesisType) {
		Entity killer = source.getEntity();
		if (killer != null && (BossManager.isBoss(killer) || BossManager.isMinion(killer) || BossManager.isPet(killer)
			|| sameKind(killer.getType(), nemesisType))) {
			return false;
		}
		return switch (this) {
			case DROWNING -> source.is(DamageTypes.DROWN);
			case BURNING -> source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA);
			case FALL -> source.is(DamageTypes.FALL);
			case SUFFOCATION -> source.is(DamageTypes.IN_WALL);
			case CACTUS -> source.is(DamageTypes.CACTUS);
			case COMMON_MOB -> killer instanceof Enemy && !(killer instanceof Player) && !NOT_COMMON.contains(killer.getType());
			default -> killer != null && mobs.contains(killer.getType());
		};
	}
}
