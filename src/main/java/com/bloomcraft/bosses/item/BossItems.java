package com.bloomcraft.bosses.item;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.EmpowermentStat;
import com.bloomcraft.bosses.config.Messages;
import com.bloomcraft.bosses.relic.RelicEffect;
import com.bloomcraft.bosses.reward.GearQuality;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The mod's special items: making them, reading their data, applying runes / relics / catalysts to equipment,
 * and the lore that lists what's on an item. Item data lives in the item's custom data, under "bloomcraft".
 */
public final class BossItems {

	public enum Kind { GEAR, RUNE, RELIC, CATALYST, COMPASS, TOTEM, STATUE, WAYSTONE, SOUL, CHALLENGE }

	/** Lore lines wrap at about this many characters so tooltips stay on screen. */
	private static final int LORE_WIDTH = 38;
	private static final String[] CURSE_NOTICES = {"So be it.", "As you wish.", "It is done.", "You asked for this."};

	public static final int EMPOWER = 0x55D7FF;
	public static final int RELIC = 0xE08CFF;
	public static final int CURSE = 0xFF4040;

	private record Pending(String token, int expiresAt) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private BossItems() {
	}

	private static Bosses bosses() {
		return Bosses.get();
	}

	private static BossSettings settings() {
		return Bosses.get().settings();
	}

	// =====================================================================
	// Data access
	// =====================================================================

	/** A copy of the item's mod data (empty if none). */
	public static CompoundTag data(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		return custom == null ? new CompoundTag() : custom.copyTag().getCompoundOrEmpty(BloomCraft.MOD_ID);
	}

