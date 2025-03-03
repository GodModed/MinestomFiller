package social.godmode.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.utils.entity.EntityFinder;
import social.godmode.user.GamePlayer;
import social.godmode.user.PlayerRank;

public class GrantCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public GrantCommand() {
        super("grant");

        Argument<PlayerRank> rankArg = new ArgumentEnum<>("rank", PlayerRank.class);
        Argument<EntityFinder> playerArg = new ArgumentEntity("player")
                .singleEntity(true)
                .onlyPlayers(true);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /grant <player> <rank>");
        });

        setCondition(((sender, commandString) -> {
            GamePlayer gamePlayer = (GamePlayer) sender;
            return gamePlayer.rank == PlayerRank.OWNER;
        }));

        addSyntax((sender, context) -> {

            Player player = context.get(playerArg).findFirstPlayer(sender);
            PlayerRank rank = context.get(rankArg);
            GamePlayer gamePlayer = (GamePlayer) player;
            assert gamePlayer != null;
            gamePlayer.setRank(rank);
            gamePlayer.saveStats();

            gamePlayer.refreshCommands();

            sender.sendMessage(MM.deserialize("<green>" + player.getUsername() + " has been granted " + rank.getPrefix()));
            player.sendMessage(MM.deserialize("<green>You have been granted " + rank.getPrefix() + "</green>"));

        }, playerArg, rankArg);

    }
}
