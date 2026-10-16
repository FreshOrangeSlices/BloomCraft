package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Each species' small passive bonus for its owner, active while the pet is out, and the jobs of pets at a post.
 * Every bonus gets a little stronger as the pet levels up ({@link Pet#power()}).
 */
public final class Bonuses {

	public static final Identifier MOD_BONUS = BloomCraft.id("pet_bonus");

	private static final List<Holder<Attribute>> OWNER_ATTRIBUTES = List.of(Attributes.ATTACK_DAMAGE, Attributes.ARMOR,
		Attributes.MAX_HEALTH, Attributes.KNOCKBACK_RESISTANCE, Attributes.MOVEMENT_SPEED, Attributes.ATTACK_SPEED);

	private static final Set<Block> SANDY = Set.of(Blocks.SAND, Blocks.RED_SAND, Blocks.SUSPICIOUS_SAND,
		Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.SNOW,
		Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW);

	/** Ground a Sniffer will dig in. */
	private static final Set<Block> DIGGABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK,
		Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK,
		Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,
		Blocks.CLAY, Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.SNOW_BLOCK, Blocks.SOUL_SOIL);

	/** Ground that sometimes turns up a find when you dig it yourself with a Sniffer out. */
	private static final Set<Block> SNIFFABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT,
		Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.MUD);

	/** Armor trims a Sniffer can dig up, with how likely each is (common 3, uncommon 2, rare 1). */
	private static final Map<Item, Integer> TRIMS = new LinkedHashMap<>();
	private static final int TRIM_WEIGHT_TOTAL;

	static {
		for (Item m : List.of(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			TRIMS.put(m, 3);
		}
		for (Item m : List.of(Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			TRIMS.put(m, 2);
		}
		for (Item m : List.of(Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
			Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			TRIMS.put(m, 1);
		}
		TRIM_WEIGHT_TOTAL = TRIMS.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static final Set<Block> EXTRA_CROPS = Set.of(Blocks.SWEET_BERRY_BUSH, Blocks.NETHER_WART, Blocks.COCOA);

	private static final Item[] CAT_GIFTS = {Items.STRING, Items.FEATHER, Items.RABBIT_HIDE,
		Items.RABBIT_FOOT, Items.CHICKEN, Items.ROTTEN_FLESH, Items.PHANTOM_MEMBRANE};

	private static final int XP_PER_BOTTLE = 7;

	private final Pets mod;
	private final Map<UUID, Long> cowReady = new HashMap<>();
	private final Map<UUID, Long> llamaReady = new HashMap<>();
	private final Map<UUID, Long> sniffReady = new HashMap<>();
	private final Map<UUID, Long> sniffNext = new HashMap<>();
	/** When each player last got trims from a Sniffer. */
	private final Map<UUID, Deque<Long>> trimsFound = new HashMap<>();
	private final Map<UUID, Long> wentToBed = new HashMap<>();
	private final Map<UUID, Vec3> pandaSpot = new HashMap<>();
	private final Map<UUID, Integer> pandaStill = new HashMap<>();
	private final Map<UUID, Long> postTimers = new HashMap<>();
	private int tick;

	Bonuses(Pets mod) {
		this.mod = mod;
	}

	// =====================================================================
	//  Stat bonuses (attribute modifiers on the owner, never saved)
	// =====================================================================

	public void apply(ServerPlayer owner, PetManager.Active a) {
		if (mod.happiness().sad(a.pet)) {
			remove(owner); // a sad pet's bonus is off until it cheers up
			return;
		}
		clear(owner); // no health clamp here: a Donkey levelling up shouldn't cost you health
		double pw = a.pet.power();
		switch (a.pet.species) {
			case WOLF -> mod(owner, Attributes.ATTACK_DAMAGE, 0.08 * pw, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case IRON_GOLEM -> mod(owner, Attributes.ARMOR, 3 * pw, AttributeModifier.Operation.ADD_VALUE);
			case DONKEY -> mod(owner, Attributes.MAX_HEALTH, 4 * pw, AttributeModifier.Operation.ADD_VALUE);
			case MULE -> mod(owner, Attributes.KNOCKBACK_RESISTANCE, Math.min(0.6, 0.3 * pw), AttributeModifier.Operation.ADD_VALUE);
			case HORSE -> mod(owner, Attributes.MOVEMENT_SPEED, 0.08 * pw, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case VEX -> mod(owner, Attributes.ATTACK_SPEED, 0.10 * pw, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			default -> {
			}
		}
	}

	public void remove(ServerPlayer owner) {
		clear(owner);
		if (owner.getHealth() > owner.getMaxHealth()) {
			owner.setHealth(owner.getMaxHealth());
		}
	}

	private static void clear(ServerPlayer owner) {
		for (Holder<Attribute> attribute : OWNER_ATTRIBUTES) {
			Attrs.remove(owner, attribute, MOD_BONUS);
		}
	}

	private static void mod(ServerPlayer p, Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {
		Attrs.setTransient(p, attribute, MOD_BONUS, amount, op);
	}

	// =====================================================================
	//  Effects that need checking all the time (every 4 ticks)
	// =====================================================================

	void tick() {
		tick += 4;
		long now = System.currentTimeMillis();
		postJobs(now);
		for (PetManager.Active a : mod.pets().all()) {
			ServerPlayer p = PetManager.player(a.owner);
			if (p == null || !p.isAlive() || a.fainting || mod.happiness().sad(a.pet)) {
				continue;
			}
			Pet pet = a.pet;
			double pw = pet.power();
			switch (pet.species) {
				case RABBIT -> effect(p, MobEffects.JUMP_BOOST, pet.level >= 8 ? 1 : 0);
				case STRIDER -> {
					if (p.level().dimension() == Level.NETHER) {
						effect(p, MobEffects.FIRE_RESISTANCE, 0);
					}
				}
				case TURTLE -> {
					if (p.isInWater()) {
						effect(p, MobEffects.WATER_BREATHING, 0);
					}
				}
				case FROG -> {
					if (p.isInWater()) {
						effect(p, MobEffects.DOLPHINS_GRACE, 0);
					}
				}
				case CAMEL -> {
					if (SANDY.contains(p.level().getBlockState(BlockPos.containing(p.position().add(0, -0.2, 0))).getBlock())
						|| SANDY.contains(p.level().getBlockState(p.blockPosition()).getBlock())) {
						effect(p, MobEffects.SPEED, pet.level >= 8 ? 1 : 0);
					}
				}
				case PANDA -> panda(p);
				case POLAR_BEAR -> {
					if (p.getTicksFrozen() > 0) {
						p.setTicksFrozen(0);
					}
				}
				case COW -> cow(p, pet, pw, now);
				case BEE -> {
					if (tick % beeEvery(pw) == 0) {
						bee(p.level(), p.blockPosition(), mod.settings().beeRadius, mod.settings().beeCrops);
					}
				}
				case AXOLOTL -> {
					if (p.getHealth() < p.getMaxHealth() - 0.01) {
						effect(p, MobEffects.REGENERATION, p.isInWater() ? 1 : 0);
					}
				}
				case BAT -> {
					// long enough that night vision never starts flickering
					MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
					if (nv == null || (!nv.isInfiniteDuration() && nv.getDuration() < 300)) {
						p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
					}
				}
				case ALLAY -> allay(p, a);
				case CHICKEN -> {
					if (p.fallDistance > 4 && !p.isFallFlying() && !p.getAbilities().flying && !p.isPassenger()) {
						effect(p, MobEffects.SLOW_FALLING, 0);
					}
				}
				case SNIFFER -> {
					if (now >= sniffNext.computeIfAbsent(p.getUUID(), k -> now + sniffGap(pw))) {
						sniffNext.put(p.getUUID(), now + sniffGap(pw));
						dig(p, a);
					}
				}
				default -> {
				}
			}
		}
	}

	/** Ticks between bee pulses (a little faster with level). */
	private int beeEvery(double pw) {
		return Math.max(4, (int) Math.round(mod.settings().beeSeconds * 20 / pw / 4) * 4);
	}

	// =====================================================================
	//  Jobs of stationed pets
	// =====================================================================

	private void postJobs(long now) {
		for (PetManager.Active a : mod.pets().postedPets()) {
			Vec3 home = a.home;
			if (home == null || a.fainting || mod.happiness().sad(a.pet)) {
				continue; // a sad pet doesn't do its job
			}
			Pet pet = a.pet;
			double pw = pet.power();
			int extra = pet.maxed() ? 2 : 0;
			switch (pet.species) {
				case BEE -> {
					if (tick % beeEvery(pw) == 0) {
						bee(a.level(), BlockPos.containing(home), mod.settings().beeRadius + 1 + extra, mod.settings().beeCrops + 1);
					}
				}
				case ALLAY -> collect(a, home, 8 + 2 * extra);
				case SNIFFER -> {
					if (now >= postTimers.computeIfAbsent(pet.id, k -> now + sniffGap(pw))) {
						postTimers.put(pet.id, now + sniffGap(pw));
						dig(PetManager.player(pet.owner), a);
					}
				}
				case LLAMA -> {
					if (tick % 20 == 0 && now >= postTimers.getOrDefault(pet.id, 0L)) {
						AABB box = new AABB(home, home).inflate(10, 5, 10);
						for (Mob mob : a.level().getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && !Pets.isPet(e))) {
							if (PetManager.worthFighting(mob)) {
								postTimers.put(pet.id, now + 2000);
								spitAt(a, mob);
								break;
							}
						}
					}
				}
				default -> {
				}
			}
		}
	}

	/** A stationed Allay gathers the drops (and loose XP, as bottles) around its post into its storage. */
	private void collect(PetManager.Active a, Vec3 home, int radius) {
		Pet pet = a.pet;
		if (pet.storageSlots() <= 0 || tick % 8 != 0) {
			return;
		}
		// only for its owner: they must be the nearest player around (no vacuuming a neighbour's farm)
		ServerPlayer nearest = null;
		double best = 48 * 48;
		for (ServerPlayer p : a.level().players()) {
			double d = p.distanceToSqr(home);
			if (d < best && !p.isSpectator()) {
				best = d;
				nearest = p;
			}
		}
		if (nearest == null || !nearest.getUUID().equals(pet.owner)) {
			return;
		}
		ItemStack bottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
		boolean stored = false;
		AABB box = new AABB(home, home).inflate(radius, 4, radius);
		for (Entity e : a.level().getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			if (e instanceof ItemEntity item) {
				if (item.getOwner() != null || item.hasPickUpDelay() || Blooms.isPetItem(item.getItem())) {
					continue;
				}
				ItemStack left = mod.menus().addToStorage(pet, item.getItem());
				if (left.isEmpty()) {
					item.discard();
					stored = true;
				} else if (left.getCount() != item.getItem().getCount()) {
					item.setItem(left);
					stored = true;
				}
			} else if (e instanceof ExperienceOrb orb && mod.settings().allayBottlesXp && mod.menus().hasRoom(pet, bottle)) {
				pet.xpBank += orb.getValue();
				orb.discard();
			}
		}
		while (pet.xpBank >= XP_PER_BOTTLE && mod.menus().hasRoom(pet, bottle)
			&& mod.menus().addToStorage(pet, bottle.copy()).isEmpty()) {
			pet.xpBank -= XP_PER_BOTTLE;
			stored = true;
		}
		if (stored && tick % 20 == 0) {
			Fx.neutral(a.entity, "entity.allay.item_taken", 0.4f, 1.3f);
		}
	}

	/** A quiet, particle-free effect that keeps topping itself up while the bonus applies. */
	private static void effect(ServerPlayer p, Holder<MobEffect> type, int amplifier) {
		MobEffectInstance current = p.getEffect(type);
		if (current != null && (current.getAmplifier() > amplifier || current.isInfiniteDuration() || current.getDuration() > 60)) {
			return;
		}
		p.addEffect(new MobEffectInstance(type, 100, amplifier, true, false, true));
	}

	/** Panda: stand still for three seconds and you slowly heal. */
	private void panda(ServerPlayer p) {
		UUID id = p.getUUID();
		Vec3 here = p.position();
		Vec3 before = pandaSpot.put(id, here);
		boolean still = before != null && before.distanceToSqr(here) < 0.0025;
		int ticks = still ? pandaStill.getOrDefault(id, 0) + 4 : 0;
		pandaStill.put(id, ticks);
		if (ticks >= 60) {
			effect(p, MobEffects.REGENERATION, 0);
		}
	}

	/** Cow: clears one bad effect, then needs two minutes (a bit less at higher levels). */
	private void cow(ServerPlayer p, Pet pet, double pw, long now) {
		if (now < cowReady.getOrDefault(p.getUUID(), 0L)) {
			return;
		}
		for (MobEffectInstance effect : List.copyOf(p.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				p.removeEffect(effect.getEffect());
				cowReady.put(p.getUUID(), now + (long) (120_000 / pw));
				PetMsg.bar(p, Component.empty().append(PetMsg.text(pet.name + "'s milk cleared your ", ChatFormatting.WHITE))
					.append(effect.getEffect().value().getDisplayName().copy().withStyle(ChatFormatting.WHITE))
					.append(PetMsg.text(".", ChatFormatting.WHITE)));
				Fx.playTo(p, "entity.generic.drink", 0.6f, 1.2f);
				return;
			}
		}
	}

	/** Bee: a couple of crops around here grow a stage. */
	private static void bee(ServerLevel level, BlockPos center, int radius, int count) {
		List<BlockPos> crops = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int y = -2; y <= 1; y++) {
					BlockPos pos = center.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if ((state.is(BlockTags.CROPS) || EXTRA_CROPS.contains(state.getBlock())) && !state.is(Blocks.PITCHER_CROP)
						&& grown(state) != null) {
						crops.add(pos);
					}
				}
			}
		}
		Collections.shuffle(crops);
		for (BlockPos pos : crops.subList(0, Math.min(count, crops.size()))) {
			BlockState next = grown(level.getBlockState(pos));
			if (next != null) {
				level.setBlock(pos, next, Block.UPDATE_CLIENTS);
				Fx.particle(level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(pos).add(0, 0.1, 0), 4, 0.25, 0.2, 0.25, 0);
			}
		}
	}

	/** The crop one growth stage on, or null if it's fully grown (or not a crop). */
	private static @Nullable BlockState grown(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			int age = crop.getAge(state);
			return age < crop.getMaxAge() ? crop.getStateForAge(age + 1) : null;
		}
		for (Property<?> prop : state.getProperties()) {
			if (prop instanceof IntegerProperty ip && ip.getName().equals("age")) {
				int age = state.getValue(ip);
				int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
				return age < max ? state.setValue(ip, age + 1) : null;
			}
		}
		return null;
	}

	/** Allay: pulls drops to you, and bottles loose XP into its storage (or pulls the XP to you). */
	private void allay(ServerPlayer p, PetManager.Active a) {
		Pet pet = a.pet;
		Vec3 to = p.position().add(0, 0.6, 0);
		ItemStack bottle = new ItemStack(Items.EXPERIENCE_BOTTLE);
		boolean bottling = mod.settings().allayBottlesXp && pet.storageSlots() > 0 && mod.menus().hasRoom(pet, bottle);
		AABB box = p.getBoundingBox().inflate(8, 4, 8);
		for (Entity e : p.level().getEntities((Entity) null, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			if (e instanceof ItemEntity item) {
				// things someone dropped on purpose stay where they are
				if (item.getOwner() != null || Blooms.isPetItem(item.getItem())) {
					continue;
				}
				pull(item, to, 0.4);
			} else if (e instanceof ExperienceOrb orb) {
				if (bottling) {
					pet.xpBank += orb.getValue();
					Fx.particle(p.level(), ParticleTypes.WAX_ON, orb.position(), 3, 0.1, 0.1, 0.1, 0);
					orb.discard();
				} else {
					pull(orb, to, 0.35);
				}
			}
		}
		int made = 0;
		while (pet.xpBank >= XP_PER_BOTTLE && mod.menus().hasRoom(pet, bottle)) {
			if (!mod.menus().addToStorage(pet, bottle.copy()).isEmpty()) {
				break;
			}
			pet.xpBank -= XP_PER_BOTTLE;
			made++;
		}
		if (made > 0) {
			Fx.neutral(a.entity, "entity.allay.item_given", 0.5f, 1.3f);
			mod.store().changed(pet.owner);
		}
	}

	private static void pull(Entity e, Vec3 to, double speed) {
		Vec3 d = to.subtract(e.position());
		double len = d.length();
		if (len < 1.2) {
			return;
		}
		Motion.set(e, d.scale(Math.min(speed, len * 0.4) / len).add(0, 0.04, 0));
	}

	// =====================================================================
	//  Bonuses when things happen
	// =====================================================================

	private PetManager.@Nullable Active outFor(@Nullable Entity e) {
		if (!(e instanceof ServerPlayer p)) {
			return null;
		}
		PetManager.Active a = mod.pets().active(p.getUUID());
		return a == null || a.fainting || mod.happiness().sad(a.pet) ? null : a;
	}

	/**
	 * Goat: softer landings. Armadillo: projectiles hurt less. Polar Bear: no freezing. Llama: spits at whatever
	 * hits you. The damage to deal, or -1 for none.
	 */
	float ownerHurt(ServerPlayer p, DamageSource source, float amount) {
		PetManager.Active a = outFor(p);
		if (a == null) {
			return amount;
		}
		double pw = a.pet.power();
		switch (a.pet.species) {
			case GOAT -> {
				if (source.is(DamageTypes.FALL)) {
					return (float) (amount * Math.max(0.3, 1 - 0.4 * pw));
				}
			}
			case ARMADILLO -> {
				if (source.is(DamageTypeTags.IS_PROJECTILE)) {
					return (float) (amount * Math.max(0.5, 1 - 0.2 * pw));
				}
			}
			case POLAR_BEAR -> {
				if (source.is(DamageTypes.FREEZE)) {
					return -1;
				}
			}
			case LLAMA -> {
				if (source.getEntity() instanceof Mob attacker && !Pets.isPet(attacker)) {
					spit(a, p, attacker);
				}
			}
			default -> {
			}
		}
		return amount;
	}

	private void spit(PetManager.Active a, ServerPlayer owner, LivingEntity attacker) {
		long now = System.currentTimeMillis();
		if (now < llamaReady.getOrDefault(owner.getUUID(), 0L)) {
			return;
		}
		llamaReady.put(owner.getUUID(), now + 1000);
		spitAt(a, attacker);
	}

	private void spitAt(PetManager.Active a, LivingEntity attacker) {
		Mob llama = a.entity;
		if (llama.level() != attacker.level() || llama.distanceToSqr(attacker) > 16 * 16 || !(llama instanceof Llama l)) {
			return;
		}
		PetManager.face(llama, PetManager.yaw(attacker.getX() - llama.getX(), attacker.getZ() - llama.getZ()));
		l.performRangedAttack(attacker, 1.0f);
	}

	/**
	 * Fox: mobs notice you from shorter range. Cat and Ocelot: creepers don't come for you and phantoms leave you be
	 * (also near a cat's post). False to stop {@code mob} from going after {@code target}.
	 */
	public boolean mayTarget(Mob mob, LivingEntity target) {
		if (!(target instanceof ServerPlayer p) || Pets.isPet(mob)) {
			return true;
		}
		boolean provoked = mob.getLastHurtByMob() == p;
		if ((mob instanceof Creeper || mob instanceof Phantom) && !provoked) {
			for (PetManager.Active cat : mod.pets().postedPets()) {
				Species s = cat.pet.species;
				double r = cat.pet.maxed() ? 36 : 24;
				if ((s == Species.CAT || s == Species.OCELOT) && cat.home != null && !mod.happiness().sad(cat.pet)
					&& cat.entity.level() == mob.level() && cat.home.distanceToSqr(mob.position()) < r * r) {
					return false;
				}
			}
		}
		PetManager.Active a = outFor(p);
		if (a == null || provoked) {
			return true;
		}
		return switch (a.pet.species) {
			case FOX -> {
				double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * Math.max(0.5, 1 - 0.25 * a.pet.power());
				yield mob.distanceToSqr(p) <= range * range;
			}
			case CAT, OCELOT -> !(mob instanceof Creeper) && !(mob instanceof Phantom);
			default -> true;
		};
	}

	/** Polar Bear: Slowness can't touch you. */
	public boolean blocksEffect(ServerPlayer p, MobEffectInstance effect) {
		PetManager.Active a = outFor(p);
		return a != null && a.pet.species == Species.POLAR_BEAR && effect.getEffect() == MobEffects.SLOWNESS;
	}

	/** Sniffer: digging dirt yourself sometimes turns up seeds and flowers. */
	void onDig(ServerPlayer p, BlockState state, BlockPos pos) {
		PetManager.Active a = outFor(p);
		if (a == null || a.pet.species != Species.SNIFFER || p.isCreative() || !SNIFFABLE.contains(state.getBlock())) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		long now = System.currentTimeMillis();
		if (now < sniffReady.getOrDefault(p.getUUID(), 0L) || r.nextDouble() >= 0.04 * a.pet.power()) {
			return;
		}
		sniffReady.put(p.getUUID(), now + 60_000); // at most one find a minute, so it can't be farmed
		Item found = plainFind(r); // trims only come from the Sniffer's own digs
		drop(p.level(), Vec3.atCenterOf(pos), new ItemStack(found));
		Fx.neutral(a.entity, "entity.sniffer.happy", 0.8f, 1f);
		announceFind(p, a.pet, found);
	}

	private static void drop(ServerLevel level, Vec3 at, ItemStack item) {
		ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, item);
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	/** Four minutes between digs at level 1, under three at level 10. */
	private static long sniffGap(double pw) {
		return (long) (240_000 / pw);
	}

	/**
	 * The Sniffer stops, sniffs the ground, digs, and pops up with a find (its own animation plays). Only on
	 * ground it could dig in, and only while you're nearby.
	 */
	private void dig(@Nullable ServerPlayer p, PetManager.Active a) {
		Mob m = a.entity;
		boolean atPost = a.home != null;
		boolean active = p != null && Players.idleSeconds(p) < 300; // no treasure for standing AFK
		if (!(m instanceof Sniffer sniffer) || !m.onGround() || mod.rides().isRidden(a)
			|| (!atPost && (!active || m.level() != p.level() || m.distanceToSqr(p) > 12 * 12))
			|| !DIGGABLE.contains(m.level().getBlockState(BlockPos.containing(m.position().add(0, -0.5, 0))).getBlock())) {
			return;
		}
		a.pausedUntil = System.currentTimeMillis() + 8000;
		sniffer.transitionTo(Sniffer.State.SNIFFING);
		Scheduler.later(40, () -> {
			if (mod.pets().of(m) == a) {
				sniffer.transitionTo(Sniffer.State.DIGGING);
				Fx.neutral(m, "entity.sniffer.digging", 1f, 1f);
			}
		});
		Scheduler.later(130, () -> {
			if (mod.pets().of(m) != a) {
				return;
			}
			sniffer.transitionTo(Sniffer.State.RISING);
			Item found = active ? treasure(a.owner, ThreadLocalRandom.current()) : plainFind(ThreadLocalRandom.current());
			double yaw = Math.toRadians(m.getYRot());
			Vec3 front = m.position().add(-Math.sin(yaw) * 1.6, 0.4, Math.cos(yaw) * 1.6);
			drop(a.level(), front, new ItemStack(found));
			Fx.neutral(m, "entity.sniffer.drop_seed", 1f, 1f);
			Fx.particle(a.level(), ParticleTypes.HAPPY_VILLAGER, front, 8, 0.3, 0.3, 0.3, 0);
			ServerPlayer owner = PetManager.player(a.owner);
			if (owner != null) {
				announceFind(owner, a.pet, found);
			}
		});
		Scheduler.later(160, () -> {
			if (!m.isRemoved()) {
				sniffer.transitionTo(Sniffer.State.IDLING);
			}
			a.pausedUntil = 0;
		});
	}

	private static void announceFind(ServerPlayer p, Pet pet, Item found) {
		Component name = new ItemStack(found).getHoverName();
		if (TRIMS.containsKey(found)) {
			PetMsg.chat(p, Component.empty().append(PetMsg.text(pet.name + " dug up something special: ", PetMsg.PINK))
				.append(name.copy().withStyle(ChatFormatting.GOLD)).append(PetMsg.text("!", ChatFormatting.GOLD)));
			Fx.playTo(p, "entity.player.levelup", 0.6f, 1.8f);
			return;
		}
		PetMsg.bar(p, Component.empty().append(PetMsg.text(pet.name + " sniffed out ", ChatFormatting.GREEN))
			.append(name.copy().withStyle(ChatFormatting.GREEN)).append(PetMsg.text("!", ChatFormatting.GREEN)));
	}

	/**
	 * What a Sniffer digs up on its own: an armor trim one dig in five (rarer trims less often, and at most three
	 * an hour per player), otherwise what {@link #plainFind} gives.
	 */
	private Item treasure(UUID owner, ThreadLocalRandom r) {
		long now = System.currentTimeMillis();
		Deque<Long> recent = trimsFound.computeIfAbsent(owner, k -> new ArrayDeque<>());
		while (!recent.isEmpty() && now - recent.peekFirst() > 3_600_000) {
			recent.pollFirst();
		}
		if (recent.size() < 3 && r.nextDouble() < 0.20) {
			int pick = r.nextInt(TRIM_WEIGHT_TOTAL);
			for (Map.Entry<Item, Integer> trim : TRIMS.entrySet()) {
				pick -= trim.getValue();
				if (pick < 0) {
					recent.addLast(now);
					return trim.getKey();
				}
			}
		}
		return plainFind(r);
	}

	/** Torchflower seeds, pitcher pods, other seeds and flowers. */
	private static Item plainFind(ThreadLocalRandom r) {
		double roll = r.nextDouble();
		if (roll < 0.30) {
			return Items.TORCHFLOWER_SEEDS;
		}
		if (roll < 0.50) {
			return Items.PITCHER_POD;
		}
		if (roll < 0.80) {
			Item[] seeds = {Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS};
			return seeds[r.nextInt(seeds.length)];
		}
		List<Item> flowers = new ArrayList<>();
		for (Holder<Block> b : BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.SMALL_FLOWERS)) {
			Item item = b.value().asItem();
			if (item != Items.AIR && item != Items.WITHER_ROSE) {
				flowers.add(item);
			}
		}
		return flowers.isEmpty() ? Items.POPPY : flowers.get(r.nextInt(flowers.size()));
	}

	/** The owner went to bed: remember the time, to see later whether the night was really skipped. */
	void wentToBed(ServerPlayer p) {
		wentToBed.put(p.getUUID(), p.level().getOverworldClockTime());
	}

	/** Cat and Ocelot: after a night's sleep you often wake up to a little gift. */
	void wokeUp(ServerPlayer p) {
		Long since = wentToBed.remove(p.getUUID());
		PetManager.Active a = outFor(p);
		// the night was actually skipped (the clock jumped ahead), not just in and out of bed
		if (a == null || (a.pet.species != Species.CAT && a.pet.species != Species.OCELOT) || since == null
			|| p.level().getOverworldClockTime() - since < 1000) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (r.nextDouble() >= 0.7) {
			return;
		}
		Item gift = CAT_GIFTS[r.nextInt(CAT_GIFTS.length)];
		Pet pet = a.pet;
		Scheduler.later(20, () -> {
			if (p.hasDisconnected()) {
				return;
			}
			Players.give(p, new ItemStack(gift));
			Fx.playTo(p, "entity.cat.purr", 1f, 1f);
			PetMsg.bar(p, Component.empty().append(PetMsg.text(pet.name + " brought you a gift: ", PetMsg.PINK))
				.append(new ItemStack(gift).getHoverName().copy().withColor(PetMsg.PINK)));
		});
	}

	void forgetPlayer(UUID id) {
		pandaSpot.remove(id);
		pandaStill.remove(id);
		llamaReady.remove(id);
		wentToBed.remove(id);
		sniffNext.remove(id);
	}

	/** A released or recalled stationed pet's timers. */
	public void forget(UUID petId) {
		postTimers.remove(petId);
	}
}
