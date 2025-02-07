package social.godmode.instance;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import social.godmode.FillerAPI.Filler;
import social.godmode.FillerAPI.FillerBlock;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.FillerAPI.FillerPlayer;
import social.godmode.Main;
import social.godmode.replay.Replay;
import social.godmode.user.GamePlayer;

import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ReplayInstance extends SharedInstance {

    private final Replay replay;
    int turn = 0;
    private final Filler filler;
    Entity[][] entities = new Entity[8][8];

    public ReplayInstance(Replay replay, GamePlayer player) {
        super(UUID.randomUUID(), Main.sharedGameInstance);
        MinecraftServer.getInstanceManager().registerSharedInstance(this);

        player.inGame = true;

        this.replay = replay;
        filler = new Filler(replay.seed(), UUID.randomUUID(), UUID.randomUUID());

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                entities[x][y] = createBlockDisplayEntity(x, y);
            }
        }

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

    private Entity createBlockDisplayEntity(int x, int y) {
        Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
        blockDisplay.setNoGravity(true);
        blockDisplay.setInstance(this, new Pos(x - 4, y + 138, 0));
        BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
        FillerBlock fillerBlock = filler.getBoard()[x][y];
        blockDisplayMeta.setBlockState(GameInstance.fillerColorToMaterial(fillerBlock.getColor()).block());
        return blockDisplay;
    }

    public void updateVisuals() {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                updateBlockDisplayVisual(x, y);
            }
        }
        highlightCurrentPlayerOwnedBlocks();
    }

    private void updateBlockDisplayVisual(int x, int y) {
        Entity blockDisplay = entities[x][y];
        blockDisplay.setGlowing(false);
        BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
        FillerBlock fillerBlock = filler.getBoard()[x][y];
        blockDisplayMeta.setBlockState(GameInstance.fillerColorToMaterial(fillerBlock.getColor()).block());
    }

    private void highlightCurrentPlayerOwnedBlocks() {
        List<FillerBlock> ownedBlocks = filler.getCurrentPlayer().getOwnedBlocks();
        for (FillerBlock ownedBlock : ownedBlocks) {
            Entity blockDisplay = entities[ownedBlock.getX()][ownedBlock.getY()];
            BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            blockDisplayMeta.setGlowColorOverride(Color.WHITE.getRGB());
            blockDisplay.setGlowing(true);
        }
    }

    private CompletableFuture<Void> teleportPlayerToLobby(GamePlayer player) {
        if (!player.isOnline()) return CompletableFuture.completedFuture(null);

        player.getInventory().clear();
        player.addEffect(GameInstance.DARKNESS_POTION);
        player.inGame = false;

        if (player.isInvisible()) {
            player.setInvisible(false);
        }

        player.sendMessage(Main.getTransferMessage("lobby", Main.lobbyInstance));

        return player.setInstance(Main.lobbyInstance);
    }


}
