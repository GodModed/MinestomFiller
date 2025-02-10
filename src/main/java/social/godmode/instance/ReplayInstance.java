package social.godmode.instance;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import social.godmode.FillerAPI.Filler;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.FillerAPI.FillerPlayer;
import social.godmode.replay.Replay;
import social.godmode.user.GamePlayer;

import java.util.UUID;

public class ReplayInstance extends AbstractFillerInstance {

    private final Replay replay;
    private int turn = 0;

    public ReplayInstance(Replay replay, GamePlayer player) {
        super(UUID.randomUUID(), new Filler(replay.seed(), UUID.randomUUID(), UUID.randomUUID()));
        this.replay = replay;
        player.inGame = true;

        updateVisuals();

        ItemStack arrow = ItemStack.of(Material.ARROW);
        player.getInventory().setItemStack(4, arrow);

        eventNode().addListener(PlayerUseItemEvent.class, event -> {
            if (event.getPlayer() != player) return;
            if (event.getItemStack().material() != Material.ARROW) return;
            if (turn >= replay.colors().size()) {
                teleportPlayerToLobby(player).thenRun(() -> {
                    MinecraftServer.getInstanceManager().unregisterInstance(this);
                });
                return;
            }

            FillerColor color = replay.colors().get(turn++);
            FillerPlayer fillerPlayer = filler.getCurrentPlayer();
            fillerPlayer.turn(color);
            updateVisuals();

        });
    }
}