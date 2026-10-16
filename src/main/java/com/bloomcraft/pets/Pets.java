package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.item.Statues;
import com.bloomcraft.core.ConfigFiles;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Nbt;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.Ticks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The pets part of the mod (the BloomPets plugin): feed an animal until it bonds with you, then summon it with its
 * Pet Bloom, ride it, store things in it and level it up to 10. One pet out at a time. Config: pets.yml.
 */
public final class Pets {

	/** Saved data on a pet mob: its pet id. */
	public static final String PET = "pet";
	/** Saved data on a pet mob: its owner's UUID. */
	public static final String PET_OWNER = "pet_owner";
	/** Saved data on a wild mob being tamed: "player uuid:feedings". */
	public static final String BOND = "pet_bond";
	/** Saved data on a player: the pet items they had when they died (given back on respawn). */
	private static final String KEPT = "kept_pet_items";

	/** A pet's owner, also known to the game clients (so a click on someone's pet goes to the server as one). */
	public static final AttachmentType<UUID> OWNER = AttachmentRegistry.create(BloomCraft.id("pet_owner"),
		b -> b.syncWith(UUIDUtil.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static @Nullable Pets instance;

	private PetSettings settings;
	private final PetStore store = new PetStore();
	private final PetManager pets = new PetManager(this);
	private final RideController rides = new RideController(this);
	private final Bonuses bonuses = new Bonuses(this);
	private final PetMenus menus = new PetMenus(this);
	private final PetSkills skills = new PetSkills(this);
	private final Happiness happiness = new Happiness(this);
	private final PetEvents events = new PetEvents(this);

	private Pets() {
		settings = new PetSettings(ConfigFiles.load("pets.yml"));
	}

	public static Pets get() {
		return instance;
	}

	public static void init() {
		instance = new Pets();
		PetItems.init();
		instance.events.register();
	}

	/** (Re)reads config/bloomcraft/pets.yml. */
	public void load() {
		settings = new PetSettings(ConfigFiles.load("pets.yml"));
	}

	public void starting(MinecraftServer server) {
		load();
		store.start(server);
	}

	public void stopping(MinecraftServer server) {
		menus.closeAll(); // open storage and gear screens save first
		rides.shutdown();
		pets.shutdown(); // every pet goes back into its bloom, so none are lost or duplicated
		store.stop();
	}

	/** The world is saving: so are the pets. */
	public void saving() {
		if (store.ready()) {
			store.flush();
		}
	}

	public void tick(MinecraftServer server) {
		if (!settings.enabled || !store.ready()) {
			return;
		}
		int now = Ticks.now();
		pets.tick();
		rides.tick();
		if (now % 4 == 0) {
			bonuses.tick();
		}
		if (now % 20 == 0) {
			happiness.tick();
			skills.tick();
		}
		if (now % 600 == 300) {
			store.flush();
		}
	}

	public void later(int ticks, Runnable task) {
		com.bloomcraft.core.Scheduler.later(ticks, task);
	}

	public boolean enabled() {
		return settings.enabled;
	}

	public PetSettings settings() {
		return settings;
	}

	public PetStore store() {
		return store;
	}

	public PetManager pets() {
		return pets;
	}

	public RideController rides() {
		return rides;
	}

	public Bonuses bonuses() {
		return bonuses;
	}

	public PetMenus menus() {
		return menus;
	}

	public PetSkills skills() {
		return skills;
	}

	public Happiness happiness() {
		return happiness;
	}

	public PetEvents events() {
		return events;
	}

	// =====================================================================
	//  Questions other parts (and mixins) ask
	// =====================================================================

	/** A pet (out with its owner or at its post). */
	public static boolean isPet(@Nullable Entity entity) {
		return entity != null && Data.has(entity, PET);
	}

	/** A pet the pets part is steering right now (its own AI is off). */
	public static boolean steered(Mob mob) {
		return instance != null && instance.pets.of(mob) != null;
	}

	/** Bosses, boss minions and statues can't be tamed. */
	public static boolean isBossThing(Entity entity) {
		return BossManager.isBoss(entity) || BossManager.isMinion(entity) || isStatue(entity);
	}

	/** A Nemesis statue: a frozen mob on display, never something to attack. */
	public static boolean isStatue(Entity entity) {
		return Statues.isStatue(entity);
	}

	/** False to stop {@code mob} from going after this player (pet bonuses: Fox, Cat, Ocelot). */
	public static boolean mayTarget(Mob mob, ServerPlayer player) {
		return instance == null || !instance.enabled() || !instance.store.ready() || instance.bonuses.mayTarget(mob, player);
	}

	/** False if this effect can't touch the entity (Polar Bears and their owners shrug off Slowness). */
	public static boolean canBeAffected(net.minecraft.world.entity.LivingEntity entity,
										 net.minecraft.world.effect.MobEffectInstance effect) {
		if (instance == null || !instance.enabled() || !instance.store.ready()) {
			return true;
		}
		if (entity instanceof ServerPlayer p) {
			return !instance.bonuses.blocksEffect(p, effect);
		}
		PetManager.Active a = instance.pets.of(entity);
		return a == null || !instance.skills.blocksEffect(a, effect);
	}

	/** True while the owner is being seated on their pet (the only way onto one). */
	public static boolean mounting() {
		return instance != null && instance.rides.mounting();
	}

	/** The player is dying: their blooms, toy and fetch stick are kept aside to give back when they respawn. */
	public static void keepPetItems(ServerPlayer player) {
		if (instance == null) {
			return;
		}
		Inventory inv = player.getInventory();
		ListTag kept = Data.of(player).getListOrEmpty(KEPT);
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			ItemStack item = inv.getItem(slot);
			UUID bloomOwner = Blooms.bloomOwner(item);
			boolean mine = Blooms.isToy(item) || Blooms.isFetchStick(item)
				|| (bloomOwner != null && bloomOwner.equals(player.getUUID()));
			if (!mine) {
				continue;
			}
			Tag tag = Nbt.item(item, player.registryAccess());
			if (tag != null) {
				kept.add(tag);
			}
			inv.setItem(slot, ItemStack.EMPTY);
		}
		if (!kept.isEmpty()) {
			Data.of(player).put(KEPT, kept);
		}
	}

	/** Gives back what {@link #keepPetItems} kept. */
	static void returnPetItems(ServerPlayer player) {
		CompoundTag data = Data.peek(player);
		if (data == null || !data.contains(KEPT)) {
			return;
		}
		ListTag kept = data.getListOrEmpty(KEPT);
		data.remove(KEPT);
		for (Tag tag : kept) {
			ItemStack item = Nbt.item(tag, player.registryAccess());
			if (!item.isEmpty()) {
				Players.give(player, item);
			}
		}
	}
}
