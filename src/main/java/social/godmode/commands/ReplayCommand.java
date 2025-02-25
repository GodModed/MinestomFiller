package social.godmode.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ReplayCommand() {
        super("replay");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("Usage: /replay <id>");
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
                sender.sendMessage(MM.deserialize("<red>You must be in the lobby to spectate!</red>"));
                return;
            }

            int replayId = context.get(id);
            if (!gamePlayer.replayIDs.contains(replayId)) {
                sender.sendMessage(MM.deserialize("<red>You do not have access to that replay!</red>"));
                return;
            }

            Replay replay = Main.REPLAY_MANAGER.loadReplay(replayId);
            if (replay == null) {
                sender.sendMessage(MM.deserialize("<red>Replay not found!</red>"));
                return;
            }

            ReplayInstance replayInstance = new ReplayInstance(replay, gamePlayer);
            sender.sendMessage(Main.getTransferMessage("replay", replayInstance));
            gamePlayer.setInstance(replayInstance, new Pos(0, 140, 0));

            sender.sendMessage(MM.deserialize("<green>Replaying " + replayId + "</green>"));

        }, id);

    }
}
