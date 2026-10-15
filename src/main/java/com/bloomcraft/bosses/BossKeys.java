package com.bloomcraft.bosses;

import com.bloomcraft.BloomCraft;
import net.minecraft.resources.Identifier;

/** Names of the saved data the boss systems keep on entities and players, and attribute modifier ids. */
public final class BossKeys {

	private BossKeys() {
	}

	// ---- on boss mobs ----
	public static final String BOSS = "boss";
	public static final String RANK = "boss_rank";
	public static final String CATEGORY = "boss_category";
	public static final String TRAITS = "boss_traits";
	public static final String UNDYING = "boss_undying";
	public static final String LAST_STAND = "boss_last_stand";
	public static final String THREAT = "boss_threat";
	public static final String THREAT_REWARD = "boss_threat_reward";
	public static final String PHASE = "boss_phase";
	/** How a player made this boss (totem, escalation, command, challenge...): no Souls or Totems from it. */
	public static final String CREATED = "boss_created";
	public static final String PLAYER_KILLS = "boss_player_kills";
	/** The rank a promoted boss was found at. */
	public static final String WILD_RANK = "boss_wild_rank";
	public static final String NEMESIS = "nemesis";
	/** On a minion: the UUID of the boss that called it. */
	public static final String MINION = "minion";
	public static final String STATUE = "statue";
	/** A mob that may still become a boss: how it spawned (rolled when it joins the world). */
	public static final String PENDING_ROLL = "boss_roll";
	/** On a dropped reward item: it can't be destroyed. */
	public static final String PROTECTED_DROP = "protected_drop";

	// ---- on players ----
	public static String kills(BossRank rank) {
		return "kills_" + rank.id();
	}

	public static final String RELICS_BOUND = "relics_bound";
	public static final String STARTER_RELICS_BOUND = "starter_relics_bound";
	public static final String GUIDE_GIVEN = "guide_given";
	public static final String SECOND_DAWN = "second_dawn";
	public static final String EMPTY_KILLS = "empty_kills";

	// ---- attribute modifiers ----
	public static final Identifier MOD_HEALTH = BloomCraft.id("boss_health");
	public static final Identifier MOD_ARMOR = BloomCraft.id("boss_armor");
	public static final Identifier MOD_TOUGHNESS = BloomCraft.id("boss_toughness");
	public static final Identifier MOD_KNOCKBACK = BloomCraft.id("boss_knockback");
	public static final Identifier MOD_SPEED = BloomCraft.id("boss_speed");
	public static final Identifier MOD_SIZE = BloomCraft.id("boss_size");
	public static final Identifier MOD_FOLLOW = BloomCraft.id("boss_follow");
	public static final Identifier MOD_DIFFICULTY = BloomCraft.id("boss_difficulty");
	public static final Identifier MOD_HEALTH_FLOOR = BloomCraft.id("boss_health_floor");
	public static final Identifier MOD_THREAT = BloomCraft.id("boss_threat");
	public static final Identifier MOD_TRAIT_SWIFT = BloomCraft.id("trait_swift");
	public static final Identifier MOD_TRAIT_BERSERK = BloomCraft.id("trait_berserk");
	public static final Identifier MOD_NEMESIS_HEALTH = BloomCraft.id("nemesis_health");
	public static final Identifier MOD_NEMESIS_SIZE = BloomCraft.id("nemesis_size");
}
