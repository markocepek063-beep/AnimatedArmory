package com.summerwaves.animatedarmor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Minecraft-free core of the mod: decides which frame file an animated equipment texture should use right now.
 *
 * <p>A texture {@code textures/entity/equipment/humanoid/netherite.png} is animated when a resource pack ships
 * {@code netherite.png.mcmeta} containing:
 * <pre>
 * { "animatedarmor": { "frames": 16, "frametime": 3 } }
 * </pre>
 * {@code frames} is either a count (frames 0..n-1) or a list of frame numbers; {@code frametime} is in ticks (default 1).
 * Frame {@code i} is the file {@code netherite_frame<i>.png} in the same folder.
 */
public final class ArmorAnimations {
	/** What the core needs from the game's resource system (kept abstract so it can be unit tested). */
	public interface Resources {
		/** Full text of the resource, or {@code null} if it does not exist. */
		String readText(String namespace, String path);

		boolean exists(String namespace, String path);
	}

	record Animation(int[] frames, int frameTime) {
	}

	private record Entry(Animation animation, long expiresAtMs) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("AnimatedArmor");
	private static final String PREFIX = "textures/entity/equipment/";
	private static final String SUFFIX = ".png";
	private static final String META_KEY = "animatedarmor";
	private static final long REFRESH_MS = 3000L;   // re-read pack data this often, so reloading packs just works
	private static final int MAX_FRAMES = 1024;

	private final Resources resources;
	private final LongSupplier clockMs;
	private final Map<String, Entry> cache = new ConcurrentHashMap<>();

	public ArmorAnimations(Resources resources, LongSupplier clockMs) {
		this.resources = resources;
		this.clockMs = clockMs;
	}

	/**
	 * @return the path of the frame to draw now, or {@code null} to keep the original texture untouched.
	 */
	public String resolve(String namespace, String path) {
		if (!path.startsWith(PREFIX) || !path.endsWith(SUFFIX)) {
			return null;
		}
		long now = clockMs.getAsLong();
		String key = namespace + ":" + path;
		Entry entry = cache.get(key);
		if (entry == null || now >= entry.expiresAtMs()) {
			entry = new Entry(load(namespace, path), now + REFRESH_MS);
			cache.put(key, entry);
		}
		Animation animation = entry.animation();
		if (animation == null) {
			return null;
		}
		long tick = now / 50L;
		int slot = (int) ((tick / animation.frameTime()) % animation.frames().length);
		return framePath(path, animation.frames()[slot]);
	}

	static String framePath(String path, int frame) {
		return path.substring(0, path.length() - SUFFIX.length()) + "_frame" + frame + SUFFIX;
	}

	private Animation load(String namespace, String path) {
		String json;
		try {
			json = resources.readText(namespace, path + ".mcmeta");
		} catch (RuntimeException e) {
			return null;
		}
		if (json == null) {
			return null;
		}
		try {
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (!root.has(META_KEY)) {
				return null;
			}
			JsonObject meta = root.getAsJsonObject(META_KEY);
			int[] frames = parseFrames(meta.get("frames"));
			int frameTime = meta.has("frametime") ? Math.max(1, meta.get("frametime").getAsInt()) : 1;
			for (int frame : frames) {
				String framePath = framePath(path, frame);
				if (!resources.exists(namespace, framePath)) {
					LOGGER.warn("Animated armor {}:{} ignored: missing frame file {}", namespace, path, framePath);
					return null;
				}
			}
			LOGGER.info("Animated armor texture {}:{} ({} frames, {} ticks each)", namespace, path, frames.length, frameTime);
			return new Animation(frames, frameTime);
		} catch (RuntimeException e) {
			LOGGER.warn("Animated armor {}:{} ignored: bad .mcmeta ({})", namespace, path, e.toString());
			return null;
		}
	}

	private static int[] parseFrames(JsonElement element) {
		if (element == null) {
			throw new IllegalArgumentException("'frames' is missing");
		}
		int[] frames;
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			frames = new int[array.size()];
			for (int i = 0; i < frames.length; i++) {
				frames[i] = array.get(i).getAsInt();
			}
		} else {
			int count = element.getAsInt();
			frames = new int[Math.max(0, count)];
			for (int i = 0; i < frames.length; i++) {
				frames[i] = i;
			}
		}
		if (frames.length == 0 || frames.length > MAX_FRAMES) {
			throw new IllegalArgumentException("'frames' must contain between 1 and " + MAX_FRAMES + " frames");
		}
		for (int frame : frames) {
			if (frame < 0) {
				throw new IllegalArgumentException("negative frame number");
			}
		}
		return frames;
	}
}
