package social.godmode.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.utils.entity.EntityFinder;
import social.godmode.GameInstance;
import social.godmode.Main;

public class SpectateCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public SpectateCommand() {
        super("spectate", "spec");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /spectate <player>");
        });

        Argument<String> playerArg = new ArgumentWord("player")
                .setSuggestionCallback((sender, context, suggestion) -> {
                    for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                        if (!GameInstance.isInGame(player)) continue;
                        suggestion.addEntry(new SuggestionEntry(player.getUsername()));
                    }
                });

        addSyntax((sender, context) -> {
            // get player
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(context.get(playerArg));
            if (player == null) {
                sender.sendMessage(MM.deserialize("<red>Player not found!</red>"));
                return;
            }

            Player spectatingPlayer = (Player) sender;
            if (spectatingPlayer.getInstance() != Main.lobbyInstance) {
                sender.sendMessage(MM.deserialize("<red>You must be in the lobby to spectate!</red>"));
                return;
            }

            if (!GameInstance.isInGame(player)) {
                sender.sendMessage(MM.deserialize("<red>Player is not in a game!</red>"));
                return;
            }

            GameInstance game = GameInstance.getGame(player);
            sender.sendMessage(MM.deserialize("<green>Spectating " + player.getUsername() + "</green>"));
            sender.sendMessage(MM.deserialize("<gray>Transferring to <white>game-" + game.getUuid()));
            spectatingPlayer.setInvisible(true);
            spectatingPlayer.setInstance(game, player.getPosition());
            spectatingPlayer.getInventory().clear();
            ItemStack item = ItemStack.builder(Material.RED_DYE)
                    .set(ItemComponent.ITEM_NAME, MM.deserialize("<red><bold>Leave Game"))
                    .build();

            spectatingPlayer.getInventory().setItemStack(4, item);


            game.spectators.add(spectatingPlayer);

        }, playerArg);



    }
}
