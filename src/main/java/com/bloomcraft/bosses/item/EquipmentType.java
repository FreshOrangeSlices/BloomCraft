package com.bloomcraft.bosses.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Broad equipment classes: which Empowerments fit an item, and where its bonuses are active. */
public enum EquipmentType {
	MELEE("Melee Weapons"),
	RANGED("Bows & Crossbows"),
	HELMET("Helmets"),
	CHESTPLATE("Chestplates"),
	LEGGINGS("Leggings"),
	BOOTS("Boots"),
	SHIELD("Shields"),
	TOOL("Tools"),
	OTHER("Other");

	private final String plural;

	EquipmentType(String plural) {
		this.plural = plural;
	}

	public String plural() {
		return plural;
	}

	public static EquipmentType of(ItemStack stack) {
		return stack.isEmpty() ? OTHER : of(stack.getItem());
	}

	public static EquipmentType of(Item item) {
		String n = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (n.endsWith("_sword") || n.endsWith("_axe") || n.endsWith("_spear") || n.equals("mace") || n.equals("trident")) {
			return MELEE;
		}
		if (n.equals("bow") || n.equals("crossbow")) {
			return RANGED;
		}
		if (n.endsWith("_helmet")) {
			return HELMET;
		}
		if (n.endsWith("_chestplate") || n.equals("elytra")) {
			return CHESTPLATE;
		}
		if (n.endsWith("_leggings")) {
			return LEGGINGS;
		}
		if (n.endsWith("_boots")) {
			return BOOTS;
		}
		if (n.equals("shield")) {
			return SHIELD;
		}
		if (n.endsWith("_pickaxe") || n.endsWith("_shovel") || n.endsWith("_hoe") || n.equals("fishing_rod")
			|| n.equals("shears") || n.equals("brush")) {
			return TOOL;
		}
		return OTHER;
	}

	public boolean isEquipment() {
		return this != OTHER;
	}

	public boolean isArmor() {
		return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
	}

	/** Where an attribute bonus on this item is active. */
	public EquipmentSlotGroup slotGroup() {
		return switch (this) {
			case HELMET -> EquipmentSlotGroup.HEAD;
			case CHESTPLATE -> EquipmentSlotGroup.CHEST;
			case LEGGINGS -> EquipmentSlotGroup.LEGS;
			case BOOTS -> EquipmentSlotGroup.FEET;
			case SHIELD -> EquipmentSlotGroup.HAND;
			default -> EquipmentSlotGroup.MAINHAND;
		};
	}

	/** Matches a group name from the config: ANY, WEAPON, ARMOR, or a single type (MELEE, RANGED, HELMET...). */
	public boolean matches(String group) {
		String g = group.trim().toUpperCase(Locale.ROOT);
		return switch (g) {
			case "ANY" -> isEquipment();
			case "WEAPON", "WEAPONS" -> this == MELEE || this == RANGED;
			case "ARMOR", "ARMOUR" -> isArmor();
			default -> name().equals(g) || (name() + "S").equals(g);
		};
	}

	/** Friendly text for a config group name. */
	public static String describeGroup(String group) {
		String g = group.trim().toUpperCase(Locale.ROOT);
		return switch (g) {
			case "ANY" -> "Any Equipment";
			case "WEAPON", "WEAPONS" -> "Weapons";
			case "ARMOR", "ARMOUR" -> "Armor";
			default -> {
				for (EquipmentType t : values()) {
					if (t.name().equals(g) || (t.name() + "S").equals(g)) {
						yield t.plural();
					}
				}
				yield group;
			}
		};
	}
}
