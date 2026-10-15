package com.bloomcraft.bosses;

import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.core.Rng;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Matching cosmetic armor sets for bosses (and the gear a Nemesis grows into). */
public final class BossArmor {

	private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
		EquipmentSlot.FEET};

	private BossArmor() {
	}

	/** Only these mobs render armor (illagers, spiders, slimes... don't). */
	public static boolean showsArmor(LivingEntity e) {
		return e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof AbstractPiglin;
	}

	/** A random set from the rank's palette, with one random trim pattern for every piece. */
	public static void applyRankSet(BossSettings s, LivingEntity e, BossRank rank) {
		if (!showsArmor(e) || !(e instanceof Mob mob)) {
			return;
		}
		List<String[]> palette = s.armorPalettes.get(rank);
		if (palette == null || palette.isEmpty()) {
			return;
		}
		String[] pick = Rng.pick(palette);
		String pattern = Rng.pick(s.trimPatterns);
		equipSet(mob, pick[0], pick[1], pattern, rank == BossRank.ASCENDANT);
	}

	/** The armor tier a mob is wearing: 0 chain/copper/gold, 1 iron, 2 diamond, 3 netherite. */
	public static int wornStage(LivingEntity e) {
		int best = 0;
		for (EquipmentSlot slot : SLOTS) {
			String n = BuiltInRegistries.ITEM.getKey(e.getItemBySlot(slot).getItem()).getPath();
			best = Math.max(best, n.startsWith("netherite") ? 3 : n.startsWith("diamond") ? 2 : n.startsWith("iron") ? 1 : 0);
		}
		return best;
	}

	/**
	 * Nemesis gear by level: chainmail, then iron, diamond and netherite, gaining trims and finally a glint. It never
	 * looks worse than its rank or what it wore as a boss ({@code floor}); the trim pattern is fixed per Nemesis.
	 */
	public static void applyNemesis(BossSettings s, LivingEntity e, int level, String seed, BossRank rank, String hand,
									int floor) {
		if (!(e instanceof Mob mob)) {
			return;
		}
		int startStage = switch (rank) {
			case GRAY -> 0;
			case GREEN, RED -> 1;
			case PURPLE -> 2;
			case GOLD -> 3;
			case ASCENDANT -> 4;
		};
		int stage = Math.min(4, Math.max(Math.max(startStage, floor), level / 10));
		String[] armor = {"chainmail", "iron", "diamond", "netherite", "netherite"};
		String[] trims = {null, "iron", "redstone", "gold", "quartz"};
		String pattern = s.trimPatterns.get(Math.floorMod(seed.hashCode(), s.trimPatterns.size()));
		if (showsArmor(e)) {
			equipSet(mob, armor[stage], trims[stage], pattern, stage >= 4);
		}
		ItemStack weapon = nemesisWeapon(e.getType(), stage, hand);
		if (weapon != null) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
		}
	}

	private static @Nullable ItemStack nemesisWeapon(EntityType<?> type, int stage, String hand) {
		String[] tiers = {"stone", "iron", "diamond", "netherite", "netherite"};
		Item item;
		if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.ZOMBIE_VILLAGER
			|| type == EntityTypes.WITHER_SKELETON || type == EntityTypes.ZOMBIFIED_PIGLIN) {
			item = item(tiers[stage] + "_sword");
		} else if (type == EntityTypes.VINDICATOR || type == EntityTypes.PIGLIN_BRUTE) {
			item = item(tiers[stage] + "_axe");
		} else if (type == EntityTypes.PIGLIN) {
			item = "CROSSBOW".equals(hand) ? Items.CROSSBOW : item(tiers[stage] + "_sword");
		} else if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED
			|| type == EntityTypes.PARCHED) {
			item = Items.BOW;
		} else if (type == EntityTypes.PILLAGER) {
			item = Items.CROSSBOW;
		} else if (type == EntityTypes.DROWNED) {
			item = Items.TRIDENT;
		} else {
			item = null;
		}
		if (item == null) {
			return null;
		}
		ItemStack stack = new ItemStack(item);
		cosmetic(stack, stage >= (item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT ? 2 : 4));
		return stack;
	}

	private static void equipSet(Mob mob, String armorPrefix, @Nullable String trim, String pattern, boolean glint) {
		Holder<TrimMaterial> material = trim == null ? null : lookup(mob, Registries.TRIM_MATERIAL, trim);
		Holder<TrimPattern> trimPattern = lookup(mob, Registries.TRIM_PATTERN, pattern);
		for (int i = 0; i < PIECES.length; i++) {
			Item item = item(armorPrefix.toLowerCase(Locale.ROOT) + "_" + PIECES[i]);
			if (item == null) {
				item = item("iron_" + PIECES[i]);
			}
			if (item == null) {
				continue;
			}
			ItemStack piece = new ItemStack(item);
			if (material != null && trimPattern != null) {
				piece.set(DataComponents.TRIM, new ArmorTrim(material, trimPattern));
			}
			cosmetic(piece, glint);
			mob.setItemSlot(SLOTS[i], piece);
			mob.setDropChance(SLOTS[i], 0f);
		}
	}

	/** No armor points or weapon damage, and it can't break. */
	private static void cosmetic(ItemStack item, boolean glint) {
		item.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		item.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		if (glint) {
			item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
	}

	private static @Nullable Item item(String path) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path)).orElse(null);
	}

	private static <T> @Nullable Holder<T> lookup(LivingEntity e, ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
												  String name) {
		Identifier id = Identifier.tryParse(name.trim().toLowerCase(Locale.ROOT));
		if (id == null) {
			return null;
		}
		return e.level().registryAccess().lookupOrThrow(registry).get(ResourceKey.create(registry, id))
			.map(h -> (Holder<T>) h).orElse(null);
	}
}
