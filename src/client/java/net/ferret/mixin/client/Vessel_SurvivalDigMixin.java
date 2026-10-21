package net.ferret.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.ferret.BodySharingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

// Mixin for restoring the ability of possessors to mine blocks
@Mixin(Minecraft.class)
public class Vessel_SurvivalDigMixin {
	@Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
	private boolean noGrabAttack(MouseHandler mh) {
		return (BodySharingClient.isHosting && ((MouseHandlerAccess)mh).bodySharingMinecraft().screen == null) || mh.isMouseGrabbed();
	}
}
