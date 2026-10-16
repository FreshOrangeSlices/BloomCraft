package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Clock;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import com.bloomcraft.skills.Skill;
import com.bloomcraft.skills.SkillSettings;
import com.bloomcraft.skills.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Taming skills for the combat pets (Wolf, Iron Golem, Vex, Polar Bear, Goat, Panda), unlocked by their owner's
 * Taming level (see {@link Skills}).
 *
 * <p>Every combat pet gets the same set (Gore, Fast Food Service, Thick Fur, Holy Hound, Shock Proof, Sharpened Claws,
 * Environmentally Aware), each animal under its own names, plus one starting skill and one special skill per animal.
 * A sad pet's skills are off until it cheers up.</p>
 */
public final class PetSkills {

	/** The skill slots every combat pet has, and the Taming level each unlocks at by default. */
	public enum Slot {
		FIRST("first", 1),
		AWARENESS("awareness", 10),
		GORE("gore", 15),
		SPECIAL("special", 20),
		FAST_FOOD("fast-food", 20),
		THICK_FUR("thick-fur", 25),
		HOLY_HOUND("holy-hound", 35),
		SHOCK_PROOF("shock-proof", 50),
		CLAWS("sharpened-claws", 75);

		public final String key;
		public final int defaultLevel;

		Slot(String key, int defaultLevel) {
			this.key = key;
			this.defaultLevel = defaultLevel;
		}
	}

	public static final Identifier MOD_UNYIELDING = BloomCraft.id("pet_unyielding");
	private static final String GUARDIAN_DAY = "guardian_day";

	private static final Map<Species, String[]> NAMES = new EnumMap<>(Species.class);

	static {
		// in Slot order
		NAMES.put(Species.WOLF, new String[]{"Pounce", "Environmentally Aware", "Gore", "Rallying Howl",
			"Fast Food Service", "Thick Fur", "Holy Hound", "Shock Proof", "Sharpened Claws"});
		NAMES.put(Species.IRON_GOLEM, new String[]{"Iron Colossus", "Construct Awareness", "Crushing Blow",
			"Guardian Intervention", "Self Repair", "Iron Plating", "Arcane Core", "Reinforced Frame", "Forged Fists"});
		NAMES.put(Species.VEX, new String[]{"Double Strike", "Phantom Awareness", "Spectral Rend", "Phase Strike",
			"Soul Siphon", "Ethereal Form", "Voidfed", "Stormproof", "Spectral Blade"});
		NAMES.put(Species.POLAR_BEAR, new String[]{"Unyielding", "Arctic Awareness", "Mauling Blow",
			"Crushing Presence", "Feast", "Thick Hide", "Winterborn", "Avalanche Proof", "Rending Claws"});
		NAMES.put(Species.GOAT, new String[]{"Momentum", "Mountain Awareness", "Headbutt", "Ram", "Graze",
			"Thick Horns", "Mountain Spirit", "Rock Solid", "Hardened Horns"});
		NAMES.put(Species.PANDA, new String[]{"Bamboo Blessing", "Bamboo Sense", "Heavy Paw", "Soothing Presence",
			"Snack Break", "Dense Fur", "Inner Balance", "Unshaken", "Heavy Paws"});
	}

	/** What a pet's swing turned into. */
	static final class Hit {
		double damage;
		/** A big hit (Gore, Double Strike, Momentum, Pounce): more bond XP. */
		boolean critical;
		boolean ram;

		Hit(double damage) {
			this.damage = damage;
		}
	}

	private final Pets mod;
	/** When each owner's wolf can howl again. */
	private final Map<UUID, Long> howlReady = new HashMap<>();

	PetSkills(Pets mod) {
		this.mod = mod;
	}

	private PetSettings s() {
		return mod.settings();
	}

	/** Animals with Taming skills. */
	public static boolean hasSkills(Species s) {
		return NAMES.containsKey(s);
	}

	public static List<Species> skilled() {
		return List.copyOf(NAMES.keySet());
	}

	public static String name(Species s, Slot slot) {
		String[] names = NAMES.get(s);
		return names == null ? slot.key : names[slot.ordinal()];
	}

	/** Whether the skills work at all (on in pets.yml, and the Taming skill is on). */
	public boolean on() {
		return s().skills && Skills.get().settings().enabled(Skill.TAMING);
	}

	public int unlock(Slot slot) {
		return s().unlock(slot);
	}

