package net.ferret.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.ferret.BodySharing;
import net.ferret.BodySharingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.network.FriendlyByteBuf;

// Mixin for reading mouse events and handling events as expected when possessed
@Mixin(MouseHandler.class)
public abstract class MouseReaderMixin {
	@Shadow
	Minecraft minecraft;
	
	// Possessor mouse movement
	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	public void interceptOnMove(long window, double d, double e, CallbackInfo ci) {
		if (BodySharingClient.isPossessing && minecraft.isRunning() && minecraft.getCurrentServer() != null) {
			FriendlyByteBuf packet = PacketByteBufs.create();
			packet.writeDouble(d);
			packet.writeDouble(e);
			ClientPlayNetworking.send(BodySharing.MOUSE_MOVE_ID, packet);
			ci.cancel();
		}
	}
	
	// Possessor mouse click
	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	public void interceptOnPress(long window, int i, int j, int k, CallbackInfo ci) {
		if (BodySharingClient.isPossessing && minecraft.isRunning() && minecraft.getCurrentServer() != null) {
			FriendlyByteBuf packet = PacketByteBufs.create();
			packet.writeInt(i);
			packet.writeInt(j);
			packet.writeInt(k);
			ClientPlayNetworking.send(BodySharing.MOUSE_PRESS_ID, packet);
			ci.cancel();
		}
	}
	
	// Possessor mouse scrolling
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	public void interceptOnScroll(long window, double d, double e, CallbackInfo ci) {
		if (BodySharingClient.isPossessing && minecraft.isRunning() && minecraft.getCurrentServer() != null) {
			FriendlyByteBuf packet = PacketByteBufs.create();
			packet.writeDouble(d);
			packet.writeDouble(e);
			ClientPlayNetworking.send(BodySharing.MOUSE_SCROLL_ID, packet);
			ci.cancel();
		}
	}
	
	// Enable vessel mouse movement when the mouse is unlocked
	@Redirect(method = "onMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
	public boolean noGrabMove(MouseHandler mh) {
		return (BodySharingClient.isHosting && minecraft.screen == null) || mh.isMouseGrabbed();
	}
	
	// Enable vessel mouse movement when window is unfocused
	@Redirect(method = "onMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z"))
	public boolean noActiveMove(Minecraft mc) {
		return mc.isWindowActive() || BodySharingClient.isHosting;
	}
	
	// Enable vessel camera turning when the mouse is unlocked
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
	public boolean noGrabTurn(MouseHandler mh) {
		return (BodySharingClient.isHosting && minecraft.screen == null) || mh.isMouseGrabbed();
	}
	
	// Enable vessel camera turning when window is unfocused
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z"))
	public boolean noActiveTurn(Minecraft mc) {
		return mc.isWindowActive() || BodySharingClient.isHosting;
	}
}
