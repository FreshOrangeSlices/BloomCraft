package com.bloomcraft.bosses.config;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.item.EquipmentType;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Text;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * One stat an Empowerment Rune can carry (config: empowerment.stats). Most are attribute bonuses; "effect" stats
 * (loot, fortune) are multipliers the mod applies itself.
 */
public record EmpowermentStat(
	String id,
	@Nullable Holder<Attribute> attribute,
	@Nullable Holder<Attribute> extraAttribute,
	@Nullable String effect,
	AttributeModifier.Operation operation,
	String displayName,
	boolean percent,
	double weight,
	List<String> appliesTo,
	Map<BossRank, double[]> ranges
) {

	public static final String LOOT = "loot";
	public static final String FORTUNE = "fortune";

	public boolean fits(EquipmentType type) {
		for (String group : appliesTo) {
			if (type.matches(group)) {
				return true;
			}
		}
		return false;
	}

	/** The amount a rune of this rank carries (with fixed values, every rune of a rank is the same so they stack). */
	public double roll(BossRank rank, boolean fixed) {
		double[] range = ranges.get(rank);
		if (range == null) {
			return 0;
		}
		double value = fixed ? range[1] : Rng.between(range[0], range[1]);
		return percent ? Math.round(value * 1000.0) / 1000.0 : Math.round(value * 100.0) / 100.0;
	}

	/** "+1.5 Attack Damage", "+6% Movement Speed" or "x1.25 Mob Loot" */
	public String format(double amount) {
		if (effect != null) {
			return "x" + Text.num(1.0 + amount) + " " + displayName;
		}
		if (percent) {
			return "+" + Text.num(amount * 100.0) + "% " + displayName;
		}
		return "+" + Text.num(amount) + " " + displayName;
	}

	public String rangeText(BossRank rank, boolean fixed) {
		double[] range = ranges.get(rank);
		if (range == null) {
			return "-";
		}
		if (fixed || range[0] == range[1]) {
			return percent ? Text.num(range[1] * 100) + "%" : Text.num(range[1]);
		}
		if (percent) {
			return Text.num(range[0] * 100) + "-" + Text.num(range[1] * 100) + "%";
		}
		return Text.num(range[0]) + "-" + Text.num(range[1]);
	}

	public String fitsText() {
		StringJoiner joiner = new StringJoiner(", ");
		for (String group : appliesTo) {
			joiner.add(EquipmentType.describeGroup(group));
		}
		return joiner.toString();
	}
}
