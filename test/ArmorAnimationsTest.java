package com.summerwaves.animatedarmor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Dependency-free tests for the core logic. Run with the commands in README.md ("Testing the core"). */
public final class ArmorAnimationsTest {
	private static int failures = 0;

	private static final String BASE = "textures/entity/equipment/humanoid/netherite.png";

	static final class FakeResources implements ArmorAnimations.Resources {
		final Map<String, String> files = new HashMap<>();
		int reads = 0;

		FakeResources put(String path, String text) {
			files.put("minecraft:" + path, text);
			return this;
		}

		FakeResources frames(String path, int... frames) {
			for (int f : frames) {
				put(ArmorAnimations.framePath(path, f), "png");
			}
			return this;
		}

		@Override
		public String readText(String ns, String path) {
			reads++;
			return files.get(ns + ":" + path);
		}

		@Override
		public boolean exists(String ns, String path) {
			return files.containsKey(ns + ":" + path);
		}
	}

	static void check(boolean ok, String what) {
		if (!ok) {
			failures++;
		}
		System.out.println((ok ? "  ok   " : "  FAIL ") + what);
	}

	static void eq(Object actual, Object expected, String what) {
		check(java.util.Objects.equals(actual, expected), what + (java.util.Objects.equals(actual, expected) ? "" : "  (got " + actual + ", expected " + expected + ")"));
	}

	public static void main(String[] args) {
		AtomicLong clock = new AtomicLong(0);

		// no mcmeta -> untouched
		ArmorAnimations a = new ArmorAnimations(new FakeResources(), clock::get);
		eq(a.resolve("minecraft", BASE), null, "texture without .mcmeta is left alone");

		// not an equipment texture -> untouched, and never even read
		FakeResources spy = new FakeResources();
		a = new ArmorAnimations(spy, clock::get);
		eq(a.resolve("minecraft", "textures/item/diamond_sword.png"), null, "non-equipment texture is left alone");
		eq(spy.reads, 0, "non-equipment texture triggers no resource reads");

		// frame count + frametime
		FakeResources r = new FakeResources()
				.put(BASE + ".mcmeta", "{\"animatedarmor\":{\"frames\":4,\"frametime\":3}}")
				.frames(BASE, 0, 1, 2, 3);
		a = new ArmorAnimations(r, clock::get);
		String prefix = "textures/entity/equipment/humanoid/netherite_frame";
		clock.set(0);
		eq(a.resolve("minecraft", BASE), prefix + "0.png", "tick 0 -> frame 0");
		clock.set(2 * 50);
		eq(a.resolve("minecraft", BASE), prefix + "0.png", "tick 2 still frame 0 (frametime 3)");
		clock.set(3 * 50);
		eq(a.resolve("minecraft", BASE), prefix + "1.png", "tick 3 -> frame 1");
		clock.set(11 * 50);
		eq(a.resolve("minecraft", BASE), prefix + "3.png", "tick 11 -> frame 3");
		clock.set(12 * 50);
		eq(a.resolve("minecraft", BASE), prefix + "0.png", "tick 12 loops back to frame 0");

		// explicit frame list
		r = new FakeResources()
				.put(BASE + ".mcmeta", "{\"animatedarmor\":{\"frames\":[2,0,2],\"frametime\":1}}")
				.frames(BASE, 0, 2);
		a = new ArmorAnimations(r, clock::get);
		clock.set(0);
		eq(a.resolve("minecraft", BASE), prefix + "2.png", "frame list: first entry");
		clock.set(50);
		eq(a.resolve("minecraft", BASE), prefix + "0.png", "frame list: second entry");

		// default frametime = 1
		r = new FakeResources().put(BASE + ".mcmeta", "{\"animatedarmor\":{\"frames\":2}}").frames(BASE, 0, 1);
		a = new ArmorAnimations(r, clock::get);
		clock.set(50);
		eq(a.resolve("minecraft", BASE), prefix + "1.png", "default frametime is 1 tick");

		// missing frame file -> animation disabled (static texture kept), no crash
		r = new FakeResources().put(BASE + ".mcmeta", "{\"animatedarmor\":{\"frames\":3}}").frames(BASE, 0, 1);
		a = new ArmorAnimations(r, clock::get);
		eq(a.resolve("minecraft", BASE), null, "missing frame file disables the animation");

		// malformed / foreign mcmeta -> untouched
		for (String bad : new String[]{"not json", "{}", "{\"animation\":{\"frames\":[0,1]}}",
				"{\"animatedarmor\":{}}", "{\"animatedarmor\":{\"frames\":0}}", "{\"animatedarmor\":{\"frames\":\"x\"}}",
				"{\"animatedarmor\":{\"frames\":[-1]}}"}) {
			r = new FakeResources().put(BASE + ".mcmeta", bad).frames(BASE, 0, 1);
			a = new ArmorAnimations(r, clock::get);
			eq(a.resolve("minecraft", BASE), null, "bad meta ignored: " + bad);
		}

		// results are cached, then refreshed after the TTL so pack reloads take effect
		r = new FakeResources().put(BASE + ".mcmeta", "{\"animatedarmor\":{\"frames\":2}}").frames(BASE, 0, 1);
		a = new ArmorAnimations(r, clock::get);
		clock.set(0);
		a.resolve("minecraft", BASE);
		int readsAfterFirst = r.reads;
		clock.set(1000);
		a.resolve("minecraft", BASE);
		eq(r.reads, readsAfterFirst, "second lookup within 3 s is served from cache");
		r.files.remove("minecraft:" + BASE + ".mcmeta");
		clock.set(3500);
		eq(a.resolve("minecraft", BASE), null, "after the refresh interval a removed .mcmeta stops the animation");

		// other namespaces work too
		FakeResources r2 = new FakeResources();
		r2.files.put("mypack:" + BASE + ".mcmeta", "{\"animatedarmor\":{\"frames\":1}}");
		r2.files.put("mypack:" + ArmorAnimations.framePath(BASE, 0), "png");
		a = new ArmorAnimations(r2, clock::get);
		eq(a.resolve("mypack", BASE), prefix + "0.png", "non-minecraft namespace");

		System.out.println(failures == 0 ? "\nALL TESTS PASSED" : "\n" + failures + " TEST(S) FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}
}
