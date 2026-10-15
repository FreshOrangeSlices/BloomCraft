package com.bloomcraft.core;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A section of a YAML file (a map), with typed getters and dotted paths ("bosses.spawn-chance").
 * Missing or wrongly typed values fall back to the given default.
 */
public final class Cfg {

	private final Map<String, Object> map;

	public Cfg(@Nullable Map<?, ?> raw) {
		Map<String, Object> m = new LinkedHashMap<>();
		if (raw != null) {
			for (Map.Entry<?, ?> e : raw.entrySet()) {
				m.put(String.valueOf(e.getKey()), e.getValue());
			}
		}
		this.map = m;
	}

	public static Cfg empty() {
		return new Cfg(null);
	}

	public @Nullable Object get(String path) {
		Object current = map;
		for (String part : path.split("\\.")) {
			if (!(current instanceof Map<?, ?> m)) {
				return null;
			}
			current = find(m, part);
		}
		return current;
	}

	private static @Nullable Object find(Map<?, ?> m, String key) {
		if (m.containsKey(key)) {
			return m.get(key);
		}
		for (Map.Entry<?, ?> e : m.entrySet()) {
			if (String.valueOf(e.getKey()).equals(key)) {
				return e.getValue();
			}
		}
		return null;
	}

	public boolean contains(String path) {
		return get(path) != null;
	}

	/** The sub-section at this path, or null. */
	public @Nullable Cfg section(String path) {
		Object o = get(path);
		return o instanceof Map<?, ?> m ? new Cfg(m) : null;
	}

	/** The sub-section, or an empty one. */
	public Cfg sectionOrEmpty(String path) {
		Cfg s = section(path);
		return s == null ? empty() : s;
	}

	public Set<String> keys() {
		return Collections.unmodifiableSet(map.keySet());
	}

	public double getDouble(String path, double def) {
		Object o = get(path);
		if (o instanceof Number n) {
			return n.doubleValue();
		}
		if (o instanceof String s) {
			try {
				return Double.parseDouble(s.trim());
			} catch (NumberFormatException ignored) {
			}
		}
		return def;
	}

	public int getInt(String path, int def) {
		Object o = get(path);
		if (o instanceof Number n) {
			return n.intValue();
		}
		if (o instanceof String s) {
			try {
				return (int) Math.round(Double.parseDouble(s.trim()));
			} catch (NumberFormatException ignored) {
			}
		}
		return def;
	}

	public boolean getBool(String path, boolean def) {
		Object o = get(path);
		if (o instanceof Boolean b) {
			return b;
		}
		if (o instanceof String s) {
			return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes");
		}
		return def;
	}

	public String getString(String path, String def) {
		Object o = get(path);
		return o == null || o instanceof Map<?, ?> || o instanceof List<?> ? def : String.valueOf(o);
	}

	public @Nullable String getString(String path) {
		Object o = get(path);
		return o == null || o instanceof Map<?, ?> || o instanceof List<?> ? null : String.valueOf(o);
	}

	public boolean isList(String path) {
		return get(path) instanceof List<?>;
	}

	public List<?> getList(String path) {
		Object o = get(path);
		return o instanceof List<?> l ? l : List.of();
	}

	public List<String> getStringList(String path) {
		List<String> out = new ArrayList<>();
		for (Object o : getList(path)) {
			if (o != null && !(o instanceof Map<?, ?>) && !(o instanceof List<?>)) {
				out.add(String.valueOf(o));
			}
		}
		return out;
	}

	public List<Double> getDoubleList(String path) {
		List<Double> out = new ArrayList<>();
		for (Object o : getList(path)) {
			if (o instanceof Number n) {
				out.add(n.doubleValue());
			}
		}
		return out;
	}

	public List<Integer> getIntList(String path) {
		List<Integer> out = new ArrayList<>();
		for (Object o : getList(path)) {
			if (o instanceof Number n) {
				out.add(n.intValue());
			}
		}
		return out;
	}

	/** Lists of maps (e.g. compass tiers) as sections. */
	public List<Cfg> getSectionList(String path) {
		List<Cfg> out = new ArrayList<>();
		for (Object o : getList(path)) {
			if (o instanceof Map<?, ?> m) {
				out.add(new Cfg(m));
			}
		}
		return out;
	}
}
