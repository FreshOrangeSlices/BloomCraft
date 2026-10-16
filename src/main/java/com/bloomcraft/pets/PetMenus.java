package com.bloomcraft.pets;

import com.bloomcraft.core.Books;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.ItemData;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.ServerMenu;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The screens: the pet menu (all your pets in one place, like an ender chest that only holds pets), a pet's storage,
 * and the Vex's gear.
 */
public final class PetMenus {

	private static final int PET_SLOTS = 45;
	private static final int DISMISS_SLOT = 45;
	private static final int TOY_SLOT = 47;
	private static final int INFO_SLOT = 49;
	private static final int PVP_SLOT = 50;
	private static final int CANCEL_SLOT = 51;
	private static final int FETCH_SLOT = 51; // the cancel button only shows in release mode
	private static final int RELEASE_SLOT = 53;

	/** The pet menu's state while it's open. */
	static final class PetMenuState {
		final UUID owner;
		final List<UUID> pets = new ArrayList<>();
		/** Release mode: click pets to pick them, then confirm twice. */
		boolean releasing;
		final Set<UUID> chosen = new LinkedHashSet<>();
		boolean armed;
		long armedAt;

		PetMenuState(UUID owner) {
			this.owner = owner;
		}
	}

	/** A pet's storage: the pet's own container, so nothing is ever copied (or duplicated). */
	final class StorageMenu extends ChestMenu {
		final Pet pet;

		StorageMenu(int id, Inventory inv, Pet pet, int rows) {
			super(type(rows), id, inv, pet.storage, rows);
			this.pet = pet;
		}

		@Override
		public boolean stillValid(Player player) {
			PetManager.Active a = mod.pets().activeFor(pet);
			return a != null && !a.fainting && !mod.happiness().sad(pet)
				&& (a.home == null || (a.entity.level() == player.level() && a.entity.distanceToSqr(player) < 10 * 10));
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			mod.store().changed(pet.owner);
			if (player instanceof ServerPlayer sp) {
				Fx.playTo(sp, "block.barrel.close", 0.5f, 1.2f);
			}
		}
	}

	/** The Vex's gear: a window straight onto what it wears (helmet, chest, legs, boots, weapon). */
	final class GearMenu extends HopperMenu {
		final Pet pet;
		final Mob vex;

		GearMenu(int id, Inventory inv, Pet pet, Mob vex) {
			super(id, inv, new GearContainer(vex));
			this.pet = pet;
			this.vex = vex;
		}

		@Override
		public boolean stillValid(Player player) {
			return vex.isAlive() && !vex.isRemoved() && mod.pets().of(vex) != null && vex.distanceToSqr(player) < 16 * 16;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			mod.store().changed(pet.owner);
		}
	}

	/** The five gear slots of a mob as a container. */
	private static final class GearContainer extends SimpleContainer {
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
		private final Mob mob;

		GearContainer(Mob mob) {
			super(5);
			this.mob = mob;
		}

