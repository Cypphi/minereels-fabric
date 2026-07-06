package dev.cypphi.minereels.client.config;

import dev.cypphi.minereels.config.OverlayConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the ModMenu config screen: a General tab and a Tokens tab. */
public final class MineReelsYaclScreen {
	private MineReelsYaclScreen() {
	}

	public static Screen create(Screen parent) {
		OverlayConfig config = OverlayConfig.get();

		ConfigCategory general = ConfigCategory.createBuilder()
				.name(Component.literal("General"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Enabled"))
						.description(OptionDescription.of(Component.literal("Master toggle for the reel overlay.")))
						.binding(true, () -> config.enabled, v -> config.enabled = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.literal("Show in HUD"))
						.description(OptionDescription.of(Component.literal("Show the overlay on the HUD. Right-clicking the card while chat is open also toggles this.")))
						.binding(true, () -> config.showInHud, v -> config.showInHud = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Double>createBuilder()
						.name(Component.literal("Card size (%)"))
						.description(OptionDescription.of(Component.literal("Card width as a percentage of the screen. Text scales with the card.")))
						.binding(18.0, () -> config.scalePercent, v -> config.scalePercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(5.0, 60.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Component.literal("Volume (%)"))
						.description(OptionDescription.of(Component.literal("Playback volume for reel audio.")))
						.binding(100.0, () -> config.volumePercent, v -> config.volumePercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Component.literal("X position (%)"))
						.binding(2.0, () -> config.xPercent, v -> config.xPercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(0.5))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Component.literal("Y position (%)"))
						.binding(5.0, () -> config.yPercent, v -> config.yPercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(0.5))
						.build())
				.build();

		ConfigCategory tokens = ConfigCategory.createBuilder()
				.name(Component.literal("Tokens"))
				.option(Option.<String>createBuilder()
						.name(Component.literal("Instagram cookie"))
						.description(OptionDescription.of(Component.literal(
								"The full Cookie header from a logged-in instagram.com session "
										+ "(contains sessionid + csrftoken). Leave blank to use the mock feed. "
										+ "Stored locally only - never share or commit this.")))
						.binding("", () -> config.instagramCookie, v -> config.instagramCookie = v)
						.controller(StringControllerBuilder::create)
						.build())
				.build();

		return YetAnotherConfigLib.createBuilder()
				.title(Component.literal("MineReels"))
				.category(general)
				.category(tokens)
				.save(OverlayConfig::save)
				.build()
				.generateScreen(parent);
	}
}
