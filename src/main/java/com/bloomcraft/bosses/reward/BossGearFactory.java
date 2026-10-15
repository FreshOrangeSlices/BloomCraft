package com.bloomcraft.bosses.reward;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.RankSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.item.EquipmentType;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Ticks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Makes Boss Gear: a rank-branded equipment piece with rolled material, quality and enchantments. */
public final class BossGearFactory {

	private final Bosses bosses;

	public BossGearFactory(Bosses bosses) {
		this.bosses = bosses;
	}

	private static HolderLookup.RegistryLookup<Enchantment> enchantments() {
		MinecraftServer server = Ticks.server();
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	public ItemStack create(BossRank rank, @Nullable EntityType<?> sourceType, String sourceName, @Nullable GearKind forcedKind) {
		RankSettings.Gear gear = bosses.settings().rank(rank).gear();
		GearKind kind = forcedKind != null ? forcedKind : rollKind(rank, sourceType);
		GearTier tier = Rng.weighted(gear.materials());
		ItemStack item = new ItemStack(kind.item(tier == null ? GearTier.IRON : tier));
		GearQuality quality = Rng.weighted(gear.quality());
		if (quality == null) {
			quality = GearQuality.STANDARD;
		}
		enchant(item, gear, quality, 0, overlaps(rank));
		BossItems.markGear(item, rank, sourceName, quality);
		return item;
	}

	/** Nemesis loot: Masterwork, breaking the normal level ceiling a little further. */
	public ItemStack createBonus(BossRank rank, @Nullable EntityType<?> sourceType, String sourceName, int overMaxBonus) {
		RankSettings.Gear gear = bosses.settings().rank(rank).gear();
		GearKind kind = rollKind(rank, sourceType);
		GearTier tier = Rng.weighted(gear.materials());
		ItemStack item = new ItemStack(kind.item(tier == null ? GearTier.DIAMOND : tier));
		enchant(item, gear, GearQuality.MASTERWORK, overMaxBonus, overlaps(rank));
		BossItems.markGear(item, rank, sourceName, GearQuality.MASTERWORK);
		return item;
	}

	private boolean overlaps(BossRank rank) {
		BossRank from = bosses.settings().overlappingEnchantsFrom;
		return from != null && rank.atLeast(from);
	}

	/** First the category by rank, then the item inside it (plus what this mob likes: skeletons bows...). */
	private GearKind rollKind(BossRank rank, @Nullable EntityType<?> sourceType) {
		BossSettings s = bosses.settings();
		Map<GearKind, Integer> weights = new EnumMap<>(s.gearWeights);
		if (sourceType != null) {
			Map<GearKind, Integer> extra = s.gearPreferences.get(sourceType);
			if (extra != null) {
				extra.forEach((k, v) -> weights.merge(k, v, Integer::sum));
			}
		}
		Map<GearKind.Category, Integer> categories = s.gearCategories.get(rank);
		if (categories != null) {
			Map<GearKind.Category, Integer> usable = new EnumMap<>(GearKind.Category.class);
			categories.forEach((cat, w) -> {
				if (w > 0 && weights.entrySet().stream().anyMatch(e -> e.getKey().category() == cat && e.getValue() > 0)) {
					usable.put(cat, w);
				}
			});
			GearKind.Category cat = Rng.weighted(usable);
			if (cat != null) {
				weights.keySet().removeIf(k -> k.category() != cat);
			}
		}
		GearKind kind = Rng.weighted(weights);
		return kind == null ? GearKind.SWORD : kind;
	}

	private static String id(Holder<Enchantment> e) {
		return e.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
	}

	private void enchant(ItemStack item, RankSettings.Gear gear, GearQuality quality, int overMaxBonus, boolean overlap) {
		BossSettings s = bosses.settings();
		List<Holder<Enchantment>> candidates = new ArrayList<>();
		enchantments().listElements().forEach(e -> {
			if (!e.value().canEnchant(item)) {
				return;
			}
			if (e.is(EnchantmentTags.CURSE) && !s.allowCurseEnchantments) {
				return;
			}
			if (s.excludedEnchantments.contains(id(e))) {
				return;
			}
			candidates.add(e);
		});
		// a random order, but on armor Protection comes up front more often
		Map<Holder<Enchantment>, Double> keys = new HashMap<>();
		for (Holder<Enchantment> e : candidates) {
			double w = id(e).equals("protection") ? s.protectionWeight : 1.0;
			keys.put(e, Math.pow(ThreadLocalRandom.current().nextDouble(), 1.0 / w));
		}
		candidates.sort((a, b) -> Double.compare(keys.get(b), keys.get(a)));
		int wanted = Math.max(0, Rng.between(gear.enchantMin(), gear.enchantMax()) + quality.extraEnchantments());
		List<Holder<Enchantment>> chosen = new ArrayList<>();
		Map<Holder<Enchantment>, Integer> levels = new HashMap<>();
		for (Holder<Enchantment> e : candidates) {
			if (chosen.size() >= wanted) {
				break;
			}
			if (clashes(e, chosen, overlap)) {
				continue;
			}
			chosen.add(e);
			int max = e.value().getMaxLevel();
			int min = Math.max(1, (int) Math.ceil(max * gear.minLevelPercent() / 100.0));
			int level = Math.max(1, Math.min(max, Rng.between(Math.min(min, max), max) + quality.levelShift()));
			int overLevels = gear.overMaxLevels() + overMaxBonus;
			double overChance = overMaxBonus > 0 ? 100 : gear.overMaxChance() * quality.overMaxFactor();
			if (max > 1 && overLevels > 0 && Rng.chance(overChance)) {
				level = max + Rng.between(1, overLevels);
			}
			levels.put(e, level);
		}
		// Boss tools can carry a weapon enchantment too (Sharpness on a pickaxe hits like on a sword).
		if (EquipmentType.of(item) == EquipmentType.TOOL && Rng.chance(s.toolOffensiveChance)) {
			addOffensive(gear, quality, chosen, levels, overlap);
		}
		EnchantmentHelper.updateEnchantments(item, mutable -> levels.forEach(mutable::set));
	}

	/** Normally exclusive enchantments never come together; with overlap they can, except ones that break the item. */
	private static boolean clashes(Holder<Enchantment> e, List<Holder<Enchantment>> chosen, boolean overlap) {
		for (Holder<Enchantment> c : chosen) {
			if (c.equals(e)) {
				return true;
			}
			if (Enchantment.areCompatible(c, e)) {
				continue;
			}
			if (!overlap || breaksTogether(c, e)) {
				return true;
			}
		}
		return false;
	}

	private static boolean breaksTogether(Holder<Enchantment> a, Holder<Enchantment> b) {
		String x = id(a);
		String y = id(b);
		return x.equals("riptide") && (y.equals("loyalty") || y.equals("channeling"))
			|| y.equals("riptide") && (x.equals("loyalty") || x.equals("channeling"));
	}

	private static final String[] OFFENSIVE = {"sharpness", "sharpness", "sharpness", "smite", "bane_of_arthropods",
		"fire_aspect", "fire_aspect", "knockback"};

	private void addOffensive(RankSettings.Gear gear, GearQuality quality, List<Holder<Enchantment>> chosen,
							  Map<Holder<Enchantment>, Integer> levels, boolean overlap) {
		HolderLookup.RegistryLookup<Enchantment> registry = enchantments();
		for (int attempt = 0; attempt < 6; attempt++) {
			String id = Rng.pick(List.of(OFFENSIVE));
			var e = registry.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id))).orElse(null);
			if (e == null || bosses.settings().excludedEnchantments.contains(id) || clashes(e, chosen, overlap)) {
				continue;
			}
			int max = e.value().getMaxLevel();
			int min = Math.max(1, (int) Math.ceil(max * gear.minLevelPercent() / 100.0));
			levels.put(e, Math.max(1, Math.min(max, Rng.between(Math.min(min, max), max) + quality.levelShift())));
			chosen.add(e);
			return;
		}
	}
}