		@Override
		public ItemStack getItem(int slot) {
			return slot >= 0 && slot < 5 ? mob.getItemBySlot(SLOTS[slot]) : ItemStack.EMPTY;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (slot >= 0 && slot < 5) {
				mob.setItemSlot(SLOTS[slot], stack);
				mob.setDropChance(SLOTS[slot], 0);
			}
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack current = getItem(slot);
			if (current.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = current.split(count);
			setItem(slot, current.isEmpty() ? ItemStack.EMPTY : current);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack current = getItem(slot);
			setItem(slot, ItemStack.EMPTY);
			return current;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < 5; i++) {
				if (!getItem(i).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	private final Pets mod;

	PetMenus(Pets mod) {
		this.mod = mod;
	}

	private static MenuType<?> type(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	// =====================================================================
	//  Pet menu
	// =====================================================================

	public void openPets(ServerPlayer p) {
		PetMenuState state = new PetMenuState(p.getUUID());
		ServerMenu.open(p, Component.literal("Your Pets"), 6, c -> render(state, c),
			(player, slot, input, button) -> click(state, player, slot, input, button), state);
		Fx.playTo(p, "block.ender_chest.open", 0.5f, 1.4f);
	}

	/** Redraws the pet menu if this player has it open (after a summon, dismiss, level-up...). */
	public void refresh(ServerPlayer p) {
		ServerMenu menu = ServerMenu.current(p);
		if (menu != null && menu.key() instanceof PetMenuState state) {
			menu.redraw(c -> render(state, c));
		}
	}

	private void render(PetMenuState menu, Container inv) {
		menu.pets.clear();
		List<Pet> pets = mod.store().pets(menu.owner);
		long now = System.currentTimeMillis();
		menu.chosen.removeIf(id -> pets.stream().noneMatch(pet -> pet.id.equals(id)));
		for (Pet pet : pets) {
			if (menu.pets.size() >= PET_SLOTS) {
				break;
			}
			inv.setItem(menu.pets.size(), menu.releasing ? releaseIcon(pet, menu.chosen.contains(pet.id)) : icon(pet, now));
			menu.pets.add(pet.id);
		}
		for (int slot = PET_SLOTS; slot < 54; slot++) {
			inv.setItem(slot, filler());
		}
		PetSettings s = mod.settings();
		inv.setItem(INFO_SLOT, button(Items.BOOK, "Your Pets (" + pets.size() + "/" + s.maxPets + ")", PetMsg.PINK,
			List.of("Click to read the Pet Guide.", "", "Sneak + feed an animal its favourite",
				"food a few times to make a friend.")));
		if (menu.releasing) {
			int n = menu.chosen.size();
			inv.setItem(CANCEL_SLOT, button(Items.ARROW, "Cancel", 0xAAAAAA, List.of("Back to your pets, nobody leaves.")));
			if (n == 0) {
				inv.setItem(RELEASE_SLOT, button(Items.FEATHER, "Pick pets to release", 0xFFAA00,
					List.of("Click pets above to pick them.")));
			} else if (!menu.armed) {
				inv.setItem(RELEASE_SLOT, button(Items.DYE.red(), "Release " + n + (n == 1 ? " pet" : " pets"), 0xFF5555,
					List.of("Anything they carry comes back to you.", "Click to continue.")));
			} else {
				inv.setItem(RELEASE_SLOT, button(Items.DYE.red(), "Click again to say goodbye to " + n
					+ (n == 1 ? " pet" : " pets"), 0xAA0000, List.of("This can't be undone.")));
			}
			return;
		}
		PetManager.Active active = mod.pets().active(menu.owner);
		if (active != null) {
			inv.setItem(DISMISS_SLOT, button(Items.BARRIER, "Dismiss " + active.pet.name, 0xFF5555,
				List.of("Sends it home for a nap.")));
		}
		inv.setItem(TOY_SLOT, toyButton());
		inv.setItem(FETCH_SLOT, button(Items.STICK, "Play fetch!", 0xFFAA00,
			List.of("Get a Fetch Stick, then right-click", "to throw it for the pet that's out.")));
		if (s.petPvp) {
			boolean on = mod.store().pvp(menu.owner);
			inv.setItem(PVP_SLOT, on
				? button(Items.WOOL.lime(), "Pets in PvP: ON", 0x55FF55,
					List.of("Your combat pet joins your fights", "with other players too.", "", "Click to turn off."))
				: button(Items.WOOL.red(), "Pets in PvP: OFF", 0xFF5555,
					List.of("Your pets stay out of fights", "with other players.", "", "Click to turn on.")));
		}
		if (!pets.isEmpty()) {
			inv.setItem(RELEASE_SLOT, button(Items.FEATHER, "Release pets...", 0xAAAAAA,
				List.of("Say goodbye to one or more pets.")));
		} else {
			inv.setItem(22, button(Items.POPPY, "No pets yet", PetMsg.PINK,
				List.of("Sneak + feed a Wolf some meat, a Fox", "berries, a Horse sugar, an Allay an",
					"amethyst shard... and it's yours.")));
		}
	}

	/** A picture of the pet's bloom (not a real one: it has no pet in it). */
	private ItemStack icon(Pet pet, long now) {
		ItemStack item = Blooms.create(pet);
		Blooms.describe(item, pet, true, now);
		item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, mod.pets().isOut(pet));
		ItemData.clear(item);
		return item;
	}

	private static ItemStack toyButton() {
		ItemStack item = Blooms.toy();
		List<Component> lore = new ArrayList<>(item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		lore.add(Component.empty());
		lore.add(Text.line("Click: get a Pet Toy (if you lost yours)", ChatFormatting.GRAY));
		item.set(DataComponents.LORE, new ItemLore(lore));
		return item;
	}

	/** A pet's icon in release mode: picked ones are marked in red. */
	private ItemStack releaseIcon(Pet pet, boolean chosen) {
		ItemStack item = icon(pet, System.currentTimeMillis());
		item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, chosen);
		item.set(DataComponents.ITEM_NAME, Text.colored((chosen ? "✖ " : "") + pet.name,
			chosen ? 0xFF5555 : pet.species.category().color()).withStyle(ChatFormatting.BOLD));
		item.set(DataComponents.LORE, new ItemLore(List.of(
			Text.line(pet.species.displayName() + " · Level " + pet.level, ChatFormatting.GRAY),
			Text.line(chosen ? "Will be released. Click to keep." : "Click to pick for release.",
				chosen ? ChatFormatting.RED : ChatFormatting.DARK_GRAY))));
		return item;
	}

	private static ItemStack filler() {
		ItemStack item = new ItemStack(Items.STAINED_GLASS_PANE.gray());
		item.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
		return item;
	}

	private static ItemStack button(Item material, String name, int color, List<String> lore) {
		ItemStack item = new ItemStack(material);
		item.set(DataComponents.ITEM_NAME, Text.colored(name, color).withStyle(ChatFormatting.BOLD));
		List<Component> lines = new ArrayList<>();
		for (String line : lore) {
			lines.add(Text.line(line, ChatFormatting.GRAY));
		}
		item.set(DataComponents.LORE, new ItemLore(lines));
		return item;
	}

	private void click(PetMenuState menu, ServerPlayer p, int slot, ContainerInput input, int button) {
		PetManager pets = mod.pets();
		if (slot == INFO_SLOT) {
			p.closeContainer();
			Books.open(p, PetGuide.book());
			return;
		}
		if (menu.releasing) {
			if (input != ContainerInput.PICKUP_ALL) {
				clickRelease(p, menu, slot);
			}
			return;
		}
		if (input != ContainerInput.PICKUP && input != ContainerInput.QUICK_MOVE) {
			return;
		}
		if (slot == DISMISS_SLOT && pets.active(menu.owner) != null) {
			pets.dismiss(p, true);
			return;
		}
		if (slot == TOY_SLOT) {
			if (Blooms.hasToy(p)) {
				PetMsg.bar(p, "You already have a Pet Toy.", ChatFormatting.GRAY);
			} else if (Blooms.giveToyIfMissing(p, true)) {
				Fx.playTo(p, "entity.item.pickup", 0.6f, 1.2f);
			}
			return;
		}
		if (slot == PVP_SLOT && mod.settings().petPvp) {
			boolean on = !mod.store().pvp(p.getUUID());
			pets.setPvp(p, on);
			Fx.playTo(p, "ui.button.click", 0.5f, on ? 1.4f : 0.8f);
			PetMsg.bar(p, on ? "Your combat pets will join PvP fights." : "Your pets will stay out of PvP fights.",
				on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
			refresh(p);
			return;
		}
		if (slot == FETCH_SLOT) {
			if (Blooms.hasFetchStick(p)) {
				PetMsg.bar(p, "You already have a Fetch Stick.", ChatFormatting.GRAY);
			} else if (Blooms.mayHandOut(p, "fetch")) {
				Players.give(p, Blooms.fetchStick());
				Fx.playTo(p, "entity.item.pickup", 0.6f, 1.2f);
			} else {
				PetMsg.bar(p, "You just got a Fetch Stick. Try again in a minute.", ChatFormatting.GRAY);
			}
			return;
		}
		if (slot == RELEASE_SLOT && !menu.pets.isEmpty()) {
			menu.releasing = true;
			menu.chosen.clear();
			menu.armed = false;
			refresh(p);
			return;
		}
		if (slot < 0 || slot >= menu.pets.size()) {
			return;
		}
		Pet pet = mod.store().get(menu.owner, menu.pets.get(slot));
		if (pet == null) {
			refresh(p);
			return;
		}
		if (input == ContainerInput.QUICK_MOVE) {
			openPetInventory(p, pet);
		} else if (button == 1) {
			giveBloom(p, pet);
		} else {
			pets.toggle(p, pet);
			Fx.playTo(p, "ui.button.click", 0.4f, 1.6f);
		}
	}

	private void clickRelease(ServerPlayer p, PetMenuState menu, int slot) {
		if (slot == CANCEL_SLOT) {
			menu.releasing = false;
			menu.chosen.clear();
			menu.armed = false;
			refresh(p);
			return;
		}
		if (slot == RELEASE_SLOT && !menu.chosen.isEmpty()) {
			if (!menu.armed || System.currentTimeMillis() - menu.armedAt < 700) {
				if (!menu.armed) {
					menu.armed = true;
					menu.armedAt = System.currentTimeMillis();
					Fx.playTo(p, "ui.button.click", 0.5f, 0.8f);
					refresh(p);
				}
				return; // a fast double-click doesn't count as the second click
			}
			List<String> names = new ArrayList<>();
			for (UUID id : List.copyOf(menu.chosen)) {
				Pet pet = mod.store().get(menu.owner, id);
				if (pet != null) {
					names.add(pet.name);
					mod.pets().release(p, pet);
				}
			}
			menu.releasing = false;
			menu.chosen.clear();
			menu.armed = false;
			refresh(p);
			if (!names.isEmpty()) {
				PetMsg.chat(p, PetMsg.text(String.join(", ", names) + " said goodbye. Take care!", ChatFormatting.GRAY));
				Fx.playTo(p, "block.azalea_leaves.break", 0.8f, 1f);
			}
			return;
		}
		if (slot >= 0 && slot < menu.pets.size()) {
			UUID id = menu.pets.get(slot);
			if (!menu.chosen.remove(id)) {
				menu.chosen.add(id);
			}
			menu.armed = false;
			Fx.playTo(p, "ui.button.click", 0.4f, 1.6f);
			refresh(p);
		}
	}

	/** Hands out a pet's bloom, unless the player already carries one. */
	public void giveBloom(ServerPlayer p, Pet pet) {
		if (p.getInventory().contains(item -> pet.id.equals(Blooms.petId(item)))) {
			PetMsg.bar(p, "You already carry " + pet.name + "'s Pet Bloom.", ChatFormatting.GRAY);
			return;
		}
		if (!Blooms.mayHandOut(p, "bloom:" + pet.id)) {
			PetMsg.bar(p, "You just took " + pet.name + "'s Pet Bloom. Try again in a minute.", ChatFormatting.GRAY);
			return;
		}
		Players.give(p, Blooms.create(pet));
		Fx.playTo(p, "entity.item.pickup", 0.6f, 1.2f);
	}

	// =====================================================================
	//  Storage and gear
	// =====================================================================

	/** Storage (or the Vex's gear) of a pet that is out. */
	public void openPetInventory(ServerPlayer p, Pet pet) {
		PetManager.Active a = mod.pets().activeFor(pet);
		boolean vex = pet.species == Species.VEX;
		if (a == null) {
			PetMsg.error(p, pet.name + " needs to be out to open its " + (vex ? "gear." : "storage."));
			return;
		}
		if (a.home != null && (a.entity.level() != p.level() || a.entity.distanceToSqr(p) > 8 * 8)) {
			PetMsg.error(p, "Go to " + pet.name + "'s post to open its " + (vex ? "gear." : "storage."));
			return;
		}
		if (vex) {
			p.openMenu(new SimpleMenuProvider((id, inv, player) -> new GearMenu(id, inv, pet, a.entity),
				Component.literal(pet.name + ": helmet · chest · legs · boots · weapon")));
			Fx.playTo(p, "item.armor.equip_generic", 0.6f, 1.2f);
		} else if (mod.happiness().sad(pet)) {
			PetMsg.error(p, pet.name + " is too sad to carry anything right now. Cheer it up first!");
		} else {
			int size = pet.storageSlots();
			if (size <= 0) {
				PetMsg.error(p, pet.name + " can't carry anything.");
				return;
			}
			int rows = Math.max(1, Math.min(6, size / 9));
			p.openMenu(new SimpleMenuProvider((id, inv, player) -> new StorageMenu(id, inv, pet, rows),
				Component.literal(pet.name + "'s Pack")));
			Fx.playTo(p, "block.barrel.open", 0.6f, 1.2f);
		}
	}

	/** Whether someone is looking at this Vex's gear right now. */
	public boolean gearOpen(Pet pet) {
		MinecraftServer server = Ticks.server();
		if (server == null) {
			return false;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof GearMenu g && g.pet == pet) {
				return true;
			}
		}
		return false;
	}

	/** Closes this pet's storage and gear screens (it's going away, or it's too sad to carry anything). */
	public void closeFor(Pet pet) {
		MinecraftServer server = Ticks.server();
		if (server == null) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if ((p.containerMenu instanceof StorageMenu s && s.pet == pet) || (p.containerMenu instanceof GearMenu g && g.pet == pet)) {
				p.closeContainer();
			}
		}
	}

	/** On shutdown: every screen of ours closes. */
	void closeAll() {
		MinecraftServer server = Ticks.server();
		if (server == null) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof StorageMenu || p.containerMenu instanceof GearMenu
				|| (ServerMenu.current(p) != null && ServerMenu.current(p).key() instanceof PetMenuState)) {
				p.closeContainer();
			}
		}
	}