	/** The owner's Taming level if this pet can use its skills now, else -1. */
	private int level(PetManager.Active a) {
		if (!on() || !hasSkills(a.pet.species) || a.fainting || mod.happiness().sad(a.pet)) {
			return -1;
		}
		return mod.pets().tamingLevel(a.owner);
	}

	private boolean unlocked(int level, Slot slot) {
		return level >= 0 && level >= unlock(slot);
	}

	private static boolean roll(double percent) {
		return percent > 0 && ThreadLocalRandom.current().nextDouble() * 100 < percent;
	}

	private int maxLevel() {
		return Skills.get().settings().maxLevel;
	}

	// =====================================================================
	//  Numbers (also shown by /pets taming)
	// =====================================================================

	double goreChance(int level) {
		return s().goreMaxChance * Math.min(1, Math.max(0, level) / s().goreMaxLevel);
	}

	double doubleStrikeChance(int level) {
		int steps = Math.max(0, level) / 5;
		return Math.min(s().doubleStrikeMax, steps * s().doubleStrikePer5);
	}

	double phaseArmor(int level) {
		return level >= s().phaseHighLevel ? s().phaseArmorHigh : s().phaseArmor;
	}

	boolean crushHigh(int level) {
		return level >= s().crushHighLevel;
	}

	double soothChance(int level) {
		double from = unlock(Slot.SPECIAL);
		double to = Math.max(from + 1, maxLevel());
		double t = Math.max(0, Math.min(1, (level - from) / (to - from)));
		return s().soothChanceLow + (s().soothChanceHigh - s().soothChanceLow) * t;
	}

	double soothHeal(int level) {
		int steps = Math.max(0, level - unlock(Slot.SPECIAL)) / 10;
		return Math.min(s().soothHealMax, s().soothHealBase + steps * s().soothHealPer10);
	}

	// =====================================================================
	//  Taming XP
	// =====================================================================

	/** Taming XP for a hit by a combat pet: damage dealt x the target's value x the multiplier (skills.yml). */
	void hitXp(ServerPlayer owner, LivingEntity target, double dealt) {
		SkillSettings ss = Skills.get().settings();
		double value = target instanceof Player ? ss.tamingPlayerValue
			: target instanceof Enemy ? ss.tamingHostileValue
			: target instanceof Animal ? ss.tamingAnimalValue : ss.tamingOtherValue;
		if (Pets.isBossThing(target) && !com.bloomcraft.bosses.BossManager.isBoss(target)) {
			value *= 0.25; // a boss's summoned minions are worth less
		}
		Skills.get().addXp(owner, Skill.TAMING, dealt * value * ss.tamingHitMultiplier);
	}

	// =====================================================================
	//  Attacking
	// =====================================================================

	/** Before a pet's swing lands: extra damage from its skills. */
	Hit before(PetManager.Active a, LivingEntity t, double damage) {
		Hit hit = new Hit(damage);
		int level = level(a);
		if (level < 0) {
			return hit;
		}
		double initial = damage;
		ServerLevel world = a.level();
		if (unlocked(level, Slot.CLAWS)) {
			hit.damage += s().clawsBonus;
		}
		if (unlocked(level, Slot.GORE) && roll(goreChance(level))) {
			hit.damage += initial * (s().goreMultiplier - 1);
			hit.critical = true;
			Fx.particle(world, ParticleTypes.CRIT, Fx.center(t), 12, t.getBbWidth() / 2, t.getBbHeight() / 3, t.getBbWidth() / 2, 0.2);
		}
		switch (a.pet.species) {
			case WOLF -> {
				if (unlocked(level, Slot.FIRST) && !t.getUUID().equals(a.lastHit)) {
					hit.damage *= 1 + s().pounceBonus / 100; // Pounce: its first bite on a new target
					hit.critical = true;
				}
			}
			case VEX -> {
				if (unlocked(level, Slot.FIRST) && roll(doubleStrikeChance(level))) {
					hit.damage *= 2;
					hit.critical = true;
					Fx.particle(world, ParticleTypes.ENCHANTED_HIT, Fx.center(t), 14, t.getBbWidth() / 2, t.getBbHeight() / 3,
						t.getBbWidth() / 2, 0.2);
					Fx.neutral(a.entity, "entity.vex.charge", 0.7f, 1.4f);
				}
				if (unlocked(level, Slot.SPECIAL)) {
					hit.damage *= phaseBoost(t, hit.damage, phaseArmor(level) / 100);
				}
			}
			case GOAT -> {
				// counted when a hit lands (in after), so blocked swings don't use up the third hit
				if (unlocked(level, Slot.FIRST) && (a.hits + 1) % s().momentumEvery == 0) {
					hit.damage *= 1 + s().momentumBonus / 100;
					hit.critical = true;
					hit.ram = unlocked(level, Slot.SPECIAL);
					Fx.neutral(a.entity, "entity.goat.ram_impact", 0.8f, 1f);
				}
			}
			default -> {
			}
		}
		return hit;
	}

