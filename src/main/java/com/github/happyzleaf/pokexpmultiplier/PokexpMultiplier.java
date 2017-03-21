package com.github.happyzleaf.pokexpmultiplier;

import com.google.inject.Inject;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.config.PixelmonConfig;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.File;

@Plugin(id = PokexpMultiplier.PLUGIN_ID, name = PokexpMultiplier.PLUGIN_NAME, version = "1.1.0", authors = {"happyzlife"}, dependencies = {@Dependency(id = "pixelmon")})
public class PokexpMultiplier {
	public static final String PLUGIN_ID = "pokexpmultiplier";
	public static final String PLUGIN_NAME = "PokexpMultiplier";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_NAME);
	
	@Inject
	@DefaultConfig(sharedRoot = true)
	private File configFile;
	
	@Inject
	@DefaultConfig(sharedRoot = true)
	ConfigurationLoader<CommentedConfigurationNode> configLoader;
	
	@Listener
	public void onGameInitialization(GameInitializationEvent event) {
		PokexpConfig.getInstance().setup(configFile, configLoader);
		Pixelmon.EVENT_BUS.register(this);
		
		CommandSpec reload = CommandSpec.builder()
				.executor((src, args) -> {
					PokexpConfig.getInstance().loadConfig();
					src.sendMessage(Text.of(TextColors.DARK_GREEN, "[PokexpMultiplier] Config(s) reloaded!"));
					return CommandResult.success();
				})
				.description(Text.of("Reload configs."))
				.permission(PLUGIN_ID + ".admin.reload")
				.build();
		CommandSpec info = CommandSpec.builder()
				.arguments(GenericArguments.optional(GenericArguments.requiringPermission(GenericArguments.player(Text.of("player")), PLUGIN_ID + ".info.others")))
				.executor((src, args) -> {
					if (args.hasAny("player")) {
						Player player = (Player) args.getOne("player").get();
						//TODO add the info from the algorithm
						src.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(AlgorithmUtilities.parseInfoWithValues(player, AlgorithmUtilities.algorithmPerUser(player))));
						return CommandResult.success();
					} else {
						if (src instanceof Player) {
							src.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(AlgorithmUtilities.parseInfoWithValues((Player) src, AlgorithmUtilities.algorithmPerUser((Player) src))));
							return CommandResult.success();
						} else {
							src.sendMessage(Text.of(TextColors.RED, "[PokexpMultiplier] Your MUST be ingame to execute this command."));
							return CommandResult.successCount(0);
						}
					}
				})
				.permission(PLUGIN_ID + ".info.me")
				.description(Text.of("Get the player's custom experience algorithm info."))
				.build();
		CommandSpec pokexp = CommandSpec.builder()
				.child(reload, "reload")
				.child(info, "info")
				.build();
		Sponge.getGame().getCommandManager().register(this, pokexp, "pokexp", "pkexp");
		
		LOGGER.info("Loaded!");
	}
	
	@SubscribeEvent
	public void onExperienceGain(ExperienceGainEvent event) {
		//This is just for the message, there are no problems to multiply the exp of a max leveled pokemon
		if (event.pokemon.getLvl().getLevel() == PixelmonConfig.maxLevel)
			return;
		
		Player player = (Player) event.pokemon.getOwner();
		if (player.hasPermission(PLUGIN_ID + ".enable")) {
			int oldExp = event.getExperience();
			String algorithm = AlgorithmUtilities.algorithmPerUser(player);
			int result = (int) AlgorithmUtilities.eval(AlgorithmUtilities.parseAlgorithmWithValues(player, algorithm, oldExp));
			event.setExperience(result);
			
			if (!PokexpConfig.getInstance().getConfig().getNode("algorithms", algorithm, "messages", "message").isVirtual())
				player.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(PokexpConfig.getInstance().getConfig().getNode("algorithms", algorithm, "messages", "message").getString()
						.replaceAll("#POKEMON", event.pokemon.getName())
						.replaceAll("#PLAYER", player.getName())
						.replaceAll("#OLD_EXP", "" + oldExp)
						.replaceAll("#NEW_EXP", "" + event.getExperience())
						.replaceAll("#VALUE", "" + AlgorithmUtilities.valuePerUser(player, algorithm))
				));
		}
	}
}
