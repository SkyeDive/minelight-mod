package net.ferret.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.ferret.BodySharing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;

// Mixin for enabling storing and loading of saved inventories alongside the standard player data
@Mixin(Player.class)
public class SaveInventoryMixin {
	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	public void saveOldInv(CompoundTag compoundTag, CallbackInfo ci) {
		UUID playerUUID = ((Player)(Object)this).getUUID();
		if (BodySharing.hasOldInventory(playerUUID))
			compoundTag.put("OldInventory", BodySharing.getOldInventory(playerUUID));
	}
	
	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	public void readOldInv(CompoundTag compoundTag, CallbackInfo ci) {
		UUID playerUUID = ((Player)(Object)this).getUUID();
		if (compoundTag.contains("OldInventory")) {
			ListTag listTag = compoundTag.getList("OldInventory", 10);
			if (!listTag.isEmpty())
				BodySharing.setOldInventory(playerUUID, listTag);
		}
	}
}
