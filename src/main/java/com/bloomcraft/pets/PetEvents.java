package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Ticks;
import com.bloomcraft.skills.Skill;
import com.bloomcraft.skills.Skills;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything players do with pets and blooms: bonding, feeding, riding, opening storage, summoning with the bloom,
 * plus keeping pets safe (they faint instead of dying) and the life cycle (joining, leaving, dying, chunks unloading).
 */
public final class PetEvents {

	/** Sent by the game client when a player punches the air (or a block) with a Pet Bloom or the Pet Toy. */
	public record OpenPetsPayload() implements CustomPacketPayload {
		public static final Type<OpenPetsPayload> TYPE = new Type<>(BloomCraft.id("open_pets"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPetsPayload> CODEC = StreamCodec.unit(new OpenPetsPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A click that went to a pet may be followed by a "use item" from the same click; that one is ignored. */
	private static final int CLICK_GAP = 5;

	private final Pets mod;
	private final Timber timber;
	private final Map<UUID, Integer> lastClick = new HashMap<>();
	private final Map<UUID, Long> lastMenu = new HashMap<>();

	PetEvents(Pets mod) {
		this.mod = mod;
		this.timber = new Timber(mod);
	}

	private boolean on() {
		return mod.enabled() && mod.store().ready();
	}

	private boolean recentClick(Player p) {
		return Ticks.now() - lastClick.getOrDefault(p.getUUID(), -100) < CLICK_GAP;
	}

	/** True (and remembers the click) unless this player clicked a moment ago. */
	private boolean click(Player p) {
		if (recentClick(p)) {
			return false;
		}
		lastClick.put(p.getUUID(), Ticks.now());
		return true;
	}

	/** Milk the cow, brush the armadillo, dress (or undress) the wolf: those still work on your own pets. */
	private static boolean vanillaUse(Species species, ItemStack item) {
		return switch (species) {
			case COW -> item.is(Items.BUCKET);
			case ARMADILLO -> item.is(Items.BRUSH);
			case WOLF -> item.is(Items.WOLF_ARMOR) || item.is(Items.SHEARS);
			default -> false;
		};
	}

	void register() {
		PayloadTypeRegistry.serverboundPlay().register(OpenPetsPayload.TYPE, OpenPetsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenPetsPayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (on() && Blooms.isPetItem(p.getMainHandItem()) && !Blooms.isFetchStick(p.getMainHandItem())) {
				openMenu(p);
			}
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				return clientUse(player, hand, entity);
			}
			return player instanceof ServerPlayer p && on() ? use(p, hand, entity) : InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack held = player.getMainHandItem();
			if (!Blooms.isPetItem(held)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS; // the server decides (and nothing gets hurt)
			}
			if (player instanceof ServerPlayer p && on()) {
				punch(p, entity);
			}
			return InteractionResult.SUCCESS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer p && level instanceof ServerLevel server && on()) {
				mod.bonuses().onDig(p, state, pos);
				timber.chopped(p, server, pos, state);
			}
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> on() ? allowDeath(entity, source) : true);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (on()) {
				died(entity, source);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			PetManager.Active a = mod.pets().of(entity);
			if (a == null) {
				return;
			}
			Entity.RemovalReason reason = entity.getRemovalReason();
			if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
				mod.pets().unloaded(a);
			} else if (reason == Entity.RemovalReason.KILLED) {
				mod.pets().died(a);
			} else {
				mod.pets().lost(a);
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> left(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> mod.later(1, () -> {
			if (!newPlayer.hasDisconnected()) {
				Pets.returnPetItems(newPlayer);
			}
		}));
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer p && on()) {
				mod.bonuses().wentToBed(p);
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer p && on()) {
				mod.bonuses().wokeUp(p);
			}
		});

