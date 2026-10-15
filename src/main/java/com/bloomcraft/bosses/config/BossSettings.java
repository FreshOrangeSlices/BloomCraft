package com.bloomcraft.bosses.config;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.reward.GearKind;
import com.bloomcraft.bosses.reward.GearQuality;
import com.bloomcraft.bosses.reward.GearTier;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A parsed, read-only snapshot of bosses.yml. A new one is made on /bosses reload. */
public final class BossSettings {

	// ---- bosses ----
	public final boolean enabled;
	public final double spawnChance;
	public final Set<EntitySpawnReason> spawnReasons;
	public final boolean worldWhitelist;
	public final Set<String> worlds = new HashSet<>();
	public final Set<EntityType<?>> excludedMobs;
	public final int maxActive;
	public final int maxHeightDifference;
	public final boolean breakFreeWhenStuck;
	public final double minDistanceBetween;
	public final boolean alwaysShowName;
	public final boolean environmentalParticles;
	public final int combatTimeoutTicks;
	public final boolean requirePlayerForRewards;
	public final int despawnAfterKills;
	public final double glowWithin;
	public final boolean glowRankColors;
	public final double areaDamageCap;

	// ---- boss bar / presentation ----
	public final double bossbarViewDistance;
	public final boolean showHealthNumbers;
	public final double announceRadius;
	public final boolean bossChat;

	// ---- ranks, categories, profiles ----
	private final Map<BossRank, RankSettings> ranks = new EnumMap<>(BossRank.class);
	public final List<MobCategory> categories = new ArrayList<>();
	private final Map<EntityType<?>, MobCategory> categoryByType = new HashMap<>();
	public final List<MobProfile> profiles = new ArrayList<>();
	private final Map<EntityType<?>, MobProfile> profileByType = new HashMap<>();

	// ---- traits ----
	public final Cfg traits;

	// ---- boss gear ----
	public final Map<GearKind, Integer> gearWeights = new EnumMap<>(GearKind.class);
	public final Map<EntityType<?>, Map<GearKind, Integer>> gearPreferences = new HashMap<>();
	public final Map<BossRank, Map<GearKind.Category, Integer>> gearCategories = new EnumMap<>(BossRank.class);
	public final double protectionWeight;
	public final boolean allowCurseEnchantments;
	public final @Nullable BossRank overlappingEnchantsFrom;
	public final Set<String> excludedEnchantments = new HashSet<>();
	public final double toolOffensiveChance;

	// ---- empowerment ----
	public final int empowermentMaxPerItem;
	public final boolean fixedRuneValues;
	public final List<EmpowermentStat> empowermentStats = new ArrayList<>();
	public final double maxLootMultiplier;
	public final double maxFortuneMultiplier;

	// ---- relics ----
	public final Cfg relics;
	public final int relicBaseSlots;
	public final double corruptionChance;
	public final boolean revealCorruption;
	public final boolean confirmBinding;
	public final boolean allowDuplicateRelics;
	public final boolean catalystEnabled;
	public final int catalystMaxSlots;
	public final double catalystCorruptionChance;

	// ---- Last Stand ----
	public final Set<BossRank> lastStandRanks;
	public final double lastStandHealthPercent;
	public final double lastStandPowerBonus;
	public final boolean lastStandDormantTrait;

	// ---- Difficulty + Threat Scaling ----
	public final double difficultyHealth;
	public final double difficultyDamage;
	public final boolean threatEnabled;
	public final double threatMaxHealth;
	public final double threatMaxDamage;
	public final double threatRewardBonus;
	public final double threatFullPieces;
	public final double threatRadius;
	public final boolean overflowArmor;
	public final double overflowArmorFrom;
	public final double overflowArmorPerPoint;

	// ---- Reward floor ----
	public final @Nullable BossRank rewardFloorFrom;
	public final int pityAfter;

	// ---- Promotion + Ascendant ----
	public final boolean promotionEnabled;
	public final Map<BossRank, int[]> promotionSteps = new EnumMap<>(BossRank.class);
	public final double grayPromotionChance;
	public final boolean ascendantPhases;
	public final List<Double> ascendantPhaseThresholds = new ArrayList<>();
	public final double ascendantShockwaveRadius;
	public final double ascendantShockwaveDamage;

	// ---- Waystones ----
	public final boolean waystonesEnabled;
	public final int waystoneAscendantDrops;
	public final Map<BossRank, Double> waystoneDropChance;
	public final double waystoneWarmupSeconds;
	public final double waystoneCooldownSeconds;
	public final boolean waystoneCrossDimension;
	public final boolean waystoneBlockInCombat;

	// ---- Boss armor ----
	public final boolean armorSets;
	public final Map<BossRank, List<String[]>> armorPalettes = new EnumMap<>(BossRank.class);
	public final List<String> trimPatterns;

	// ---- Anti-trap ----
	public final boolean blockVehicles;
	public final boolean unstuckEnabled;
	public final int unstuckTicks;

