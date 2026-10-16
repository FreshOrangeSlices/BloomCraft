package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Nbt;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Spawns;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import com.bloomcraft.mixin.AbstractHorseAccess;
import com.bloomcraft.mixin.AgeableMobInvoker;
import com.bloomcraft.mixin.FoxInvoker;
import com.bloomcraft.mixin.VexAccess;
import com.bloomcraft.skills.Skill;
import com.bloomcraft.skills.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pets that are out in the world: summoning and putting them away, bonding with wild animals, following, fighting,
 * fainting and bond XP.
 *
 * <p>A pet's own AI is switched off (see the Mob mixin) and it is steered from here every tick, so every species
 * behaves the same way: it follows you, never wanders off, never sits down, rams you or rolls up. Ground pets walk
 * along a path found by the game's pathfinder; Bees, Allays, Bats and the Vex fly beside your shoulder.</p>
 */
public final class PetManager {

	/** A pet that is out in the world right now. */
	public static final class Active {
		public final Pet pet;
		public final UUID owner;
		public final Mob entity;
		@Nullable LivingEntity target;
		long lastAttack;
		long lastFeed;
		boolean fainting;
		int ticks;
		/** Where a stationed pet keeps its post (null: it follows its owner). */
		public @Nullable Vec3 home;
		/** A max-level pet shrugs off one fainting blow each time it comes out. */
		boolean secondWindUsed;
		// fetch: the stick it's chasing, and whether it's bringing it back
		@Nullable ItemEntity fetchItem;
		boolean fetchReturning;
		long fetchStarted;
		// bond XP for walking together
		@Nullable Vec3 walkLast;
		@Nullable Level walkLevel;
		double walked;
		/** The pet stays put until then (the Sniffer digging something up). */
		public long pausedUntil;
		// flyers: whether the owner is standing still
		@Nullable Vec3 ownerLast;
		int ownerStill;
		// walking
		@Nullable List<Vec3> path;
		int pathIndex;
		int nextPathTick;
		@Nullable Vec3 pathGoal;
		@Nullable Vec3 lastPos;
		int stuck;
		/** Hits landed this outing (the Goat's every-third-hit Momentum). */
		int hits;
		/** The last mob it hit (the Wolf's Pounce: its first hit on a new target hits harder). */
		@Nullable UUID lastHit;
		/** The player this pet fights, and when a hit between them and its owner last went through. */
		@Nullable UUID pvpFoe;
		long pvpAt;
		/** A stationed pet strolls around its post: where it's headed, and when it picks a new spot. */
		@Nullable Vec3 roamSpot;
		int roamNext;

		Active(Pet pet, UUID owner, Mob entity) {
			this.pet = pet;
			this.owner = owner;
			this.entity = entity;
		}

		public ServerLevel level() {
			return (ServerLevel) entity.level();
		}

		public @Nullable LivingEntity target() {
			return target;
		}
	}

	private static final double TELEPORT_DISTANCE = 16;
	private static final double RECALL_DISTANCE = 40;
	private static final double TARGET_RANGE = 24;
	private static final long ATTACK_COOLDOWN = 1000;
	private static final long FEED_XP_COOLDOWN = 30_000;
	/**
	 * A pet keeps fighting a player only this long after the last hit between them and its owner went through, so
	 * a player who escapes into a protected area (where the owner can't hit them) is left alone.
	 */
	private static final long PVP_MEMORY = 6000;

	public static final Identifier MOD_LEVEL = BloomCraft.id("pet_level");
	public static final Identifier MOD_STEP = BloomCraft.id("pet_step");

	private final Pets mod;
	private final Map<UUID, Active> byOwner = new HashMap<>();
	/** Stationed pets that are in the world right now, by pet id. */
	private final Map<UUID, Active> posted = new HashMap<>();
	private final Map<UUID, Active> byEntity = new HashMap<>();
	/** Stationed pets that couldn't be placed (the spawn was blocked): when to try again. */
	private final Map<UUID, Long> postRetry = new HashMap<>();

	PetManager(Pets mod) {
		this.mod = mod;
	}

	private PetSettings s() {
		return mod.settings();
	}

	static @Nullable ServerPlayer player(UUID id) {
		MinecraftServer server = Ticks.server();
		return server == null ? null : server.getPlayerList().getPlayer(id);
	}

	/** Every pet goes back into its bloom (on shutdown), so none are lost or duplicated. */
	public void shutdown() {
		for (Active a : List.copyOf(byOwner.values())) {
			stash(a);
		}
		for (Active a : List.copyOf(posted.values())) {
			stash(a);
		}
		byOwner.clear();
		posted.clear();
		byEntity.clear();
		postRetry.clear();
	}

	public @Nullable Active active(UUID owner) {
		return byOwner.get(owner);
	}

	public @Nullable Active of(@Nullable Entity entity) {
		return entity == null ? null : byEntity.get(entity.getUUID());
	}

	/** Pets out with their owners. */
	public Collection<Active> all() {
		return List.copyOf(byOwner.values());
	}

	/** Stationed pets that are in the world right now. */
	public Collection<Active> postedPets() {
		return List.copyOf(posted.values());
	}

	/** This pet in the world, whether it's with its owner or at its station. */
	public @Nullable Active activeFor(Pet pet) {
		Active a = byOwner.get(pet.owner);
		return a != null && a.pet == pet ? a : posted.get(pet.id);
	}

	public boolean isOut(Pet pet) {
		Active a = byOwner.get(pet.owner);
		return a != null && a.pet == pet;
	}

	public boolean fainting(Active a) {
		return a.fainting;
	}

	private static boolean valid(Entity e) {
		return e.isAlive() && !e.isRemoved();
	}

	// =====================================================================
	//  Summon / dismiss
	// =====================================================================

	public void toggle(ServerPlayer owner, Pet pet) {
		if (isOut(pet)) {
			dismiss(owner, true);
		} else {
			summon(owner, pet, true);
		}
	}

	/** The Pet Toy: sends your pet home, or calls the one you had out last (or opens the menu to pick one). */
	public void toggleLast(ServerPlayer owner) {
		if (byOwner.containsKey(owner.getUUID())) {
			dismiss(owner, true);
			return;
		}
		Pet last = mod.store().last(owner.getUUID());
		if (last != null && !last.stationed()) { // a pet at its post stays there; pick it in the menu to call it
			if (!summon(owner, last, true)) {
				mod.menus().openPets(owner); // resting or turned off: pick someone else
			}
		} else if (mod.store().count(owner.getUUID()) == 0) {
			PetMsg.bar(owner, "No pets yet! Sneak + feed an animal its favourite food to make a friend.", PetMsg.PINK);
		} else {
			mod.menus().openPets(owner);
		}
	}

	public boolean summon(ServerPlayer owner, Pet pet, boolean announce) {
		long now = System.currentTimeMillis();
		if (!s().species.contains(pet.species)) {
			PetMsg.error(owner, pet.species.displayName() + " pets are turned off in this world.");
			return false;
		}
		if (pet.resting(now)) {
			PetMsg.error(owner, pet.name + " is still resting (" + ((pet.restingUntil - now + 999) / 1000) + "s).");
			return false;
		}
		if (!owner.isAlive() || owner.isSpectator()) {
			return false;
		}
		if (pet.species == Species.VEX && owner.level().getDifficulty() == Difficulty.PEACEFUL) {
			PetMsg.error(owner, "Vexes can't come out in Peaceful.");
			return false;
		}
		Active current = byOwner.get(owner.getUUID());
		if (current != null) {
			if (current.pet == pet) {
				return true;
			}
			stash(current);
		}
		Pet.Station post = pet.station;
		if (post != null) {
			// called back from its post
			Active atPost = posted.get(pet.id);
			pet.station = null;
			if (atPost != null) {
				stash(atPost);
			}
		}
		Mob mob = spawn(pet, owner.level(), spawnSpot(owner, pet.species), owner.getYRot());
		if (mob == null) {
			pet.station = post; // couldn't come: it keeps its post
			PetMsg.error(owner, pet.name + " can't come out here.");
			return false;
		}
		mod.bonuses().forget(pet.id);
		track(owner, pet, mob);
		mod.store().setLast(pet);
		if (announce) {
			PetMsg.bar(owner, Text.colored(pet.name + " is here!", pet.species.category().color()));
			Vec3 at = Fx.center(mob);
			Fx.particle(owner.level(), ParticleTypes.CHERRY_LEAVES, at, 18, mob.getBbWidth() / 2 + 0.2,
				mob.getBbHeight() / 2, mob.getBbWidth() / 2 + 0.2, 0);
			Fx.neutral(mob, "block.amethyst_block.chime", 0.8f, 1.4f);
			if (mod.happiness().sad(pet)) {
				PetMsg.chat(owner, Component.empty().append(PetMsg.text(pet.name + " looks a little down. ", ChatFormatting.GOLD))
					.append(PetMsg.text("Feed it, pet it (sneak + right-click with an empty hand) or play fetch "
						+ "to cheer it up; until then its bonus, skills and storage rest.", ChatFormatting.GRAY)));
			}
		}
		mod.menus().refresh(owner);
		return true;
	}

