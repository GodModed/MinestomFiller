package social.godmode.commands;

import net.minestom.server.command.builder.Command;
import social.godmode.Main;
import social.godmode.replay.Replay;
import social.godmode.user.GamePlayer;

public class ReplayCommand extends Command {
    public ReplayCommand() {
        super("replay");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /replay");
        });

        addSyntax((sender, context) -> {
            GamePlayer gamePlayer = (GamePlayer) sender;
            for (int replayID : gamePlayer.replayIDs) {
                gamePlayer.sendMessage("Replay ID: " + replayID);
                Replay replay = Main.REPLAY_MANAGER.loadReplay(replayID);
                gamePlayer.sendMessage("Replay seed: " + replay.seed());
            }
        });

    }
}
