# Animated Armor (Fabric, Minecraft Java 1.21.11)

A tiny client-side mod that lets **resource packs animate worn armor and elytra textures**. Vanilla can only animate
item/block textures; worn armor is drawn from plain textures that ignore animation files. This mod fixes that.

- Needs only **Fabric Loader** (no Fabric API).
- Client-side only. Servers do not need it.
- Packs that use it still work without it: they show the first frame as a normal static texture.

## Status: source only, not yet compiled or tested in the game

This project was written without access to Minecraft's download servers, so it could not be built or run.
What *was* checked:

- every Minecraft class, field and method it uses was looked up in the Yarn `1.21.11+build.4` javadocs;
- the logic in `ArmorAnimations` has unit tests (22 checks, all passing);
- the two Minecraft-facing files compile against stubs of those documented signatures.

If the build or the game reports an error, copy the message — it will point at exactly one line.

## Building

You need a JDK (21 or newer).

```
./gradlew build          # Windows: gradlew.bat build
```

The jar is `build/libs/animatedarmor-1.0.0.jar` (ignore the `-sources` one). Put it in your `mods` folder next to
Fabric Loader for 1.21.11.

No JDK? Push this folder to a GitHub repository: the included workflow (`.github/workflows/build.yml`) builds it on
every push, and the jar appears under the run's **Artifacts**.

## How a pack animates an armor texture

For a texture `assets/<namespace>/textures/entity/equipment/<layer>/<name>.png`, add:

| File | What it is |
|---|---|
| `<name>.png` | The normal texture. Used as the static fallback when the mod is missing. |
| `<name>_frame0.png`, `<name>_frame1.png`, ... | One file per animation frame, same size as the base texture. |
| `<name>.png.mcmeta` | `{ "animatedarmor": { "frames": 16, "frametime": 3 } }` |

- `frames` is a count (frames 0 to n-1) **or** a list of frame numbers, e.g. `[0, 1, 2, 1]`.
- `frametime` is ticks per frame (20 ticks = 1 s). Default 1.
- If any listed frame file is missing, that animation is ignored and the static texture is kept (a warning is logged).
- Works for every layer type: `humanoid`, `humanoid_leggings`, `wings` (elytra) and so on.
- The pack data is re-read every few seconds, so reloading resource packs (F3+T) just works.

## How it works

Every armor layer asks one memoized function in `EquipmentRenderer` (`layerTextures`) which texture to use.
`EquipmentRendererMixin` wraps that function, and for animated textures it swaps in the frame for the current time.
The frames are ordinary textures, so no GPU or texture-upload code is involved, which keeps the mod very small.

Limits: no frame interpolation (use more frames for smoother motion), and animation does not pause with the game.

## Testing the core

```
CP=path/to/gson.jar:path/to/slf4j-api.jar
javac -d out -cp $CP src/main/java/com/summerwaves/animatedarmor/ArmorAnimations.java test/ArmorAnimationsTest.java
java -cp out:$CP com.summerwaves.animatedarmor.ArmorAnimationsTest
```

## License

CC0-1.0 (same as the Fabric example mod this build setup is based on).
