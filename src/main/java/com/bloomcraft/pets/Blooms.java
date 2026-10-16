package com.bloomcraft.pets;

import com.bloomcraft.core.ItemData;
import com.bloomcraft.core.Nbt;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pet Blooms (the flower that summons a pet), the Pet Toy and the Fetch Stick: making them, describing them and
 * telling them apart.
 */
public final class Blooms {

	private static final String[] NAMES = {"Mochi", "Biscuit", "Pebble", "Nugget", "Waffles", "Pudding", "Clover",
		"Maple", "Pickles", "Sprout", "Tofu", "Bean", "Noodle", "Peanut", "Muffin", "Dumpling", "Juniper", "Pepper",
		"Hazel", "Oreo", "Cocoa", "Marshmallow", "Pumpkin", "Basil", "Fig", "Sushi", "Tater", "Buttons", "Ziggy",
		"Luna", "Comet", "Pixel", "Toast", "Bubbles", "Snickers", "Peaches", "Ginger", "Olive", "Rolo", "Taffy"};

	/** Free pet items handed out recently (player:what -> when), so they can't be farmed. */
	private static final Map<String, Long> HANDED_OUT = new HashMap<>();

	private Blooms() {
	}

	/** A cute name this player isn't using yet. */
	public static String freshName(PetStore store, UUID owner) {
		List<String> free = new ArrayList<>();
		for (String n : NAMES) {
			if (!store.nameTaken(owner, n)) {
				free.add(n);
			}
		}
		if (!free.isEmpty()) {
			return free.get(ThreadLocalRandom.current().nextInt(free.size()));
		}
		int n = 2;
		while (store.nameTaken(owner, "Mochi " + n)) {
			n++;
		}
		return "Mochi " + n;
	}

	// =====================================================================
	//  Pet Blooms
	// =====================================================================