	// ---- Hunter's Compass ----
	public final boolean compassEnabled;
	public final boolean compassRecipe;
	public final List<CompassTier> compassTiers = new ArrayList<>();

	public record CompassTier(double range, double revealDistance) {
	}

	// ---- Boss Totem ----
	public final boolean totemEnabled;
	public final Map<BossRank, Double> totemDropChance;
	public final double totemHigherChance;

	// ---- Escalation ----
	public final boolean escalationEnabled;
	public final int escalationKills;
	public final double escalationWindowDays;
	public final int escalationBosses;
	public final double escalationDelaySeconds;
	public final Map<BossRank, Double> escalationRanks;

	// ---- Souls ----
	public final boolean soulsEnabled;
	public final Map<BossRank, Double> soulChance;
	public final Map<BossRank, int[]> soulCount = new EnumMap<>(BossRank.class);

	// ---- Nemesis ----
	public final boolean nemesisEnabled;
	public final double nemesisKillChance;
	public final double nemesisEscapeChance;
	public final BossRank nemesisEscapeMinRank;
	public final double nemesisEscapeDamagePercent;
	public final double nemesisEscapeDistance;
	public final int nemesisMaxPerPlayer;
	public final int nemesisMaxLevel;
	public final int nemesisLevelsOnKill;
	public final int nemesisLevelsOnEscape;
	public final int nemesisLevelsPerRank;
	public final int nemesisLevelsPerTrait;
	public final int nemesisMaxTraits;
	public final double nemesisReturnDays;
	public final double nemesisHealthPerLevel;
	public final double nemesisDamagePerLevel;
	public final double nemesisMaxDamageMultiplier;
	public final double nemesisPowerPerLevel;
	public final double nemesisSizePerLevel;
	public final double nemesisMaxExtraSize;
	public final double nemesisRevengeXpMultiplier;
	public final boolean nemesisGearEvolution;
	public final double statueScale;
	public final double nemesisProwlMinutes;
	public final int nemesisKillLevelsPerOuting;
	public final double nemesisAggroSwitchPercent;
	public final boolean nemesisAdaptation;
	public final double nemesisResistedPercent;
	public final int nemesisMaxImmunities;
	public final double nemesisPetDamageTakenPercent;
	public final double nemesisPetDamageBonus;

	// ---- guide ----
	public final boolean guideOnFirstJoin;

	public final Messages messages;

