package com.summerwaves.animatedarmor.mixin;

import com.summerwaves.animatedarmor.ArmorAnimationBridge;
import com.summerwaves.animatedarmor.ArmorAnimations;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Every armor layer (and elytra wings) gets its texture {@link Identifier} from {@code layerTextures}.
 * That function is memoized by vanilla, so we wrap it: the original lookup still happens once, and our wrapper then
 * swaps in the current animation frame on every call.
 */
@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
	// Raw Function on purpose: its key type is a private vanilla record we do not need to touch.
	@SuppressWarnings({"rawtypes", "unused"})
	@Shadow
	@Mutable
	@Final
	private Function layerTextures;

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "<init>", at = @At("RETURN"))
	private void animatedarmor$wrapLayerTextures(EquipmentModelLoader equipmentModelLoader,
												 SpriteAtlasTexture armorTrimsAtlas, CallbackInfo ci) {
		final Function original = this.layerTextures;
		final ArmorAnimations animations = ArmorAnimationBridge.createAnimations();
		this.layerTextures = key -> ArmorAnimationBridge.resolve(animations, (Identifier) original.apply(key));
	}
}