	public void dismiss(ServerPlayer owner, boolean announce) {
		Active a = byOwner.get(owner.getUUID());
		if (a == null) {
			if (announce) {
				PetMsg.error(owner, "None of your pets is out.");
			}
			return;
		}
		Vec3 at = Fx.center(a.entity);
		ServerLevel level = a.level();
		stash(a);
		if (announce) {
			PetMsg.bar(owner, a.pet.name + " went back into its bloom.", ChatFormatting.GRAY);
			Fx.particle(level, ParticleTypes.CHERRY_LEAVES, at, 12, 0.3, 0.3, 0.3, 0);
			Fx.play(level, at, "block.azalea_leaves.break", 0.8f, 1.2f);
		}
		mod.menus().refresh(owner);
	}

	/** Puts a pet back into its bloom: the whole mob is saved and removed from the world. */
	public void stash(Active a) {
		mod.menus().closeFor(a.pet); // a storage or gear screen saves first
		mod.rides().eject(a);
		endFetch(a, false);
		untrack(a);
		mod.happiness().wentIn(a);
		Mob mob = a.entity;
		if (!mob.isRemoved()) {
			if (!a.fainting && mob.isAlive()) {
				a.pet.health = mob.getHealth() / maxHealth(mob);
			}
			mob.clearFire();
			mob.setTicksFrozen(0);
			mob.resetFallDistance();
			mob.setTarget(null);
			if (mob.isAlive()) {
				snapshot(a);
			}
			mob.discard();
		}
		mod.store().changed(a.owner);
	}

	/** The pet's chunk is unloading under it: save it and let it go (pets are never saved with the world). */
	public void unloaded(Active a) {
		mod.menus().closeFor(a.pet);
		mod.rides().eject(a);
		endFetch(a, false);
		untrack(a);
		mod.happiness().wentIn(a);
		if (a.entity.isAlive()) {
			if (!a.fainting) {
				a.pet.health = a.entity.getHealth() / maxHealth(a.entity);
			}
			snapshot(a);
		}
		mod.store().changed(a.owner);
	}

	/** The pet entity disappeared without us (removed by a command, another mod). Its last saved state is kept. */
	public void lost(Active a) {
		mod.menus().closeFor(a.pet);
		mod.rides().eject(a);
		endFetch(a, false);
		untrack(a);
		mod.happiness().wentIn(a);
		mod.store().changed(a.owner);
	}

	private void snapshot(Active a) {
		try {
			a.pet.snapshot = Nbt.entity(a.entity);
		} catch (RuntimeException ex) {
			BloomCraft.LOG.warn("Couldn't save {} ({})", a.pet.name, a.pet.species.id(), ex);
		}
	}

	private void track(ServerPlayer owner, Pet pet, Mob mob) {
		Active a = new Active(pet, owner.getUUID(), mob);
		byOwner.put(a.owner, a);
		byEntity.put(mob.getUUID(), a);
		mod.happiness().cameOut(pet);
		prepare(a, owner);
		mod.bonuses().apply(owner, a);
	}

	private void untrack(Active a) {
		byEntity.remove(a.entity.getUUID(), a);
		if (a.home != null) {
			posted.remove(a.pet.id, a);
			return;
		}
		if (byOwner.remove(a.owner, a)) {
			ServerPlayer owner = player(a.owner);
			if (owner != null) {
				mod.bonuses().remove(owner);
			}
		}
	}

	private boolean tracked(Active a) {
		return byEntity.get(a.entity.getUUID()) == a;
	}

	private void recall(Active a, ServerPlayer owner) {
		Pet pet = a.pet;
		stash(a);
		if (!summon(owner, pet, false)) {
			PetMsg.error(owner, pet.name + " couldn't follow you here and went back into its bloom.");
			mod.menus().refresh(owner);
		}
	}