	public BossSettings(Cfg c) {
		enabled = c.getBool("bosses.enabled", true);
		spawnChance = c.getDouble("bosses.spawn-chance", 5);
		spawnReasons = parseReasons(c.getStringList("bosses.spawn-reasons"));
		if (spawnReasons.isEmpty()) {
			spawnReasons.add(EntitySpawnReason.NATURAL);
		}
		worldWhitelist = "whitelist".equalsIgnoreCase(c.getString("bosses.worlds.mode", "blacklist"));
		for (String w : c.getStringList("bosses.worlds.list")) {
			worlds.add(dimensionId(w));
		}
		excludedMobs = parseMobs(c.getStringList("bosses.excluded-mobs"));
		maxActive = Math.max(0, c.getInt("bosses.max-active", 15));
		maxHeightDifference = Math.max(0, c.getInt("bosses.max-height-difference", 20));
		breakFreeWhenStuck = c.getBool("bosses.break-free-when-stuck", true);
		minDistanceBetween = Math.max(0, c.getDouble("bosses.min-distance-between", 32));
		alwaysShowName = c.getBool("bosses.always-show-name", false);
		environmentalParticles = c.getBool("bosses.environmental-particles", true);
		combatTimeoutTicks = Math.max(20, (int) Math.round(c.getDouble("bosses.combat-timeout", 15) * 20));
		requirePlayerForRewards = c.getBool("bosses.require-player-for-rewards", true);
		despawnAfterKills = Math.max(0, c.getInt("bosses.despawn-after-player-kills", 2));
		glowWithin = Math.max(0, c.getDouble("bosses.glow-within", 30));
		glowRankColors = c.getBool("bosses.glow-rank-colors", true);
		areaDamageCap = Math.max(0, c.getDouble("traits.area-damage-cap", 8));

		bossbarViewDistance = c.getDouble("bossbar.view-distance", 48);
		showHealthNumbers = c.getBool("bossbar.show-health-numbers", true);
		announceRadius = c.getDouble("presentation.announce-radius", 48);
		bossChat = c.getBool("presentation.boss-chat", false);

		for (BossRank rank : BossRank.values()) {
			ranks.put(rank, parseRank(rank, c.sectionOrEmpty("ranks." + rank.name())));
		}

		Cfg cats = c.sectionOrEmpty("mob-categories");
		for (String id : cats.keys()) {
			Cfg s = cats.section(id);
			if (s == null) {
				continue;
			}
			Map<BossRank, Double> weights = rankWeights(s.sectionOrEmpty("weights"));
			if (weights.isEmpty()) {
				weights.put(BossRank.GRAY, 1.0);
			}
			Map<BossRank, Double> netherWeights = s.section("nether-weights") == null ? null
				: rankWeights(s.sectionOrEmpty("nether-weights"));
			if (netherWeights != null && netherWeights.isEmpty()) {
				netherWeights = null;
			}
			Set<EntitySpawnReason> reasons = s.isList("spawn-reasons") ? parseReasons(s.getStringList("spawn-reasons")) : null;
			MobCategory category = new MobCategory(id, s.getString("display-name", Text.pretty(id)),
				s.getString("description", ""), s.getBool("enabled", true),
				s.getDouble("spawn-chance-multiplier", 1.0), weights, netherWeights,
				parseMobs(s.getStringList("mobs")), reasons);
			categories.add(category);
			for (EntityType<?> type : category.mobs()) {
				categoryByType.put(type, category);
			}
		}

		Cfg profs = c.sectionOrEmpty("mob-profiles");
		for (String id : profs.keys()) {
			Cfg s = profs.section(id);
			if (s == null) {
				continue;
			}
			MobProfile profile = new MobProfile(id, parseMobs(s.getStringList("mobs")), s.getDouble("health", 1),
				s.getDouble("damage", 1), s.getDouble("defense", 1), s.getDouble("speed", 1), s.getDouble("size", 1));
			profiles.add(profile);
			for (EntityType<?> type : profile.mobs()) {
				profileByType.put(type, profile);
			}
		}

		traits = c.sectionOrEmpty("traits");

		Cfg gw = c.sectionOrEmpty("boss-gear.item-weights");
		for (String key : gw.keys()) {
			GearKind kind = GearKind.parse(key);
			if (kind != null) {
				gearWeights.put(kind, Math.max(0, gw.getInt(key, 0)));
			}
		}
		if (gearWeights.isEmpty()) {
			for (GearKind kind : GearKind.values()) {
				gearWeights.put(kind, 10);
			}
		}
		Cfg prefs = c.sectionOrEmpty("boss-gear.mob-preferences");
		for (String mob : prefs.keys()) {
			EntityType<?> type = parseMob(mob);
			Cfg ps = prefs.section(mob);
			if (type == null || ps == null) {
				continue;
			}
			Map<GearKind, Integer> extra = new EnumMap<>(GearKind.class);
			for (String key : ps.keys()) {
				GearKind kind = GearKind.parse(key);
				if (kind != null) {
					extra.put(kind, Math.max(0, ps.getInt(key, 0)));
				}
			}
			gearPreferences.put(type, extra);
		}
		int[][] catDefaults = {{60, 30, 10}, {60, 30, 10}, {34, 33, 33}, {30, 0, 70}, {30, 0, 70}, {34, 33, 33}};
		for (BossRank rank : BossRank.values()) {
			Cfg cw = c.sectionOrEmpty("boss-gear.category-weights." + rank.name());
			int[] def = catDefaults[rank.ordinal()];
			Map<GearKind.Category, Integer> map = new EnumMap<>(GearKind.Category.class);
			map.put(GearKind.Category.ARMOR, Math.max(0, cw.getInt("ARMOR", def[0])));
			map.put(GearKind.Category.TOOL, Math.max(0, cw.getInt("TOOL", def[1])));
			map.put(GearKind.Category.WEAPON, Math.max(0, cw.getInt("WEAPON", def[2])));
			gearCategories.put(rank, map);
		}
		protectionWeight = Math.max(1, c.getDouble("boss-gear.protection-weight", 3));
		allowCurseEnchantments = c.getBool("boss-gear.allow-curse-enchantments", false);
		String overlapFrom = c.getString("boss-gear.overlapping-enchantments-from", "GOLD");
		overlappingEnchantsFrom = overlapFrom.equalsIgnoreCase("NONE") ? null : BossRank.parse(overlapFrom);
		for (String e : c.getStringList("boss-gear.excluded-enchantments")) {
			excludedEnchantments.add(stripNamespace(e));
		}
		toolOffensiveChance = c.getDouble("boss-gear.tool-offensive-enchant-chance", 35);

		empowermentMaxPerItem = Math.max(1, c.getInt("empowerment.max-per-item", 2));
		fixedRuneValues = c.getBool("empowerment.fixed-values", true);
		maxLootMultiplier = Math.max(1, c.getDouble("empowerment.max-loot-multiplier", 3));
		maxFortuneMultiplier = Math.max(1, c.getDouble("empowerment.max-fortune-multiplier", 3));
		Cfg stats = c.sectionOrEmpty("empowerment.stats");
		for (String id : stats.keys()) {
			Cfg s = stats.section(id);
			if (s == null || !s.getBool("enabled", true)) {
				continue;
			}
			String effect = s.getString("effect");
			if (effect != null) {
				effect = effect.trim().toLowerCase(Locale.ROOT);
				if (!effect.equals(EmpowermentStat.LOOT) && !effect.equals(EmpowermentStat.FORTUNE)) {
					BloomCraft.LOG.warn("empowerment.stats.{}: unknown effect '{}'", id, effect);
					continue;
				}
			}
			Holder<Attribute> attribute = effect != null ? null : parseAttribute(s.getString("attribute", ""));
			if (effect == null && attribute == null) {
				BloomCraft.LOG.warn("empowerment.stats.{}: unknown attribute '{}'", id, s.getString("attribute", ""));
				continue;
			}
			Holder<Attribute> extra = s.getString("extra-attribute") != null ? parseAttribute(s.getString("extra-attribute", "")) : null;
			AttributeModifier.Operation op = switch (s.getString("operation", "ADD_NUMBER").toUpperCase(Locale.ROOT)) {
				case "ADD_SCALAR", "ADD_MULTIPLIED_BASE" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
				case "MULTIPLY_SCALAR_1", "ADD_MULTIPLIED_TOTAL" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
				default -> AttributeModifier.Operation.ADD_VALUE;
			};
			Map<BossRank, double[]> ranges = new EnumMap<>(BossRank.class);
			Cfg vs = s.sectionOrEmpty("values");
			for (BossRank rank : BossRank.values()) {
				if (vs.contains(rank.name())) {
					double v = vs.getDouble(rank.name(), 0);
					ranges.put(rank, new double[]{v, v});
				}
			}
			if (ranges.isEmpty()) {
				continue;
			}
			List<String> applies = s.getStringList("applies-to");
			if (applies.isEmpty()) {
				applies = List.of("ANY");
			}
			empowermentStats.add(new EmpowermentStat(id, attribute, extra, effect, op, s.getString("display", id),
				s.getBool("percent", effect != null), s.getDouble("weight", 10), List.copyOf(applies), ranges));
		}

		relics = c.sectionOrEmpty("relics");
		relicBaseSlots = Math.max(1, c.getInt("relics.base-slots", 1));
		corruptionChance = c.getDouble("relics.corruption-chance", 25);
		revealCorruption = c.getBool("relics.reveal-corruption", false);
		confirmBinding = c.getBool("relics.confirm-binding", true);
		allowDuplicateRelics = c.getBool("relics.allow-duplicates-on-item", false);
		catalystEnabled = c.getBool("relics.catalyst.enabled", true);
		catalystMaxSlots = Math.max(relicBaseSlots, c.getInt("relics.catalyst.max-slots", 2));
		catalystCorruptionChance = c.getDouble("relics.catalyst.corruption-chance", 50);

		lastStandRanks = ranks(c.getStringList("last-stand.ranks"), EnumSet.of(BossRank.PURPLE, BossRank.GOLD));
		lastStandRanks.add(BossRank.ASCENDANT);
		lastStandHealthPercent = c.getDouble("last-stand.health-percent", 25);
		lastStandPowerBonus = c.getDouble("last-stand.trait-power-bonus", 50);
		lastStandDormantTrait = c.getBool("last-stand.awaken-dormant-trait", true);

		difficultyHealth = Math.max(0.1, c.getDouble("difficulty.health-multiplier", 1.3));
		difficultyDamage = Math.max(0.1, c.getDouble("difficulty.damage-multiplier", 1.2));
		threatEnabled = c.getBool("difficulty.threat-scaling.enabled", true);
		threatMaxHealth = Math.max(0, c.getDouble("difficulty.threat-scaling.max-health-bonus", 60));
		threatMaxDamage = Math.max(0, c.getDouble("difficulty.threat-scaling.max-damage-bonus", 30));
		threatRewardBonus = Math.max(0, c.getDouble("difficulty.threat-scaling.max-reward-bonus", 25));
		threatFullPieces = Math.max(1, c.getDouble("difficulty.threat-scaling.full-gear-pieces", 6));
		threatRadius = Math.max(4, c.getDouble("difficulty.threat-scaling.radius", 32));
		overflowArmor = c.getBool("difficulty.overflow-armor.enabled", true);
		overflowArmorFrom = Math.max(0, c.getDouble("difficulty.overflow-armor.from", 20));
		overflowArmorPerPoint = Math.max(0, c.getDouble("difficulty.overflow-armor.boss-damage-reduction-per-point", 5));

		String floor = c.getString("reward-floor.guaranteed-from", "RED");
		rewardFloorFrom = floor.equalsIgnoreCase("NONE") ? null : BossRank.parse(floor);
		pityAfter = Math.max(0, c.getInt("reward-floor.pity-after-empty-kills", 3));

		promotionEnabled = c.getBool("promotion.enabled", true);
		int[][] steps = {{1, 1}, {1, 1}, {1, 2}, {1, 3}, {2, 4}, {3, 5}};
		for (BossRank rank : BossRank.values()) {
			promotionSteps.put(rank, range(c.getIntList("promotion.ranks-gained." + rank.name()), steps[rank.ordinal()], 0, 10));
		}
		grayPromotionChance = c.getDouble("promotion.gray-soul-chance", 25);
		ascendantPhases = c.getBool("ascendant.phases.enabled", true);
		List<Double> thresholds = c.getDoubleList("ascendant.phases.at-health-percent");
		ascendantPhaseThresholds.addAll(thresholds.isEmpty() ? List.of(66.0, 33.0) : thresholds);
		ascendantPhaseThresholds.sort(Comparator.reverseOrder());
		ascendantShockwaveRadius = c.getDouble("ascendant.phases.shockwave-radius", 7);
		ascendantShockwaveDamage = c.getDouble("ascendant.phases.shockwave-damage", 6);

		waystonesEnabled = c.getBool("waystones.enabled", true);
		waystoneAscendantDrops = Math.max(0, c.getInt("waystones.ascendant-drops", 2));
		waystoneDropChance = rankMap(c.section("waystones.drop-chance"), new double[]{0, 0, 0, 0, 3, 0});
		waystoneWarmupSeconds = Math.max(0, c.getDouble("waystones.warmup-seconds", 3));
		waystoneCooldownSeconds = Math.max(0, c.getDouble("waystones.cooldown-seconds", 5));
		waystoneCrossDimension = c.getBool("waystones.cross-dimension", true);
		waystoneBlockInCombat = c.getBool("waystones.blocked-during-boss-fights", true);

		armorSets = c.getBool("boss-armor.enabled", true);
		String[][][] palettes = {
			{{"CHAINMAIL", "iron"}, {"CHAINMAIL", "quartz"}, {"CHAINMAIL", "copper"}},
			{{"COPPER", "emerald"}, {"IRON", "emerald"}, {"IRON", "copper"}},
			{{"IRON", "redstone"}, {"DIAMOND", "redstone"}, {"IRON", "netherite"}},
			{{"DIAMOND", "amethyst"}, {"NETHERITE", "amethyst"}, {"DIAMOND", "lapis"}},
			{{"GOLDEN", "redstone"}, {"NETHERITE", "gold"}, {"DIAMOND", "gold"}},
			{{"NETHERITE", "quartz"}, {"DIAMOND", "quartz"}, {"NETHERITE", "diamond"}}};
		for (BossRank rank : BossRank.values()) {
			List<String[]> list = new ArrayList<>();
			for (String entry : c.getStringList("boss-armor.palettes." + rank.name())) {
				String[] parts = entry.trim().split("\\s+");
				if (parts.length >= 2) {
					list.add(new String[]{parts[0], parts[1]});
				}
			}
			if (list.isEmpty()) {
				list.addAll(List.of(palettes[rank.ordinal()]));
			}
			armorPalettes.put(rank, list);
		}
		List<String> patterns = c.getStringList("boss-armor.trim-patterns");
		trimPatterns = patterns.isEmpty() ? List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide",
			"snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt") : patterns;