		Skills.get().onLevelUp((player, skill, from, to) -> {
			if (skill == Skill.TAMING && on()) {
				mod.store().setTamingLevel(player.getUUID(), to);
				mod.skills().announceUnlocks(player, from, to);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			PetsCommand.register(dispatcher, mod));
	}

	// =====================================================================
	//  Right-clicking animals and pets
	// =====================================================================

	/** On the game client: a click on a pet always goes to the server as a click on it (never as "use item"). */
	private static InteractionResult clientUse(Player player, InteractionHand hand, Entity entity) {
		UUID owner = entity.getAttached(Pets.OWNER);
		if (owner == null) {
			return InteractionResult.PASS;
		}
		Species species = Species.of(entity.getType());
		if (owner.equals(player.getUUID()) && species != null && vanillaUse(species, player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	private InteractionResult use(ServerPlayer p, InteractionHand hand, Entity clicked) {
		PetManager pets = mod.pets();
		PetManager.Active a = pets.of(clicked);
		if (a != null) {
			ItemStack held = p.getItemInHand(hand);
			if (a.owner.equals(p.getUUID()) && vanillaUse(a.pet.species, held)) {
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.SUCCESS;
			}
			if (!a.owner.equals(p.getUUID())) {
				ServerPlayer owner = PetManager.player(a.owner);
				PetMsg.bar(p, "That's " + (owner == null ? "someone" : owner.getName().getString()) + "'s pet, "
					+ a.pet.name + ".", ChatFormatting.GRAY);
				return InteractionResult.SUCCESS;
			}
			if (!click(p)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack item = p.getMainHandItem();
			if (a.pet.species.likes(item)) {
				pets.feed(p, a, InteractionHand.MAIN_HAND);
			} else if (p.isShiftKeyDown() && item.isEmpty()) {
				mod.happiness().pet(p, a); // a pat on the head
			} else if (p.isShiftKeyDown()) {
				mod.menus().openPetInventory(p, a.pet); // with anything in hand (the Pet Toy too): its bag
			} else if (Blooms.isPetItem(item) && a.home != null) {
				PetMsg.bar(p, a.pet.name + " is keeping its post. Sneak + punch it with your Pet Toy to call it back.",
					ChatFormatting.GRAY);
			} else if (Blooms.isPetItem(item) && !Blooms.isFetchStick(item)) {
				mod.rides().tryMount(p, a);
			} else if (item.is(Items.NAME_TAG) && item.get(DataComponents.CUSTOM_NAME) != null) {
				if (pets.rename(p, a.pet, item.get(DataComponents.CUSTOM_NAME).getString())) {
					Players.consume(p, InteractionHand.MAIN_HAND);
				}
			} else {
				// swap to another pet (and a lost Pet Toy comes back)
				if (Blooms.giveToyIfMissing(p, false)) {
					PetMsg.bar(p, "Here's a new Pet Toy!", PetMsg.PINK);
				}
				openMenu(p);
			}
			return InteractionResult.SUCCESS;
		}
		if (Pets.isPet(clicked)) {
			clicked.discard(); // a leftover pet nobody is looking after
			return InteractionResult.SUCCESS;
		}
		// sneak + feed a wild animal its favourite food to bond with it (plain feeding still breeds as usual)
		if (hand != InteractionHand.MAIN_HAND || !p.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		Species species = Species.of(clicked.getType());
		if (species == null || !(clicked instanceof Mob mob) || !mob.isAlive() || !species.likes(p.getMainHandItem())) {
			return InteractionResult.PASS;
		}
		if (click(p)) {
			pets.bond(p, mob, species, InteractionHand.MAIN_HAND);
		}
		return InteractionResult.SUCCESS;
	}

	// =====================================================================
	//  The Pet Bloom, Pet Toy and Fetch Stick
	// =====================================================================

	/** Right-click with a bloom: summon or dismiss its pet. */
	void useBloom(ServerPlayer p, InteractionHand hand) {
		if (!on() || recentClick(p) || !click(p)) {
			return;
		}
		ItemStack item = p.getItemInHand(hand);
		UUID petId = Blooms.petId(item);
		UUID owner = Blooms.bloomOwner(item);
		if (petId == null || !p.getUUID().equals(owner)) {
			PetMsg.error(p, "This Pet Bloom belongs to someone else.");
			return;
		}
		Pet pet = mod.store().get(p.getUUID(), petId);
		if (pet == null) {
			PetMsg.error(p, "That pet is gone, and its bloom wilts away.");
			p.setItemInHand(hand, ItemStack.EMPTY);
			return;
		}
		mod.pets().toggle(p, pet);
	}

	/** Right-click with the Pet Toy: call your last pet or send it home. */
	void useToy(ServerPlayer p, InteractionHand hand) {
		if (on() && !recentClick(p) && click(p)) {
			mod.pets().toggleLast(p);
		}
	}

	/** Right-click with the Fetch Stick: throw it. */
	void useFetchStick(ServerPlayer p, InteractionHand hand) {
		if (!on() || recentClick(p) || !click(p)) {
			return;
		}
		if (mod.pets().throwFetch(p, p.getItemInHand(hand))) {
			p.setItemInHand(hand, ItemStack.EMPTY);
		}
	}

	/** Punching with a pet item: your own pet while sneaking keeps a post there (or comes back); else the menu. */
	private void punch(ServerPlayer p, Entity entity) {
		PetManager.Active a = mod.pets().of(entity);
		if (p.isShiftKeyDown() && a != null && a.owner.equals(p.getUUID())) {
			if (!click(p)) {
				return;
			}
			if (a.home == null) {
				mod.pets().station(p, a);
			} else {
				mod.pets().summon(p, a.pet, true);
			}
			return;
		}
		if (!Blooms.isFetchStick(p.getMainHandItem())) {
			openMenu(p);
		}
	}

	void openMenu(ServerPlayer p) {
		long now = System.currentTimeMillis();
		if (now - lastMenu.getOrDefault(p.getUUID(), 0L) < 300) {
			return;
		}
		lastMenu.put(p.getUUID(), now);
		mod.menus().openPets(p);
	}

	// =====================================================================
	//  Deaths
	// =====================================================================

	/** Pets never die (they faint), and an Iron Golem may step in for its owner. False: the entity lives. */
	private boolean allowDeath(LivingEntity entity, DamageSource source) {
		PetManager.Active a = mod.pets().of(entity);
		if (a != null) {
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true; // the void, /kill: it rests like after fainting (see died)
			}
			entity.setHealth(1);
			mod.pets().faint(a);
			return false;
		}
		if (entity instanceof ServerPlayer p) {
			return !mod.skills().guardian(p, source);
		}
		return true;
	}

	private void died(LivingEntity dead, DamageSource source) {
		PetManager.Active pet = mod.pets().of(dead);
		if (pet != null) {
			mod.pets().died(pet);
			return;
		}
		if (dead instanceof ServerPlayer owner) {
			PetManager.Active mine = mod.pets().active(owner.getUUID());
			if (mine != null) {
				mod.pets().stash(mine); // your pet goes back into its bloom when you die
			}
			return;
		}
		if (dead instanceof Player || Pets.isPet(dead)) {
			return;
		}
		PetSettings s = mod.settings();
		PetManager.Active byPet = mod.pets().of(source.getEntity());
		if (byPet != null) {
			if (mod.pets().earns(byPet, PetManager.player(byPet.owner))) { // a guard only with you nearby
				mod.pets().addXp(byPet.pet, s.xpPetKill);
			}
			mod.skills().killed(byPet);
			return;
		}
		if (source.getEntity() instanceof ServerPlayer killer && dead instanceof Enemy) {
			PetManager.Active mine = mod.pets().active(killer.getUUID());
			if (mine != null && !mine.fainting) {
				mod.pets().addXp(mine.pet, s.xpOwnerKill);
			}
		}
	}

	// =====================================================================
	//  Joining and leaving
	// =====================================================================

	/** Quest Book: catch up on what this player's pets already did. */
	private void joined(ServerPlayer p) {
		if (!on()) {
			return;
		}
		mod.store().setTamingLevel(p.getUUID(), Skills.get().level(p, Skill.TAMING));
		for (Pet pet : mod.store().pets(p.getUUID())) {
			QuestFlags.markOnce(p, "pet_adopted");
			if (pet.species.combat()) {
				QuestFlags.markOnce(p, "pet_combat_owned");
			}
			if (pet.level >= 2) {
				QuestFlags.markOnce(p, "pet_level_up");
			}
			if (pet.maxed()) {
				QuestFlags.markOnce(p, "pet_max_level");
			}
			if (pet.stationed()) {
				QuestFlags.markOnce(p, "pet_stationed");
			}
		}
	}

	private void left(ServerPlayer p) {
		if (!mod.store().ready()) {
			return;
		}
		mod.rides().forget(p.getUUID());
		PetManager.Active a = mod.pets().active(p.getUUID());
		if (a != null) {
			mod.pets().stash(a);
		}
		mod.bonuses().forgetPlayer(p.getUUID());
		mod.store().setTamingLevel(p.getUUID(), Skills.get().level(p, Skill.TAMING));
		mod.store().unload(p.getUUID());
		lastClick.remove(p.getUUID());
		lastMenu.remove(p.getUUID());
	}
}