	/** Brings the pet into the world at this spot: as it was saved, or a new one of its species. */
	private @Nullable Mob spawn(Pet pet, ServerLevel level, Vec3 at, float yaw) {
		Mob mob = create(pet, level);
		if (mob == null) {
			return null;
		}
		mob.setUUID(UUID.randomUUID()); // never clashes with an old copy
		mob.snapTo(at.x, at.y, at.z, yaw, 0);
		mob.setDeltaMovement(Vec3.ZERO);
		mark(mob, pet);
		if (pet.snapshot == null) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.COMMAND, null);
		}
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		return valid(mob) ? mob : null;
	}

	/** The pet as saved in its bloom (or a fresh one), not yet in the world. */
	private @Nullable Mob create(Pet pet, ServerLevel level) {
		if (pet.snapshot != null) {
			try {
				Entity e = pet.species.type().create(level, EntitySpawnReason.LOAD);
				if (e instanceof Mob mob) {
					Nbt.load(mob, pet.snapshot);
					return mob;
				}
			} catch (RuntimeException ex) {
				BloomCraft.LOG.warn("Couldn't restore {}; it comes back as a new {}", pet.name, pet.species.displayName(), ex);
			}
		}
		Entity fresh = pet.species.type().create(level, EntitySpawnReason.COMMAND);
		return fresh instanceof Mob mob ? mob : null;
	}

	/** The pet as saved in its bloom, rebuilt but not placed in the world (to read or change what it wears). */
	@Nullable LivingEntity restore(Pet pet, ServerLevel level) {
		if (pet.snapshot == null) {
			return null;
		}
		try {
			Entity e = pet.species.type().create(level, EntitySpawnReason.LOAD);
			if (e instanceof LivingEntity le) {
				Nbt.load(le, pet.snapshot);
				return le;
			}
		} catch (RuntimeException ex) {
			BloomCraft.LOG.warn("Couldn't read the saved {}", pet.name, ex);
		}
		return null;
	}

	/** Everything an entity wears or holds (a wolf's armor, a llama's carpet, the Vex's sword...). */
	static List<ItemStack> worn(LivingEntity e) {
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack item = e.getItemBySlot(slot);
			if (!item.isEmpty()) {
				items.add(item.copy());
			}
		}
		return items;
	}

	/**
	 * The Vex is gone while its gear screen was open: puts the gear from the screen into its saved copy (so it
	 * neither vanishes nor exists twice). False if there is no saved copy to put it in.
	 */
	boolean writeGear(Pet pet, ServerLevel level, Map<EquipmentSlot, ItemStack> gear) {
		LivingEntity body = restore(pet, level);
		if (body == null) {
			return false;
		}
		for (Map.Entry<EquipmentSlot, ItemStack> e : gear.entrySet()) {
			body.setItemSlot(e.getKey(), e.getValue());
		}
		try {
			pet.snapshot = Nbt.entity(body);
			mod.store().changed(pet.owner);
			return true;
		} catch (RuntimeException ex) {
			BloomCraft.LOG.warn("Couldn't save {}'s gear", pet.name, ex);
			return false;
		}
	}

	private static void mark(Entity e, Pet pet) {
		Data.of(e).putString(Pets.PET, pet.id.toString());
		Data.of(e).putString(Pets.PET_OWNER, pet.owner.toString());
		Data.flag(e, Spawns.TRANSIENT, true); // never saved with the world: it lives in its bloom
		e.setAttached(Pets.OWNER, pet.owner);
	}

	/** Behind the owner if there's room, otherwise right where they stand. Flyers appear beside their head. */
	private static Vec3 spawnSpot(ServerPlayer owner, Species species) {
		Vec3 base = owner.position();
		double yaw = Math.toRadians(owner.getYRot());
		if (species.flies()) {
			// beside your head, a little in front, where you can see (and reach) it
			return owner.getEyePosition().add(-Math.cos(yaw) * 0.9 - Math.sin(yaw) * 0.6, 0.1,
				-Math.sin(yaw) * 0.9 + Math.cos(yaw) * 0.6);
		}
		Vec3 behind = base.add(Math.sin(yaw) * 1.5, 0, -Math.cos(yaw) * 1.5);
		ServerLevel level = owner.level();
		BlockPos feet = BlockPos.containing(behind);
		boolean room = passable(level, feet) && !liquid(level, feet) && passable(level, feet.above())
			&& !passable(level, feet.below());
		return room ? behind : base;
	}

	static boolean passable(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	static boolean liquid(Level level, BlockPos pos) {
		return !level.getFluidState(pos).isEmpty();
	}

	/** Turns a freshly summoned (or freshly bonded) mob into a calm pet. */
	private void prepare(Active a, @Nullable ServerPlayer owner) {
		Mob m = a.entity;
		Pet pet = a.pet;
		mark(m, pet);
		m.setPersistenceRequired();
		m.setCanPickUpLoot(false);
		m.setTarget(null);
		m.getNavigation().stop();
		m.setNoGravity(pet.species.flies());
		rename(m, pet);
		if (m instanceof AgeableMob ageable && ageable.isBaby()) {
			((AgeableMobInvoker) ageable).bloomcraft$setAgeLocked(true);
		}
		if (m instanceof TamableAnimal tame) {
			tame.setTame(true, false);
			tame.setOwnerReference(EntityReference.of(pet.owner));
			tame.setOrderedToSit(false);
			tame.setInSittingPose(false);
		}
		if (m instanceof AbstractHorse horse) {
			horse.setTamed(true);
			if (owner != null) {
				horse.setOwner(owner);
			}
		}
		switch (m) {
			case Wolf wolf -> {
				wolf.setPersistentAngerTarget(null);
				wolf.setPersistentAngerEndTime(0);
			}
			case IronGolem golem -> golem.setPlayerCreated(true);
			case Fox fox -> {
				((FoxInvoker) fox).bloomcraft$setSleeping(false);
				fox.setIsCrouching(false);
				fox.setSitting(false);
			}
			case Bee bee -> {
				bee.setPersistentAngerTarget(null);
				bee.setPersistentAngerEndTime(0);
				bee.setStayOutOfHiveCountdown(Integer.MAX_VALUE);
			}
			case Vex vex -> {
				((VexAccess) vex).bloomcraft$setHasLimitedLife(false);
				vex.setOwner((Mob) null);
				vex.setIsCharging(false);
				for (EquipmentSlot slot : EquipmentSlot.VALUES) {
					vex.setDropChance(slot, 0);
				}
			}
			case PolarBear bear -> bear.setStanding(false);
			case Armadillo armadillo -> {
				if (armadillo.getState() != Armadillo.ArmadilloState.IDLE) {
					armadillo.rollOut();
				}
			}
			case Bat bat -> bat.setResting(false);
			default -> {
			}
		}
		applyStats(a);
		if (pet.health > 0) {
			m.setHealth((float) Math.max(1, Math.min(maxHealth(m), pet.health * maxHealth(m))));
		}
	}

	/** The name above the pet: gold with a star at max level, its category's colour otherwise. */
	private static void rename(Mob m, Pet pet) {
		m.setCustomName(pet.maxed() ? Text.colored("✦ " + pet.name, 0xFFAA00)
			: Text.colored(pet.name, pet.species.category().color()));
		m.setCustomNameVisible(false); // shows when you look at it
	}

	/** Level-based stats: a little more health each level, and the category's step height. */
	void applyStats(Active a) {
		Mob m = a.entity;
		var health = m.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			double base = health.getBaseValue();
			Attrs.setTransient(m, Attributes.MAX_HEALTH, MOD_LEVEL, targetHealth(a.pet.species, base, a.pet.level) - base,
				AttributeModifier.Operation.ADD_VALUE);
		}
		var step = m.getAttribute(Attributes.STEP_HEIGHT);
		if (step != null) {
			Attrs.setTransient(m, Attributes.STEP_HEIGHT, MOD_STEP,
				Math.max(0, a.pet.species.category().stepHeight() - step.getBaseValue()), AttributeModifier.Operation.ADD_VALUE);
		}
		if (m.getHealth() > maxHealth(m)) {
			m.setHealth((float) maxHealth(m));
		}
	}

	/**
	 * Max health at a level: never below a floor that rises from 10 to 20 (combat pets: 20 to 40) by level 10,
	 * and sturdier animals (an Iron Golem, a Camel) keep their own health plus 2% a level.
	 */
	static double targetHealth(Species species, double base, int level) {
		double t = (level - 1) / (double) (Pet.MAX_LEVEL - 1);
		double floor = species.combat() ? 20 + 20 * t : 10 + 10 * t;
		return Math.max(base * (1 + 0.02 * (level - 1)), floor);
	}

	static double maxHealth(LivingEntity e) {
		return Math.max(1, e.getMaxHealth());
	}

	// =====================================================================
	//  Fainting
	// =====================================================================

	/** Pets never die: at zero health they faint, go back into their bloom and rest a while. */
	public void faint(Active a) {
		if (a.fainting) {
			return;
		}
		Pet pet = a.pet;
		Mob m = a.entity;
		if (pet.maxed() && !a.secondWindUsed) {
			// max level: one second wind each time it comes out
			a.secondWindUsed = true;
			m.setHealth((float) (maxHealth(m) * 0.5));
			Fx.particle(a.level(), ParticleTypes.TOTEM_OF_UNDYING, Fx.center(m), 40, 0.4, 0.5, 0.4, 0.4);
			Fx.neutral(m, "item.totem.use", 0.5f, 1.4f);
			ServerPlayer owner = player(a.owner);
			if (owner != null) {
				PetMsg.bar(owner, pet.name + " got a second wind!", ChatFormatting.GOLD);
			}
			return;
		}
		a.fainting = true;
		m.setHealth(1);
		mod.happiness().fainted(pet);
		pet.restingUntil = System.currentTimeMillis() + s().restSeconds * 1000L;
		pet.health = s().restHealth;
		// a moment later, so whatever was hitting it finishes first
		Scheduler.later(1, () -> {
			if (!tracked(a)) {
				return;
			}
			Fx.particle(a.level(), ParticleTypes.POOF, Fx.center(m), 20, m.getBbWidth() / 2, m.getBbHeight() / 2,
				m.getBbWidth() / 2, 0.02);
			Fx.neutral(m, "block.azalea_leaves.break", 1f, 0.7f);
			boolean atPost = a.home != null;
			stash(a);
			ServerPlayer owner = player(a.owner);
			if (owner != null) {
				int rest = s().restSeconds;
				PetMsg.chat(owner, PetMsg.text(pet.name + (atPost
					? " fainted at its post. It'll be back there after a rest"
					: " fainted and went back into its bloom to rest") + (rest > 0 ? " (" + rest + "s)." : "."),
					ChatFormatting.GRAY));
				Blooms.refresh(owner, pet);
				mod.menus().refresh(owner);
			}
		});
	}

	/** Something killed the pet outright (/kill, the void). It rests like after fainting. */
	public void died(Active a) {
		if (!tracked(a)) {
			return;
		}
		a.fainting = true;
		mod.happiness().fainted(a.pet);
		a.pet.restingUntil = System.currentTimeMillis() + s().restSeconds * 1000L;
		a.pet.health = s().restHealth;
		mod.menus().closeFor(a.pet);
		mod.rides().eject(a);
		endFetch(a, false);
		untrack(a);
		mod.happiness().wentIn(a);
		mod.store().changed(a.owner);
		ServerPlayer owner = player(a.owner);
		if (owner != null) {
			PetMsg.chat(owner, PetMsg.text(a.pet.name + (a.home != null ? " fainted at its post and will be back "
				+ "there after a rest." : " fainted and went back into its bloom to rest."), ChatFormatting.GRAY));
			mod.menus().refresh(owner);
		}
	}

	// =====================================================================
	//  Renaming
	// =====================================================================

	/** A tidy pet name (1-24 letters, numbers, spaces and a little punctuation, no commas), or null. */
	static @Nullable String cleanName(String raw) {
		String name = raw.replace("§", "").trim().replaceAll("\\s+", " ");
		return name.isEmpty() || name.length() > 24 || !name.matches("[\\p{L}\\p{N} '!?.\\-_]+") ? null : name;
	}

	public boolean rename(ServerPlayer p, Pet pet, String raw) {
		String name = cleanName(raw);
		if (name == null) {
			PetMsg.error(p, "Pet names are 1-24 letters, numbers and spaces.");
			return false;
		}
		Pet other = mod.store().byName(pet.owner, name);
		if (other != null && other != pet) {
			PetMsg.error(p, "You already have a pet called " + other.name + ".");
			return false;
		}
		pet.name = name;
		Active a = activeFor(pet);
		if (a != null) {
			rename(a.entity, pet);
		}
		Blooms.refresh(p, pet);
		mod.store().changed(pet.owner);
		mod.menus().refresh(p);
		PetMsg.bar(p, "Your " + pet.species.displayName() + " is now called " + name + ".", PetMsg.PINK);
		return true;
	}

	// =====================================================================
	//  Bonding with wild animals, feeding
	// =====================================================================

	/** Sneak + feed a wild animal its favourite food. Enough feedings and it's yours. */
	public void bond(ServerPlayer p, Mob mob, Species species, InteractionHand hand) {
		PetSettings s = s();
		if (!s.species.contains(species)) {
			PetMsg.error(p, species.plural() + " can't become pets in this world.");
			return;
		}
		if (Pets.isBossThing(mob)) {
			PetMsg.error(p, "This one is far too wild to tame.");
			return;
		}
		if (mob instanceof OwnableEntity own && own.getOwnerReference() != null
			&& !own.getOwnerReference().getUUID().equals(p.getUUID())) {
			PetMsg.error(p, "That one already has an owner.");
			return;
		}
		if (mob.isLeashed() && mob.getLeashHolder() instanceof Player holder && holder != p) {
			PetMsg.error(p, "Someone else is leading that one.");
			return;
		}
		if (mob.isVehicle() || mob.isPassenger()) {
			PetMsg.error(p, "Wait until nothing is riding it.");
			return;
		}
		if (mod.store().count(p.getUUID()) >= s.maxPets) {
			PetMsg.error(p, "You already have " + s.maxPets + " pets. Release one first (/pets release <name>).");
			return;
		}
		int need = s.feedsToTame(species);
		String raw = Data.str(mob, Pets.BOND);
		int count = 0;
		String me = p.getUUID().toString();
		if (raw != null && raw.startsWith(me + ":")) {
			try {
				count = Integer.parseInt(raw.substring(me.length() + 1));
			} catch (NumberFormatException ignored) {
				count = 0;
			}
		}
		count++;
		Players.consume(p, hand);
		Fx.neutral(mob, "entity.generic.eat", 0.8f, 1.1f);
		Fx.particle((ServerLevel) mob.level(), ParticleTypes.HAPPY_VILLAGER, mob.position().add(0, mob.getBbHeight() + 0.2, 0),
			6, 0.3, 0.2, 0.3, 0);
		if (count < need) {
			Data.of(mob).putString(Pets.BOND, me + ":" + count);
			PetMsg.bar(p, Component.empty().append(PetMsg.text("Bonding with the " + species.displayName() + "  ", ChatFormatting.GRAY))
				.append(PetMsg.text("❤".repeat(count), PetMsg.PINK))
				.append(PetMsg.text("❤".repeat(need - count), ChatFormatting.DARK_GRAY)));
			return;
		}
		Data.remove(mob, Pets.BOND);
		adopt(p, mob, species);
	}

	private void adopt(ServerPlayer p, Mob mob, Species species) {
		Pet.RideStyle style = !species.small() ? Pet.RideStyle.NORMAL
			: ThreadLocalRandom.current().nextBoolean() ? Pet.RideStyle.GROW : Pet.RideStyle.SHRINK;
		String name = null;
		if (mob.getCustomName() != null) {
			String given = cleanName(mob.getCustomName().getString());
			if (given != null && !mod.store().nameTaken(p.getUUID(), given)) {
				name = given;
			}
		}
		if (name == null) {
			name = Blooms.freshName(mod.store(), p.getUUID());
		}
		boolean newSpecies = !mod.store().bondedBefore(p.getUUID(), species);
		boolean first = mod.store().count(p.getUUID()) == 0;
		Pet pet = new Pet(UUID.randomUUID(), p.getUUID(), species, name, style);
		pet.happiness = s().happyStart;
		mod.store().add(pet);

		Active current = byOwner.get(p.getUUID());
		if (current != null) {
			stash(current);
		}
		unpack(p, mob);
		if (mob.isLeashed()) {
			mob.dropLeash();
		}
		track(p, pet, mob);
		mod.store().setLast(pet);
		boolean gotToy = Blooms.giveToyIfMissing(p, false);
		if (first) {
			Players.give(p, PetGuide.book());
		}
		QuestFlags.mark(p, "pet_adopted");
		if (species.combat()) {
			QuestFlags.mark(p, "pet_combat_owned");
		}
		Skills skills = Skills.get();
		skills.addXp(p, Skill.TAMING, skills.settings().tamingNewPet + (newSpecies ? skills.settings().tamingNewSpecies : 0));

		Fx.particle(p.level(), ParticleTypes.HEART, mob.position().add(0, mob.getBbHeight() + 0.3, 0), 7, 0.4, 0.3, 0.4, 0);
		Fx.playTo(p, "entity.player.levelup", 0.7f, 1.6f);
		Fx.title(p, Text.colored("♥ " + name, species.category().color()),
			PetMsg.text("your new " + species.displayName(), ChatFormatting.GRAY), 4, 44, 12);
		PetMsg.chat(p, PetMsg.text("You bonded with a " + species.displayName() + "! Say hi to " + name + ".", PetMsg.PINK));
		PetMsg.chat(p, PetMsg.text((gotToy ? "Here's a Pet Toy: right-click it to call your pet or send it home, "
			+ "punch with it to see all your pets, use it on your pet to ride. " : "")
			+ (first ? "There's a little Pet Guide in your bag too!" : "Punch with your Pet Toy to see "
			+ name + " with your other pets."), ChatFormatting.GRAY));
		if (style == Pet.RideStyle.GROW) {
			PetMsg.chat(p, PetMsg.text("When you ride " + name + ", it grows big enough to carry you.", ChatFormatting.GRAY));
		} else if (style == Pet.RideStyle.SHRINK) {
			PetMsg.chat(p, PetMsg.text("When you ride " + name + ", you shrink down to its size.", ChatFormatting.GRAY));
		}
		mod.menus().refresh(p);
	}

	/**
	 * A saddle, or a chest and what's in it, goes to the animal's new owner instead of being locked away (pets
	 * don't use saddles, and their own storage replaces chests).
	 */
	private static void unpack(ServerPlayer p, Mob mob) {
		ItemStack saddle = mob.getItemBySlot(EquipmentSlot.SADDLE);
		if (!saddle.isEmpty()) {
			Players.give(p, saddle.copy());
			mob.setItemSlot(EquipmentSlot.SADDLE, ItemStack.EMPTY);
		}
		if (mob instanceof AbstractChestedHorse chested && chested.hasChest()) {
			SimpleContainer inv = ((AbstractHorseAccess) chested).bloomcraft$inventory();
			for (int slot = 0; slot < inv.getContainerSize(); slot++) {
				ItemStack item = inv.getItem(slot);
				if (!item.isEmpty() && slot > 0) { // (slot 0 is the saddle)
					Players.give(p, item.copy());
					inv.setItem(slot, ItemStack.EMPTY);
				}
			}
			chested.setChest(false);
			Players.give(p, new ItemStack(Items.CHEST));
		}
	}

	/** Feeding your own pet heals it, and gives a little bond XP (at most every 30 seconds). */
	public void feed(ServerPlayer p, Active a, InteractionHand hand) {
		long now = System.currentTimeMillis();
		if (now - a.lastFeed < 300) {
			return;
		}
		Mob m = a.entity;
		Pet pet = a.pet;
		double max = maxHealth(m);
		boolean hurt = m.getHealth() < max - 0.01;
		boolean xp = !pet.maxed() && now - pet.lastFeedXp >= FEED_XP_COOLDOWN;
		boolean cheer = mod.happiness().canFeedCheer(pet);
		if (!hurt && !xp && !cheer) {
			PetMsg.bar(p, pet.name + " isn't hungry right now.", ChatFormatting.GRAY);
			return;
		}
		a.lastFeed = now;
		boolean bucket = p.getItemInHand(hand).is(Items.TROPICAL_FISH_BUCKET);
		Players.consume(p, hand);
		if (bucket && !p.isCreative()) {
			Players.give(p, new ItemStack(Items.WATER_BUCKET)); // like the game: the fish goes, the water stays
		}
		QuestFlags.mark(p, "pet_fed");
		if (hurt) {
			m.setHealth((float) Math.min(max, m.getHealth() + max * s().healPercent / 100.0));
		}
		Fx.neutral(m, "entity.generic.eat", 0.8f, 1.1f);
		Fx.particle(a.level(), hurt ? ParticleTypes.HEART : ParticleTypes.HAPPY_VILLAGER,
			m.position().add(0, m.getBbHeight() + 0.2, 0), hurt ? 3 : 6, 0.3, 0.2, 0.3, 0);
		var msg = Component.empty().append(Text.colored(pet.name + "  ", pet.species.category().color()))
			.append(PetMsg.text("❤ " + Math.round(m.getHealth()) + "/" + Math.round(max), ChatFormatting.RED));
		if (xp) {
			pet.lastFeedXp = now;
			msg.append(PetMsg.text("  +" + s().xpFeed + " bond", PetMsg.PINK));
			addXp(pet, s().xpFeed);
		}
		if (cheer && mod.happiness().fed(p, a)) {
			msg.append(Component.literal("  ")).append(mod.happiness().bar(pet, now));
		}
		PetMsg.bar(p, msg);
	}

	// =====================================================================
	//  Bond XP
	// =====================================================================

	public void addXp(Pet pet, int amount) {
		if (amount <= 0 || pet.maxed()) {
			return;
		}
		int before = pet.level;
		pet.xp += amount;
		while (!pet.maxed() && pet.xp >= Pet.xpToNext(pet.level)) {
			pet.xp -= Pet.xpToNext(pet.level);
			pet.level++;
		}
		if (pet.maxed()) {
			pet.xp = 0;
		}
		mod.store().changed(pet.owner);
		if (pet.level != before) {
			leveledUp(pet, before);
		}
	}

	private void leveledUp(Pet pet, int before) {
		ServerPlayer owner = player(pet.owner);
		Active a = activeFor(pet);
		if (a != null) {
			applyStats(a);
			rename(a.entity, pet);
			if (owner != null && a.home == null) {
				mod.bonuses().apply(owner, a);
			}
			Mob m = a.entity;
			Fx.particle(a.level(), ParticleTypes.TOTEM_OF_UNDYING, Fx.center(m), 30, m.getBbWidth() / 2,
				m.getBbHeight() / 2, m.getBbWidth() / 2, 0.3);
		}
		if (owner == null) {
			return;
		}
		QuestFlags.mark(owner, "pet_level_up");
		if (pet.maxed()) {
			QuestFlags.mark(owner, "pet_max_level");
		}
		Skills skills = Skills.get();
		skills.addXp(owner, Skill.TAMING, skills.settings().tamingPetLevelUp * pet.level);
		List<String> perks = new ArrayList<>();
		int ride = s().rideLevel(pet.species);
		if (pet.species.rideable() && before < ride && pet.level >= ride) {
			perks.add("you can ride it now (use your Pet Toy on it)");
		}
		if (before < s().stationLevel && pet.level >= s().stationLevel) {
			perks.add("it can keep a post (sneak + punch it with your Pet Toy)");
		}
		int oldSlots = pet.species.storage().slots(before);
		if (pet.storageSlots() > oldSlots) {
			perks.add("storage " + oldSlots + " → " + pet.storageSlots() + " slots");
		}
		perks.add(pet.maxed() ? "max level: its bonus is at full strength" : "its bonus got a little stronger");
		String list = String.join(", ", perks);
		PetMsg.chat(owner, Component.empty().append(PetMsg.text(pet.name + " reached level " + pet.level + "! ", ChatFormatting.GOLD))
			.append(PetMsg.text(Character.toUpperCase(list.charAt(0)) + list.substring(1) + ".", ChatFormatting.GRAY)));
		Fx.playTo(owner, "entity.player.levelup", 0.6f, 1.4f);
		Blooms.refresh(owner, pet);
	}

	// =====================================================================
	//  Releasing
	// =====================================================================

	/**
	 * Says goodbye to a pet for good: everything it carries (storage, plus anything it wears or holds, like the
	 * Vex's gear or a wolf's armor) goes to the owner, and its blooms wilt.
	 */
	public void release(ServerPlayer owner, Pet pet) {
		Active a = activeFor(pet);
		if (a != null) {
			stash(a);
		}
		pet.station = null;
		mod.bonuses().forget(pet.id);
		mod.menus().closeFor(pet);
		List<ItemStack> carried = new ArrayList<>();
		for (int slot = 0; slot < pet.storage.getContainerSize(); slot++) {
			ItemStack item = pet.storage.getItem(slot);
			if (!item.isEmpty()) {
				carried.add(item.copy());
			}
		}
		pet.storage.clearContent();
		LivingEntity body = restore(pet, owner.level());
		if (body != null) {
			carried.addAll(worn(body));
		}
		for (ItemStack item : carried) {
			Players.give(owner, item);
		}
		var inv = owner.getInventory();
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			if (pet.id.equals(Blooms.petId(inv.getItem(slot)))) {
				inv.setItem(slot, ItemStack.EMPTY);
			}
		}
		mod.store().remove(pet);
		mod.menus().refresh(owner);
	}

	// =====================================================================
	//  Combat
	// =====================================================================

	/** A mob hurt this pet: a combat pet at its post fights back. */
	public void defend(Active a, LivingEntity attacker) {
		if (a.home != null && a.pet.species.combat() && !a.fainting && validGuardTarget(a, attacker)) {
			a.target = attacker;
		}
	}

	private boolean validGuardTarget(Active a, LivingEntity t) {
		Vec3 home = a.home;
		double r = guardRadius(a) + 8;
		return home != null && valid(t) && t.level() == a.entity.level() && t.distanceToSqr(home) <= r * r
			&& !(t instanceof Player) && !(t instanceof ArmorStand) && !Pets.isPet(t) && !Pets.isStatue(t)
			&& !t.isInvulnerable() && !ownedBy(t, a.owner);
	}

	private static boolean ownedBy(Entity e, UUID owner) {
		return e instanceof OwnableEntity own && own.getOwnerReference() != null
			&& owner.equals(own.getOwnerReference().getUUID());
	}

	/** Not creepers (they'd blow up the base), and neutral mobs only once they're angry. */
	static boolean worthFighting(Mob mob) {
		if (mob instanceof Creeper) {
			return false;
		}
		boolean neutral = mob instanceof Enderman || mob instanceof ZombifiedPiglin;
		return !neutral || mob.getTarget() != null;
	}

	double guardRadius(Active a) {
		return s().guardRadius * (a.pet.maxed() ? 1.5 : 1);
	}

	/** A stationed combat pet keeps hostile mobs away from its post. */
	private void guard(Active a) {
		if (a.target != null && !validGuardTarget(a, a.target)) {
			a.target = null;
		}
		if (a.target != null || a.ticks % 10 != 0 || a.home == null) {
			return;
		}
		double r = guardRadius(a);
		Vec3 home = a.home;
		LivingEntity best = null;
		double bestDist = r * r;
		AABB box = new AABB(home, home).inflate(r, Math.min(r, 8), r);
		for (Mob mob : a.level().getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy)) {
			if (validGuardTarget(a, mob) && worthFighting(mob)) {
				double d = mob.distanceToSqr(home);
				if (d < bestDist) {
					bestDist = d;
					best = mob;
				}
			}
		}
		a.target = best;
	}

	/** Whether this player turned on "pets join PvP fights" (the wool button in the pet menu). */
	public boolean pvp(ServerPlayer owner) {
		return s().petPvp && mod.store().pvp(owner.getUUID());
	}

	public void setPvp(ServerPlayer owner, boolean on) {
		mod.store().setPvp(owner.getUUID(), on);
		Active a = byOwner.get(owner.getUUID());
		if (!on && a != null && a.target instanceof Player) {
			a.target = null;
		}
	}

	/** A player this owner's pet may fight: PvP is on for the owner and in the world, and it's someone else. */
	private boolean fairGame(ServerPlayer owner, ServerPlayer victim) {
		return pvp(owner) && victim != owner && victim.isAlive() && Players.survivalLike(victim)
			&& victim.level().getGameRules().get(GameRules.PVP);
	}

	/** Whether the pet {@code a} may hurt this player right now (it's fighting them, and PvP is on). */
	public boolean mayHurt(Active a, ServerPlayer victim) {
		ServerPlayer owner = player(a.owner);
		return owner != null && a.home == null && victim == a.target && fairGame(owner, victim)
			&& victim.getUUID().equals(a.pvpFoe) && System.currentTimeMillis() - a.pvpAt < PVP_MEMORY;
	}

	/** The owner is fighting this mob (they hit it, or it hit them or their pet): combat pets join in. */
	public void engage(ServerPlayer owner, LivingEntity target) {
		Active a = byOwner.get(owner.getUUID());
		if (a == null || !a.pet.species.combat() || a.fainting || mod.rides().isRidden(a)) {
			return;
		}
		if (target instanceof ServerPlayer victim) {
			// a hit between you and them just went through: the pet takes them on (or keeps at it)
			UUID foe = a.pvpFoe;
			long at = a.pvpAt;
			a.pvpFoe = victim.getUUID();
			a.pvpAt = System.currentTimeMillis();
			if (validTarget(a, victim, owner)) {
				a.target = victim;
			} else {
				a.pvpFoe = foe; // not someone it can fight: the old fight's clock keeps running
				a.pvpAt = at;
			}
			return;
		}
		if (target != a.entity && validTarget(a, target, owner)) {
			a.target = target;
		}
	}

	private boolean validTarget(Active a, LivingEntity t, ServerPlayer owner) {
		if (!valid(t) || t.level() != owner.level() || t.distanceToSqr(owner) > TARGET_RANGE * TARGET_RANGE) {
			return false;
		}
		if (t instanceof ServerPlayer victim) {
			return fairGame(owner, victim) && victim.getUUID().equals(a.pvpFoe)
				&& System.currentTimeMillis() - a.pvpAt < PVP_MEMORY;
		}
		if (t instanceof ArmorStand || Pets.isPet(t) || Pets.isStatue(t) || t.isInvulnerable()) {
			return false;
		}
		return !ownedBy(t, owner.getUUID());
	}

	private void updateTarget(Active a, ServerPlayer owner) {
		if (a.target != null && !validTarget(a, a.target, owner)) {
			a.target = null;
		}
		if (a.target != null || a.ticks % 10 != 0) {
			return;
		}
		// defend the owner: the closest hostile mob that is after them
		LivingEntity best = null;
		double bestDist = 12 * 12;
		for (Mob mob : owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12, 6, 12),
			e -> e instanceof Enemy && !(e instanceof Creeper))) {
			if (mob.getTarget() == owner && validTarget(a, mob, owner)) {
				double d = mob.distanceToSqr(owner);
				if (d < bestDist) {
					bestDist = d;
					best = mob;
				}
			}
		}
		a.target = best;
	}

	private void tryAttack(Active a, @Nullable ServerPlayer owner, LivingEntity t) {
		long now = System.currentTimeMillis();
		if (now - a.lastAttack < ATTACK_COOLDOWN) {
			return;
		}
		Mob m = a.entity;
		double dx = t.getX() - m.getX();
		double dz = t.getZ() - m.getZ();
		double reach = reach(m, t);
		double dy = (t.getY() + t.getBbHeight() / 2) - (m.getY() + m.getBbHeight() / 2);
		if (dx * dx + dz * dz > reach * reach || Math.abs(dy) > 1.5 + (m.getBbHeight() + t.getBbHeight()) / 2) {
			return;
		}
		Species sp = a.pet.species;
		if (sp != Species.VEX && !m.hasLineOfSight(t)) {
			return;
		}
		a.lastAttack = now;
		double base = sp.attackDamage();
		if (sp == Species.VEX) {
			base = Math.max(base, m.getAttributeValue(Attributes.ATTACK_DAMAGE)); // includes its weapon
		}
		face(m, yaw(dx, dz));
		a.level().getChunkSource().sendToTrackingPlayers(m, new ClientboundAnimatePacket(m, 0));
		if (m instanceof IronGolem) {
			a.level().broadcastEntityEvent(m, (byte) 4);
		}
		if (owner != null && owner.level() == t.level() && owner.distanceToSqr(t) < 32 * 32) {
			// the kill counts as yours (XP, drops), like a tamed wolf's, when you're there
			if (t instanceof Mob) {
				t.setLastHurtByPlayer(owner, 100);
			}
		}
		PetSkills.Hit hit = mod.skills().before(a, t, base * a.pet.power());
		boolean earns = earns(a, owner);
		double before = t.getHealth() + t.getAbsorptionAmount();
		t.hurtServer(a.level(), m.damageSources().mobAttack(m), (float) hit.damage);
		double dealt = Math.max(0, before - (t.isDeadOrDying() ? 0 : t.getHealth() + t.getAbsorptionAmount()));
		if (dealt <= 0) {
			return; // it didn't land (blocked, protected, still flinching from the last hit)
		}
		a.lastHit = t.getUUID();
		if (sp == Species.GOAT) {
			Vec3 away = new Vec3(dx, 0, dz);
			if (away.lengthSqr() > 1.0E-4) {
				away = away.normalize().scale(1.1);
				Motion.set(t, t.getDeltaMovement().scale(0.5).add(away.x, t.onGround() ? 0.4 : 0, away.z));
			}
		} else if (sp == Species.IRON_GOLEM) {
			Motion.set(t, t.getDeltaMovement().add(0, 0.4, 0));
		}
		mod.skills().after(a, owner, t, hit, dealt);
		if (earns && owner != null) {
			// bond XP for every hit that lands (more for a big one): fighting is the fastest way to level
			if (now - a.pet.lastCombatXp >= s().combatXpCooldown) {
				a.pet.lastCombatXp = now;
				addXp(a.pet, hit.critical ? s().xpCombatCrit : s().xpCombatHit);
				QuestFlags.mark(owner, "pet_combat_xp");
			}
			if (PetSkills.hasSkills(sp)) {
				mod.skills().hitXp(owner, t, dealt);
			}
		}
	}

	/** Whether this pet's fighting earns XP now: its owner is online, and close by if the pet guards a post. */
	boolean earns(Active a, @Nullable ServerPlayer owner) {
		if (owner == null || owner.hasDisconnected() || owner.level() != a.entity.level()) {
			return false;
		}
		double r = s().guardOwnerRange;
		return a.home == null || owner.distanceToSqr(a.entity) <= r * r;
	}

	private static double reach(Mob m, LivingEntity t) {
		return 1.3 + (m.getBbWidth() + t.getBbWidth()) / 2;
	}

	// =====================================================================
	//  The tick: following, fighting, recalls
	// =====================================================================

	public void tick() {
		if (Ticks.now() % 40 == 0) {
			placeStationed();
		}
		if (!posted.isEmpty()) {
			tickPosted();
		}
		if (byOwner.isEmpty()) {
			return;
		}
		PetSettings s = s();
		for (Active a : List.copyOf(byOwner.values())) {
			if (byOwner.get(a.owner) != a) {
				continue;
			}
			ServerPlayer owner = player(a.owner);
			if (owner == null) {
				stash(a);
				continue;
			}
			Mob m = a.entity;
			if (!valid(m)) {
				lost(a);
				continue;
			}
			if (a.fainting) {
				continue;
			}
			a.ticks++;
			if (a.ticks % 1200 == 0) {
				addXp(a.pet, s.xpPerMinute);
			}
			if (a.ticks % 1200 == 600 && !m.isVehicle() && !mod.menus().gearOpen(a.pet)) {
				a.pet.health = m.getHealth() / maxHealth(m);
				snapshot(a); // in case the game stops without warning
				mod.store().changed(a.owner);
			}
			if (owner.isSpectator()) {
				stash(a);
				mod.menus().refresh(owner);
				continue;
			}
			if (mod.rides().isRidden(a)) {
				a.target = null;
				continue;
			}
			if (m.level() != owner.level() || m.distanceToSqr(owner) > RECALL_DISTANCE * RECALL_DISTANCE) {
				recall(a, owner);
				continue;
			}
			if (!owner.isAlive()) {
				continue;
			}
			sparkle(a);
			walkingXp(a, owner);
			if (a.fetchItem != null && fetch(a, owner)) {
				continue;
			}
			if (a.pet.species.combat()) {
				updateTarget(a, owner);
			}
			LivingEntity target = a.target;
			if (a.pet.species.flies()) {
				fly(a, owner, target, null);
			} else {
				walk(a, owner, target, null);
			}
			if (target != null && byOwner.get(a.owner) == a) {
				tryAttack(a, owner, target);
			}
		}
	}

	/** Where the pet belongs: its post, or its owner. */
	private static Vec3 anchor(Active a, @Nullable ServerPlayer owner) {
		if (a.home != null) {
			return a.roamSpot != null && a.target == null ? a.roamSpot : a.home;
		}
		return owner != null ? owner.position() : a.entity.position();
	}

	/** {@code spot}: somewhere to run to instead (the fetch stick). */
	private void walk(Active a, @Nullable ServerPlayer owner, @Nullable LivingEntity target, @Nullable Vec3 spot) {
		Mob m = a.entity;
		Pet pet = a.pet;
		Vec3 pos = m.position();
		Vec3 goal = spot != null ? spot : target != null ? target.position() : anchor(a, owner);
		double dx = goal.x - pos.x;
		double dz = goal.z - pos.z;
		double dy = goal.y - pos.y;
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (target == null && spot == null && (flat > teleportDistance(a) || Math.abs(dy) > 8)) {
			teleportNear(a, owner);
			return;
		}
		double stop = spot != null ? 0.6 : target != null ? reach(m, target) * 0.8
			: a.home != null ? 1.0 : 2.2 + m.getBbWidth() / 2;
		if ((flat <= stop && Math.abs(dy) < 2.5) || System.currentTimeMillis() < a.pausedUntil) {
			a.path = null;
			a.stuck = 0;
			a.lastPos = null;
			if (a.ticks % 5 == 0) {
				face(m, yaw(dx, dz));
			}
			return;
		}
		double speed = 0.2 * pet.species.category().followSpeed() * (1 + 0.02 * (pet.level - 1));
		if (flat > 7) {
			speed *= 1.5;
		}
		if (target != null) {
			speed *= 1.2;
		}
		Vec3 wp = waypoint(a, goal);
		double wx = wp.x - pos.x;
		double wz = wp.z - pos.z;
		double wl = Math.sqrt(wx * wx + wz * wz);
		double vx = 0;
		double vz = 0;
		if (wl > 0.01) {
			double step = Math.min(speed, wl);
			vx = wx / wl * step;
			vz = wz / wl * step;
		}
		double vy = m.getDeltaMovement().y;
		if (m.isInWater()) {
			double swim = pet.species.swims() ? 1.6 : 0.7;
			vx *= swim;
			vz *= swim;
			vy = dy > -1 ? 0.1 : vy; // swim up, unless you went diving
		}
		// stuck against something: hop, and in the end just come over
		if (a.lastPos != null) {
			double mx = pos.x - a.lastPos.x;
			double mz = pos.z - a.lastPos.z;
			a.stuck = mx * mx + mz * mz < speed * speed * 0.04 ? a.stuck + 1 : Math.max(0, a.stuck - 2);
		}
		a.lastPos = pos;
		if (a.stuck > 60) {
			a.stuck = 0;
			if (spot != null) {
				endFetch(a, true); // can't get to it: the stick comes back to you anyway
			} else if (target == null) {
				teleportNear(a, owner);
			} else {
				a.target = null;
			}
			return;
		}
		double stepHeight = pet.species.category().stepHeight();
		if (m.onGround() && ((a.stuck >= 8 && a.stuck % 8 == 0) || wp.y - pos.y > stepHeight + 0.05)) {
			vy = 0.42 + 0.1 * Math.max(0, stepHeight - 1);
		} else if (pet.species == Species.RABBIT && m.onGround() && Math.abs(vx) + Math.abs(vz) > 0.01) {
			vy = 0.3; // rabbits hop
			a.level().broadcastEntityEvent(m, (byte) 1); // the rabbit's jump animation
		}
		Motion.set(m, new Vec3(vx, vy, vz));
		if (Math.abs(vx) + Math.abs(vz) > 0.001) {
			face(m, yaw(vx, vz));
		}
	}

	/** The next point along the path to {@code goal}, or the goal itself when there is no path. */
	private static Vec3 waypoint(Active a, Vec3 goal) {
		Mob m = a.entity;
		Vec3 pos = m.position();
		boolean stale = a.path == null || a.pathGoal == null || a.pathGoal.distanceToSqr(goal) > 2.25
			|| a.ticks - a.nextPathTick > 40;
		if (stale && a.ticks >= a.nextPathTick) {
			Path found = null;
			try {
				found = m.getNavigation().createPath(BlockPos.containing(goal), 0);
			} catch (RuntimeException ignored) {
				// no path today
			}
			List<Vec3> points = null;
			if (found != null) {
				points = new ArrayList<>();
				for (int i = 0; i < found.getNodeCount(); i++) {
					Node n = found.getNode(i);
					points.add(new Vec3(n.x + 0.5, n.y, n.z + 0.5));
				}
			}
			a.path = points;
			a.pathIndex = 0;
			a.pathGoal = goal;
			a.nextPathTick = a.ticks + 10;
		}
		List<Vec3> path = a.path;
		if (path != null) {
			while (a.pathIndex < path.size()) {
				Vec3 p = path.get(a.pathIndex);
				double cx = p.x - pos.x;
				double cz = p.z - pos.z;
				if (cx * cx + cz * cz < 0.36) {
					a.pathIndex++;
					continue;
				}
				return p;
			}
		}
		return goal;
	}

	private void fly(Active a, @Nullable ServerPlayer owner, @Nullable LivingEntity target, @Nullable Vec3 spot) {
		Mob m = a.entity;
		Vec3 pos = m.position();
		Vec3 o = owner != null ? owner.position() : pos;
		boolean ownerMoving = a.ownerLast == null || a.ownerLast.distanceToSqr(o) > 0.0016;
		a.ownerLast = o;
		a.ownerStill = ownerMoving ? 0 : a.ownerStill + 1;
		float ownerYaw = owner != null ? owner.getYRot() : m.getYRot();
		Vec3 goal;
		if (spot != null) {
			goal = spot.add(0, 0.3, 0);
		} else if (target != null) {
			goal = target.position().add(0, target.getBbHeight() * 0.5, 0);
		} else if (a.home != null || owner == null) {
			// at its post: hovers over the spot
			goal = anchor(a, owner).add(0, 1.2 + Math.sin(a.ticks / 12.0) * 0.15, 0);
		} else {
			Vec3 eye = owner.getEyePosition();
			double near = pos.distanceTo(eye);
			if ((a.ownerStill > 8 && near < 5 && near > 0.8) || System.currentTimeMillis() < a.pausedUntil) {
				// you're standing still: it hovers right where it is (easy to reach), bobbing a little
				Motion.set(m, new Vec3(0, Math.cos(a.ticks / 10.0) * 0.02, 0));
				if (a.ticks % 5 == 0) {
					face(m, yaw(eye.x - pos.x, eye.z - pos.z));
				}
				a.stuck = 0;
				a.lastPos = pos;
				return;
			}
			// on the move: it flits beside your head, a little ahead of you, like a fairy
			double yaw = Math.toRadians(ownerYaw);
			goal = eye.add(-Math.cos(yaw) * 0.9 - Math.sin(yaw) * 0.4, 0.15 + Math.sin(a.ticks / 9.0) * 0.15,
				-Math.sin(yaw) * 0.9 + Math.cos(yaw) * 0.4);
		}
		Vec3 d = goal.subtract(pos);
		double len = d.length();
		if (target == null && spot == null && len > teleportDistance(a)) {
			teleportNear(a, owner);
			return;
		}
		if (len < 0.25) {
			Motion.set(m, Vec3.ZERO);
			if (a.ticks % 5 == 0) {
				face(m, ownerYaw);
			}
			a.stuck = 0;
			return;
		}
		double max = 0.5 * (1 + 0.02 * (a.pet.level - 1));
		double speed = Math.min(max, len * 0.3);
		Motion.set(m, d.scale(speed / len));
		face(m, len > 1.5 ? yaw(d.x, d.z) : ownerYaw);
		// Bees, Allays and Bats can get stuck behind walls (the Vex flies through them)
		if (a.lastPos != null && len > 2 && a.lastPos.distanceToSqr(pos) < 0.0025) {
			if (++a.stuck > 40) {
				a.stuck = 0;
				if (spot != null) {
					endFetch(a, true);
				} else if (target == null) {
					teleportNear(a, owner);
				} else {
					a.target = null;
				}
			}
		} else {
			a.stuck = Math.max(0, a.stuck - 1);
		}
		a.lastPos = pos;
	}

	/** How far behind a pet may fall before it pops over (a strolling guard may cross its whole area). */
	private double teleportDistance(Active a) {
		return a.home == null ? TELEPORT_DISTANCE : TELEPORT_DISTANCE + 2 * s().roamRadius;
	}

	/** Environmentally Aware and friends: out of the fire, back to its owner (or its post). */
	void escape(Active a) {
		teleportNear(a, player(a.owner));
	}

	private void teleportNear(Active a, @Nullable ServerPlayer owner) {
		a.roamSpot = null;
		Mob m = a.entity;
		Vec3 spot;
		if (a.home != null || owner == null || owner.level() != m.level()) {
			spot = anchor(a, null).add(0, a.pet.species.flies() && a.home != null ? 1.2 : 0, 0);
		} else {
			spot = spawnSpot(owner, a.pet.species);
		}
		m.setDeltaMovement(Vec3.ZERO);
		m.teleportTo(spot.x, spot.y, spot.z);
		m.resetFallDistance();
		a.path = null;
		a.stuck = 0;
		a.lastPos = null;
		a.nextPathTick = a.ticks;
	}

	// =====================================================================
	//  Stationing: a pet keeps a post (level 8+)
	// =====================================================================

	/** Leaves the pet that's out at the spot where it stands. */
	public void station(ServerPlayer owner, Active a) {
		PetSettings s = s();
		Pet pet = a.pet;
		if (a.home != null) {
			return;
		}
		if (pet.level < s.stationLevel) {
			PetMsg.error(owner, pet.name + " can keep a post from level " + s.stationLevel + " (now level " + pet.level + ").");
			return;
		}
		long count = mod.store().pets(owner.getUUID()).stream().filter(Pet::stationed).count();
		if (count >= s.maxStations) {
			PetMsg.error(owner, "You already have " + s.maxStations + " pets at posts. Call one back first.");
			return;
		}
		Mob m = a.entity;
		if (!m.onGround() && !pet.species.flies() && !m.isInWater()) {
			PetMsg.error(owner, "Let " + pet.name + " land first.");
			return;
		}
		mod.rides().eject(a);
		endFetch(a, true);
		if (byOwner.remove(owner.getUUID(), a)) {
			mod.bonuses().remove(owner);
		}
		Vec3 home = m.position();
		if (pet.species.flies()) {
			BlockPos b = m.blockPosition();
			Level level = m.level();
			while (b.getY() > level.getMinY() && passable(level, b.below()) && m.getY() - b.getY() < 4) {
				b = b.below(); // a flyer's post is the ground under it
			}
			home = new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
		}
		a.home = home;
		a.target = null;
		pet.station = new Pet.Station(m.level().dimension(), home, m.getYRot());
		posted.put(pet.id, a);
		mod.store().changed(owner.getUUID());
		String job = switch (pet.species) {
			case BEE -> "tend the crops around here";
			case ALLAY -> "collect the drops around here";
			case SNIFFER -> "dig around here";
			case CAT, OCELOT -> "keep creepers and phantoms away from here";
			case LLAMA -> "spit at monsters that come close";
			default -> pet.species.combat() ? "guard this spot" : "wait right here";
		};
		QuestFlags.mark(owner, "pet_stationed");
		PetMsg.chat(owner, Component.empty().append(PetMsg.text(pet.name + " will " + job + ". ", PetMsg.PINK))
			.append(PetMsg.text("Sneak + punch it with your Pet Toy (or pick it in the menu) to call it back.",
				ChatFormatting.GRAY)));
		Fx.playTo(owner, "block.note_block.chime", 0.7f, 1.2f);
		mod.menus().refresh(owner);
	}

	/** Puts stationed pets whose spot is loaded back at their post (after a rest, a restart, or a chunk load). */
	private void placeStationed() {
		MinecraftServer server = Ticks.server();
		if (server == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Pet pet : mod.store().stationed()) {
			Pet.Station station = pet.station;
			if (station == null || posted.containsKey(pet.id) || pet.resting(now) || !s().species.contains(pet.species)
				|| now < postRetry.getOrDefault(pet.id, 0L)) {
				continue;
			}
			ServerLevel level = station.level(server);
			BlockPos at = station.block();
			if (level == null || !level.isPositionEntityTicking(at)
				|| (pet.species == Species.VEX && level.getDifficulty() == Difficulty.PEACEFUL)) {
				continue;
			}
			Mob mob = spawn(pet, level, station.pos().add(0, pet.species.flies() ? 1.2 : 0, 0), station.yaw());
			if (mob == null) {
				postRetry.put(pet.id, now + 60_000);
				continue;
			}
			Active a = new Active(pet, pet.owner, mob);
			a.home = station.pos();
			posted.put(pet.id, a);
			byEntity.put(mob.getUUID(), a);
			mod.happiness().cameOut(pet);
			prepare(a, player(pet.owner));
		}
	}

	private void tickPosted() {
		for (Active a : List.copyOf(posted.values())) {
			if (!tracked(a) || a.home == null) {
				continue;
			}
			Mob m = a.entity;
			if (!valid(m)) {
				lost(a);
				continue;
			}
			if (a.fainting) {
				continue;
			}
			a.ticks++;
			if (a.ticks % 1200 == 600 && !mod.menus().gearOpen(a.pet)) {
				a.pet.health = m.getHealth() / maxHealth(m);
				snapshot(a);
				mod.store().changed(a.owner);
			}
			if (m.distanceToSqr(a.home) > 48 * 48) {
				teleportNear(a, null);
				continue;
			}
			sparkle(a);
			ServerPlayer owner = player(a.owner);
			if (a.pet.species.combat()) {
				guard(a);
			}
			roam(a);
			LivingEntity target = a.target;
			if (a.pet.species.flies()) {
				fly(a, owner, target, null);
			} else {
				walk(a, owner, target, null);
			}
			if (target != null && tracked(a)) {
				tryAttack(a, owner, target);
			}
		}
	}

	/**
	 * Guard duty: a stationed pet strolls around its post (within the roam radius), now and then stopping for a
	 * look around. A fight comes first; afterwards it heads back to its post.
	 */
	private void roam(Active a) {
		Vec3 home = a.home;
		int radius = s().roamRadius;
		if (home == null || radius <= 0) {
			a.roamSpot = null;
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (a.target != null) {
			a.roamSpot = null; // back to the post once the fight is over, then strolling again
			a.roamNext = a.ticks + 100 + r.nextInt(100);
			return;
		}
		if (a.ticks < a.roamNext || System.currentTimeMillis() < a.pausedUntil) {
			return;
		}
		a.roamNext = a.ticks + 100 + r.nextInt(160); // 5-13 seconds per spot
		if (r.nextInt(4) == 0) {
			a.roamSpot = null; // back to the post itself for a bit
			return;
		}
		Level level = a.entity.level();
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = r.nextDouble() * Math.PI * 2;
			double dist = 2 + r.nextDouble() * Math.max(0, radius - 2);
			Vec3 spot = home.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
			if (a.pet.species.flies()) {
				if (passable(level, BlockPos.containing(spot.add(0, 1.2, 0)))) {
					a.roamSpot = spot;
					return;
				}
				continue;
			}
			Vec3 ground = standable(level, spot);
			if (ground != null) {
				a.roamSpot = ground;
				return;
			}
		}
	}

	/** Somewhere to stand near this spot (solid, dry ground with room above), or null. */
	private static @Nullable Vec3 standable(Level level, Vec3 spot) {
		BlockPos base = BlockPos.containing(spot);
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos feet = base.above(dy);
			BlockState below = level.getBlockState(feet.below());
			BlockState here = level.getBlockState(feet);
			if (passable(level, feet) && !liquid(level, feet) && passable(level, feet.above())
				&& !below.getCollisionShape(level, feet.below()).isEmpty() && !below.is(Blocks.MAGMA_BLOCK)
				&& !below.is(Blocks.CACTUS) && !below.is(Blocks.CAMPFIRE) && !below.is(Blocks.SOUL_CAMPFIRE)
				&& !here.is(Blocks.FIRE) && !here.is(Blocks.SWEET_BERRY_BUSH) && !here.is(Blocks.POWDER_SNOW)) {
				return new Vec3(spot.x, feet.getY(), spot.z);
			}
		}
		return null;
	}

	// =====================================================================
	//  Fetch
	// =====================================================================

	/** Throws the Fetch Stick for the pet that's out. False (and nothing thrown) if nobody can play. */
	public boolean throwFetch(ServerPlayer owner, ItemStack stick) {
		Active a = byOwner.get(owner.getUUID());
		if (a == null) {
			PetMsg.error(owner, "Call a pet out to play fetch!");
			return false;
		}
		if (!a.pet.species.playsFetch()) {
			PetMsg.error(owner, a.pet.name + " doesn't play fetch.");
			return false;
		}
		if (a.fetchItem != null || a.fainting || mod.rides().isRidden(a)) {
			return false;
		}
		Vec3 eye = owner.getEyePosition();
		ItemEntity item = new ItemEntity(owner.level(), eye.x, eye.y - 0.1, eye.z, stick.copyWithCount(1));
		item.setNeverPickUp();
		item.setUnlimitedLifetime();
		item.setThrower(owner);
		Data.flag(item, Spawns.TRANSIENT, true);
		item.setDeltaMovement(owner.getLookAngle().scale(1.1).add(0, 0.15, 0));
		if (!owner.level().addFreshEntity(item)) {
			return false;
		}
		a.fetchItem = item;
		a.fetchReturning = false;
		a.fetchStarted = System.currentTimeMillis();
		a.target = null;
		Fx.neutral(owner, "entity.snowball.throw", 0.7f, 0.8f);
		return true;
	}

	/** One tick of fetch: run to the stick, carry it back, drop it at your feet. True if fetch drove the pet. */
	private boolean fetch(Active a, ServerPlayer owner) {
		ItemEntity item = a.fetchItem;
		if (item == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (!valid(item) || now - a.fetchStarted > 25_000 || item.level() != a.entity.level()) {
			endFetch(a, true);
			return false;
		}
		Mob m = a.entity;
		Vec3 carry = m.position().add(0, m.getBbHeight() + 0.1, 0);
		if (!a.fetchReturning) {
			if (item.distanceToSqr(m) < 1.6 * 1.6 && now - a.fetchStarted > 600) {
				a.fetchReturning = true;
				Fx.neutral(m, "entity.item.pickup", 0.6f, 1.4f);
				item.setNoGravity(true);
			} else {
				Vec3 spot = item.position();
				if (a.pet.species.flies()) {
					fly(a, owner, null, spot);
				} else {
					walk(a, owner, null, spot);
				}
				return true;
			}
		}
		item.setDeltaMovement(Vec3.ZERO);
		item.teleportTo(carry.x, carry.y, carry.z); // it carries the stick home
		if (m.distanceToSqr(owner) < 2.4 * 2.4) {
			endFetch(a, true);
			Vec3 at = m.position().add(0, m.getBbHeight() + 0.3, 0);
			Fx.particle(a.level(), ParticleTypes.HEART, at, 4, 0.3, 0.2, 0.3, 0);
			Fx.neutral(m, "entity.allay.item_given", 0.7f, 1.2f);
			var msg = Component.empty().append(PetMsg.text(a.pet.name + " brought the stick back! ♥", PetMsg.PINK));
			QuestFlags.mark(owner, "pet_play");
			if (now - a.pet.lastFetchXp >= 15_000 && !a.pet.maxed()) {
				a.pet.lastFetchXp = now;
				msg.append(PetMsg.text("  +2 bond", ChatFormatting.LIGHT_PURPLE));
				addXp(a.pet, 2);
			}
			if (s().happiness) {
				mod.happiness().fetched(owner, a);
				msg.append(Component.literal("  ")).append(mod.happiness().bar(a.pet, now));
			}
			PetMsg.bar(owner, msg);
			return true;
		}
		if (a.pet.species.flies()) {
			fly(a, owner, null, owner.position());
		} else {
			walk(a, owner, null, owner.position());
		}
		return true;
	}

	/** Fetch is over: the thrown stick disappears and is back in its owner's inventory. */
	private void endFetch(Active a, boolean giveBack) {
		ItemEntity item = a.fetchItem;
		if (item == null) {
			return;
		}
		a.fetchItem = null;
		a.fetchReturning = false;
		ItemStack stick = item.getItem().copy();
		if (!item.isRemoved()) {
			item.discard();
		}
		ServerPlayer owner = player(a.owner);
		if (owner != null && !stick.isEmpty()) {
			Players.give(owner, stick); // (an offline owner can take a new stick from the menu)
		}
	}

	// =====================================================================
	//  Little things
	// =====================================================================

	/** Max-level pets sparkle a little. */
	private static void sparkle(Active a) {
		if (a.pet.maxed() && a.ticks % 30 == 0) {
			Mob m = a.entity;
			Fx.particle(a.level(), ParticleTypes.WAX_OFF, m.position().add(0, m.getBbHeight() + 0.2, 0), 2,
				m.getBbWidth() / 2, 0.2, m.getBbWidth() / 2, 0);
		}
	}

	/** Bond XP for walking around together (riding has its own). */
	private void walkingXp(Active a, ServerPlayer owner) {
		Vec3 here = owner.position();
		Vec3 last = a.walkLast;
		Level lastLevel = a.walkLevel;
		a.walkLast = here;
		a.walkLevel = owner.level();
		if (last == null || lastLevel != owner.level() || owner.isPassenger() || owner.getAbilities().flying
			|| owner.isFallFlying() || owner.isSwimming() || owner.isInWater() || Players.idleSeconds(owner) >= 20) {
			return; // walking, not drifting in a water stream or standing AFK
		}
		double dx = here.x - last.x;
		double dz = here.z - last.z;
		double step = Math.sqrt(dx * dx + dz * dz);
		if (step > 1.5) {
			return; // teleported, not walked
		}
		a.walked += step;
		int per = s().walkBlocksPerXp;
		if (a.walked >= per) {
			int xp = (int) (a.walked / per);
			a.walked -= xp * per;
			addXp(a.pet, xp);
		}
	}

	static void face(LivingEntity e, float yaw) {
		e.setYRot(yaw);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		e.setXRot(0);
	}

	static float yaw(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Taming level the skills use for this owner: live while they're here, the last known one while away. */
	int tamingLevel(UUID owner) {
		ServerPlayer p = player(owner);
		if (p != null) {
			int level = Skills.get().level(p, Skill.TAMING);
			mod.store().setTamingLevel(owner, level);
			return level;
		}
		return mod.store().tamingLevel(owner);
	}
}