	/**
	 * Phase Strike: the hit acts as if the target wore {@code ignored} less armor. Worked out with the game's armor
	 * formula, so it's the same as really ignoring that much armor.
	 */
	private static double phaseBoost(LivingEntity t, double damage, double ignored) {
		double armor = t.getAttributeValue(Attributes.ARMOR);
		double tough = t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		if (armor <= 0 || ignored <= 0) {
			return 1;
		}
		double full = armorFactor(armor, tough, damage);
		return full <= 0.001 ? 1 : armorFactor(armor * (1 - ignored), tough, damage) / full;
	}

	private static double armorFactor(double armor, double toughness, double damage) {
		double effective = Math.max(armor / 5, armor - 4 * damage / (toughness + 8));
		return 1 - Math.min(20, effective) / 25;
	}

	/** After a pet's swing landed and did {@code dealt} damage. */
	void after(PetManager.Active a, @Nullable ServerPlayer owner, LivingEntity t, Hit hit, double dealt) {
		int level = level(a);
		if (level < 0 || dealt <= 0) {
			return;
		}
		Mob m = a.entity;
		if (a.pet.species == Species.GOAT && unlocked(level, Slot.FIRST)) {
			a.hits++;
		}
		if (unlocked(level, Slot.FAST_FOOD) && roll(s().fastFoodChance)) {
			double max = m.getMaxHealth();
			if (m.getHealth() < max) {
				m.setHealth((float) Math.min(max, m.getHealth() + dealt));
				Fx.particle(a.level(), ParticleTypes.HAPPY_VILLAGER, m.position().add(0, m.getBbHeight() + 0.2, 0), 4, 0.3, 0.2, 0.3, 0);
			}
		}
		switch (a.pet.species) {
			case POLAR_BEAR -> {
				if (unlocked(level, Slot.SPECIAL) && t.isAlive()) {
					boolean high = crushHigh(level);
					int ticks = s().crushSeconds * 20;
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, high ? 3 : 1), m);
					t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, high ? 1 : 0), m);
				}
			}
			case GOAT -> {
				if (hit.ram && t.isAlive()) {
					Vec3 away = new Vec3(t.getX() - m.getX(), 0, t.getZ() - m.getZ());
					if (away.lengthSqr() > 1.0E-4) {
						away = away.normalize().scale(0.4);
					}
					com.bloomcraft.core.Motion.set(t, new Vec3(away.x, s().ramLaunch, away.z));
					Fx.particle(a.level(), ParticleTypes.CLOUD, t.position(), 10, 0.3, 0.1, 0.3, 0.05);
				}
			}
			case PANDA -> {
				if (unlocked(level, Slot.SPECIAL) && roll(soothChance(level))) {
					soothe(a, soothHeal(level));
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Soothing Presence: heals the panda, its owner and friends nearby, and hurts undead monsters. Other players
	 * only count as friends while its owner has pet PvP off (no healing the other side of a fight).
	 */
	private void soothe(PetManager.Active a, double amount) {
		Mob panda = a.entity;
		double r = s().soothRadius;
		Vec3 at = panda.position();
		Fx.particle(a.level(), ParticleTypes.HAPPY_VILLAGER, at.add(0, 1, 0), 24, r / 2, 0.6, r / 2, 0);
		Fx.neutral(panda, "entity.panda.eat", 0.8f, 1.2f);
		heal(panda, amount);
		ServerPlayer owner = PetManager.player(a.owner);
		boolean pvpOn = owner != null && mod.pets().pvp(owner);
		for (LivingEntity le : a.level().getEntitiesOfClass(LivingEntity.class, panda.getBoundingBox().inflate(r, r / 2 + 1, r))) {
			if (le == panda || !le.isAlive() || le == a.target) {
				continue;
			}
			if (le.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
				// undead monsters only: not someone's skeleton horse, a named mob, or a villager being cured
				if (le instanceof Monster && !Pets.isPet(le) && !Pets.isStatue(le) && le.getCustomName() == null
					&& !(le instanceof ZombieVillager zv && zv.isConverting())) {
					le.hurtServer(a.level(), panda.damageSources().indirectMagic(panda, panda), (float) amount);
				}
				continue;
			}
			boolean friend = le.getUUID().equals(a.owner) || Pets.isPet(le) || ownedBy(le, a.owner)
				|| (le instanceof Player && !pvpOn);
			if (friend) {
				heal(le, amount);
			}
		}
	}

	private static boolean ownedBy(Entity e, UUID owner) {
		return e instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerReference() != null
			&& owner.equals(own.getOwnerReference().getUUID());
	}

	private static void heal(LivingEntity e, double amount) {
		if (amount > 0 && e.getHealth() < e.getMaxHealth()) {
			e.setHealth((float) Math.min(e.getMaxHealth(), e.getHealth() + amount));
			if (e.level() instanceof ServerLevel level) {
				Fx.particle(level, ParticleTypes.HEART, e.position().add(0, e.getBbHeight() + 0.2, 0), 1, 0.2, 0.1, 0.2, 0);
			}
		}
	}

	/** Bamboo Blessing: when the panda wins a fight, it and its owner get Regeneration I. */
	void killed(PetManager.Active a) {
		if (a.pet.species != Species.PANDA || !unlocked(level(a), Slot.FIRST)) {
			return;
		}
		int ticks = s().blessingSeconds * 20;
		a.entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0));
		ServerPlayer owner = PetManager.player(a.owner);
		if (owner != null && owner.level() == a.entity.level() && owner.distanceToSqr(a.entity) < 32 * 32) {
			owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 0));
		}
	}

	// =====================================================================
	//  Getting hurt
	// =====================================================================

	/** The pet is about to take damage: its defensive skills. The damage to take, or -1 for none. */
	float hurt(PetManager.Active a, DamageSource source, float amount) {
		int level = level(a);
		if (level < 0) {
			return amount;
		}
		Mob m = a.entity;
		boolean golem = a.pet.species == Species.IRON_GOLEM;
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.IN_FIRE)
			|| source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.CAMPFIRE)) {
			if (unlocked(level, Slot.AWARENESS)) {
				// steps out of the danger, back to its owner (or its post)
				m.clearFire();
				mod.pets().escape(a);
				return -1;
			}
			if (golem && unlocked(level, Slot.THICK_FUR)) {
				return (float) (amount / s().thickFurDivisor);
			}
			return amount;
		}
		if (source.is(DamageTypes.ON_FIRE)) {
			if (unlocked(level, Slot.THICK_FUR)) {
				m.clearFire();
				return golem ? (float) (amount / s().thickFurDivisor) : amount;
			}
			return amount;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)) {
			if (unlocked(level, Slot.HOLY_HOUND)) {
				m.setHealth((float) Math.min(m.getMaxHealth(), m.getHealth() + amount));
				Fx.particle(a.level(), ParticleTypes.HEART, m.position().add(0, m.getBbHeight() + 0.2, 0), 2, 0.3, 0.2, 0.3, 0);
				return -1;
			}
			return amount;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_LIGHTNING)) {
			return unlocked(level, Slot.SHOCK_PROOF) ? (float) (amount / s().shockProofDivisor) : amount;
		}
		if (source.is(DamageTypes.FREEZE)) {
			if (a.pet.species == Species.POLAR_BEAR && unlocked(level, Slot.FIRST)) {
				m.setTicksFrozen(0);
				return -1;
			}
			return amount;
		}
		if (source.getEntity() != null && unlocked(level, Slot.THICK_FUR)) {
			return (float) (amount / s().thickFurDivisor); // attacks and arrows
		}
		return amount;
	}

	/** Unyielding: the Polar Bear can't be slowed. */
	public boolean blocksEffect(PetManager.Active a, MobEffectInstance effect) {
		return a.pet.species == Species.POLAR_BEAR && effect.getEffect() == MobEffects.SLOWNESS
			&& unlocked(level(a), Slot.FIRST);
	}

	/** Every second: the Polar Bear's Unyielding (no knockback, no freezing). */
	void tick() {
		for (PetManager.Active a : mod.pets().all()) {
			unyielding(a);
		}
		for (PetManager.Active a : mod.pets().postedPets()) {
			unyielding(a);
		}
	}

	private void unyielding(PetManager.Active a) {
		if (a.pet.species != Species.POLAR_BEAR) {
			return;
		}
		boolean on = unlocked(level(a), Slot.FIRST);
		Attrs.setTransient(a.entity, Attributes.KNOCKBACK_RESISTANCE, MOD_UNYIELDING, on ? 1.0 : 0,
			AttributeModifier.Operation.ADD_VALUE);
		if (on && a.entity.getTicksFrozen() > 0) {
			a.entity.setTicksFrozen(0);
		}
	}

	// =====================================================================
	//  Guardian Intervention and Rallying Howl
	// =====================================================================

	/**
	 * Iron Golem: a blow that would kill you leaves you at half a heart with Resistance II instead, once per
	 * Minecraft day, while the golem follows you (not while it guards a post). A totem in your hand goes first.
	 * True if it saved you.
	 */
	public boolean guardian(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		PetManager.Active a = mod.pets().active(p.getUUID());
		if (a == null || a.pet.species != Species.IRON_GOLEM || a.home != null || !unlocked(level(a), Slot.SPECIAL)
			|| a.entity.level() != p.level() || a.entity.distanceToSqr(p) > 32 * 32) {
			return false;
		}
		if (p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.TOTEM_OF_UNDYING)
			|| p.getItemInHand(InteractionHand.OFF_HAND).is(Items.TOTEM_OF_UNDYING)) {
			return false; // the totem saves you; the golem's help is kept for later
		}
		long day = Clock.day();
		if (Data.longValue(p, GUARDIAN_DAY, -1) == day) {
			return false;
		}
		Data.of(p).putLong(GUARDIAN_DAY, day);
		p.setHealth(1.0f);
		p.clearFire();
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, s().guardianSeconds * 20, s().guardianLevel - 1));
		Fx.particle(p.level(), ParticleTypes.TOTEM_OF_UNDYING, p.position().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
		Fx.neutral(p, "entity.iron_golem.repair", 1f, 0.8f);
		PetMsg.chat(p, Component.empty().append(PetMsg.text(a.pet.name + " stepped in and saved you! ", ChatFormatting.GOLD))
			.append(PetMsg.text("(Guardian Intervention: once per day)", ChatFormatting.GRAY)));
		return true;
	}

	/**
	 * Wolf: when you drop low, it howls and rallies you: Strength I and Speed I for a few seconds (once a minute).
	 * Called after you took damage.
	 */
	void ownerHurt(ServerPlayer p) {
		PetManager.Active a = mod.pets().active(p.getUUID());
		if (a == null || a.pet.species != Species.WOLF || !unlocked(level(a), Slot.SPECIAL)
			|| p.getHealth() > p.getMaxHealth() * s().howlHealth / 100 || !p.isAlive()
			|| a.entity.level() != p.level() || a.entity.distanceToSqr(p) > 32 * 32) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < howlReady.getOrDefault(p.getUUID(), 0L)) {
			return;
		}
		howlReady.put(p.getUUID(), now + s().howlCooldown * 1000L);
		int ticks = s().howlSeconds * 20;
		p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 0));
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0));
		Fx.neutral(a.entity, "entity.wolf.howl", 1f, 1f);
		PetMsg.bar(p, a.pet.name + " howls: you're not alone in this!", ChatFormatting.GOLD);
	}

	// =====================================================================
	//  /pets taming <animal>
	// =====================================================================

	/** One line per skill: whether it's unlocked for this level, and what it does with today's numbers. */
	List<Component> describe(Species sp, int level) {
		PetSettings s = s();
		List<Component> lines = new ArrayList<>();
		for (Slot slot : Slot.values()) {
			int at = unlock(slot);
			boolean open = level >= at;
			String what = switch (slot) {
				case FIRST -> switch (sp) {
					case WOLF -> "Its first bite on a new target hits " + fmt(s.pounceBonus) + "% harder.";
					case IRON_GOLEM -> "A walking fortress with 100+ health.";
					case VEX -> fmt(doubleStrikeChance(level)) + "% chance to hit twice as hard (+"
						+ fmt(s.doubleStrikePer5) + "% per 5 levels, up to " + fmt(s.doubleStrikeMax) + "%).";
					case POLAR_BEAR -> "Can't be knocked back, slowed or frozen.";
					case GOAT -> "Every " + ordinal(s.momentumEvery) + " hit lands " + fmt(s.momentumBonus) + "% harder.";
					case PANDA -> "When it wins a fight, it and you get Regeneration I for " + s.blessingSeconds + "s.";
					default -> "";
				};
				case AWARENESS -> "Steps out of fire, lava and cacti, back to your side. No fall damage.";
				case GORE -> fmt(goreChance(level)) + "% chance to deal " + fmt(s.goreMultiplier) + "x damage.";
				case SPECIAL -> switch (sp) {
					case WOLF -> "When you drop below " + fmt(s.howlHealth) + "% health it howls: Strength I and Speed I "
						+ "for " + s.howlSeconds + "s (once every " + s.howlCooldown + "s).";
					case IRON_GOLEM -> "Once per day, a blow that would kill you leaves you at half a heart with "
						+ "Resistance " + roman(s.guardianLevel) + " for " + s.guardianSeconds + "s (while it follows you).";
					case VEX -> "Its strikes ignore " + fmt(phaseArmor(level)) + "% of armor (" + fmt(s.phaseArmorHigh)
						+ "% from level " + s.phaseHighLevel + ").";
					case POLAR_BEAR -> crushHigh(level)
						? "Hits slow (IV) and weaken (II) for " + s.crushSeconds + "s."
						: "Hits slow (II) and weaken (I) for " + s.crushSeconds + "s (IV and II from level "
						+ s.crushHighLevel + ").";
					case GOAT -> "Its " + ordinal(s.momentumEvery) + " hit launches the target high into the air.";
					case PANDA -> fmt(soothChance(Math.max(level, at))) + "% chance on hit to heal itself, you and "
						+ "friends nearby for " + fmt(soothHeal(Math.max(level, at))) + " health, and hurt the undead.";
					default -> "";
				};
				case FAST_FOOD -> fmt(s.fastFoodChance) + "% chance to heal by the damage it deals.";
				case THICK_FUR -> "Takes 1/" + fmt(s.thickFurDivisor) + " damage from attacks and arrows"
					+ (sp == Species.IRON_GOLEM ? " and fire" : "") + "; shakes off burning.";
				case HOLY_HOUND -> "Magic, poison and wither heal it instead of hurting it.";
				case SHOCK_PROOF -> "Takes 1/" + fmt(s.shockProofDivisor) + " damage from explosions and lightning.";
				case CLAWS -> "+" + fmt(s.clawsBonus) + " damage on every hit.";
			};
			MutableComponent line = Component.empty()
				.append(PetMsg.text(open ? " ✔ " : " ✖ ", open ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
				.append(PetMsg.text(name(sp, slot), open ? ChatFormatting.GOLD : ChatFormatting.GRAY))
				.append(PetMsg.text(at > 1 || !open ? " (" + at + ")" : "", ChatFormatting.DARK_GRAY))
				.append(PetMsg.text(" " + what, open ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
			lines.add(line);
		}
		return lines;
	}

	/** A Taming level-up: tells the owner which pet skills just unlocked. */
	void announceUnlocks(ServerPlayer p, int from, int to) {
		if (!on()) {
			return;
		}
		List<String> opened = new ArrayList<>();
		Species out = null;
		PetManager.Active a = mod.pets().active(p.getUUID());
		if (a != null && hasSkills(a.pet.species)) {
			out = a.pet.species;
		}
		for (Slot slot : Slot.values()) {
			int at = unlock(slot);
			if (from < at && to >= at) {
				opened.add(out != null ? name(out, slot) : slot.key.replace('-', ' '));
			}
		}
		if (opened.isEmpty()) {
			return;
		}
		PetMsg.chat(p, Component.empty().append(PetMsg.text("New Taming skill" + (opened.size() > 1 ? "s" : "") + ": ",
				ChatFormatting.GOLD))
			.append(Text.colored(String.join(", ", opened), Skill.TAMING.color()))
			.append(PetMsg.text(out != null ? " (see /pets taming)" : " for your combat pets (see /pets taming)",
				ChatFormatting.GRAY)));
	}

	private static String fmt(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
	}

	private static String ordinal(int n) {
		return n + (n % 100 >= 11 && n % 100 <= 13 ? "th" : switch (n % 10) {
			case 1 -> "st";
			case 2 -> "nd";
			case 3 -> "rd";
			default -> "th";
		});
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> "V";
		};
	}
}
