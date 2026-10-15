package com.bloomcraft.bosses.relic.effects;

import com.bloomcraft.bosses.relic.BaseRelic;
import com.bloomcraft.bosses.relic.RelicContext;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Text;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** "Burdened" relics: good relics with a built-in drawback (a risk you choose, unlike a curse). */
public final class Burdened {

	private Burdened() {
	}

	public static final class Greed extends BaseRelic {
		private double extraDropChance = 40;
		private double hungerSeconds = 20;

		public Greed() {
			super("greed", "Greed", false);
		}

		@Override
		public void load(Cfg s) {
			extraDropChance = s.getDouble("extra-drop-chance", 40);
			hungerSeconds = s.getDouble("hunger-drain-seconds", 20);
		}

		@Override
		public String description() {
			return "Burdened: " + Text.num(extraDropChance) + "% chance mobs drop an extra item, but you lose"
				+ " hunger every " + Text.num(hungerSeconds) + "s.";
		}

		@Override
		public boolean passive() {
			return true;
		}

		@Override
		public void onPassive(RelicContext ctx) {
			ServerPlayer p = ctx.player();
			if (ctx.manager().ready(p, id(), (int) Math.round(hungerSeconds * 20))) {
				p.causeFoodExhaustion(4f); // 4 exhaustion = 1 hunger point
			}
		}

		@Override
		public void onKill(RelicContext ctx, LivingEntity victim, boolean victimWasBoss) {
			// normal mobs only, so Greed doesn't overlap with Hunter's Mark's boss rewards
			if (victimWasBoss || victim instanceof Player || !Rng.chance(extraDropChance)) {
				return;
			}
			List<ItemEntity> drops = RelicUtil.freshDrops(victim);
			if (drops.isEmpty()) {
				return;
			}
			ItemStack pick = Rng.pick(drops).getItem().copyWithCount(1);
			victim.spawnAtLocation(ctx.player().level(), pick);
		}
	}

	public static final class HeavyCrown extends BaseRelic {
		private double armor = 4;
		private double knockbackResistance = 0.5;
		private double slowness = 15;

		public HeavyCrown() {
			super("heavy-crown", "Heavy Crown", false);
		}

		@Override
		public void load(Cfg s) {
			armor = s.getDouble("armor", 4);
			knockbackResistance = s.getDouble("knockback-resistance", 0.5);
			slowness = s.getDouble("speed-penalty", 15);
		}

		@Override
		public String description() {
			return "Burdened: +" + Text.num(armor) + " armor and +" + Text.num(knockbackResistance * 100)
				+ "% knockback resistance, but " + Text.num(slowness) + "% slower.";
		}

		@Override
		public List<AttributeBonus> attributeBonuses() {
			return List.of(
				new AttributeBonus(Attributes.ARMOR, armor, AttributeModifier.Operation.ADD_VALUE),
				new AttributeBonus(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance, AttributeModifier.Operation.ADD_VALUE),
				new AttributeBonus(Attributes.MOVEMENT_SPEED, -slowness / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