	/** Puts an item into a pet's storage (its open slots only). Returns what didn't fit (empty if it all did). */
	public ItemStack addToStorage(Pet pet, ItemStack item) {
		Container c = pet.storage;
		int slots = Math.min(pet.storageSlots(), c.getContainerSize());
		ItemStack left = item.copy();
		for (int i = 0; i < slots && !left.isEmpty(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, left) && s.getCount() < s.getMaxStackSize()) {
				int add = Math.min(left.getCount(), s.getMaxStackSize() - s.getCount());
				s.grow(add);
				left.shrink(add);
			}
		}
		for (int i = 0; i < slots && !left.isEmpty(); i++) {
			if (c.getItem(i).isEmpty()) {
				int put = Math.min(left.getCount(), left.getMaxStackSize());
				c.setItem(i, left.copyWithCount(put));
				left.shrink(put);
			}
		}
		if (left.getCount() != item.getCount()) {
			c.setChanged();
			mod.store().changed(pet.owner);
		}
		return left;
	}

	/** Whether at least one of this item fits in the pet's storage. */
	public boolean hasRoom(Pet pet, ItemStack item) {
		Container c = pet.storage;
		int slots = Math.min(pet.storageSlots(), c.getContainerSize());
		for (int i = 0; i < slots; i++) {
			ItemStack s = c.getItem(i);
			if (s.isEmpty() || (ItemStack.isSameItemSameComponents(s, item) && s.getCount() < s.getMaxStackSize())) {
				return true;
			}
		}
		return false;
	}
}
