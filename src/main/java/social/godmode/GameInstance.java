package social.godmode;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;
import social.godmode.FillerAPI.Filler;
import social.godmode.FillerAPI.FillerBlock;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.FillerAPI.FillerPlayer;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GameInstance extends SharedInstance {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pos SCORE_TEXT_DISPLAY_POS_PLAYER1 = new Pos(-4, 148, 0.5);
    private static final Pos SCORE_TEXT_DISPLAY_POS_PLAYER2 = new Pos(4, 148, 0.5);
    private static final Vec TEXT_DISPLAY_SCALE = new Vec(2);
    public static final ArrayList<GameInstance> games = new ArrayList<>();

    private final Filler filler;
    private final Entity[][] board = new Entity[8][8];
    private final Entity[] playerScoreTextDisplays = new Entity[2];
    public final Player player1;
    public final Player player2;

    public final ArrayList<Player> spectators = new ArrayList<>();

    public GameInstance(Player player1, Player player2) {
        super(UUID.randomUUID(), Main.sharedGameInstance);
        MinecraftServer.getInstanceManager().registerSharedInstance(this);

        this.player1 = player1;

        GamePlayer gamePlayer1 = (GamePlayer) player1;
        gamePlayer1.inGame = true;

        this.player2 = player2;
        GamePlayer gamePlayer2 = (GamePlayer) player2;
        gamePlayer2.inGame = true;

        filler = new Filler(player1.getUuid(), player2.getUuid());

        initializeBoardVisuals();
        initializeScoreDisplays();

        updateVisuals();
        sendTurnMessage();

        setupEventListeners();

        setHotBar(player1);
        setHotBar(player2);

        games.add(this);
    }

    private void initializeBoardVisuals() {
        FillerBlock[][] fillerBoard = filler.getBoard();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
                blockDisplay.setNoGravity(true);
                blockDisplay.setInstance(this, new Pos(x - 4, y + 138, 0));
                BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
                blockDisplayMeta.setBlockState(fillerColorToMaterial(fillerBoard[x][y].getColor()).block());
                board[x][y] = blockDisplay;
            }
        }
    }

    private void initializeScoreDisplays() {
        for (int i = 0; i < 2; i++) {
            Entity scoreTextDisplay = new Entity(EntityType.TEXT_DISPLAY);
            scoreTextDisplay.setNoGravity(true);
            scoreTextDisplay.setInstance(this, i == 0 ? SCORE_TEXT_DISPLAY_POS_PLAYER1 : SCORE_TEXT_DISPLAY_POS_PLAYER2);
            TextDisplayMeta textDisplayMeta = (TextDisplayMeta) scoreTextDisplay.getEntityMeta();
            textDisplayMeta.setUseDefaultBackground(false);
            textDisplayMeta.setBackgroundColor(0);
            textDisplayMeta.setScale(TEXT_DISPLAY_SCALE);
            textDisplayMeta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
            playerScoreTextDisplays[i] = scoreTextDisplay;
        }
    }

    private void setupEventListeners() {
        eventNode().addListener(PlayerUseItemEvent.class, event -> {
            Player player = event.getPlayer();
            ItemStack itemStack = event.getItemStack();

            if (itemStack.material() == Material.RED_DYE) {
                spectators.remove(player);
                player.setInvisible(false);
                player.getInventory().clear();
                player.setInstance(Main.lobbyInstance);
                return;
            }

            if (!isPlayerTurn(player.getUuid()) || itemStack.isAir() || itemStack.material() == Material.BARRIER) {
                return;
            }

            FillerColor color = materialToFillerColor(itemStack.material());
            if (color == null || !filler.getCurrentPlayer().getAvailableColors().contains(color)) {
                return;
            }

            filler.getCurrentPlayer().turn(color);

            Sound sound = Sound.sound(Key.key("minecraft", "ui.hud.bubble_pop"), Sound.Source.MASTER, 1, 1);
            player1.playSound(sound);
            player2.playSound(sound);

            GamePlayer gamePlayer = (GamePlayer) player;

            sendMessage(MM.deserialize(gamePlayer.rank.getPrefix() + " " + player.getUsername() + "<white> has chosen <bold><" + fillerColorToHex(color) + ">" + color.name()));
            updateVisuals();

            if (!filler.isGameEnded()) {
                sendTurnMessage();
                setHotBar(player1);
                setHotBar(player2);
            } else {
                endGame();
            }
        }).addListener(PlayerDisconnectEvent.class, event -> cleanupAndReturnToLobby());
    }

    private boolean isPlayerTurn(UUID playerUUID) {
        return filler.getCurrentPlayer().playerUUID.equals(playerUUID);
    }

    private void endGame() {
        FillerPlayer gameWinner = filler.getWinner();
        Player winner = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(gameWinner.playerUUID);
        FillerPlayer loserPlayer = Arrays.stream(filler.getPlayers()).filter(p -> !p.equals(gameWinner)).findFirst().orElse(null);
        Player loser = loserPlayer != null ? MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(loserPlayer.playerUUID) : null;

        if (winner != null && loser != null) {

            // TODO: make notification

            winner.sendMessage(MM.deserialize("<green><bold>You win!"));

            GamePlayer gameWinnerPlayer = (GamePlayer) winner;
            gameWinnerPlayer.addWin();
            gameWinnerPlayer.inGame = false;

            loser.sendMessage(MM.deserialize("<red><bold>You lose!"));
            GamePlayer gameLoserPlayer = (GamePlayer) loser;
            gameLoserPlayer.addLoss();
            gameLoserPlayer.inGame = false;

            cleanupAndReturnToLobby();
        } else {
            System.err.println("Error: Could not find winner or loser player object.");
        }
    }

    private void cleanupAndReturnToLobby() {

        games.remove(this);

        player1.getInventory().clear();
        player2.getInventory().clear();

        Potion potion = new Potion(PotionEffect.DARKNESS, 1, 60);
        player1.addEffect(potion);
        player2.addEffect(potion);

        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[spectators.size()];
        for (int i = 0; i < spectators.size(); i++) {
            Player spectator = spectators.get(i);
            spectator.getInventory().clear();
            spectator.setInvisible(false);
            futures[i] = spectator.setInstance(Main.lobbyInstance);
        }

        sendMessage(MM.deserialize("<gray>Transferring to <white>lobby-" + Main.lobbyInstance.getUuid()));
        CompletableFuture.allOf(futures).join();
        CompletableFuture.allOf(player1.setInstance(Main.lobbyInstance), player2.setInstance(Main.lobbyInstance))
                .whenComplete((v, t) -> MinecraftServer.getInstanceManager().unregisterInstance(this));
    }

    public void sendTurnMessage() {
        FillerPlayer currentFillerPlayer = filler.getCurrentPlayer();
        Player currentPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(currentFillerPlayer.playerUUID);
        Player otherPlayer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(currentFillerPlayer.getOtherPlayer().playerUUID);

        if (currentPlayer != null && otherPlayer != null) {
            GamePlayer gamePlayer = (GamePlayer) currentPlayer;
            sendMessage(MM.deserialize(gamePlayer.rank.getPrefix() + " " + currentPlayer.getUsername() + "<white>'s turn!"));
        } else {
            System.err.println("Error: Could not find current or other player object.");
        }
    }

    public void updateVisuals() {
        FillerBlock[][] fillerBoard = filler.getBoard();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Entity blockDisplay = board[x][y];
                blockDisplay.setGlowing(false);
                BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
                blockDisplayMeta.setBlockState(fillerColorToMaterial(fillerBoard[x][y].getColor()).block());
            }
        }

        List<FillerBlock> ownedBlocks = filler.getCurrentPlayer().getOwnedBlocks();
        for (FillerBlock ownedBlock : ownedBlocks) {
            Entity blockDisplay = board[ownedBlock.getX()][ownedBlock.getY()];
            BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            blockDisplayMeta.setGlowColorOverride(Color.WHITE.getRGB());
            blockDisplay.setGlowing(true);
        }

        for (int i = 0; i < 2; i++) {
            FillerPlayer fillerPlayer = filler.getPlayers()[i];
            Entity scoreTextDisplay = playerScoreTextDisplays[i];
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(fillerPlayer.playerUUID);

            if (player != null) {
                int score = fillerPlayer.getOwnedBlocks().size();
                TextDisplayMeta textDisplayMeta = (TextDisplayMeta) scoreTextDisplay.getEntityMeta();
                FillerColor color = fillerPlayer.getColor();
                String hex = fillerColorToHex(color);
                GamePlayer gamePlayer = (GamePlayer) player;
                textDisplayMeta.setText(MM.deserialize( gamePlayer.rank.getPrefix() + " " + player.getUsername() + "<bold><newline><" + hex + ">" + score));
            } else {
                System.err.println("Error: Could not find player object to update score visual.");
            }
        }
    }

    public void setHotBar(Player player) {
        player.getInventory().clear();

        boolean isTurn = isPlayerTurn(player.getUuid());
        FillerColor[] colors = FillerColor.values();
        int i = 0;
        for (FillerColor color : colors) {
            ItemStack item = ItemStack.builder(Material.BARRIER)
                    .set(ItemComponent.ITEM_NAME, MM.deserialize("<bold><" + fillerColorToHex(color) + ">" + color.name()))
                    .build();

            if (!isTurn) {
                player.getInventory().setItemStack(i++, item);
                continue;
            }

            List<FillerColor> availableColors = filler.getCurrentPlayer().getAvailableColors();
            if (availableColors.contains(color)) {
                ItemStack block = ItemStack.builder(fillerColorToMaterial(color))
                        .set(ItemComponent.ITEM_NAME, MM.deserialize("<bold><" + fillerColorToHex(color) + ">" + color.name()))
                        .build();
                player.getInventory().setItemStack(i++, block);
            } else {
                player.getInventory().setItemStack(i++, item);
            }
        }
    }

    public Material fillerColorToMaterial(FillerColor color) {
        return switch (color) {
            case RED -> Material.RED_CONCRETE;
            case GREEN -> Material.GREEN_CONCRETE;
            case BLUE -> Material.BLUE_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
            case PURPLE -> Material.PURPLE_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
        };
    }

    public FillerColor materialToFillerColor(Material material) {
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