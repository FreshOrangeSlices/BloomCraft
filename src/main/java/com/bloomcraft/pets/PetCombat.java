package com.bloomcraft.pets;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;

/**
 * The pets' part of every hit (called from LivingEntity#hurtServer, before the boss pipeline):
 * <ul>
 *   <li>pets don't take fall, suffocation or drowning damage, can't be hurt by their owner, other pets or (unless
 *   pet PvP is on) other players, and their Taming skills soften or turn away what's left;</li>
 *   <li>pets only hurt players they're fighting in a PvP fight their owner turned on;</li>
 *   <li>owners' pet bonuses (a goat's soft landing, an armadillo's scutes...);</li>
 *   <li>afterwards: whoever you fight (or who fights you), your combat pet fights too.</li>
 * </ul>
 */
public final class PetCombat {

	private PetCombat() {
	}

	private static boolean on() {
		Pets mod = Pets.get();
		return mod != null && mod.enabled() && mod.store().ready();
	}

	/** The damage to deal (maybe changed), or -1 to cancel the hit. */
	public static float before(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
		if (!on() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		Pets mod = Pets.get();
		PetManager pets = mod.pets();
		Entity src = source.getEntity();
		PetManager.Active a = pets.of(victim);
		if (a != null) {
			return petHurt(mod, a, source, src, amount);
		}
		if (Pets.isPet(victim)) {
			return -1; // a leftover pet nobody is looking after
		}
		if (src != null && Pets.isPet(src)) {
			// pets never hurt each other, and only hurt players in a PvP fight their owner turned on
			if (victim instanceof ServerPlayer player) {
				PetManager.Active attacker = pets.of(src);
				if (attacker == null || !pets.mayHurt(attacker, player)) {
					return -1;
				}
			}
			if (source.getDirectEntity() instanceof LlamaSpit) {
				PetManager.Active llama = pets.of(src);
				if (llama != null) {
					amount = (float) (2 * llama.pet.power()); // a pet llama's spit stings more as it levels up
				}
			}
		}
		if (victim instanceof ServerPlayer player) {
			amount = mod.rides().riderFall(player, source, amount);
			if (amount < 0) {
				return -1;
			}
			amount = mod.bonuses().ownerHurt(player, source, amount);
		}
		return amount;
	}

	private static float petHurt(Pets mod, PetManager.Active a, DamageSource source, Entity src, float amount) {
		if (a.fainting) {
			return -1;
		}
		if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
			|| source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.DRY_OUT)
			|| source.is(DamageTypes.STALAGMITE)) {
			return -1;
		}
		if (source.is(DamageTypeTags.IS_FIRE) && a.pet.species == Species.PIG && a.pet.maxed()) {
			a.entity.clearFire();
			return -1; // a max-level pig doesn't burn (it'd be bacon)
		}
		if (src != null) {
			// other players can't hurt your pet, unless it's in a PvP fight with them (pet PvP turned on)
			if (src.getUUID().equals(a.owner) || Pets.isPet(src)
				|| (src instanceof ServerPlayer attacker && mod.settings().protectFromPlayers
				&& !mod.pets().mayHurt(a, attacker))) {
				return -1;
			}
			if (src instanceof LivingEntity attacker && !(src instanceof Player)) {
				if (a.home != null) {
					mod.pets().defend(a, attacker);
				} else {
					ServerPlayer owner = PetManager.player(a.owner);
					if (owner != null) {
						mod.pets().engage(owner, attacker);
					}
				}
			}
		}
		return mod.skills().hurt(a, source, amount); // Taming: Thick Fur, Holy Hound, Shock Proof...
	}

	/** A hit went through and took {@code dealt} health: pets join the fight. */
	public static void after(LivingEntity victim, DamageSource source, float dealt) {
		if (!on()) {
			return;
		}
		PetManager pets = Pets.get().pets();
		Entity src = source.getEntity();
		if (src == null || src == victim) {
			if (victim instanceof ServerPlayer p) {
				Pets.get().skills().ownerHurt(p);
			}
			return;
		}
		if (src instanceof ServerPlayer p && !Pets.isPet(victim) && (!(victim instanceof Player) || pets.pvp(p))) {
			pets.engage(p, victim);
		}
		if (victim instanceof ServerPlayer p && src instanceof LivingEntity attacker && !Pets.isPet(src)
			&& (!(src instanceof Player) || pets.pvp(p))) {
			pets.engage(p, attacker);
		}
		if (victim instanceof ServerPlayer p) {
			Pets.get().skills().ownerHurt(p); // a wolf's Rallying Howl when you drop low
		}
	}
}
