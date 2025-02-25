package social.godmode.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.command.builder.arguments.number.ArgumentLong;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.utils.entity.EntityFinder;
import social.godmode.Main;
import social.godmode.instance.GameInstance;
import social.godmode.user.GamePlayer;
import social.godmode.user.PlayerRank;

public class StartCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public StartCommand() {
        super("startgame", "start");

        Argument<Long> seedArg = new ArgumentLong("seed");
        Argument<EntityFinder> playerArg = new ArgumentEntity("player")
                .singleEntity(true)
                .onlyPlayers(true);

        setCondition(((sender, commandString) -> {
            GamePlayer gamePlayer = (GamePlayer) sender;
            return gamePlayer.rank == PlayerRank.OWNER;
        }));

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /startgame <seed> <player>");
        });

        addSyntax((sender, context) -> {
            long seed = context.get(seedArg);
            EntityFinder entityFinder = context.get(playerArg);
            GamePlayer gamePlayer = (GamePlayer) entityFinder.findFirstPlayer(sender);
            GamePlayer senderGamePlayer = (GamePlayer) sender;

            if (gamePlayer == null) {
                sender.sendMessage(MM.deserialize("<red>Player not found!</red>"));
                return;
            }

            if (GameInstance.isInGame(gamePlayer)) {
                sender.sendMessage(MM.deserialize("<red>Player is already in a game!</red>"));
                return;
            }

            // 50% chance swap them
            if (Math.random() > 0.5) {
                GamePlayer temp = gamePlayer;
                gamePlayer = senderGamePlayer;
                senderGamePlayer = temp;
            }

            GameInstance gameInstance = new GameInstance(senderGamePlayer, gamePlayer, seed);
            senderGamePlayer.setInstance(gameInstance, Main.PLAYER1_GAME_POS);
            gamePlayer.setInstance(gameInstance, Main.PLAYER2_GAME_POS);
        }, seedArg, playerArg);

    }


}
