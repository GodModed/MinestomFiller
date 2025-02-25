package social.godmode.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.FillerAPI.FillerPlayer;
import social.godmode.instance.AbstractFillerInstance;
import social.godmode.instance.GameInstance;
import social.godmode.user.GamePlayer;
import social.godmode.user.PlayerRank;

import java.util.UUID;

public class ColorCommand extends Command {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ColorCommand() {
        super("playcolor", "color");

        Argument<FillerColor> colorArgument = new ArgumentEnum<>("color", FillerColor.class);

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage("/playcolor <color>");
        });

        addSyntax((sender, context) -> {
            GamePlayer player = (GamePlayer) sender;
            GameInstance game = AbstractFillerInstance.getGame(player);

            if (!player.inGame || game == null) {
                sender.sendMessage(MM.deserialize("<red>You are not in a game!</red>"));
                return;
            }

            FillerColor color = context.get(colorArgument);

            FillerPlayer fillerPlayer = game.getCurrentPlayer();
            boolean isSender = fillerPlayer.playerUUID == player.getUuid();

            if (!isSender && player.rank != PlayerRank.OWNER) {
                sender.sendMessage(MM.deserialize("<red>It is not your turn!</red>"));
                return;
            }

            game.performPlayerTurn(player, color);

        }, colorArgument);

    }

}