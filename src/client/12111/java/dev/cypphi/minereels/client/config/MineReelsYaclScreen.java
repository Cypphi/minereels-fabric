package dev.cypphi.minereels.client.config;

import dev.cypphi.minereels.config.OverlayConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Builds the ModMenu config screen: a General tab and a Tokens tab. */
public final class MineReelsYaclScreen {
	private MineReelsYaclScreen() {
	}

	public static Screen create(Screen parent) {
		OverlayConfig config = OverlayConfig.get();

		ConfigCategory general = ConfigCategory.createBuilder()
				.name(Text.literal("General"))
				.option(Option.<Boolean>createBuilder()
						.name(Text.literal("Enabled"))
						.description(OptionDescription.of(Text.literal("Master toggle for the reel overlay.")))
						.binding(true, () -> config.enabled, v -> config.enabled = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Text.literal("Show in HUD"))
						.description(OptionDescription.of(Text.literal("Show the overlay on the HUD. Right-clicking the card while chat is open also toggles this.")))
						.binding(true, () -> config.showInHud, v -> config.showInHud = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Card size (%)"))
						.description(OptionDescription.of(Text.literal("Card width as a percentage of the screen. Text scales with the card.")))
						.binding(18.0, () -> config.scalePercent, v -> config.scalePercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(5.0, 60.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Volume (%)"))
						.description(OptionDescription.of(Text.literal("Playback volume for reel audio.")))
						.binding(100.0, () -> config.volumePercent, v -> config.volumePercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("X position (%)"))
						.binding(2.0, () -> config.xPercent, v -> config.xPercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(0.5))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Y position (%)"))
						.binding(5.0, () -> config.yPercent, v -> config.yPercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(0.5))
						.build())
				.build();

		ConfigCategory tokens = ConfigCategory.createBuilder()
				.name(Text.literal("Tokens"))
				.option(Option.<String>createBuilder()
						.name(Text.literal("Instagram cookie"))
						.description(OptionDescription.of(Text.literal(
								"The full Cookie header from a logged-in instagram.com session "
										+ "(contains sessionid + csrftoken). Leave blank to use the mock feed. "
										+ "Stored locally only — never share or commit this.")))
						.binding("", () -> config.instagramCookie, v -> config.instagramCookie = v)
						.controller(StringControllerBuilder::create)
						.build())
				.build();

		return YetAnotherConfigLib.createBuilder()
				.title(Text.literal("MineReels"))
				.category(general)
				.category(tokens)
				.save(OverlayConfig::save)
				.build()
				.generateScreen(parent);
	}
}
