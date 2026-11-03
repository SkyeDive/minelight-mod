package net.ferret;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.ferret.mixin.client.KeyboardHandlerAccess;
import net.ferret.mixin.client.MouseHandlerAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class BodySharingClient implements ClientModInitializer {
	public static HashMap<Integer, Boolean> possessorKeys = new HashMap<>();
	public static HashMap<Integer, Boolean> selfKeyRecord = new HashMap<>();
	public static ArrayList<Integer> selfUpdatedKeys = new ArrayList<>();
	public static boolean isPossessing = false;
	public static @Nullable UUID possessing = null;
	public static boolean isHosting = false;
	public static KeyMapping stopPossession = new KeyMapping("key.minelight.stop_possess", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.KEY_F12, KeyMapping.CATEGORY_MULTIPLAYER);
	public static KeyMapping getGUI = new KeyMapping("key.minelight.get_gui", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), KeyMapping.CATEGORY_INVENTORY);
	public static ArrayList<KeyMapping> guiKeys = new ArrayList<>();
	public static TextureTarget guiTarget;
	public static RenderBuffers renderBuffers = new RenderBuffers();
	// used by vessels for sending screen update info to possessors
	public static final Object guiFrameSync = new Object();
	public static @Nullable NativeImage guiFrameLast = null;
	public static @Nullable NativeImage guiFrame = null;
	public static boolean sendFullScreen = true;
	// used by spirits for receiving and rendering screens from vessels
	public static final Object copycatScreenSync = new Object();
	public static @Nullable NativeImage vesselScreen = null; // in original size from vessel
	public static @Nullable NativeImage resizedScreen = null; // stretched to possessor's screen size
	
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		
		Minecraft.getInstance().execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			guiTarget = new TextureTarget(
				mc.getMainRenderTarget().width,
				mc.getMainRenderTarget().height,
				true, Minecraft.ON_OSX);
		});
		
		// Possessor copycat GUI
		MenuScreens.register(BodySharing.VESSEL_SCREEN_MENU, VesselScreen::new);
		
		// Keybinds
		KeyBindingHelper.registerKeyBinding(stopPossession);
		guiKeys.add(KeyBindingHelper.registerKeyBinding(getGUI));
		
		// Debug clientside command for field manipulation
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("mlc")
				.then(ClientCommandManager.literal("host")
					.then(ClientCommandManager.argument("isHosting", BoolArgumentType.bool())
					.executes(context -> {
						Boolean newHostingVal = context.getArgument("isHosting", Boolean.class);
						isHosting = newHostingVal;
						context.getSource().sendFeedback(Component.literal("Set isHosting to " + String.valueOf(newHostingVal)));
						return 0;
					}))
				)
			);
		});
		
		// run every tick, clientside
		ClientTickEvents.START_CLIENT_TICK.register((minecraftClient) -> {
			// Debug hotkey for testing GUI code
			if (getGUI.consumeClick()) {}
			
			if (minecraftClient.isRunning() && serverRunning()) {
				// Hotkey to stop possessing
				if (stopPossession.consumeClick()) {
					if (isPossessing) {
						requestStopPossessing();
					}
				}
					
				// Vessel per-tick code
				if (isHosting) {
					RenderSystem.recordRenderCall(() -> {
						synchronized (guiFrameSync) {
							if (renderGUI()) {
								if (sendFullScreen || guiFrameLast == null) {
									sendFullScreen = false;
									FriendlyByteBuf packet = PacketByteBufs.create();
									// isReplacing
									packet.writeBoolean(true);
									// x, y, w, h
									packet.writeInt(0);
									packet.writeInt(0);
									packet.writeInt(guiFrame.getWidth());
									packet.writeInt(guiFrame.getHeight());
									// pixels
									packet.writeVarIntArray(guiFrame.getPixelsRGBA());
									// client sends packet VESSEL_SCREEN
									ClientPlayNetworking.send(BodySharing.VESSEL_SCREEN_ID, packet);
								} else if (guiFrameLast != null) {
									NativeImageHandler.Bounds bounds = NativeImageHandler.getChangedRect(guiFrameLast, guiFrame);
									if (bounds != null) {
										NativeImage changed = NativeImageHandler.crop(guiFrame, bounds);
										if (changed != null) {
											FriendlyByteBuf packet = PacketByteBufs.create();
											// isReplacing
											packet.writeBoolean(bounds.w == guiFrame.getWidth() && bounds.h == guiFrame.getHeight());
											// x, y, w, h
											packet.writeInt(bounds.x);
											packet.writeInt(bounds.y);
											packet.writeInt(bounds.w);
											packet.writeInt(bounds.h);
											// pixels
											packet.writeVarIntArray(changed.getPixelsRGBA());
											// client sends packet VESSEL_SCREEN
											ClientPlayNetworking.send(BodySharing.VESSEL_SCREEN_ID, packet);
											if (changed != guiFrame)
												changed.close();
										} else {
											BodySharing.LOGGER.info("Unable to crop");
										}
									}
								}
							} else if (!isGUIOpen() && guiFrame != null) {
								// gui has been closed; clean up gui frames
								if (guiFrameLast != null)
									guiFrameLast.close();
								guiFrameLast = null;
								guiFrame.close();
								guiFrame = null;
								
								// Send packet to close container
								FriendlyByteBuf packet = PacketByteBufs.create();
								// isReplacing
								packet.writeBoolean(true);
								// x, y, w, h
								packet.writeInt(0);
								packet.writeInt(0);
								packet.writeInt(0);
								packet.writeInt(0);
								// pixels
								int[] pixels = {};
								packet.writeVarIntArray(pixels);
								// client sends packet VESSEL_SCREEN
								ClientPlayNetworking.send(BodySharing.VESSEL_SCREEN_ID, packet);
							}
						}
					});
					
				// Possessor per-tick code
				} else if (isPossessing) {
					// Clear copycat screen when the GUI is closed
					synchronized (copycatScreenSync) {
						if (vesselScreen != null && !isGUIOpen()) {
							// Delete old copycat data
							vesselScreen.close();
							vesselScreen = null;
							// Wipe resizedScreen, it will automatically reinstantiate later to transparent
							if (resizedScreen != null)
								resizedScreen.close();
							resizedScreen = null;
						}
					}
					
					FriendlyByteBuf packet = PacketByteBufs.create();
					// Encode held keys
					// Stored in string as <int><boolean>;<int><boolean>
					String keyUpdateCode = "";
					for (Integer keysym : selfUpdatedKeys) {
						keyUpdateCode += keysym.toString();
						keyUpdateCode += selfKeyRecord.getOrDefault(keysym, false) ? "T" : "F";
						keyUpdateCode += ";";
					}
					// Ensure no trailing semicolon
					if (keyUpdateCode.length() > 0)
						keyUpdateCode = keyUpdateCode.substring(0, keyUpdateCode.length() - 1);
					// Store data
					packet.writeInt(keyUpdateCode.length());
					packet.writeCharSequence(keyUpdateCode, StandardCharsets.US_ASCII);
					selfUpdatedKeys.clear();
					// Bounce over server to vessels
					// client sends packet UPDATE_KEYS
					ClientPlayNetworking.send(BodySharing.UPDATE_KEYS_ID, packet);
				}
			}
		});
		
		// client receives packet UPDATE_KEYS
		// update state of held keys, as influenced by this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.UPDATE_KEYS_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			int codeLength = buffer.readInt();
			CharSequence keyUpdateSequence = buffer.readCharSequence(codeLength, StandardCharsets.US_ASCII);
			String keyUpdateCode = String.valueOf(keyUpdateSequence);
			if (keyUpdateCode.length() > 0)
				for (String update : keyUpdateCode.split(";")) {
					if (update.length() != 0) {
						int keysym = Integer.valueOf(update.substring(0, update.length() - 1));
						boolean value = update.charAt(update.length() - 1) == 'T' ? true : false;
						possessorKeys.put(keysym, value);
					}
				}
		});
		
		// client receives packet KEY_PRESS
		// Receive and process keypress events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.KEY_PRESS_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			int keysym = buffer.readInt();
			int scancode = buffer.readInt();
			int action = buffer.readInt();
			int mods = buffer.readInt();
			minecraftClient.execute(() -> {
				if (!(minecraftClient.screen == null && keysym == InputConstants.KEY_ESCAPE))
					minecraftClient.keyboardHandler.keyPress(window, keysym, scancode, action, mods);
			});
		});

		// client receives packet CHAR_TYPE
		// Receive and process chartype events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.CHAR_TYPE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			int codepoint = buffer.readInt();
			int mods = buffer.readInt();
			minecraftClient.execute(() ->
				((KeyboardHandlerAccess)(minecraftClient.keyboardHandler)).bodySharingCharTyped(window, codepoint, mods));
		});

		// client receives packet MOUSE_MOVE
		// Receive and process mouse movement events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.MOUSE_MOVE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			Window w = minecraftClient.getWindow();
			long window = w.getWindow();
			double d = buffer.readDouble() * w.getWidth();
			double e = buffer.readDouble() * w.getHeight();
			((MouseHandlerAccess)(minecraftClient.mouseHandler)).bodySharingOnMove(window, d, e);
		});

		// client receives packet MOUSE_PRESS
		// Receive and process mouse click events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.MOUSE_PRESS_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			int i = buffer.readInt();
			int j = buffer.readInt();
			int k = buffer.readInt();
			minecraftClient.execute(() ->
				((MouseHandlerAccess)(minecraftClient.mouseHandler)).bodySharingOnPress(window, i, j, k));
		});

		// client receives packet MOUSE_SCROLL
		// Receive and process mouse scrolling events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.MOUSE_SCROLL_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			double d = buffer.readDouble();
			double e = buffer.readDouble();
			minecraftClient.execute(() ->
				((MouseHandlerAccess)(minecraftClient.mouseHandler)).bodySharingOnScroll(window, d, e));
		});
		
		// client receives packet POSSESSING_UPDATE
		// update this client's state of isPossessing
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.POSSESSING_UPDATE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			boolean end = buffer.readBoolean();
			UUID vessel = buffer.readUUID();
			if (!end) {
				if (!isPossessing)
					BodySharing.LOGGER.info("Beginning possessing");
				possessing = vessel;

				synchronized (copycatScreenSync) {
					Window window = minecraftClient.getWindow();
					if (vesselScreen != null)
						vesselScreen.close();
					vesselScreen = null;
					if (resizedScreen != null)
						resizedScreen.close();
					resizedScreen = new NativeImage(window.getWidth(), window.getHeight(), false);
				}
			} else {
				if (isPossessing)
					BodySharing.LOGGER.info("Stopping possession");
				possessing = null;
				selfUpdatedKeys.clear();
			}
			isPossessing = !end;
		});
		
		// client receives packet VESSEL_UPDATE
		// update this client's state of isHosting
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.VESSEL_UPDATE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			boolean isVessel = buffer.readBoolean();
			if (!isVessel) {
				if (isHosting) {
					BodySharing.LOGGER.info("Stopping hosting");
					
					// clear guiFrame fields
					synchronized (guiFrameSync) {
						if (guiFrameLast != null)
							guiFrameLast.close();
						guiFrameLast = null;
						if (guiFrame != null)
							guiFrame.close();
						guiFrame = null;
					}
				}
				possessorKeys.clear();
			} else {
				if (!isHosting) {
					BodySharing.LOGGER.info("Beginning hosting");

					// clear guiFrame fields
					synchronized (guiFrameSync) {
						if (guiFrameLast != null)
							guiFrameLast.close();
						guiFrameLast = null;
						if (guiFrame != null)
							guiFrame.close();
						guiFrame = null;
					}
				} else
					BodySharing.LOGGER.info("Resending full screen");
				sendFullScreen = true;
			}
			isHosting = isVessel;
		});
		
		// client receives packet VESSEL_SCREEN
		// update this client's state of copycat gui
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.VESSEL_SCREEN_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			// isReplacing
			boolean isReplacing = buffer.readBoolean();
			// x, y, w, h
			int x = buffer.readInt();
			int y = buffer.readInt();
			int w = buffer.readInt();
			int h = buffer.readInt();
			// pixels
			int[] pixels = buffer.readVarIntArray();
			
			// reconstruct NativeImage
			NativeImage update = NativeImageHandler.fromPixelArray(w, h, pixels);
			BodySharing.LOGGER.info("screen update (%d, %d, %d, %d)".formatted(x, y, w, h));
			synchronized (copycatScreenSync) {
				// GUI has closed, clear vesselScreen
				if (update == null) {
					if (vesselScreen != null)
						vesselScreen.close();
					vesselScreen = null;
					if (resizedScreen != null)
						resizedScreen.close(); //TODO make sure this gets rid of the flicker and doesn't crash
					resizedScreen = null;
					return;
				}
				
				if (isReplacing || (vesselScreen == null && x == 0 && y == 0)) {
					if (vesselScreen != null)
						vesselScreen.close();
					vesselScreen = update;
				} else if (vesselScreen == null) {
					// we've been sent an update but don't have a base--ignore it to avoid errors or broken screens
					return;
				} else {
					// update existing screen
					update.copyRect(vesselScreen, 0, 0, x, y, w, h, false, false);
					update.close();
				}
				Window window = minecraftClient.getWindow();
				if (resizedScreen == null) {
					resizedScreen = new NativeImage(window.getWidth(), window.getHeight(), false);
				} else if (BodySharingClient.resizedScreen.getWidth() != window.getWidth()
					|| BodySharingClient.resizedScreen.getHeight() != window.getHeight()) {
					BodySharingClient.resizedScreen.close();
					BodySharingClient.resizedScreen = new NativeImage(window.getWidth(), window.getHeight(), false);
				}
				vesselScreen.resizeSubRectTo(0, 0, vesselScreen.getWidth(), vesselScreen.getHeight(), resizedScreen);
			}
		});
		
		ClientPlayConnectionEvents.DISCONNECT.register((cpl, minecraft) -> {
			// Clean up possession fields
			isPossessing = false;
			possessing = null;
			selfUpdatedKeys.clear();
			synchronized (copycatScreenSync) {
				if (vesselScreen != null)
					vesselScreen.close();
				vesselScreen = null;
				if (resizedScreen != null)
					resizedScreen.close();
				resizedScreen = null;
			}
			// Clean up hosting fields
			isHosting = false;
			synchronized (guiFrameSync) {
				if (guiFrameLast != null)
					guiFrameLast.close();
				guiFrameLast = null;
				if (guiFrame != null)
					guiFrame.close();
				guiFrame = null;
			}
			sendFullScreen = false;
			possessorKeys.clear();
		});
	}
	
	public static boolean serverRunning() {
		Minecraft client = Minecraft.getInstance();
		return client.getCurrentServer() != null || client.hasSingleplayerServer();
	}
	
	public static boolean isGUIOpen() {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		return player.containerMenu != player.inventoryMenu
			|| client.screen instanceof AbstractContainerScreen
			|| client.screen instanceof BookEditScreen
			|| client.screen instanceof BookViewScreen
			|| client.screen instanceof AbstractSignEditScreen
			|| client.screen instanceof AbstractCommandBlockEditScreen
			|| client.screen instanceof JigsawBlockEditScreen
			|| client.screen instanceof StructureBlockEditScreen
			|| client.screen instanceof ChatScreen
			|| client.screen instanceof DeathScreen;
	}
	
	// Send packet to server to request to stop being a possessor
	public static void requestStopPossessing() {
		// Immediately update locally--the server will probably approve the request
		isPossessing = false;
		
		// Notify server
		FriendlyByteBuf startPossessPacket = PacketByteBufs.create();
		startPossessPacket.writeBoolean(true);
		Minecraft mc = Minecraft.getInstance();
		startPossessPacket.writeUUID(mc.player.getUUID());
		possessing = null;
		// client sends packet POSSESSING_UPDATE
		ClientPlayNetworking.send(BodySharing.POSSESSING_UPDATE_ID, startPossessPacket);
	}
	
	// Try to re-render the currently-opened GUI; update client fields on success
	public static boolean renderGUI() {
		Minecraft minecraftClient = Minecraft.getInstance();
		// Failsafe
		if (minecraftClient.screen == null)
			return false;
		// Cancel if no GUI open
		if (!isGUIOpen())
			return false;
		
		// change render target
		// always resize rendering target to ensure correct size
		Window window = minecraftClient.getWindow();
		guiTarget.resize(window.getWidth(), window.getHeight(), Minecraft.ON_OSX);
		// rebind targets
		RenderTarget mrt = minecraftClient.getMainRenderTarget();
		mrt.unbindWrite();
		guiTarget.bindWrite(false);
		
		// set up pose et al for screen translation
		Matrix4f matrix4f = new Matrix4f()
			.setOrtho(0.0F, (float)(window.getWidth() / window.getGuiScale()), (float)(window.getHeight() / window.getGuiScale()), 0.0F, 1000.0F, 21000.0F);
		RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
		PoseStack poseStack = RenderSystem.getModelViewStack();
		poseStack.pushPose();
		poseStack.setIdentity();
		poseStack.translate(0.0F, 0.0F, -11000.0F);
		RenderSystem.applyModelViewMatrix();
		Lighting.setupFor3DItems();
		
		// render
		int guiMX = (int)(minecraftClient.mouseHandler.xpos() * minecraftClient.getWindow().getGuiScaledWidth() / minecraftClient.getWindow().getScreenWidth());
		int guiMY = (int)(minecraftClient.mouseHandler.ypos() * minecraftClient.getWindow().getGuiScaledHeight() / minecraftClient.getWindow().getScreenHeight());
		GuiGraphics guiGraphics = new GuiGraphics(minecraftClient, renderBuffers.bufferSource());
		RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
		RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		minecraftClient.screen.renderWithTooltip(guiGraphics, guiMX, guiMY, minecraftClient.getDeltaFrameTime());
		
		// cleanup
		guiGraphics.flush();
		poseStack.popPose();
		RenderSystem.applyModelViewMatrix();
		guiTarget.unbindWrite();
		mrt.bindWrite(Minecraft.ON_OSX);
		// screenshot
		NativeImage screenshot = TransparentScreenshot.takeScreenshot(guiTarget);
		if (BodySharingClient.guiFrameLast != null)
			BodySharingClient.guiFrameLast.close();
		BodySharingClient.guiFrameLast = BodySharingClient.guiFrame;
		BodySharingClient.guiFrame = screenshot;
		return true;
	}
	
	public static @Nullable NativeImage renderHUD() {
		Minecraft minecraftClient = Minecraft.getInstance();
		if (minecraftClient.gui == null)
			return null;
		
		// change render target
		// always resize rendering target to ensure correct size
		Window window = minecraftClient.getWindow();
		guiTarget.resize(window.getWidth(), window.getHeight(), Minecraft.ON_OSX);
		// rebind targets
		RenderTarget mrt = minecraftClient.getMainRenderTarget();
		mrt.unbindWrite();
		guiTarget.bindWrite(false);
		
		// set up pose et al for screen translation
		Matrix4f matrix4f = new Matrix4f()
			.setOrtho(0.0F, (float)(window.getWidth() / window.getGuiScale()), (float)(window.getHeight() / window.getGuiScale()), 0.0F, 1000.0F, 21000.0F);
		RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
		PoseStack poseStack = RenderSystem.getModelViewStack();
		poseStack.pushPose();
		poseStack.setIdentity();
		poseStack.translate(0.0F, 0.0F, -11000.0F);
		RenderSystem.applyModelViewMatrix();
		Lighting.setupFor3DItems();

		// render
		GuiGraphics guiGraphics = new GuiGraphics(minecraftClient, renderBuffers.bufferSource());
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
		RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		// doesn't keep transparency--probably because of a RenderSystem call in here
		minecraftClient.gui.render(guiGraphics, minecraftClient.getFrameTime());
		
		// cleanup
		guiGraphics.flush();
		poseStack.popPose();
		RenderSystem.applyModelViewMatrix();
		guiTarget.unbindWrite();
		mrt.bindWrite(Minecraft.ON_OSX);
		// screenshot
		NativeImage screenshot = TransparentScreenshot.takeScreenshot(guiTarget);
		return screenshot;
	}
}