	public static void edit(ItemStack stack, Consumer<CompoundTag> change) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
			CompoundTag tag = root.getCompoundOrEmpty(BloomCraft.MOD_ID);
			change.accept(tag);
			root.put(BloomCraft.MOD_ID, tag);
		});
	}

	public static @Nullable Kind kind(@Nullable ItemStack item) {
		if (item == null || item.isEmpty()) {
			return null;
		}
		String raw = data(item).getStringOr("kind", "");
		if (raw.isEmpty()) {
			return null;
		}
		try {
			return Kind.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public static boolean isGear(@Nullable ItemStack item) {
		return kind(item) == Kind.GEAR;
	}

	public static @Nullable BossRank rank(ItemStack item) {
		return BossRank.parse(data(item).getStringOr("rank", ""));
	}

	public static List<String> list(ItemStack item, String key) {
		return Data.strings(data(item), key);
	}

	private static void putList(CompoundTag tag, String key, List<String> values) {
		ListTag list = new ListTag();
		for (String v : values) {
			list.add(StringTag.valueOf(v));
		}
		tag.put(key, list);
	}

	public static List<String> relics(ItemStack item) {
		return list(item, "relics");
	}

	public static List<String> curses(ItemStack item) {
		return list(item, "curses");
	}

	public static int relicSlots(ItemStack item) {
		return data(item).getIntOr("relic_slots", settings().relicBaseSlots);
	}

	/** Empowerments summed per stat, in the order they were first applied. */
	public static Map<String, Double> empowerments(ItemStack item) {
		Map<String, Double> out = new LinkedHashMap<>();
		for (String entry : list(item, "empowerments")) {
			int colon = entry.lastIndexOf(':');
			if (colon <= 0) {
				continue;
			}
			try {
				out.merge(entry.substring(0, colon), Double.parseDouble(entry.substring(colon + 1)), Double::sum);
			} catch (NumberFormatException ignored) {
			}
		}
		return out;
	}

	/** Sum of an effect stat (loot / fortune) on an item, e.g. 0.35 for a x1.35 multiplier. */
	public static double effectBonus(ItemStack item, String effect) {
		if (item.isEmpty()) {
			return 0;
		}
		double total = 0;
		for (Map.Entry<String, Double> e : empowerments(item).entrySet()) {
			EmpowermentStat stat = settings().stat(e.getKey());
			if (stat != null && effect.equals(stat.effect())) {
				total += e.getValue();
			}
		}
		return total;
	}

	private static void setModel(ItemStack item, String key) {
		item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(key), List.of()));
	}

	private static void lore(ItemStack item, List<Component> lines) {
		item.set(DataComponents.LORE, new ItemLore(lines));
	}

	// =====================================================================
	// Making items
	// =====================================================================

	/** Gives an item its Boss Gear identity: rank-coloured name with stars, label and source. */
	public static void markGear(ItemStack item, BossRank rank, String sourceName, GearQuality quality) {
		String rankName = settings().rank(rank).name();
		String itemName = Text.pretty(BuiltInRegistries.ITEM.getKey(item.getItem()).getPath());
		item.set(DataComponents.ITEM_NAME, rank.styled(rank.starText() + " " + rankName + " " + itemName));
		edit(item, tag -> {
			tag.putString("kind", "gear");
			tag.putString("rank", rank.name());
			tag.putString("source", sourceName);
			tag.putString("quality", quality.name());
		});
		refreshLore(item);
	}

	public static @Nullable ItemStack randomRune(BossRank rank) {
		EmpowermentStat stat = Rng.weighted(settings().empowermentStats, EmpowermentStat::weight);
		return stat == null ? null : rune(rank, stat, stat.roll(rank, settings().fixedRuneValues));
	}

	public static ItemStack rune(BossRank rank, EmpowermentStat stat, double amount) {
		ItemStack item = new ItemStack(ModItems.EMPOWERMENT_RUNE);
		item.set(DataComponents.ITEM_NAME, Text.colored(rank.starText() + " Empowerment Rune", rank.rgb()));
		setModel(item, stat.id());
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line(stat.format(amount), EMPOWER));
		lore.add(Text.noItalic(Component.literal("Fits: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(stat.fitsText()).withStyle(ChatFormatting.WHITE))));
		lore.add(Component.empty());
		lore.add(Text.line("Adds a permanent stat bonus.", ChatFormatting.DARK_GRAY));
		lore.add(Text.line("Pick it up and click it onto an item.", ChatFormatting.DARK_GRAY));
		lore(item, lore);
		edit(item, tag -> {
			tag.putString("kind", "rune");
			tag.putString("rank", rank.name());
			tag.putString("stat", stat.id());
			tag.putDouble("amount", amount);
		});
		return item;
	}

	/** Rolls a relic: always one good effect, plus the corruption chance of one curse on top. */
	public static @Nullable ItemStack randomRelic() {
		RelicEffect effect = bosses().relics().rollEffect();
		if (effect == null) {
			return null;
		}
		RelicEffect curse = Rng.chance(settings().corruptionChance) ? bosses().relics().rollCurse() : null;
		return relic(effect, curse);
	}

	public static ItemStack relic(RelicEffect effect, @Nullable RelicEffect curse) {
		boolean showCurse = curse != null && settings().revealCorruption;
		ItemStack item = new ItemStack(ModItems.RELIC);
		item.set(DataComponents.ITEM_NAME, showCurse
			? Text.colored("Corrupted Relic: " + effect.displayName(), CURSE)
			: Text.colored("Relic: " + effect.displayName(), RELIC));
		setModel(item, effect.id());
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("✦ " + effect.displayName(), RELIC));
		describe(lore, "  ", effect.description(), ChatFormatting.GRAY);
		if (showCurse) {
			lore.add(Text.line("☠ Curse: " + curse.displayName(), CURSE));
			describe(lore, "  ", curse.description(), ChatFormatting.DARK_RED);
		} else if (!settings().revealCorruption) {
			lore.add(Text.line("Its aura can't be read until it is bound...", ChatFormatting.DARK_GRAY));
		}
		lore.add(Component.empty());
		lore.add(Text.line("Permanent once bound to equipment.", ChatFormatting.YELLOW));
		lore.add(Text.line("Pick it up and click it onto any equipment.", ChatFormatting.DARK_GRAY));
		lore(item, lore);
		edit(item, tag -> {
			tag.putString("kind", "relic");
			tag.putString("relic", effect.id());
			if (curse != null) {
				tag.putString("curse", curse.id());
			}
		});
		return item;
	}

	/** A gift relic (the Quest Book's welcome): counts when bound, but not as "found". */
	public static void markStarter(ItemStack relic) {
		edit(relic, tag -> tag.putBoolean("starter", true));
	}

	public static ItemStack catalyst() {
		ItemStack item = new ItemStack(ModItems.RELIC_CATALYST);
		item.set(DataComponents.ITEM_NAME, Component.literal("Relic Catalyst").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Gives a piece of equipment +1 Relic slot", ChatFormatting.GRAY));
		lore.add(Text.line("(up to " + settings().catalystMaxSlots + " slots per item).", ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Text.line("Permanent. Extremely rare.", ChatFormatting.YELLOW));
		lore.add(Text.line("Pick it up and click it onto equipment.", ChatFormatting.DARK_GRAY));
		lore(item, lore);
		edit(item, tag -> tag.putString("kind", "catalyst"));
		return item;
	}

	/** A Boss Totem: use it to call a boss of this rank. */
	public static ItemStack totem(BossRank rank) {
		ItemStack item = new ItemStack(ModItems.BOSS_TOTEM);
		item.set(DataComponents.ITEM_NAME, Text.colored(rank.starText() + " " + settings().rank(rank).name() + " Boss Totem",
			rank.rgb()).withStyle(ChatFormatting.BOLD));
		setModel(item, rank.id());
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Calls a " + settings().rank(rank).name() + " boss.", ChatFormatting.GRAY));
		lore.add(Text.line("Right-click to begin the ritual.", ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Text.line("The boss arrives a few blocks away", ChatFormatting.DARK_GRAY));
		lore.add(Text.line("after 3 seconds. Be ready.", ChatFormatting.DARK_GRAY));
		lore(item, lore);
		edit(item, tag -> {
			tag.putString("kind", "totem");
			tag.putString("rank", rank.name());
		});
		return item;
	}

	/** A stackable Boss Soul of the given rank: offer it to a boss to make it rise. */
	public static ItemStack soul(BossRank rank, int amount) {
		ItemStack item = new ItemStack(ModItems.BOSS_SOUL, Math.max(1, Math.min(64, amount)));
		item.set(DataComponents.ITEM_NAME, rank.styled(rank.starText() + " " + settings().rank(rank).name() + " Boss Soul"));
		setModel(item, rank.id());
		if (rank.atLeast(BossRank.PURPLE)) {
			item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Right-click a boss with it, or throw", ChatFormatting.GRAY));
		lore.add(Text.line("(drop) it at one, and the boss rises.", ChatFormatting.GRAY));
		lore(item, lore);
		edit(item, tag -> {
			tag.putString("kind", "soul");
			tag.putString("rank", rank.name());
		});
		return item;
	}

	/** Waystones arrive with the waystone part of the mod. */
	public static @Nullable ItemStack waystone() {
		return null;
	}

	public static final String[] COMPASS_TIER_NAMES = {"Worn", "Keen", "Masterwork", "Mythic", "Legendary"};

	/** The Hunter's Compass: higher tiers track further and read a boss's rank from further away. */
	public static ItemStack compass(int tier) {
		var tiers = settings().compassTiers;
		int t = Math.max(1, Math.min(tiers.size(), tier));
		var info = settings().compassTier(t);
		ItemStack item = new ItemStack(ModItems.HUNTERS_COMPASS);
		String label = COMPASS_TIER_NAMES[Math.min(COMPASS_TIER_NAMES.length - 1, t - 1)];
		item.set(DataComponents.ITEM_NAME, Component.literal(label + " Hunter's Compass").withStyle(ChatFormatting.GOLD));
		if (t > 1) {
			item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Tier " + Text.roman(t) + " / " + Text.roman(tiers.size()), ChatFormatting.YELLOW));
		lore.add(Text.line("Hold it to track the nearest boss", ChatFormatting.GRAY));
		lore.add(Text.line("within " + Math.round(info.range()) + " blocks.", ChatFormatting.GRAY));
		lore.add(Text.line("Reveals its rank within " + Math.round(info.revealDistance()) + " blocks.", ChatFormatting.GRAY));
		lore.add(Component.empty());
		if (t < tiers.size()) {
			lore.add(Text.line("Click an Empowerment Rune onto it", ChatFormatting.DARK_GRAY));
			lore.add(Text.line("to upgrade it" + (t + 1 == tiers.size() ? " (Red rune or better)." : "."), ChatFormatting.DARK_GRAY));
		} else {
			lore.add(Text.line("Fully upgraded.", ChatFormatting.DARK_GRAY));
		}
		lore.add(Text.line("Click Boss Gear onto it to break the", ChatFormatting.DARK_GRAY));
		lore.add(Text.line("gear down into a Boss Soul of its rank.", ChatFormatting.DARK_GRAY));
		lore(item, lore);
		edit(item, tag -> {
			tag.putString("kind", "compass");
			tag.putInt("tier", t);
		});
		return item;
	}

	public static int compassTier(ItemStack compass) {
		return data(compass).getIntOr("tier", 1);
	}

	// =====================================================================
	// Applying runes / relics / catalysts
	// =====================================================================

	public static boolean isConsumable(@Nullable ItemStack item) {
		Kind k = kind(item);
		return k == Kind.RUNE || k == Kind.RELIC || k == Kind.CATALYST;
	}

	public static boolean isCompassUpgrade(ItemStack consumable, ItemStack target) {
		return kind(consumable) == Kind.RUNE && kind(target) == Kind.COMPASS;
	}

	private static @Nullable Component validateCompass(ItemStack rune, ItemStack compass) {
		int tier = compassTier(compass);
		int max = settings().compassTiers.size();
		if (tier >= max) {
			return Text.mm("<red>This compass is already fully upgraded.</red>");
		}
		BossRank rank = rank(rune);
		if (tier + 1 == max && (rank == null || !rank.atLeast(BossRank.RED))) {
			return Text.mm("<red>The last upgrade needs a Red, Purple or Gold rune.</red>");
		}
		return compass.getCount() == 1 ? null : settings().messages.get("apply-not-equipment");
	}

	/** An error message, or null if the consumable can be applied to the target. */
	public static @Nullable Component validate(ItemStack consumable, ItemStack target) {
		Messages m = settings().messages;
		if (isCompassUpgrade(consumable, target)) {
			return validateCompass(consumable, target);
		}
		EquipmentType type = EquipmentType.of(target);
		Kind targetKind = kind(target);
		if (targetKind != null && targetKind != Kind.GEAR) {
			return m.get("apply-not-equipment");
		}
		if (target.isEmpty() || !type.isEquipment() || target.getCount() != 1) {
			return m.get("apply-not-equipment");
		}
		CompoundTag c = data(consumable);
		Kind k = kind(consumable);
		if (k == Kind.RUNE) {
			EmpowermentStat stat = settings().stat(c.getStringOr("stat", ""));
			if (stat == null) {
				return m.get("apply-not-equipment");
			}
			if (!stat.fits(type)) {
				return m.get("apply-wrong-type", Map.of("fits", Component.literal(stat.fitsText())));
			}
			if (list(target, "empowerments").size() >= settings().empowermentMaxPerItem) {
				return m.get("apply-empowerment-full", Map.of("count", Component.literal(String.valueOf(settings().empowermentMaxPerItem))));
			}
			return null;
		}
		if (k == Kind.RELIC) {
			RelicEffect effect = bosses().relics().get(c.getStringOr("relic", ""));
			if (effect == null) {
				return m.get("apply-not-equipment");
			}
			List<String> relics = relics(target);
			if (relics.size() >= relicSlots(target)) {
				return m.get("apply-no-relic-slot");
			}
			if (!settings().allowDuplicateRelics && relics.contains(effect.id())) {
				return m.get("apply-duplicate-relic", Map.of("relic", Component.literal(effect.displayName())));
			}
			return null;
		}
		if (k == Kind.CATALYST) {
			if (relicSlots(target) >= settings().catalystMaxSlots) {
				return m.get("apply-catalyst-max", Map.of("count", Component.literal(String.valueOf(settings().catalystMaxSlots))));
			}
			return null;
		}
		return m.get("apply-not-equipment");
	}

	/** Relics and catalysts are permanent, so they need a second click within 10 seconds. Null once confirmed. */
	public static @Nullable Component confirmationPrompt(ServerPlayer player, ItemStack consumable, ItemStack target, String where) {
		Kind k = kind(consumable);
		if (!settings().confirmBinding || (k != Kind.RELIC && k != Kind.CATALYST)) {
			return null;
		}
		String token = k + ":" + data(consumable).getStringOr("relic", "") + ":"
			+ BuiltInRegistries.ITEM.getKey(target.getItem()) + ":" + where;
		int now = Ticks.now();
		Pending p = PENDING.get(player.getUUID());
		if (p != null && p.token().equals(token) && now <= p.expiresAt()) {
			PENDING.remove(player.getUUID());
			return null;
		}
		PENDING.put(player.getUUID(), new Pending(token, now + 200));
		Messages m = settings().messages;
		if (k == Kind.CATALYST) {
			return m.get("confirm-catalyst", Map.of("item", target.getHoverName()));
		}
		RelicEffect effect = bosses().relics().get(data(consumable).getStringOr("relic", ""));
		MutableComponent prompt = m.get("confirm-relic", Map.of(
			"relic", Component.literal(effect == null ? "?" : effect.displayName()), "item", target.getHoverName()));
		if (hasCatalyst(target) && settings().catalystCorruptionChance > settings().corruptionChance) {
			prompt.append(Component.literal(" ")).append(m.get("confirm-relic-catalyst",
				Map.of("count", Component.literal(Text.num(settings().catalystCorruptionChance)))));
		}
		return prompt;
	}

	/** Applies the consumable to the target (validate first). Returns the message to show. */
	public static Component apply(ServerPlayer player, ItemStack consumable, ItemStack target) {
		Messages m = settings().messages;
		CompoundTag c = data(consumable);
		Kind k = kind(consumable);
		Component itemName = target.getHoverName();

		if (isCompassUpgrade(consumable, target)) {
			int tier = compassTier(target) + 1;
			ItemStack upgraded = compass(tier);
			target.applyComponents(upgraded.getComponentsPatch());
			Fx.play(player, "block.lodestone.place", 1.0f, 1.2f);
			Fx.play(player, "block.enchantment_table.use", 1.0f, 1.4f);
			return m.get("compass-upgraded", Map.of("count", Component.literal(Text.roman(compassTier(upgraded)))));
		}

		if (k == Kind.RUNE) {
			EmpowermentStat stat = settings().stat(c.getStringOr("stat", ""));
			double amount = c.getDoubleOr("amount", 0.0);
			if (stat == null) {
				return m.get("apply-not-equipment");
			}
			if (stat.effect() == null) {
				addModifier(target, stat, amount, EquipmentType.of(target));
			}
			edit(target, tag -> {
				List<String> emps = Data.strings(tag, "empowerments");
				emps.add(stat.id() + ":" + String.format(Locale.ROOT, "%.4f", amount));
				putList(tag, "empowerments", emps);
			});
			refreshLore(target);
			Fx.play(player, "block.enchantment_table.use", 1.0f, 1.2f);
			return m.get("rune-applied", Map.of("item", itemName, "stat", Component.literal(stat.format(amount))));
		}

		if (k == Kind.RELIC) {
			RelicEffect effect = bosses().relics().get(c.getStringOr("relic", ""));
			RelicEffect curse = bosses().relics().get(c.getStringOr("curse", ""));
			if (effect == null) {
				return m.get("apply-not-equipment");
			}
			if (curse == null && hasCatalyst(target) && Rng.chance(extraCatalystCurseChance())) {
				curse = bosses().relics().rollCurse(); // a Catalyst draws corruption
			}
			RelicEffect finalCurse = curse;
			edit(target, tag -> {
				List<String> relics = Data.strings(tag, "relics");
				relics.add(effect.id());
				putList(tag, "relics", relics);
				List<String> curses = Data.strings(tag, "curses");
				if (finalCurse != null) {
					curses.add(finalCurse.id());
				}
				putList(tag, "curses", curses);
				if (!tag.contains("relic_slots")) {
					tag.putInt("relic_slots", settings().relicBaseSlots);
				}
			});
			target.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			refreshLore(target);
			CompoundTag pd = Data.of(player);
			pd.putInt("relics_bound", pd.getIntOr("relics_bound", 0) + 1);
			QuestFlags.mark(player, "relic_bound");
			if (c.getBooleanOr("starter", false)) {
				pd.putInt("starter_relics_bound", pd.getIntOr("starter_relics_bound", 0) + 1);
			}
			Fx.play(player, "block.end_portal_frame.fill", 1.0f, 0.8f);
			Component msg = m.get("relic-bound", Map.of("item", itemName, "relic", Component.literal(effect.displayName())));
			if (curse != null) {
				boolean bound = bindCursedArmor(player.level(), target);
				Fx.play(player, "entity.wither.ambient", 0.7f, 0.6f);
				msg = m.get("relic-corrupted", Map.of("curse", Component.literal(curse.displayName())));
				curseNotice(player, curse, bound);
			}
			return msg;
		}

		if (k == Kind.CATALYST) {
			int slots = relicSlots(target) + 1;
			edit(target, tag -> tag.putInt("relic_slots", slots));
			target.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			refreshLore(target);
			Fx.play(player, "block.beacon.activate", 1.0f, 1.3f);
			return m.get("catalyst-applied", Map.of("item", itemName, "count", Component.literal(String.valueOf(slots))));
		}
		return m.get("apply-not-equipment");
	}

	public static boolean hasCatalyst(ItemStack item) {
		return relicSlots(item) > settings().relicBaseSlots;
	}

	/** The extra roll that lifts the total curse chance from the normal one to the Catalyst one. */
	private static double extraCatalystCurseChance() {
		double base = Math.max(0, Math.min(100, settings().corruptionChance));
		double total = Math.max(0, Math.min(100, settings().catalystCorruptionChance));
		return total <= base || base >= 100 ? 0 : (total - base) / (100 - base) * 100.0;
	}

	/** Cursed armor can't be taken off: it gets Curse of Binding. True if the item is cursed armor (now bound). */
	public static boolean bindCursedArmor(Level level, ItemStack item) {
		if (item.isEmpty() || !EquipmentType.of(item).isArmor() || curses(item).isEmpty()) {
			return false;
		}
		var binding = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE);
		if (EnchantmentHelper.getItemEnchantmentLevel(binding, item) <= 0) {
			EnchantmentHelper.updateEnchantments(item, e -> e.set(binding, 1));
		}
		return true;
	}

	private static void curseNotice(ServerPlayer player, RelicEffect curse, boolean bound) {
		String line = Rng.pick(List.of(CURSE_NOTICES));
		Component sub = Text.colored(curse.displayName(), CURSE).append(Component.literal(bound
			? " binds to you. Only death will part you." : " takes hold.").withStyle(ChatFormatting.GRAY));
		Fx.title(player, Component.literal(line).withStyle(ChatFormatting.DARK_RED), sub, 6, 50, 18);
		Fx.playTo(player, "entity.elder_guardian.curse", 0.6f, 0.7f);
	}

	/** Adds the empowerment as an attribute modifier, keeping the item's normal stats. Hidden in the tooltip. */
	private static void addModifier(ItemStack target, EmpowermentStat stat, double amount, EquipmentType type) {
		ItemAttributeModifiers current = target.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(current.modifiers());
		for (Holder<Attribute> attribute : new ArrayList<>(java.util.Arrays.asList(stat.attribute(), stat.extraAttribute()))) {
			if (attribute == null) {
				continue;
			}
			AttributeModifier modifier = new AttributeModifier(
				BloomCraft.id("empower_" + UUID.randomUUID().toString().substring(0, 8)), amount, stat.operation());
			entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, type.slotGroup(),
				ItemAttributeModifiers.Display.hidden()));
		}
		target.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
	}

	// =====================================================================
	// Hunter's Compass: Boss Gear -> Boss Soul
	// =====================================================================

	public static boolean isSalvage(ItemStack gear, ItemStack compass) {
		return kind(gear) == Kind.GEAR && kind(compass) == Kind.COMPASS && gear.getCount() == 1;
	}

	public static BossRank gearRank(ItemStack gear) {
		BossRank rank = rank(gear);
		return rank == null ? BossRank.GRAY : rank;
	}

	/** The Soul this gear breaks down into (the rank its boss was found at), or null (a summoned boss's gear). */
	public static @Nullable BossRank salvageRank(ItemStack gear) {
		String raw = data(gear).getStringOr("salvage", "");
		if (raw.isEmpty()) {
			return gearRank(gear);
		}
		return BossRank.parse(raw);
	}

	public static void setSalvageRank(ItemStack item, @Nullable BossRank rank) {
		if (kind(item) == Kind.GEAR) {
			edit(item, tag -> tag.putString("salvage", rank == null ? "NONE" : rank.name()));
		}
	}

	/** Breaking gear down destroys it, so it asks for a second click within 10 seconds. Null once confirmed. */
	public static @Nullable Component salvagePrompt(ServerPlayer player, ItemStack gear, String where) {
		BossRank soulRank = salvageRank(gear) == null ? gearRank(gear) : salvageRank(gear);
		String token = "SALVAGE:" + BuiltInRegistries.ITEM.getKey(gear.getItem()) + ":" + salvageRank(gear) + ":" + where;
		int now = Ticks.now();
		Pending p = PENDING.get(player.getUUID());
		if (p != null && p.token().equals(token) && now <= p.expiresAt()) {
			PENDING.remove(player.getUUID());
			return null;
		}
		PENDING.put(player.getUUID(), new Pending(token, now + 200));
		return settings().messages.get("confirm-salvage", Map.of("item", gear.getHoverName(),
			"soul", soul(soulRank, 1).getHoverName()));
	}

	// =====================================================================
	// Lore
	// =====================================================================

	/** Rebuilds the mod's lore block at the top of the item's lore; lines added by anything else stay below. */
	public static void refreshLore(ItemStack item) {
		List<Component> ours = buildLore(item);
		ItemLore existing = item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		int previous = data(item).getIntOr("lore_lines", 0);
		List<Component> all = new ArrayList<>(ours);
		if (existing.lines().size() > previous) {
			all.addAll(existing.lines().subList(previous, existing.lines().size()));
		}
		if (all.isEmpty()) {
			item.remove(DataComponents.LORE);
		} else {
			item.set(DataComponents.LORE, new ItemLore(all));
		}
		edit(item, tag -> tag.putInt("lore_lines", ours.size()));
	}

	private static void describe(List<Component> lines, String indent, String text, ChatFormatting color) {
		for (String part : Text.wrap(text, LORE_WIDTH)) {
			lines.add(Text.line(indent + part, color));
		}
	}

	public static List<Component> buildLore(ItemStack item) {
		List<Component> lines = new ArrayList<>();
		CompoundTag view = data(item);
		if (kind(item) == Kind.GEAR) {
			BossRank rank = rank(item);
			if (rank != null) {
				lines.add(Text.line("◆ Boss-Touched Gear", rank.rgb()));
				GearQuality quality = GearQuality.parse(view.getStringOr("quality", ""));
				if (quality != null && quality != GearQuality.STANDARD) {
					lines.add(Text.line("Quality: " + quality.displayName(), quality == GearQuality.CRUDE
						? ChatFormatting.GRAY : quality == GearQuality.FINE ? ChatFormatting.AQUA : ChatFormatting.GOLD));
				}
				String source = view.getStringOr("source", "");
				if (!source.isEmpty()) {
					lines.add(Text.line("Dropped by: " + source, ChatFormatting.DARK_GRAY));
				}
			}
		}
		Map<String, Double> emps = empowerments(item);
		if (!emps.isEmpty()) {
			if (!lines.isEmpty()) {
				lines.add(Component.empty());
			}
			lines.add(Text.line("Empowered:", EMPOWER));
			for (Map.Entry<String, Double> e : emps.entrySet()) {
				EmpowermentStat stat = settings().stat(e.getKey());
				String text = stat == null ? "+" + Text.num(e.getValue()) + " " + Text.pretty(e.getKey()) : stat.format(e.getValue());
				lines.add(Text.line("  " + text, ChatFormatting.BLUE));
			}
		}
		List<String> relics = relics(item);
		List<String> curses = curses(item);
		int slots = relicSlots(item);
		if (!relics.isEmpty() || !curses.isEmpty() || slots > settings().relicBaseSlots) {
			if (!lines.isEmpty()) {
				lines.add(Component.empty());
			}
			lines.add(Text.line("Relics (" + relics.size() + "/" + slots + "):", RELIC));
			for (String id : relics) {
				RelicEffect effect = bosses().relics().get(id);
				lines.add(Text.line("  ✦ " + (effect == null ? Text.pretty(id) : effect.displayName()), RELIC));
			}
			for (int i = relics.size(); i < slots; i++) {
				lines.add(Text.line("  ◇ Empty Relic slot", ChatFormatting.DARK_GRAY));
			}
			for (String id : curses) {
				RelicEffect curse = bosses().relics().get(id);
				lines.add(Text.line("  ☠ Curse: " + (curse == null ? Text.pretty(id) : curse.displayName()), CURSE));
			}
			if (!relics.isEmpty() || !curses.isEmpty()) {
				lines.add(Text.line("  What they do: see the Compendium", ChatFormatting.DARK_GRAY));
			}
		}
		return lines;
	}

	/** Quest Book flags for having this item. */
	public static void questFlagFor(ServerPlayer player, ItemStack item) {
		Kind kind = kind(item);
		if (kind == null) {
			return;
		}
		switch (kind) {
			case GEAR -> QuestFlags.markOnce(player, "boss_gear");
			case RELIC -> {
				if (!data(item).getBooleanOr("starter", false)) {
					QuestFlags.markOnce(player, "relic_found");
				}
			}
			case SOUL -> QuestFlags.markOnce(player, "soul_found");
			default -> {
			}
		}
	}

	/** Lists tag values (for other classes). */
	public static List<String> strings(Tag tag) {
		List<String> out = new ArrayList<>();
		if (tag instanceof ListTag list) {
			for (Tag t : list) {
				t.asString().ifPresent(out::add);
			}
		}
		return out;
	}
}
