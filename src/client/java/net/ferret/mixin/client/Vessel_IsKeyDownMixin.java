package net.ferret.mixin.client;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;

import net.ferret.BodySharingClient;

// Mixin for enabling detection of keys being held down by possessors
@Mixin(InputConstants.class)
public abstract class Vessel_IsKeyDownMixin {
	@Inject(method = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z", at = @At("HEAD"), cancellable = true)
	private static void PossessorKeyHook(long glfwWindowPointer, int keysym, CallbackInfoReturnable<Boolean> cir) {
		boolean nativelyPressed = GLFW.glfwGetKey(glfwWindowPointer, keysym) == 1;
		// Remove the key's held status if the key is released by the vessel
		if (nativelyPressed)
			BodySharingClient.possessorKeys.put(keysym, false);
		// If the key is being pressed natively or virtually, consider it held
		cir.setReturnValue(nativelyPressed || BodySharingClient.possessorKeys.getOrDefault(keysym, false));
		cir.cancel();
	}
}
