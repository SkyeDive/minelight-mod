package net.ferret;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BodySharing implements ModInitializer {
	public static final String MOD_ID = "body-sharing";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	// {vessel1: [possessor1, possessor2], vessel2: [possessor]}
	private static final HashMap<UUID, ArrayList<UUID>> SERVER_POSSESSION_RECORD = new HashMap<>();
	private static final HashMap<UUID, ListTag> SPIRIT_OLD_INVENTORY_RECORD = new HashMap<>();
	
	// Sent by possessor spirit to server to update possessed vessels of key input updates
	// Sent by server to vessels to update key inputs
    	// int codeLength
    	// CharSequence keyUpdateCode
    		// CLIENT SENDER	DONE
			// CLIENT RECEIVER	DONE
    		// SERVER SENDER	DONE
			// SERVER RECEIVED	DONE
    public static final ResourceLocation UPDATE_KEYS_ID = new ResourceLocation(MOD_ID, "update_keys");
    
    // Sent by to-be possessor spirit to server to request a change in possessing status
    // Returned by server to update on current possessing status
    	// boolean end
    	// UUID vessel
    		// CLIENT SENDER
    			// REQUEST		TBD
    			// EXIT VESSEL	TBD
    		// CLIENT RECEIVER	TBD
    		// SERVER SENDER
    			// REPLY YES			DONE
    			// REPLY NO				DONE
    			// VESSEL DISCONNECT	TBD
    		// SERVER RECEIVER	WIP
    		// TODO SERVER REMOVES VESSELHOOD AND POSSESSORHOOD OF CLIENTS WHEN CLIENTS DISCONNECT
    		// TODO CLIENTS CLOSE CURRENT SCREEN(S) WHEN RECEIVING SERVER RESPONSE
    		// TODO SERVER RETURNS POSSESSOR GAMEMODE FROM SPECTATOR
    public static final ResourceLocation POSSESSING_UPDATE_ID = new ResourceLocation(MOD_ID, "possessing_update");
    
    // Sent by server to update on current hosting status
    	// boolean hosting
			// CLIENT RECEIVER	DONE
    		// SERVER SENDER
    			// STOP HOST	DONE
    			// START HOST	DONE
    public static final ResourceLocation VESSEL_UPDATE_ID = new ResourceLocation(MOD_ID, "vessel_update");
    
    // TODO in need of redesign
    // Sent by vessel to render their GUI screen onto possessors' screens
    // Bounced by server to possessors
    // executes once per tick, for bandwidth and because GUIs shouldn't need many updates
    	// int x, y, w, h
    	// int[] pixels
    		// CLIENT SENDER	TBD
			// CLIENT RECEIVER	TBD
    		// SERVER SENDER	TBD
			// SERVER RECEIVED	TBD
    public static final ResourceLocation VESSEL_SCREEN_ID = new ResourceLocation(MOD_ID, "vessel_screen");
    
    public static final ResourceLocation KEY_PRESS_ID = new ResourceLocation(MOD_ID, "key_press");
    
    public static final ResourceLocation CHAR_TYPE_ID = new ResourceLocation(MOD_ID, "char_type");
    
    public static final ResourceLocation MOUSE_MOVE_ID = new ResourceLocation(MOD_ID, "mouse_move");
    
    public static final ResourceLocation MOUSE_PRESS_ID = new ResourceLocation(MOD_ID, "mouse_press");
    
    public static final ResourceLocation MOUSE_SCROLL_ID = new ResourceLocation(MOD_ID, "mouse_scroll");
    
    // fields for possessors' copycat GUI
    public static final MenuType<VesselScreenMenu> VESSEL_SCREEN_MENU =
    	Registry.register(BuiltInRegistries.MENU, "%s:vessel_screen".formatted(MOD_ID),
    			new MenuType<VesselScreenMenu>(VesselScreenMenu::new, FeatureFlags.VANILLA_SET));
    public static final VSMProvider VSM_PROVIDER = new VSMProvider();

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		
		LOGGER.info("Initializing...");
		
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			dispatcher.register(
				Commands.literal("ml")
				// Possession command
				.then(Commands.literal("possess")
					// Stop possessing (debug, theoretically impossible to run in-game--use the hotkey instead)
					.then(Commands.literal("stop")
					.executes(context -> {
						Entity src = context.getSource().getEntityOrException();
						if (src instanceof ServerPlayer) {
							ServerPlayer srcPlayer = (ServerPlayer)src;
							if (!isPossessing(srcPlayer.getUUID()))
								context.getSource().sendSystemMessage(Component.literal("(You already aren't possessing anyone)"));
							boolean success = updatePossessing(srcPlayer.server, srcPlayer.getUUID(), true, null);
							if (success)
								context.getSource().sendSuccess(()->Component.literal("Stopped possessing"), true);
							else
								context.getSource().sendFailure(Component.literal("Failed to stop possessing"));
						} else {
							context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
						}
						return 0;
					})
					)
					// Begin possessing someone
					.then(Commands.argument("vessel", EntityArgument.player())
					.executes(context -> {
						Entity src = context.getSource().getEntityOrException();
						if (src instanceof ServerPlayer) {
							ServerPlayer srcPlayer = (ServerPlayer)src;
							for (ServerPlayer vessel : context.getArgument("vessel", EntitySelector.class).findPlayers(context.getSource())) {
								if (srcPlayer.getUUID() != vessel.getUUID()) {
								boolean success = updatePossessing(srcPlayer.server, srcPlayer.getUUID(), false, vessel.getUUID());
									if (success)
										context.getSource().sendSuccess(()->Component.literal("Started possessing"), true);
									else
										context.getSource().sendFailure(Component.literal("Failed to start possessing (are you or your target already possessing?)"));
								} else
									context.getSource().sendFailure(Component.literal("You cannot possess yourself"));
								break;
							}
						} else {
							context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
						}
						return 0;
					})
					)
				)
				// Debug command for testing inventory saving
				.then(Commands.literal("inv")
						// Save executor's inventory
						.then(Commands.literal("save")
						.executes(context -> {
							Entity src = context.getSource().getEntityOrException();
							if (src instanceof ServerPlayer) {
								ServerPlayer srcPlayer = (ServerPlayer)src;
								setOldInventory(srcPlayer.getUUID(), srcPlayer.getInventory().save(new ListTag()));
								context.getSource().sendSuccess(()->Component.literal("Saved inventory"), true);
							} else {
								context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
							}
							return 0;
						})
						)
						// Does executor have a saved inventory?
						.then(Commands.literal("has")
						.executes(context -> {
							Entity src = context.getSource().getEntityOrException();
							if (src instanceof ServerPlayer) {
								ServerPlayer srcPlayer = (ServerPlayer)src;
								boolean success = hasOldInventory(srcPlayer.getUUID());
								if (success)
									context.getSource().sendSuccess(()->Component.literal("True, you have an old inventory"), false);
								else
									context.getSource().sendFailure(Component.literal("False, you do not have an old inventory"));
							} else {
								context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
							}
							return 0;
						})
						)
						// Load executor's saved inventory
						.then(Commands.literal("load")
						.executes(context -> {
							Entity src = context.getSource().getEntityOrException();
							if (src instanceof ServerPlayer) {
								ServerPlayer srcPlayer = (ServerPlayer)src;
								if (hasOldInventory(srcPlayer.getUUID())) {
									srcPlayer.getInventory().load(getOldInventory(srcPlayer.getUUID()));
									context.getSource().sendSuccess(()->Component.literal("Loaded inventory"), true);
								} else
									context.getSource().sendFailure(Component.literal("You do not have an old inventory"));
							} else {
								context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
							}
							return 0;
						})
						)
						// Delete executor's saved inventory
						.then(Commands.literal("delete")
						.executes(context -> {
							Entity src = context.getSource().getEntityOrException();
							if (src instanceof ServerPlayer) {
								ServerPlayer srcPlayer = (ServerPlayer)src;
								removeOldInventory(srcPlayer.getUUID());
								context.getSource().sendSuccess(()->Component.literal("Deleted inventory"), true);
							} else {
								context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
							}
							return 0;
						})
						)
				)
				// Debug command for generic testing--liable to change or vanish
				.then(Commands.literal("test")
				.executes(context -> {
					Entity src = context.getSource().getEntityOrException();
					if (src instanceof ServerPlayer) {
						ServerPlayer srcPlayer = (ServerPlayer)src;
						// Compare UUIDs of the executor as obtained through command context and through the player list
						context.getSource().sendSystemMessage(Component.literal("SV object:"));
						context.getSource().sendSystemMessage(Component.literal(srcPlayer.server.getPlayerList().getPlayer(srcPlayer.getUUID()).toString()));
						context.getSource().sendSystemMessage(Component.literal("Self UUID"));
						context.getSource().sendSystemMessage(Component.literal(srcPlayer.getUUID().toString()));
					} else {
						context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
					}
					return 0;
				})
				)
				// Debug command for testing possessors' copycat GUIs
				.then(Commands.literal("gui")
				.executes(context -> {
					Entity src = context.getSource().getEntityOrException();
					if (src instanceof Player) {
						Player player = (Player)src;
						player.openMenu(VSM_PROVIDER);
					} else {
						context.getSource().sendFailure(Component.literal("Not a ServerPlayer"));
					}
					return 0;
				}))
			);
		});
		
		// server receives packet POSSESSING_UPDATE
		// see if client is trying to start or stop hosting, update values accordingly, respond accordingly if needed
		// doesn't send a response if the field "end" is true
		ServerPlayNetworking.registerGlobalReceiver(POSSESSING_UPDATE_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID aspirant = sendingPlayer.getUUID();
				boolean end = buffer.readBoolean();
				UUID vessel = buffer.readUUID();
				updatePossessing(minecraftServer, aspirant, end, vessel);
			} else {
				LOGGER.error("possessing_update packet: server not running");
			}
		});
		
		// server receives packet UPDATE_KEYS
		// bounce to vessels that the client is possessing, if any
		ServerPlayNetworking.registerGlobalReceiver(UPDATE_KEYS_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID spirit = sendingPlayer.getUUID();
				if (isPossessing(spirit)) {
					UUID vessel = getVessel(spirit);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					int codeLength = buffer.readInt();
					CharSequence keyUpdateSequence = buffer.readCharSequence(codeLength, StandardCharsets.US_ASCII);
					buf2.writeInt(codeLength);
					buf2.writeCharSequence(keyUpdateSequence, StandardCharsets.US_ASCII);
					// server sends packet UPDATE_KEYS
					ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(vessel), UPDATE_KEYS_ID, buf2);
				}
			} else {
				LOGGER.error("update_keys packet: server not running");
			}
		});
		
		// server receives packet VESSEL_SCREEN
		// bounce GUI-screen updates from the sender to anyone possessing them
		ServerPlayNetworking.registerGlobalReceiver(VESSEL_SCREEN_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID vessel = sendingPlayer.getUUID();
				if (isHosting(vessel)) {
					List<UUID> spirits = getPossessors(vessel);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					// x, y, w, h
					buf2.writeInt(buffer.readInt());
					buf2.writeInt(buffer.readInt());
					buf2.writeInt(buffer.readInt());
					buf2.writeInt(buffer.readInt());
					// pixels
					buf2.writeVarIntArray(buffer.readVarIntArray());
					for (UUID spirit : spirits) {
						ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(spirit), VESSEL_SCREEN_ID, buf2);
					}
				}
			} else {
				LOGGER.error("vessel_screen packet: server not running");
			}
		});
		
		// server receives packet KEY_PRESS
		// bounce keypress events from the sender to any vessels they're possessing
		ServerPlayNetworking.registerGlobalReceiver(KEY_PRESS_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID spirit = sendingPlayer.getUUID();
				if (isPossessing(spirit)) {
					UUID vessel = getVessel(spirit);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					// keysym
					buf2.writeInt(buffer.readInt());
					// scancode
					buf2.writeInt(buffer.readInt());
					// action
					buf2.writeInt(buffer.readInt());
					// mods
					buf2.writeInt(buffer.readInt());
					// server sends packet KEY_PRESS
					ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(vessel), KEY_PRESS_ID, buf2);
				}
			} else {
				LOGGER.error("key_press packet: server not running");
			}
		});
		
		// server receives packet CHAR_TYPE
		// bounce chartype events from the sender to any vessels they're possessing
		ServerPlayNetworking.registerGlobalReceiver(CHAR_TYPE_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID spirit = sendingPlayer.getUUID();
				if (isPossessing(spirit)) {
					UUID vessel = getVessel(spirit);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					// codepoint
					buf2.writeInt(buffer.readInt());
					// mods
					buf2.writeInt(buffer.readInt());
					// server sends packet CHAR_TYPE
					ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(vessel), CHAR_TYPE_ID, buf2);
				}
			} else {
				LOGGER.error("char_type packet: server not running");
			}
		});

		// server receives packet MOUSE_MOVE
		// bounce mouse movement events from the sender to any vessels they're possessing
		ServerPlayNetworking.registerGlobalReceiver(MOUSE_MOVE_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID spirit = sendingPlayer.getUUID();
				if (isPossessing(spirit)) {
					UUID vessel = getVessel(spirit);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					// param "d" in source
					buf2.writeDouble(buffer.readDouble());
					// param "e" in source
					buf2.writeDouble(buffer.readDouble());
					// server sends packet MOUSE_MOVE
					ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(vessel), MOUSE_MOVE_ID, buf2);
				}
			} else {
				LOGGER.error("mouse_move packet: server not running");
			}
		});

		// server receives packet MOUSE_PRESS
		// bounce mouse click events from the sender to any vessels they're possessing
		ServerPlayNetworking.registerGlobalReceiver(MOUSE_PRESS_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID spirit = sendingPlayer.getUUID();
				if (isPossessing(spirit)) {
					UUID vessel = getVessel(spirit);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					// param "i" in source
					buf2.writeInt(buffer.readInt());
					// param "j" in source
					buf2.writeInt(buffer.readInt());
					// param "k" in source
					buf2.writeInt(buffer.readInt());
					// server sends packet MOUSE_PRESS
					ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(vessel), MOUSE_PRESS_ID, buf2);
				}
			} else {
				LOGGER.error("mouse_press packet: server not running");
			}
		});

		// server receives packet MOUSE_SCROLL
		// bounce mouse scrolling events from the sender to any vessels they're possessing
		ServerPlayNetworking.registerGlobalReceiver(MOUSE_SCROLL_ID, (minecraftServer, sendingPlayer, serverPacketListener, buffer, responseSender) -> {
			if (minecraftServer.isRunning()) {
				UUID spirit = sendingPlayer.getUUID();
				if (isPossessing(spirit)) {
					UUID vessel = getVessel(spirit);
					FriendlyByteBuf buf2 = PacketByteBufs.create();
					// param "d" in source
					buf2.writeDouble(buffer.readDouble());
					// param "e" in source
					buf2.writeDouble(buffer.readDouble());
					// server sends packet MOUSE_SCROLL
					ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(vessel), MOUSE_SCROLL_ID, buf2);
				}
			} else {
				LOGGER.error("mouse_scroll packet: server not running");
			}
		});
	}
	
	// is player a possessor
	public static boolean isPossessing(UUID player) {
		for (ArrayList<UUID> spiritList : SERVER_POSSESSION_RECORD.values()) {
			for (UUID spirit : spiritList) {
				if (spirit == player)
					return true;
			}
		}
		return false;
	}
	
	// get vessel of this player
	public static UUID getVessel(UUID possessor) {
		for (UUID vessel : SERVER_POSSESSION_RECORD.keySet()) {
			for (UUID spirit : SERVER_POSSESSION_RECORD.getOrDefault(vessel, new ArrayList<UUID>())) {
				if (spirit == possessor)
					return vessel;
			}
		}
		return null;
	}
	
	// is player a vessel
	public static boolean isHosting(UUID player) {
		return SERVER_POSSESSION_RECORD.containsKey(player) && SERVER_POSSESSION_RECORD.get(player).size() != 0;
	}
	
	// get List of possessors of this player
	public static ArrayList<UUID> getPossessors(UUID vessel) {
		if (isHosting(vessel))
			return SERVER_POSSESSION_RECORD.get(vessel);
		return new ArrayList<UUID>();
	}
	
	// common code for updating possession status
	public static boolean updatePossessing(MinecraftServer minecraftServer, UUID aspirant, boolean end, UUID vessel) {
		if (minecraftServer.isRunning()) {
			boolean aspirantIsPossessing = isPossessing(aspirant);
			ServerPlayer sendingPlayer = minecraftServer.getPlayerList().getPlayer(aspirant);
			// If ceasing possession
			if (end) {
				for (UUID iterVessel : SERVER_POSSESSION_RECORD.keySet()) {
					boolean broken = false;
					for (UUID iterSpirit : SERVER_POSSESSION_RECORD.getOrDefault(iterVessel, new ArrayList<UUID>())) {
						if (iterSpirit == aspirant) {
							SERVER_POSSESSION_RECORD.get(iterVessel).remove(aspirant);
							// If this spirit was the final one possessing the vessel
							if (SERVER_POSSESSION_RECORD.get(iterVessel).size() == 0) {
								// Remove empty list
								SERVER_POSSESSION_RECORD.remove(iterVessel);
								// Inform the vessel that they are no longer hosting
								FriendlyByteBuf vesselUpdatePacket = PacketByteBufs.create();
								vesselUpdatePacket.writeBoolean(false);
								// server sends packet VESSEL_UPDATE (stop hosting)
								ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(iterVessel), VESSEL_UPDATE_ID, vesselUpdatePacket);
							}
							broken = true;
							break;
						}
					}
					if (broken)
						break;
				}
				
				// Send failsafe possession update to exiting spirit
				FriendlyByteBuf spiritUpdatePacket = PacketByteBufs.create();
				spiritUpdatePacket.writeBoolean(true);
				spiritUpdatePacket.writeUUID(aspirant);
				ServerPlayNetworking.send(minecraftServer.getPlayerList().getPlayer(aspirant), POSSESSING_UPDATE_ID, spiritUpdatePacket);
				
				// load possessor's inventory
				if (hasOldInventory(aspirant)) {
					ListTag inv = getOldInventory(aspirant);
					if (!inv.isEmpty())
						sendingPlayer.getInventory().load(inv);
				}
				// success
				return true;
			// If (attempting to be) beginning possession
			} else {
				FriendlyByteBuf spiritUpdatePacket = PacketByteBufs.create();
				boolean isInvalid = isHosting(aspirant) || aspirantIsPossessing || isPossessing(vessel);
				
				// aspirant is a vessel || aspirant is already possessing || target vessel is already possessing
				if (isInvalid) {
					// respond NO
					spiritUpdatePacket.writeBoolean(!aspirantIsPossessing);
					if (aspirantIsPossessing)
						spiritUpdatePacket.writeUUID(getVessel(aspirant));
					else
						spiritUpdatePacket.writeUUID(aspirant);
				// If aspirant is not hosting, was not possessing, and target vessel is not possessing
				} else {
					// respond YES
					spiritUpdatePacket.writeBoolean(false);
					spiritUpdatePacket.writeUUID(vessel);
					
					// notify vessel if newly hosting
					ServerPlayer vesselPlayer = minecraftServer.getPlayerList().getPlayer(vessel);
					if (!isHosting(vessel)) {
						FriendlyByteBuf vesselUpdatePacket = PacketByteBufs.create();
						vesselUpdatePacket.writeBoolean(true);
						// server sends packet VESSEL_UPDATE (start hosting)
						ServerPlayNetworking.send(vesselPlayer, VESSEL_UPDATE_ID, vesselUpdatePacket);
					}
					sendingPlayer.setGameMode(GameType.SPECTATOR);
					sendingPlayer.setCamera(vesselPlayer);
					
					// add possessor to record
					if (!SERVER_POSSESSION_RECORD.containsKey(vessel))
						SERVER_POSSESSION_RECORD.put(vessel, new ArrayList<>());
					SERVER_POSSESSION_RECORD.get(vessel).add(aspirant);
					
					// save possessor's inventory
					setOldInventory(aspirant, sendingPlayer.getInventory().save(new ListTag()));
				}
				ServerPlayNetworking.send(sendingPlayer, POSSESSING_UPDATE_ID, spiritUpdatePacket);
				
				// state of success
				return !isInvalid;
			}
		} else {
			LOGGER.error("updatePossessing: server not running");
			return false;
		}
	}
	
	// saved inventory manipulation methods
	public static void setOldInventory(UUID player, ListTag inv) {
		SPIRIT_OLD_INVENTORY_RECORD.put(player, inv);
	}
	
	public static boolean hasOldInventory(UUID player) {
		ListTag inv = SPIRIT_OLD_INVENTORY_RECORD.getOrDefault(player, null);
		if (inv == null)
			inv = new ListTag();
		return !inv.isEmpty();
	}
	
	public static ListTag getOldInventory(UUID player) {
		ListTag inv = SPIRIT_OLD_INVENTORY_RECORD.getOrDefault(player, null);
		if (inv == null)
			inv = new ListTag();
		return inv;
	}
	
	public static void removeOldInventory(UUID player) {
		SPIRIT_OLD_INVENTORY_RECORD.put(player, null);
	}
}