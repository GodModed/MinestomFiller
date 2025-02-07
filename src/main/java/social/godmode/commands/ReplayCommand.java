package social.godmode.commands;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.number.ArgumentInteger;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Pos;
import social.godmode.Main;
import social.godmode.instance.ReplayInstance;
import social.godmode.replay.Replay;
import social.godmode.user.GamePlayer;

public class ReplayCommand extends Command {
    public ReplayCommand() {
        super("replay");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /replay");
        });

        Argument<Integer> id = new ArgumentInteger("id")
                .setSuggestionCallback((sender, context, suggestion) -> {
                    GamePlayer gamePlayer = (GamePlayer) sender;
                    for (int replayId : gamePlayer.replayIDs) {
                        suggestion.addEntry(new SuggestionEntry(String.valueOf(replayId)));
                    }
                });

        addSyntax((sender, context) -> {
            GamePlayer gamePlayer = (GamePlayer) sender;

            if (gamePlayer.inGame) {
                sender.sendMessage("You must be in the lobby to spectate!");
                return;
            }

            int replayId = context.get(id);
            if (!gamePlayer.replayIDs.contains(replayId)) {
                sender.sendMessage("You do not have access to this replay!");
                return;
            }

            Replay replay = Main.REPLAY_MANAGER.loadReplay(replayId);
            if (replay == null) {
                sender.sendMessage("Replay not found!");
                return;
            }

            ReplayInstance replayInstance = new ReplayInstance(replay, gamePlayer);
            sender.sendMessage(Main.getTransferMessage("replay", replayInstance));
            gamePlayer.setInstance(replayInstance, new Pos(0, 140, 0));

            sender.sendMessage("Replay started!");

        }, id);

    }
}