		blockVehicles = c.getBool("anti-trap.block-vehicles-and-leads", true);
		unstuckEnabled = c.getBool("anti-trap.unstuck.enabled", true);
		unstuckTicks = (int) Math.round(Math.max(1, c.getDouble("anti-trap.unstuck.seconds", 6)) * 20);

		compassEnabled = c.getBool("hunters-compass.enabled", true);
		compassRecipe = c.getBool("hunters-compass.recipe", true);
		for (Cfg tier : c.getSectionList("hunters-compass.tiers")) {
			compassTiers.add(new CompassTier(tier.getDouble("range", 150), tier.getDouble("reveal-rank-within", 40)));
		}
		if (compassTiers.isEmpty()) {
			compassTiers.add(new CompassTier(150, 40));
			compassTiers.add(new CompassTier(300, 100));
			compassTiers.add(new CompassTier(600, 600));
		}

		totemEnabled = c.getBool("boss-totem.enabled", true);
		totemDropChance = rankMap(c.section("boss-totem.drop-chance"), new double[]{15, 15, 17, 20, 25, 30});
		totemHigherChance = Math.max(0, Math.min(100, c.getDouble("boss-totem.higher-rank-chance", 20)));

		escalationEnabled = c.getBool("escalation.enabled", true);
		escalationKills = Math.max(2, c.getInt("escalation.kills", 5));
		escalationWindowDays = Math.max(0.1, c.getDouble("escalation.within-days", 1));
		escalationBosses = Math.max(1, c.getInt("escalation.bosses", 2));
		escalationDelaySeconds = c.getDouble("escalation.delay-seconds", 10);
		escalationRanks = rankMap(c.section("escalation.rank-weights"), new double[]{0, 0, 0, 70, 30, 0});

