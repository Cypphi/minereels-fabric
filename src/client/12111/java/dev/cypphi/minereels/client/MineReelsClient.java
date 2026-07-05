package dev.cypphi.minereels.client;

import dev.cypphi.minereels.MineReels;
import dev.cypphi.minereels.config.OverlayConfig;
import dev.cypphi.minereels.input.MultiTapDetector;
import dev.cypphi.minereels.reel.InstagramProvider;
import dev.cypphi.minereels.reel.ReelProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class MineReelsClient implements ClientModInitializer {
	private static final long TAP_WINDOW_MILLIS = 350;

	private final KeyBinding.Category category =
			KeyBinding.Category.create(Identifier.of(MineReels.MOD_ID, "controls"));

	@Override
	public void onInitializeClient() {
		ReelProvider provider = createProvider();
		ReelFeedOverlay overlay = new ReelFeedOverlay(provider);

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.of(MineReels.MOD_ID, "reel_feed"),
				overlay::render
		);
		overlay.registerChatInteraction();
		registerKeys(overlay);
		MineReels.LOGGER.info("MineReels client initialized (provider: {})", provider.name());
	}

	private static ReelProvider createProvider() {
		String cookie = OverlayConfig.get().instagramCookie;
		if (cookie == null || cookie.isBlank()) {
			MineReels.LOGGER.warn("No Instagram cookie set — the overlay will be empty. Add it in the MineReels config (Tokens tab).");
		}
		return new InstagramProvider(cookie == null ? "" : cookie);
	}

	private void registerKeys(ReelFeedOverlay overlay) {
		KeyBinding next = key("next-reel", GLFW.GLFW_KEY_DOWN);
		KeyBinding previous = key("previous-reel", GLFW.GLFW_KEY_UP);
		KeyBinding like = key("like-reel", GLFW.GLFW_KEY_L);
		KeyBinding toggle = key("toggle-overlay", GLFW.GLFW_KEY_INSERT);
		// Multi-tap control: 1 tap = next, 2 = like, 3 = previous.
		KeyBinding action = key("reel-action", GLFW.GLFW_KEY_RIGHT);
		MultiTapDetector tapDetector = new MultiTapDetector(TAP_WINDOW_MILLIS);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (next.wasPressed()) {
				overlay.nextReel();
			}
			while (previous.wasPressed()) {
				overlay.previousReel();
			}
			while (like.wasPressed()) {
				overlay.likeCurrent();
			}
			while (toggle.wasPressed()) {
				overlay.toggleEnabled();
			}
			while (action.wasPressed()) {
				tapDetector.onTap();
			}
			dispatchMultiTap(tapDetector.poll(), overlay);
		});
	}

	private static void dispatchMultiTap(int taps, ReelFeedOverlay overlay) {
		switch (taps) {
			case 1 -> overlay.nextReel();
			case 2 -> overlay.likeCurrent();
			case 3 -> overlay.previousReel();
			default -> {
			}
		}
	}

	private KeyBinding key(String name, int defaultKey) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key." + MineReels.MOD_ID + "." + name, InputUtil.Type.KEYSYM, defaultKey, category));
	}
}
