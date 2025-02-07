package social.godmode.commands;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import social.godmode.user.GamePlayer;
import social.godmode.user.PlayerRank;

public class StopCommand extends Command {
    public StopCommand() {
        super("stop", "restart");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /stop");
        });

        setCondition(((sender, commandString) -> {
            GamePlayer gamePlayer = (GamePlayer) sender;
            return gamePlayer.rank == PlayerRank.OWNER;
        }));

        addSyntax((sender, context) -> {

            MinecraftServer.stopCleanly();

        });
    }
}
