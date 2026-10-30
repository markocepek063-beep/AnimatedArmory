package com.summerwaves.animatedarmor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/** Connects {@link ArmorAnimations} to Minecraft's resource manager and {@link Identifier}s. */
public final class ArmorAnimationBridge {
	private static final ArmorAnimations.Resources MINECRAFT = new ArmorAnimations.Resources() {
		@Override
		public String readText(String namespace, String path) {
			Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager()
					.getResource(Identifier.of(namespace, path));
			if (resource.isEmpty()) {
				return null;
			}
			try (BufferedReader reader = resource.get().getReader()) {
				return reader.lines().collect(Collectors.joining("\n"));
			} catch (IOException e) {
				return null;
			}
		}

		@Override
		public boolean exists(String namespace, String path) {
			return MinecraftClient.getInstance().getResourceManager()
					.getResource(Identifier.of(namespace, path)).isPresent();
		}
	};

	/** Frame identifiers are reused so animating does not allocate on every armor piece of every frame. */
	private static final ConcurrentMap<String, Identifier> INTERNED = new ConcurrentHashMap<>();

	private ArmorAnimationBridge() {
	}

	public static ArmorAnimations createAnimations() {
		return new ArmorAnimations(MINECRAFT, System::currentTimeMillis);
	}

	/** The identifier to draw right now for {@code id}: a frame of its animation, or {@code id} itself. */
	public static Identifier resolve(ArmorAnimations animations, Identifier id) {
		String path = animations.resolve(id.getNamespace(), id.getPath());
		if (path == null) {
			return id;
		}
		return INTERNED.computeIfAbsent(id.getNamespace() + ":" + path, k -> Identifier.of(id.getNamespace(), path));
	}
}
