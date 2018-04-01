package com.github.happyzleaf.pokexpmultiplier;

import com.github.happyzleaf.pokexpmultiplier.placeholder.PlaceholderHelper;
import com.github.happyzleaf.pokexpmultiplier.placeholder.PlaceholderUtility;
import com.google.inject.Inject;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.events.ExperienceGainEvent;
import com.pixelmonmod.pixelmon.config.PixelmonConfig;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import ninja.leaping.configurate.ConfigurationNode;
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
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.File;

@Plugin(id = PokexpMultiplier.PLUGIN_ID, name = PokexpMultiplier.PLUGIN_NAME, version = "1.1.7", authors = {"happyzlife"},
		dependencies = {@Dependency(id = "pixelmon"), @Dependency(id = "placeholderapi", version = "[4.4,)", optional = true)})
public class PokexpMultiplier {
	public static final String PLUGIN_ID = "pokexpmultiplier";
	public static final String PLUGIN_NAME = "PokexpMultiplier";
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_NAME);
	
	public static PokexpMultiplier instance;
	
	@Inject
	@DefaultConfig(sharedRoot = true)
	private File configFile;
	
	@Inject
	@DefaultConfig(sharedRoot = true)
	ConfigurationLoader<CommentedConfigurationNode> configLoader;
	
	@Listener
	public void preInit(GamePreInitializationEvent event) {
		instance = this;
	}
	
	@Listener
	public void init(GameInitializationEvent event) {
		PlaceholderUtility.init();
		PokexpConfig.getInstance().setup(configFile, configLoader);
		Pixelmon.EVENT_BUS.register(this);
		
		CommandSpec reload = CommandSpec.builder()
				.executor((src, args) -> {
					PokexpConfig.getInstance().loadConfig();
					src.sendMessage(Text.of(TextColors.DARK_GREEN, "[" + PLUGIN_NAME + "]", TextColors.GREEN, " Config(s) reloaded!"));
					return CommandResult.success();
				})
				.description(Text.of("Reload configs."))
				.permission(PLUGIN_ID + ".admin.reload")
				.build();
		CommandSpec info = CommandSpec.builder()
				.arguments(GenericArguments.optional(GenericArguments.requiringPermission(GenericArguments.onlyOne(GenericArguments.player(Text.of("player"))), PLUGIN_ID + ".info.others")))
				.executor((src, args) -> {
					if (args.hasAny("player")) {
						Player player = (Player) args.getOne("player").get();
						src.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(AlgorithmUtilities.parseInfoWithValues(player, AlgorithmUtilities.algorithmPerUser(player))));
						return CommandResult.success();
					} else {
						if (src instanceof Player) {
							src.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(AlgorithmUtilities.parseInfoWithValues((Player) src, AlgorithmUtilities.algorithmPerUser((Player) src))));
							return CommandResult.success();
						} else {
							src.sendMessage(Text.of(TextColors.DARK_RED, "[" + PLUGIN_NAME + "]", TextColors.RED, " Your MUST be in-game in order to execute this command."));
							return CommandResult.successCount(0);
						}
					}
				})
				.permission(PLUGIN_ID + ".info.me")
				.description(Text.of("Get the player's experience algorithm info."))
				.build();
		CommandSpec pokexp = CommandSpec.builder()
				.child(reload, "reload")
				.child(info, "info")
				.build();
		Sponge.getGame().getCommandManager().register(this, pokexp, "pokexp", "pkexp");
		
		//Just to check if the method works well
		/*Sponge.getGame().getCommandManager().register(this, CommandSpec.builder()
				.arguments(GenericArguments.onlyOne(GenericArguments.remainingJoinedStrings(Text.of("operation"))))
				.executor(((src, args) -> {
					src.sendMessage(Text.of(TextColors.GREEN, "result: " + AlgorithmUtilities.eval((String) args.getOne("operation").get())));
					return CommandResult.success();
				}))
				.build(), "math");*/
		
		LOGGER.info("Loaded!");
	}
	
	@SubscribeEvent
	public void onExperienceGain(ExperienceGainEvent event) {
		if (event.pokemon == null || event.pokemon.getPlayerOwner() == null || event.pokemon.getNickname() == null) return;
		
		//This is just for the message, there are no problems to multiply the exp of a max leveled pokemon
		if (event.pokemon.getLevel() == PixelmonConfig.maxLevel) return;
		
		Player player = (Player) event.pokemon.getPlayerOwner();
		EntityPixelmon pixelmon = PlaceholderHelper.getPixelmonByPos(player, event.pokemon.getPartyPosition());
		
		if (pixelmon == null) return;
		
		if (player.hasPermission(PLUGIN_ID + ".enable")) {
			int oldExp = event.getExperience();
			String algorithm = AlgorithmUtilities.algorithmPerUser(player);
			int result = (int) AlgorithmUtilities.eval(AlgorithmUtilities.parseAlgorithmWithValues(player, algorithm, oldExp, event.pokemon.getPartyPosition(), pixelmon.getPokemonName()));
			event.setExperience(result);
			ConfigurationNode message = PokexpConfig.getInstance().getConfig().getNode("algorithms", algorithm, "messages", "message");
			if (!message.isVirtual()) {
				player.sendMessage(TextSerializers.FORMATTING_CODE.deserialize(
						PlaceholderUtility.replaceIfAvailable(message.getString()
							.replaceAll("#POKEMON", event.pokemon.getNickname())
							.replaceAll("#PLAYER", player.getName())
							.replaceAll("#PARTY-POSITION", "" + event.pokemon.getPartyPosition())
							.replaceAll("#OLD-EXP", "" + oldExp)
							.replaceAll("#NEW-EXP", "" + event.getExperience())
							.replaceAll("#VALUE", "" + AlgorithmUtilities.valuePerUser(player, algorithm))
						, player)
				));
			}
		}
	}
}