		soulsEnabled = c.getBool("souls.enabled", true);
		soulChance = rankMap(c.section("souls.chance"), new double[]{30, 30, 35, 40, 60, 100});
		int[][] counts = {{1, 1}, {1, 1}, {1, 2}, {2, 2}, {2, 3}, {3, 3}};
		for (BossRank rank : BossRank.values()) {
			soulCount.put(rank, range(c.getIntList("souls.count." + rank.name()), counts[rank.ordinal()], 1, 64));
		}

		nemesisEnabled = c.getBool("nemesis.enabled", true);
		nemesisKillChance = c.getDouble("nemesis.become-on-kill-chance", 30);
		nemesisEscapeChance = c.getDouble("nemesis.become-on-escape-chance", 30);
		BossRank minRank = BossRank.parse(c.getString("nemesis.escape-min-rank", "RED"));
		nemesisEscapeMinRank = minRank == null ? BossRank.RED : minRank;
		nemesisEscapeDamagePercent = c.getDouble("nemesis.escape-min-damage-percent", 25);
		nemesisEscapeDistance = c.getDouble("nemesis.escape-distance", 80);
		nemesisMaxPerPlayer = Math.max(1, c.getInt("nemesis.max-per-player", 5));
		nemesisMaxLevel = Math.max(1, c.getInt("nemesis.max-level", 15));
		nemesisLevelsOnKill = Math.max(0, c.getInt("nemesis.levels-when-it-kills-you", 2));
		nemesisLevelsOnEscape = Math.max(0, c.getInt("nemesis.levels-when-you-flee", 1));
		nemesisLevelsPerRank = Math.max(1, c.getInt("nemesis.levels-per-rank-up", 4));
		nemesisLevelsPerTrait = Math.max(1, c.getInt("nemesis.levels-per-new-trait", 3));
		nemesisMaxTraits = Math.max(1, c.getInt("nemesis.max-traits", 7));
		nemesisReturnDays = Math.max(0.05, c.getDouble("nemesis.returns-after-days", 10));
		nemesisHealthPerLevel = c.getDouble("nemesis.health-per-level", 8);
		nemesisDamagePerLevel = c.getDouble("nemesis.damage-per-level", 4);
		nemesisMaxDamageMultiplier = Math.max(1, c.getDouble("nemesis.max-damage-multiplier", 5));
		nemesisPowerPerLevel = c.getDouble("nemesis.trait-power-per-level", 3);
		nemesisSizePerLevel = c.getDouble("nemesis.size-per-level", 0.02);
		nemesisMaxExtraSize = c.getDouble("nemesis.max-extra-size", 0.5);
		nemesisRevengeXpMultiplier = Math.max(1, c.getDouble("nemesis.revenge-xp-multiplier", 2));
		nemesisGearEvolution = c.getBool("nemesis.gear-evolution", true);
		statueScale = Math.max(0.0625, Math.min(2.0, c.getDouble("nemesis.statue-scale", 0.5)));
		nemesisProwlMinutes = Math.max(0, c.getDouble("nemesis.prowl-minutes", 1));
		nemesisKillLevelsPerOuting = Math.max(0, c.getInt("nemesis.levels-from-kills-per-outing", 5));
		nemesisAggroSwitchPercent = Math.max(0, c.getDouble("nemesis.aggro-switch-percent", 10));
		nemesisAdaptation = c.getBool("nemesis.adaptation.enabled", true);
		nemesisResistedPercent = Math.max(0, Math.min(100, c.getDouble("nemesis.adaptation.resisted-damage-percent", 65)));
		nemesisMaxImmunities = Math.max(0, c.getInt("nemesis.adaptation.max-immunities", 2));
		nemesisPetDamageTakenPercent = Math.max(0, Math.min(100, c.getDouble("nemesis.adaptation.pet-damage-taken-percent", 50)));
		nemesisPetDamageBonus = Math.max(0, c.getDouble("nemesis.adaptation.pet-damage-dealt-bonus", 50));

