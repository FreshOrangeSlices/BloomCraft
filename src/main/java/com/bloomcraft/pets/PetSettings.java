package com.bloomcraft.pets;

import com.bloomcraft.core.Cfg;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything pets.yml says. */
public final class PetSettings {

	public final boolean enabled;
	public final int maxPets;
	public final Set<Species> species = EnumSet.noneOf(Species.class);
	private final Map<Species, Integer> feeds = new EnumMap<>(Species.class);
	public final int rideUnlockLevel;
	/** Pets that can be ridden from level 1. */
	public final Set<Species> rideFromStart = EnumSet.noneOf(Species.class);
	public final double hoverHeight;
	public final double minRiderScale;
	public final int restSeconds;
	public final double restHealth;
	public final boolean protectFromPlayers;
	public final boolean allayBottlesXp;
	public final int xpFeed;
	public final int xpOwnerKill;
	public final int xpPetKill;
	public final int rideBlocksPerXp;
	public final int xpPerMinute;
	public final int walkBlocksPerXp;
	public final int beeRadius;
	public final int beeCrops;
	public final double beeSeconds;
	public final int stationLevel;
	public final int maxStations;
	public final double guardRadius;
	public final double healPercent;
	public final int roamRadius;
	public final int xpCombatHit;
	public final int xpCombatCrit;
	public final long combatXpCooldown;
	public final double guardOwnerRange;
	public final boolean petPvp;
	// Taming skills of combat pets
	public final boolean skills;
	private final Map<String, Integer> unlocks = new HashMap<>();
	public final double goreMaxChance;
	public final double goreMaxLevel;
	public final double goreMultiplier;
	public final double fastFoodChance;
	public final double thickFurDivisor;
	public final double shockProofDivisor;
	public final double clawsBonus;
	public final int guardianSeconds;
	public final int guardianLevel;
	public final double doubleStrikePer5;
	public final double doubleStrikeMax;
	public final double phaseArmor;
	public final double phaseArmorHigh;
	public final int phaseHighLevel;
	public final int crushSeconds;
	public final int crushHighLevel;
	public final int momentumEvery;
	public final double momentumBonus;
	public final double ramLaunch;
	public final int blessingSeconds;
	public final double soothChanceLow;
	public final double soothChanceHigh;
	public final double soothHealBase;
	public final double soothHealPer10;
	public final double soothHealMax;
	public final double soothRadius;
	public final double pounceBonus;
	public final double howlHealth;
	public final int howlSeconds;
	public final int howlCooldown;
	// happiness
	public final boolean happiness;
	public final double happyStart;
	public final double sadBelow;
	public final double happyFeed;
	public final long happyFeedCooldown;
	public final double happyFetch;
	public final long happyFetchCooldown;
	public final double happyPetting;
	public final long happyPettingCooldown;
	public final double minutesPerPointOut;
	public final double minutesPerPointPost;
	public final boolean postDropsOffline;
	public final long tiredAfter;
	public final double tiredMultiplier;
	public final double restPerMinute;
	public final double minutesToRest;
	public final long lovedMillis;
	public final double lovedMultiplier;

