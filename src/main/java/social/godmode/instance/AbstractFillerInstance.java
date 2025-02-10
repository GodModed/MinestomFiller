package social.godmode.instance;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.item.Material;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import social.godmode.FillerAPI.Filler;
import social.godmode.FillerAPI.FillerBlock;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.Main;
import social.godmode.user.GamePlayer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractFillerInstance extends SharedInstance {

    protected static final MiniMessage MM = MiniMessage.miniMessage();
    protected static final Pos SCORE_TEXT_DISPLAY_POS_PLAYER1 = new Pos(-4, 148, 0.5);
    protected static final Pos SCORE_TEXT_DISPLAY_POS_PLAYER2 = new Pos(4, 148, 0.5);
    protected static final Vec TEXT_DISPLAY_SCALE = new Vec(2);
    protected static final Key SOUND_KEY = Key.key("minecraft", "ui.hud.bubble_pop");
    public static final Potion DARKNESS_POTION = new Potion(PotionEffect.DARKNESS, 1, 60);
    protected static final String SPECTATOR_LEAVE_MESSAGE = "<red>You are no longer spectating.</red>";
    public static final ArrayList<GameInstance> games = new ArrayList<>();

    protected final Filler filler;
    protected final Entity[][] entities = new Entity[8][8];

    protected AbstractFillerInstance(UUID uuid, Filler filler) {
        super(uuid, Main.sharedGameInstance);
        MinecraftServer.getInstanceManager().registerSharedInstance(this);
        this.filler = filler;
        initializeBoardVisuals();
    }

    private void initializeBoardVisuals() {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                entities[x][y] = createBlockDisplayEntity(x, y);
            }
        }
    }

    protected Entity createBlockDisplayEntity(int x, int y) {
        Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
        blockDisplay.setNoGravity(true);
        blockDisplay.setInstance(this, new Pos(x - 4, y + 138, 0));
        BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
        FillerBlock fillerBlock = filler.getBoard()[x][y];
        blockDisplayMeta.setBlockState(fillerColorToMaterial(fillerBlock.getColor()).block());
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

    protected void updateBlockDisplayVisual(int x, int y) {
        Entity blockDisplay = entities[x][y];
        blockDisplay.setGlowing(false);
        BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
        FillerBlock fillerBlock = filler.getBoard()[x][y];
        blockDisplayMeta.setBlockState(fillerColorToMaterial(fillerBlock.getColor()).block());
    }

    protected void highlightCurrentPlayerOwnedBlocks() {
        List<FillerBlock> ownedBlocks = filler.getCurrentPlayer().getOwnedBlocks();
        for (FillerBlock ownedBlock : ownedBlocks) {
            Entity blockDisplay = entities[ownedBlock.getX()][ownedBlock.getY()];
            BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            blockDisplayMeta.setGlowColorOverride(Color.WHITE.getRGB());
            blockDisplay.setGlowing(true);
        }
    }

    protected CompletableFuture<Void> teleportPlayerToLobby(GamePlayer player) {
        if (!player.isOnline()) return CompletableFuture.completedFuture(null);

        player.getInventory().clear();
        player.addEffect(DARKNESS_POTION);
        player.inGame = false;

        if (player.isInvisible()) {
            player.setInvisible(false);
        }

        player.sendMessage(Main.getTransferMessage("lobby", Main.lobbyInstance));

        return player.setInstance(Main.lobbyInstance);
    }


    public static Material fillerColorToMaterial(FillerColor color) {
        return switch (color) {
            case RED -> Material.RED_CONCRETE;
            case GREEN -> Material.GREEN_CONCRETE;
            case BLUE -> Material.BLUE_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
            case PURPLE -> Material.PURPLE_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
        };
    }

    public static FillerColor materialToFillerColor(Material material) {
        if (material.equals(Material.RED_CONCRETE)) {
            return FillerColor.RED;
        } else if (material.equals(Material.GREEN_CONCRETE)) {
            return FillerColor.GREEN;
        } else if (material.equals(Material.BLUE_CONCRETE)) {
            return FillerColor.BLUE;
        } else if (material.equals(Material.YELLOW_CONCRETE)) {
            return FillerColor.YELLOW;
        } else if (material.equals(Material.PURPLE_CONCRETE)) {
            return FillerColor.PURPLE;
        } else if (material.equals(Material.BLACK_CONCRETE)) {
            return FillerColor.BLACK;
        } else {
            return null;
        }
    }

    public String fillerColorToHex(FillerColor color) {
        int rgb = color.color.getRGB();
        return String.format("#%06X", (0xFFFFFF & rgb));
    }

    public static boolean isInGame(Player player) {
        for (GameInstance game : games) {
            if (game.player1 == player || game.player2 == player) {
                return true;
            }
        }
        return false;
    }

    public static GameInstance getGame(Player player) {
        for (GameInstance game : games) {
            if (game.player1 == player || game.player2 == player) {
                return game;
            }
        }
        return null;
    }
}