		guideOnFirstJoin = c.getBool("guide.give-on-first-join", true);
		messages = new Messages(c.sectionOrEmpty("messages"));
	}

	// =====================================================================

	public RankSettings rank(BossRank rank) {
		return ranks.get(rank);
	}

	public @Nullable MobCategory categoryFor(EntityType<?> type) {
		return categoryByType.get(type);
	}

	public @Nullable MobCategory category(String id) {
		for (MobCategory category : categories) {
			if (category.id().equalsIgnoreCase(id)) {
				return category;
			}
		}
		return null;
	}

	public MobProfile profileFor(EntityType<?> type) {
		return profileByType.getOrDefault(type, MobProfile.DEFAULT);
	}

	public boolean worldAllowed(Level level) {
		boolean listed = worlds.contains(level.dimension().identifier().toString());
		return worldWhitelist == listed;
	}

	public @Nullable EmpowermentStat stat(String id) {
		for (EmpowermentStat stat : empowermentStats) {
			if (stat.id().equalsIgnoreCase(id)) {
				return stat;
			}
		}
		return null;
	}

	public Set<EntityType<?>> eligibleMobs() {
		Set<EntityType<?>> out = new HashSet<>(categoryByType.keySet());
		out.removeAll(excludedMobs);
		return out;
	}

	public CompassTier compassTier(int tier) {
		return compassTiers.get(Math.max(0, Math.min(compassTiers.size() - 1, tier - 1)));
	}

	// =====================================================================

	private static RankSettings parseRank(BossRank rank, Cfg s) {
		int i = rank.ordinal();
		double[] health = {1.6, 2.0, 2.75, 3.5, 6.0, 10.0};
		double[] damage = {1.2, 1.35, 1.55, 1.8, 2.4, 3.0};
		double[] minHealth = {0, 0, 0, 0, 500, 800};
		double[] minDamage = {0, 0, 0, 0, 20, 30};
		double[] armor = {2, 4, 6, 8, 10, 14};
		double[] tough = {0, 1, 2, 4, 6, 8};
		double[] kb = {0.1, 0.2, 0.3, 0.45, 0.6, 0.8};
		double[] size = {0.15, 0.22, 0.3, 0.4, 0.5, 0.65};
		int[] tMin = {1, 1, 2, 2, 3, 4};
		int[] tMax = {1, 2, 2, 3, 4, 5};
		double[] power = {1.0, 1.15, 1.3, 1.5, 1.75, 2.1};
		double[] xpMul = {3, 4, 6, 8, 12, 20};
		int[] xpBonus = {10, 25, 50, 100, 300, 800};
		double[] gear = {20, 30, 45, 65, 100, 100};
		double[] emp = {10, 18, 28, 40, 60, 100};
		double[] relic = {70, 70, 75, 80, 90, 100};
		double[][] extra = {{}, {10}, {35}, {40, 10}, {60, 20}, {100, 50}};
		double[] cat = {0, 0, 0, 1.5, 5, 15};

		Cfg st = s.sectionOrEmpty("stats");
		RankSettings.Stats stats = new RankSettings.Stats(
			st.getDouble("health", health[i]), st.getDouble("damage", damage[i]), st.getDouble("armor", armor[i]),
			st.getDouble("armor-toughness", tough[i]), st.getDouble("knockback-resistance", kb[i]),
			st.getDouble("speed", 1.0), st.getDouble("size", size[i]),
			Math.max(0, st.getDouble("min-health", minHealth[i])), Math.max(0, st.getDouble("min-damage", minDamage[i])));

		Cfg tr = s.sectionOrEmpty("traits");
		int min = tr.getInt("min", tMin[i]);
		int max = Math.max(min, tr.getInt("max", tMax[i]));
		RankSettings.TraitRoll traits = new RankSettings.TraitRoll(min, max, tr.getDouble("power", power[i]));

		Cfg xp = s.sectionOrEmpty("xp");
		Cfg rw = s.sectionOrEmpty("rewards");
		List<Double> extraRelics = new ArrayList<>();
		if (rw.isList("extra-relics")) {
			for (double chance : rw.getDoubleList("extra-relics")) {
				extraRelics.add(Math.max(0, chance));
			}
		} else {
			for (double chance : extra[i]) {
				extraRelics.add(chance);
			}
		}
		RankSettings.Rewards rewards = new RankSettings.Rewards(rw.getDouble("boss-gear", gear[i]),
			rw.getDouble("empowerment", emp[i]), rw.getDouble("relic", relic[i]), List.copyOf(extraRelics),
			rw.getDouble("relic-catalyst", cat[i]));

		Cfg gs = s.sectionOrEmpty("gear");
		Map<GearTier, Integer> materials = new LinkedHashMap<>();
		Cfg ms = gs.sectionOrEmpty("materials");
		for (String key : ms.keys()) {
			GearTier tier = GearTier.parse(key);
			if (tier != null) {
				materials.put(tier, Math.max(0, ms.getInt(key, 0)));
			}
		}
		if (materials.isEmpty()) {
			materials.put(rank.atLeast(BossRank.PURPLE) ? GearTier.NETHERITE : GearTier.IRON, 1);
		}
		Map<GearQuality, Integer> quality = new LinkedHashMap<>();
		Cfg qs = gs.sectionOrEmpty("quality");
		for (String key : qs.keys()) {
			GearQuality q = GearQuality.parse(key);
			if (q != null) {
				quality.put(q, Math.max(0, qs.getInt(key, 0)));
			}
		}
		if (quality.isEmpty()) {
			quality.put(GearQuality.STANDARD, 1);
		}
		Cfg es = gs.sectionOrEmpty("enchantments");
		int eMin = es.getInt("min", 1 + i / 2);
		int eMax = Math.max(eMin, es.getInt("max", 2 + i));
		RankSettings.Gear gearSettings = new RankSettings.Gear(materials, quality, eMin, eMax,
			gs.getDouble("min-level-percent", Math.min(100, 30 + i * 17)), gs.getDouble("over-max-chance", i >= 3 ? 25 : 0),
			gs.getInt("over-max-levels", Math.max(0, i - 2)));

		Cfg ps = s.sectionOrEmpty("presentation");
		RankSettings.Presentation presentation = new RankSettings.Presentation(
			parseEnum(RankSettings.Announce.class, ps.getString("announce", "NONE"), RankSettings.Announce.NONE),
			Fx.parseSound(ps.getString("sound", "")),
			Fx.parseSound(ps.getString("death-sound", "")),
			ps.getInt("particles", i),
			parseEnum(RankSettings.Broadcast.class, ps.getString("death-broadcast", "NONE"), RankSettings.Broadcast.NONE));

		return new RankSettings(rank, s.getString("name", rank.defaultName()), stats, traits,
			xp.getDouble("multiplier", xpMul[i]), xp.getInt("bonus", xpBonus[i]), rewards, gearSettings,
			s.getBool("persistent", i >= 1), presentation);
	}

	private static Map<BossRank, Double> rankWeights(Cfg s) {
		Map<BossRank, Double> out = new EnumMap<>(BossRank.class);
		for (String key : s.keys()) {
			BossRank rank = BossRank.parse(key);
			if (rank != null) {
				out.put(rank, s.getDouble(key, 0));
			}
		}
		return out;
	}

	private static Map<BossRank, Double> rankMap(@Nullable Cfg s, double[] def) {
		Map<BossRank, Double> out = new EnumMap<>(BossRank.class);
		for (BossRank rank : BossRank.values()) {
			double d = rank.ordinal() < def.length ? def[rank.ordinal()] : 0;
			out.put(rank, s == null ? d : s.getDouble(rank.name(), d));
		}
		return out;
	}

	private static int[] range(List<Integer> raw, int[] def, int lowest, int highest) {
		if (raw.isEmpty()) {
			return def;
		}
		int lo = Math.max(lowest, Math.min(highest, raw.get(0)));
		int hi = raw.size() >= 2 ? Math.max(lo, Math.min(highest, raw.get(1))) : lo;
		return new int[]{lo, hi};
	}

	private static Set<BossRank> ranks(List<String> raw, Set<BossRank> def) {
		if (raw.isEmpty()) {
			return EnumSet.copyOf(def);
		}
		Set<BossRank> out = EnumSet.noneOf(BossRank.class);
		for (String r : raw) {
			BossRank rank = BossRank.parse(r);
			if (rank != null) {
				out.add(rank);
			}
		}
		return out;
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E def) {
		try {
			return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return def;
		}
	}

	/** Vanilla spawn reasons; the plugin's names (RAID, DEFAULT, BUILD_WITHER...) are understood too. */
	private static Set<EntitySpawnReason> parseReasons(List<String> raw) {
		Set<EntitySpawnReason> out = EnumSet.noneOf(EntitySpawnReason.class);
		for (String r : raw) {
			String s = r.trim().toUpperCase(Locale.ROOT);
			s = switch (s) {
				case "RAID" -> "EVENT";
				case "DEFAULT" -> "STRUCTURE";
				case "BUILD_WITHER" -> "TRIGGERED";
				case "CUSTOM" -> "COMMAND";
				case "SPAWNER_EGG", "SPAWN_EGG" -> "SPAWN_ITEM_USE";
				default -> s;
			};
			try {
				out.add(EntitySpawnReason.valueOf(s));
			} catch (IllegalArgumentException ex) {
				BloomCraft.LOG.warn("Unknown spawn reason '{}'", r);
			}
		}
		return out;
	}

	public static @Nullable EntityType<?> parseMob(String raw) {
		Identifier id = Identifier.tryParse(raw.trim().toLowerCase(Locale.ROOT));
		return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
	}

	private static Set<EntityType<?>> parseMobs(List<String> raw) {
		Set<EntityType<?>> out = new HashSet<>();
		for (String r : raw) {
			EntityType<?> type = parseMob(r);
			if (type == null) {
				BloomCraft.LOG.warn("Unknown mob '{}' in bosses.yml (ignored)", r);
				continue;
			}
			out.add(type);
		}
		return out;
	}

	private static @Nullable Holder<Attribute> parseAttribute(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.startsWith("generic.") || s.startsWith("player.")) {
			s = s.substring(s.indexOf('.') + 1);
		}
		Identifier id = Identifier.tryParse(s);
		if (id == null) {
			return null;
		}
		return BuiltInRegistries.ATTRIBUTE.get(id).map(h -> (Holder<Attribute>) h).orElse(null);
	}

	private static String dimensionId(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		return switch (s) {
			case "world", "overworld" -> "minecraft:overworld";
			case "world_nether", "nether", "the_nether" -> "minecraft:the_nether";
			case "world_the_end", "end", "the_end" -> "minecraft:the_end";
			default -> s.contains(":") ? s : "minecraft:" + s;
		};
	}

	public static String stripNamespace(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		int colon = s.indexOf(':');
		return colon >= 0 ? s.substring(colon + 1) : s;
	}
}