	public static ItemStack create(Pet pet) {
		ItemStack item = new ItemStack(PetItems.PET_BLOOM);
		item.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(pet.species.bloomModel()));
		describe(item, pet, false, System.currentTimeMillis());
		ItemData.edit(item, tag -> {
			tag.putString("pet", pet.id.toString());
			tag.putString("owner", pet.owner.toString());
		});
		return item;
	}

	/** Name and lore for a bloom, or for the pet's icon in the menu ({@code menu} adds the click hints). */
	public static void describe(ItemStack item, Pet pet, boolean menu, long now) {
		Pets mod = Pets.get();
		int color = pet.species.category().color();
		item.set(DataComponents.ITEM_NAME, Text.colored("Pet Bloom — " + pet.name, color).withStyle(ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		if (menu && mod.pets().isOut(pet)) {
			lore.add(Text.line("● Out with you now", ChatFormatting.GREEN));
		}
		if (menu && pet.station != null) {
			var at = pet.station.block();
			lore.add(Text.line("⚑ At its post: " + at.getX() + ", " + at.getY() + ", " + at.getZ(), ChatFormatting.GOLD));
		}
		lore.add(Text.line(pet.species.displayName() + " · " + pet.species.category().displayName() + " · Level "
			+ pet.level, ChatFormatting.GRAY));
		if (pet.maxed()) {
			lore.add(Text.line("Bond: max level", ChatFormatting.LIGHT_PURPLE));
		} else {
			int need = Pet.xpToNext(pet.level);
			int filled = (int) Math.round(10.0 * pet.xp / need);
			lore.add(Text.noItalic(Component.literal("Bond: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("■".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(" " + pet.xp + "/" + need).withStyle(ChatFormatting.GRAY))));
		}
		if (menu && mod.settings().happiness) { // (a bloom in your bag would go stale)
			lore.add(Text.noItalic(mod.happiness().line(pet, now)));
		}
		for (String line : Text.wrap(pet.species.bonus(), 40)) {
			lore.add(Text.line(line, ChatFormatting.DARK_AQUA));
		}
		if (PetSkills.hasSkills(pet.species) && mod.skills().on()) {
			lore.add(Text.line("Taming skills: /pets taming " + pet.species.id(), ChatFormatting.DARK_AQUA));
		}
		if (pet.storageSlots() > 0) {
			lore.add(Text.line("Storage: " + pet.storageSlots() + " slots", ChatFormatting.GRAY));
		}
		if (menu && pet.resting(now)) {
			lore.add(Text.line("Resting: " + ((pet.restingUntil - now + 999) / 1000) + "s", ChatFormatting.RED));
		}
		lore.add(Component.empty());
		if (!mod.settings().species.contains(pet.species)) {
			lore.add(Text.line(pet.species.displayName() + " pets are turned off in this world", ChatFormatting.RED));
		}
		if (menu) {
			lore.add(Text.line(pet.stationed() ? "Left-click: call it back from its post" : "Left-click: summon / dismiss",
				ChatFormatting.DARK_GRAY));
			lore.add(Text.line("Right-click: take its Pet Bloom", ChatFormatting.DARK_GRAY));
			if (pet.species == Species.VEX) {
				lore.add(Text.line("Shift-click: its gear (while it's out)", ChatFormatting.DARK_GRAY));
			} else if (pet.storageSlots() > 0) {
				lore.add(Text.line("Shift-click: its storage (while it's out)", ChatFormatting.DARK_GRAY));
			}
		} else {
			lore.add(Text.line("Right-click: summon / dismiss " + pet.name, ChatFormatting.DARK_GRAY));
			lore.add(Text.line("Punch: open all your pets", ChatFormatting.DARK_GRAY));
			if (pet.species.rideable()) {
				int ride = mod.settings().rideLevel(pet.species);
				lore.add(Text.line("Use it on your pet: ride" + (ride > 1 ? " (level " + ride + "+)" : ""),
					ChatFormatting.DARK_GRAY));
			}
		}
		item.set(DataComponents.LORE, new ItemLore(lore));
	}

	public static @Nullable UUID petId(@Nullable ItemStack item) {
		if (item == null || item.isEmpty() || !item.is(PetItems.PET_BLOOM)) {
			return null;
		}
		return Nbt.uuid(ItemData.of(item).getStringOr("pet", ""));
	}

	public static @Nullable UUID bloomOwner(@Nullable ItemStack item) {
		if (item == null || item.isEmpty() || !item.is(PetItems.PET_BLOOM)) {
			return null;
		}
		return Nbt.uuid(ItemData.of(item).getStringOr("owner", ""));
	}

	public static boolean isBloom(@Nullable ItemStack item) {
		return item != null && item.is(PetItems.PET_BLOOM);
	}

	/** Refreshes the lore of this pet's blooms in the owner's inventory (after a level-up or rename). */
	public static void refresh(ServerPlayer owner, Pet pet) {
		Inventory inv = owner.getInventory();
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack item = inv.getItem(slot);
			if (pet.id.equals(petId(item))) {
				describe(item, pet, false, System.currentTimeMillis());
			}
		}
	}

	// =====================================================================
	//  The Pet Toy and the Fetch Stick
	// =====================================================================

	/** The Pet Toy: summons your pet, opens all your pets, lets you ride. Works for whoever holds it. */
	public static ItemStack toy() {
		ItemStack item = new ItemStack(PetItems.PET_TOY);
		item.set(DataComponents.ITEM_NAME, Text.colored("Pet Toy", PetMsg.PINK).withStyle(ChatFormatting.BOLD));
		item.set(DataComponents.LORE, new ItemLore(List.of(
			Text.line("Squeaks when you squeeze it.", ChatFormatting.GRAY),
			Component.empty(),
			Text.line("Right-click: call your pet / send it home", ChatFormatting.DARK_GRAY),
			Text.line("Punch: see all your pets and pick one", ChatFormatting.DARK_GRAY),
			Text.line("Use it on your pet: ride", ChatFormatting.DARK_GRAY),
			Text.line("Right-click your pet: swap to another", ChatFormatting.DARK_GRAY),
			Text.line("Sneak + right-click your pet: its bag", ChatFormatting.DARK_GRAY),
			Text.line("Sneak + punch your pet: station it there (level 8+)", ChatFormatting.DARK_GRAY))));
		return item;
	}

	public static boolean isToy(@Nullable ItemStack item) {
		return item != null && item.is(PetItems.PET_TOY);
	}

	/** The Fetch Stick: right-click to throw it, and your pet brings it back. */
	public static ItemStack fetchStick() {
		ItemStack item = new ItemStack(PetItems.FETCH_STICK);
		item.set(DataComponents.ITEM_NAME, Text.colored("Fetch Stick", 0xFFAA00).withStyle(ChatFormatting.BOLD));
		item.set(DataComponents.LORE, new ItemLore(List.of(
			Text.line("A very good stick.", ChatFormatting.GRAY),
			Component.empty(),
			Text.line("Right-click: throw it for your pet", ChatFormatting.DARK_GRAY))));
		return item;
	}

	public static boolean isFetchStick(@Nullable ItemStack item) {
		return item != null && item.is(PetItems.FETCH_STICK);
	}

	/** A bloom, the toy or the fetch stick (kept through death). */
	public static boolean isPetItem(@Nullable ItemStack item) {
		return isBloom(item) || isToy(item) || isFetchStick(item);
	}

	public static boolean hasToy(Player p) {
		return p.getInventory().contains(Blooms::isToy) || isToy(p.containerMenu.getCarried());
	}

	public static boolean hasFetchStick(Player p) {
		return p.getInventory().contains(Blooms::isFetchStick);
	}

	/** Gives the player a Pet Toy unless they already carry one (or just got one). True if one was given. */
	public static boolean giveToyIfMissing(ServerPlayer p, boolean tell) {
		if (hasToy(p)) {
			return false;
		}
		if (!mayHandOut(p, "toy")) {
			if (tell) {
				PetMsg.bar(p, "You just got a Pet Toy. Try again in a minute.", ChatFormatting.GRAY);
			}
			return false;
		}
		Players.give(p, toy());
		return true;
	}

	/**
	 * Free pet items (toys, blooms, sticks) at most once a minute each, so nobody fills chests with them.
	 * True (and remembered) if this one may be handed out now.
	 */
	public static boolean mayHandOut(Player p, String what) {
		long now = System.currentTimeMillis();
		HANDED_OUT.values().removeIf(t -> now - t > 60_000);
		String key = p.getUUID() + ":" + what;
		if (HANDED_OUT.containsKey(key)) {
			return false;
		}
		HANDED_OUT.put(key, now);
		return true;
	}
}
