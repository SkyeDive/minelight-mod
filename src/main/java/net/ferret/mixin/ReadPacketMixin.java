package net.ferret.mixin;

import net.ferret.BodySharing;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Unused mixin for intercepting received packets on the server side
@Mixin(Connection.class)
public class ReadPacketMixin {
	private static boolean goneOnce = false;
	
	@Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
	private static <T extends PacketListener> void readPacket(Packet<T> packet, PacketListener packetListener, CallbackInfo ci) {
		if (packetListener instanceof ServerGamePacketListener && !goneOnce) {
			BodySharing.LOGGER.info("Received packet: " + ((ServerGamePacketListenerImpl)packetListener).player.toString());
			goneOnce = true;
		}
	}
}