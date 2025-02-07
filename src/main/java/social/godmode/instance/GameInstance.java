package social.godmode.instance;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.advancements.FrameType;
import net.minestom.server.advancements.Notification;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import social.godmode.FillerAPI.Filler;
import social.godmode.FillerAPI.FillerBlock;
import social.godmode.FillerAPI.FillerColor;
import social.godmode.FillerAPI.FillerPlayer;
import social.godmode.replay.Replay;
import social.godmode.user.GamePlayer;
import social.godmode.Main;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GameInstance extends SharedInstance {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pos SCORE_TEXT_DISPLAY_POS_PLAYER1 = new Pos(-4, 148, 0.5);
    private static final Pos SCORE_TEXT_DISPLAY_POS_PLAYER2 = new Pos(4, 148, 0.5);
    private static final Vec TEXT_DISPLAY_SCALE = new Vec(2);
    public static final ArrayList<GameInstance> games = new ArrayList<>();
    private static final Key SOUND_KEY = Key.key("minecraft", "ui.hud.bubble_pop");
    public static final Potion DARKNESS_POTION = new Potion(PotionEffect.DARKNESS, 1, 60);
    private static final String SPECTATOR_LEAVE_MESSAGE = "<red>You are no longer spectating.</red>";

    public final GamePlayer player1;
    public final GamePlayer player2;
    public final List<Player> spectators = new ArrayList<>();
    private final Filler filler;
    private final Entity[][] board = new Entity[8][8];
    private final Entity[] playerScoreTextDisplays = new Entity[2];
    private final List<FillerColor> moves = new ArrayList<>();

    public GameInstance(GamePlayer player1, GamePlayer player2) {
        this(player1, player2, new Random().nextLong());
    }

    public GameInstance(GamePlayer player1, GamePlayer player2, long seed) {
        super(UUID.randomUUID(), Main.sharedGameInstance);
        MinecraftServer.getInstanceManager().registerSharedInstance(this);

        this.player1 = player1;
        player1.inGame = true;

        this.player2 = player2;
        player2.inGame = true;

        filler = new Filler(seed, player1.getUuid(), player2.getUuid());

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
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                Entity blockDisplay = createBlockDisplayEntity(x, y);
                board[x][y] = blockDisplay;
            }
        }
    }

    private Entity createBlockDisplayEntity(int x, int y) {
        Entity blockDisplay = new Entity(EntityType.BLOCK_DISPLAY);
        blockDisplay.setNoGravity(true);
        blockDisplay.setInstance(this, new Pos(x - 4, y + 138, 0));
        BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
        FillerBlock fillerBlock = filler.getBoard()[x][y];
        blockDisplayMeta.setBlockState(fillerColorToMaterial(fillerBlock.getColor()).block());
        return blockDisplay;
    }

    private void initializeScoreDisplays() {
        for (int i = 0; i < 2; i++) {
            Entity scoreTextDisplay = createScoreTextDisplayEntity(i);
            playerScoreTextDisplays[i] = scoreTextDisplay;
        }
    }

    private Entity createScoreTextDisplayEntity(int playerIndex) {
        Entity scoreTextDisplay = new Entity(EntityType.TEXT_DISPLAY);
        scoreTextDisplay.setNoGravity(true);
        scoreTextDisplay.setInstance(this, playerIndex == 0 ? SCORE_TEXT_DISPLAY_POS_PLAYER1 : SCORE_TEXT_DISPLAY_POS_PLAYER2);
        TextDisplayMeta textDisplayMeta = (TextDisplayMeta) scoreTextDisplay.getEntityMeta();
        textDisplayMeta.setUseDefaultBackground(false);
        textDisplayMeta.setBackgroundColor(0);
        textDisplayMeta.setScale(TEXT_DISPLAY_SCALE);
        textDisplayMeta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
        return scoreTextDisplay;
    }

    private void setupEventListeners() {
        eventNode().addListener(PlayerUseItemEvent.class, event -> {
            GamePlayer player = (GamePlayer) event.getPlayer();
            ItemStack itemStack = event.getItemStack();

            // Handle spectator leaving
            if (itemStack.material() == Material.RED_DYE) {
                handleSpectatorLeave(player);
                return;
            }

            if (!isPlayerTurn(player.getUuid()) || itemStack.isAir() || itemStack.material() == Material.BARRIER) {
                return;
            }

            FillerColor color = materialToFillerColor(itemStack.material());
            if (color == null || !filler.getCurrentPlayer().getAvailableColors().contains(color)) {
                return;
            }

            performPlayerTurn(player, color);

        }).addListener(PlayerDisconnectEvent.class, event -> cleanupAndReturnToLobby());
    }

    private void handleSpectatorLeave(Player player) {
        spectators.remove(player);
        player.setInvisible(false);
        player.getInventory().clear();
        player.sendMessage(MM.deserialize(SPECTATOR_LEAVE_MESSAGE));
        player.setInstance(Main.lobbyInstance);
    }

    private void performPlayerTurn(GamePlayer player, FillerColor color) {
        filler.getCurrentPlayer().turn(color);
        moves.add(color);

        for (Player p : getPlayers()) {
            playSound(p, SOUND_KEY, 1, 5);
        }

        sendMessage(MM.deserialize(player.rank.getPrefix() + " " + player.getUsername() + "<white> has chosen <bold><" + fillerColorToHex(color) + ">" + color.name()));

        updateVisuals();

        if (!filler.isGameEnded()) {
            sendTurnMessage();
            setHotBar(player1);
            setHotBar(player2);
        } else {
            endGame();
        }
    }

    private void playSound(Player player, Key soundKey, float volume, float pitch) {
        Sound sound = Sound.sound(soundKey, Sound.Source.MASTER, volume, pitch);
        player.playSound(sound);
    }

    private boolean isPlayerTurn(UUID playerUUID) {
        return filler.getCurrentPlayer().playerUUID.equals(playerUUID);
    }

    private void endGame() {
        FillerPlayer gameWinner = filler.getWinner();
        GamePlayer winner = (GamePlayer) MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(gameWinner.playerUUID);
        FillerPlayer loserPlayer = Arrays.stream(filler.getPlayers()).filter(p -> !p.equals(gameWinner)).findFirst().orElse(null);
        GamePlayer loser = (GamePlayer) (loserPlayer != null ? MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(loserPlayer.playerUUID) : null);

        if (winner != null && loser != null) {
            Replay replay = new Replay(filler.getSeed(), moves, winner.getUuid(), loser.getUuid());
            int replayID = Main.REPLAY_MANAGER.saveReplay(replay);
            winner.replayIDs.add(replayID);
            loser.replayIDs.add(replayID);
            announceWinnerAndLoser(winner, loser);
            cleanupAndReturnToLobby();
        } else {
            System.err.println("Error: Could not find winner or loser player object.");
        }
    }

    private void announceWinnerAndLoser(GamePlayer winner, GamePlayer loser) {

        Notification winnerNotification = new Notification(
                MM.deserialize("<green><bold>You win!"),
                FrameType.CHALLENGE,
                ItemStack.of(Material.DIAMOND)
        );

        Notification loserNotification = new Notification(
                MM.deserialize("<red><bold>You lose!"),
                FrameType.TASK,
                ItemStack.of(Material.BARRIER)
        );

        winner.sendMessage(MM.deserialize("<green><bold>You win!"));
        winner.sendNotification(winnerNotification);
        winner.addWin();
        playSound(winner, Key.key("minecraft", "entity.player.levelup"), 1, 1);

        loser.sendMessage(MM.deserialize("<red><bold>You lose!"));
        loser.sendNotification(loserNotification);
        loser.addLoss();
        playSound(loser, Key.key("minecraft", "entity.villager.no"), 1, 1);
    }

    private CompletableFuture<Void> teleportPlayerToLobby(GamePlayer player) {
        if (!player.isOnline()) return CompletableFuture.completedFuture(null);

        clearPlayerInventory(player);
        player.addEffect(DARKNESS_POTION);
        player.inGame = false;

        if (player.isInvisible()) {
            player.setInvisible(false);
        }

        player.sendMessage(Main.getTransferMessage("lobby", Main.lobbyInstance));

        return player.setInstance(Main.lobbyInstance);
    }

    private void cleanupAndReturnToLobby() {
        games.remove(this);

        @SuppressWarnings("rawtypes")
        CompletableFuture[] futures = new CompletableFuture[getPlayers().size()];

        ArrayList<Player> players = new ArrayList<>(getPlayers());
        for (int i = 0; i < players.size(); i++) {
            futures[i] = teleportPlayerToLobby((GamePlayer) players.get(i));
        }

        CompletableFuture.allOf(futures).thenRun(() -> MinecraftServer.getInstanceManager().unregisterInstance(this));
    }

    private void clearPlayerInventory(Player player) {
        player.getInventory().clear();
    }

    public void sendTurnMessage() {
        FillerPlayer currentFillerPlayer = filler.getCurrentPlayer();
        GamePlayer currentPlayer = (GamePlayer) MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(currentFillerPlayer.playerUUID);

        if (currentPlayer != null) {
            sendMessage(MM.deserialize(currentPlayer.rank.getPrefix() + " " + currentPlayer.getUsername() + "<white>'s turn!"));
        } else {
            System.err.println("Could not find current player object to send turn message.");
        }
    }

    public void updateVisuals() {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                updateBlockDisplayVisual(x, y);
            }
        }
        highlightCurrentPlayerOwnedBlocks();
        updateScoreDisplays();
    }

    private void updateBlockDisplayVisual(int x, int y) {
        Entity blockDisplay = board[x][y];
        blockDisplay.setGlowing(false);
        BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
        FillerBlock fillerBlock = filler.getBoard()[x][y];
        blockDisplayMeta.setBlockState(fillerColorToMaterial(fillerBlock.getColor()).block());
    }

    private void highlightCurrentPlayerOwnedBlocks() {
        List<FillerBlock> ownedBlocks = filler.getCurrentPlayer().getOwnedBlocks();
        for (FillerBlock ownedBlock : ownedBlocks) {
            Entity blockDisplay = board[ownedBlock.getX()][ownedBlock.getY()];
            BlockDisplayMeta blockDisplayMeta = (BlockDisplayMeta) blockDisplay.getEntityMeta();
            blockDisplayMeta.setGlowColorOverride(Color.WHITE.getRGB());
            blockDisplay.setGlowing(true);
        }
    }

    private void updateScoreDisplays() {
        for (int i = 0; i < 2; i++) {
            FillerPlayer fillerPlayer = filler.getPlayers()[i];
            Entity scoreTextDisplay = playerScoreTextDisplays[i];
            GamePlayer player = (GamePlayer) MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(fillerPlayer.playerUUID);

            if (player != null) {
                updateSingleScoreDisplay(player, scoreTextDisplay, fillerPlayer);
            } else {
                System.err.println("Could not find player object to update score visual.");
            }
        }
    }

    private void updateSingleScoreDisplay(GamePlayer player, Entity scoreTextDisplay, FillerPlayer fillerPlayer) {
        int score = fillerPlayer.getOwnedBlocks().size();
        TextDisplayMeta textDisplayMeta = (TextDisplayMeta) scoreTextDisplay.getEntityMeta();
        FillerColor color = fillerPlayer.getColor();
        String hex = fillerColorToHex(color);
        Component scoreComponent = MM.deserialize(player.rank.getPrefix() + " " + player.getUsername() + "<bold><newline><" + hex + ">" + score);
        textDisplayMeta.setText(scoreComponent);
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