	public PetSettings(Cfg c) {
		enabled = c.getBool("enabled", true);
		maxPets = clamp(c.getInt("max-pets", 27), 1, 45);
		Cfg on = c.sectionOrEmpty("pets");
		for (Species s : Species.values()) {
			if (on.getBool(s.id(), true)) {
				species.add(s);
			}
		}
		int def = Math.max(1, c.getInt("taming.feeds.default", 5));
		for (Species s : Species.values()) {
			int fallback = switch (s) {
				case IRON_GOLEM -> 8;
				case SNIFFER -> 6;
				case VEX, BAT -> 3;
				default -> def;
			};
			feeds.put(s, Math.max(1, c.getInt("taming.feeds." + s.id(), fallback)));
		}
		rideUnlockLevel = clamp(c.getInt("riding.unlock-level", 3), 1, Pet.MAX_LEVEL);
		List<String> easy = c.isList("riding.ride-from-start") ? c.getStringList("riding.ride-from-start")
			: List.of("cat", "wolf", "rabbit", "horse");
		for (String id : easy) {
			Species s = Species.parse(id);
			if (s != null) {
				rideFromStart.add(s);
			}
		}
		hoverHeight = clamp(c.getDouble("riding.hover-height", 3), 1, 8);
		minRiderScale = clamp(c.getDouble("riding.smallest-rider-scale", 0.3), 0.1, 1);
		restSeconds = Math.max(0, c.getInt("fainting.rest-seconds", 60));
		restHealth = clamp(c.getDouble("fainting.comes-back-with-health", 0.5), 0.1, 1);
		protectFromPlayers = c.getBool("protect-pets-from-other-players", true);
		allayBottlesXp = c.getBool("allay-bottles-xp", true);
		healPercent = Math.max(0, c.getDouble("feeding.heal-percent", 25));
		xpFeed = Math.max(0, c.getInt("bond-xp.feeding", 10));
		xpOwnerKill = Math.max(0, c.getInt("bond-xp.you-kill-a-mob", 3));
		xpPetKill = Math.max(0, c.getInt("bond-xp.pet-kills-a-mob", 6));
		rideBlocksPerXp = Math.max(1, c.getInt("bond-xp.blocks-ridden-per-xp", 10));
		walkBlocksPerXp = Math.max(1, c.getInt("bond-xp.blocks-walked-per-xp", 20));
		xpPerMinute = Math.max(0, c.getInt("bond-xp.per-minute-out", 2));
		xpCombatHit = Math.max(0, c.getInt("bond-xp.combat-hit", 1));
		xpCombatCrit = Math.max(0, c.getInt("bond-xp.combat-critical-hit", 2));
		combatXpCooldown = Math.max(0, (long) (c.getDouble("bond-xp.combat-hit-cooldown-seconds", 1.5) * 1000));
		guardOwnerRange = Math.max(0, c.getDouble("bond-xp.guard-owner-range", 30));
		beeRadius = clamp(c.getInt("bee.radius", 5), 1, 16);
		beeCrops = clamp(c.getInt("bee.crops-per-pulse", 3), 1, 32);
		beeSeconds = Math.max(0.5, c.getDouble("bee.seconds-between", 3));
		stationLevel = clamp(c.getInt("stationing.unlock-level", 8), 1, Pet.MAX_LEVEL);
		maxStations = Math.max(0, c.getInt("stationing.max-per-player", 3));
		guardRadius = clamp(c.getDouble("stationing.guard-radius", 16), 4, 48);
		roamRadius = clamp(c.getInt("stationing.roam-radius", 6), 0, 16);
		petPvp = c.getBool("pet-pvp.allow-toggle", true);

		skills = c.getBool("taming-skills.enabled", true);
		for (PetSkills.Slot slot : PetSkills.Slot.values()) {
			unlocks.put(slot.key, Math.max(0, c.getInt("taming-skills.unlock." + slot.key, slot.defaultLevel)));
		}
		goreMaxChance = clamp(c.getDouble("taming-skills.gore.max-chance", 100), 0, 100);
		goreMaxLevel = Math.max(1, c.getDouble("taming-skills.gore.max-chance-level", 100));
		goreMultiplier = Math.max(1, c.getDouble("taming-skills.gore.damage-multiplier", 2.0));
		fastFoodChance = clamp(c.getDouble("taming-skills.fast-food-chance", 50), 0, 100);
		thickFurDivisor = Math.max(1, c.getDouble("taming-skills.thick-fur-divisor", 2.0));
		shockProofDivisor = Math.max(1, c.getDouble("taming-skills.shock-proof-divisor", 6.0));
		clawsBonus = Math.max(0, c.getDouble("taming-skills.sharpened-claws-bonus", 2.0));
		guardianSeconds = Math.max(1, c.getInt("taming-skills.iron-golem.guardian-resistance-seconds", 20));
		guardianLevel = clamp(c.getInt("taming-skills.iron-golem.guardian-resistance-level", 2), 1, 5);
		doubleStrikePer5 = clamp(c.getDouble("taming-skills.vex.double-strike-chance-per-5-levels", 2), 0, 100);
		doubleStrikeMax = clamp(c.getDouble("taming-skills.vex.double-strike-max-chance", 10), 0, 100);
		phaseArmor = clamp(c.getDouble("taming-skills.vex.phase-strike-armor-ignored", 25), 0, 100);
		phaseArmorHigh = clamp(c.getDouble("taming-skills.vex.phase-strike-armor-ignored-high", 50), 0, 100);
		phaseHighLevel = Math.max(0, c.getInt("taming-skills.vex.phase-strike-high-level", 50));
		crushSeconds = Math.max(1, c.getInt("taming-skills.polar-bear.crushing-presence-seconds", 5));
		crushHighLevel = Math.max(0, c.getInt("taming-skills.polar-bear.crushing-presence-high-level", 50));
		momentumEvery = Math.max(1, c.getInt("taming-skills.goat.momentum-every-hits", 3));
		momentumBonus = Math.max(0, c.getDouble("taming-skills.goat.momentum-bonus", 30));
		ramLaunch = clamp(c.getDouble("taming-skills.goat.ram-launch", 1.1), 0, 3);
		blessingSeconds = Math.max(1, c.getInt("taming-skills.panda.bamboo-blessing-seconds", 8));
		soothChanceLow = clamp(c.getDouble("taming-skills.panda.soothing-chance-at-unlock", 15), 0, 100);
		soothChanceHigh = clamp(c.getDouble("taming-skills.panda.soothing-chance-at-max", 60), 0, 100);
		soothHealBase = Math.max(0, c.getDouble("taming-skills.panda.soothing-heal-at-unlock", 3));
		soothHealPer10 = Math.max(0, c.getDouble("taming-skills.panda.soothing-heal-per-10-levels", 1));
		soothHealMax = Math.max(0, c.getDouble("taming-skills.panda.soothing-heal-max", 9));
		soothRadius = clamp(c.getDouble("taming-skills.panda.soothing-radius", 6), 1, 16);
		pounceBonus = Math.max(0, c.getDouble("taming-skills.wolf.pounce-bonus", 50));
		howlHealth = clamp(c.getDouble("taming-skills.wolf.howl-below-health", 40), 1, 100);
		howlSeconds = Math.max(1, c.getInt("taming-skills.wolf.howl-seconds", 8));
		howlCooldown = Math.max(1, c.getInt("taming-skills.wolf.howl-cooldown-seconds", 60));

		happiness = c.getBool("happiness.enabled", true);
		happyStart = clamp(c.getDouble("happiness.start", 80), 0, 100);
		sadBelow = clamp(c.getDouble("happiness.sad-below", 30), 0, 100);
		happyFeed = Math.max(0, c.getDouble("happiness.feeding", 15));
		happyFeedCooldown = seconds(c, "happiness.feeding-cooldown-seconds", 60);
		happyFetch = Math.max(0, c.getDouble("happiness.fetch", 8));
		happyFetchCooldown = seconds(c, "happiness.fetch-cooldown-seconds", 15);
		happyPetting = Math.max(0, c.getDouble("happiness.petting", 5));
		happyPettingCooldown = seconds(c, "happiness.petting-cooldown-seconds", 30);
		minutesPerPointOut = Math.max(0.1, c.getDouble("happiness.minutes-per-point-out", 3));
		minutesPerPointPost = Math.max(0.1, c.getDouble("happiness.minutes-per-point-at-post", 2));
		postDropsOffline = c.getBool("happiness.post-drops-while-owner-offline", false);
		tiredAfter = Math.max(1, (long) (c.getDouble("happiness.tired-after-minutes", 90) * 60_000));
		tiredMultiplier = Math.max(1, c.getDouble("happiness.tired-drop-multiplier", 3));
		restPerMinute = Math.max(0, c.getDouble("happiness.rest-per-minute", 10));
		minutesToRest = Math.max(0.1, c.getDouble("happiness.minutes-to-fully-rest", 5));
		lovedMillis = Math.max(0, (long) (c.getDouble("happiness.loved-minutes", 30) * 60_000));
		lovedMultiplier = clamp(c.getDouble("happiness.loved-drop-multiplier", 0.25), 0, 1);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	private static long seconds(Cfg c, String path, double def) {
		return Math.max(0, (long) (c.getDouble(path, def) * 1000));
	}

	/** The Taming level this skill slot unlocks at. */
	public int unlock(PetSkills.Slot slot) {
		return unlocks.getOrDefault(slot.key, slot.defaultLevel);
	}

	/** The level this species can be ridden from. */
	public int rideLevel(Species species) {
		return rideFromStart.contains(species) ? 1 : rideUnlockLevel;
	}

	public int feedsToTame(Species species) {
		return feeds.getOrDefault(species, 5);
	}
}
