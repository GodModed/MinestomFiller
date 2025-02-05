package social.godmode;

import com.github.echolightmc.msnametags.NameTag;
import com.github.echolightmc.msnametags.NameTagManager;
import com.github.echolightmc.msnpcs.NPC;
import com.github.echolightmc.msnpcs.NPCManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.metadata.animal.PandaMeta;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.scoreboard.Team;
import net.minestom.server.scoreboard.TeamBuilder;
import net.minestom.server.scoreboard.TeamManager;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.world.DimensionType;

import java.util.ArrayList;
import java.util.function.Consumer;

public class Main {

    public static Instance lobbyInstance;
    public static InstanceContainer sharedGameInstance;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ArrayList<Player> queue = new ArrayList<>();
    private static NameTag queueNameTag;

    private static final Pos QUEUE_NPC_POSITION = new Pos(0, 1, 10, 180, 0);
    private static final Pos QUEUE_NAME_TAG_TRANSLATION = new Pos(0, 4, 0);
    private static final Vec QUEUE_NAME_TAG_SCALE = new Vec(5);
    private static final Pos PLAYER1_GAME_POS = new Pos(0, 138, -6).withLookAt(new Pos(0, 140, 0));
    private static final Pos PLAYER2_GAME_POS = new Pos(0, 138, 6).withLookAt(new Pos(0, 140, 0));


    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init();

        // Initialize dimension and instances
        initializeDimensionAndInstances();

        // Initialize name tags and NPCs
        NameTagManager nameTagManager = initializeNameTags(MinecraftServer.getGlobalEventHandler());
        NPC queueNPC = createQueueNPC(nameTagManager, MinecraftServer.getGlobalEventHandler());

        // Set the queue name tag
        queueNameTag = nameTagManager.createNameTag(queueNPC);
        configureQueueNameTag();
        updateQueue();

        // Register event listeners
        registerEventListeners(nameTagManager);
        CommandLoader.loadCommands();

        // Enable Mojang authentication and start the server
        MojangAuth.init();
        server.start("0.0.0.0", 25565);
    }

    private static void initializeDimensionAndInstances() {
        DimensionType type = DimensionType.builder()
                .ambientLight(1)
                .fixedTime(18000L)
                .build();
        DynamicRegistry.Key<DimensionType> dimensionKey = MinecraftServer.getDimensionTypeRegistry().register("dimension:bright", type);
        lobbyInstance = MinecraftServer.getInstanceManager().createInstanceContainer(dimensionKey);

        lobbyInstance.setGenerator(chunk -> chunk.modifier().fillHeight(0, 1, Block.QUARTZ_BRICKS));

        sharedGameInstance = new MapInstance(dimensionKey);
    }

    private static NameTagManager initializeNameTags(GlobalEventHandler handler) {
        TeamManager teamManager = MinecraftServer.getTeamManager();
        Team nameTagTeam = new TeamBuilder("name-tags", teamManager)
                .seeInvisiblePlayers()
                .build();
        return new NameTagManager(handler, entity -> nameTagTeam);
    }

    private static NPC createQueueNPC(NameTagManager nameTagManager, GlobalEventHandler handler) {
        Consumer<net.minestom.server.event.Event> eventConsumer = event -> {
            Player player;
            if (event instanceof PlayerEntityInteractEvent) {
                player = ((PlayerEntityInteractEvent) event).getPlayer();
            } else if (event instanceof EntityAttackEvent) {
                player = (Player) ((EntityAttackEvent) event).getEntity();
            } else {
                return;
            }
            joinQueue(player);
        };

        NPCManager npcManager = new NPCManager(handler);
        NPC queueNPC = npcManager.createNPC(EntityType.PANDA, null, eventConsumer::accept, eventConsumer::accept);
        queueNPC.setNoGravity(true);
        PandaMeta pandaMeta = (PandaMeta) queueNPC.getEntityMeta();
        queueNPC.getAttribute(Attribute.SCALE).setBaseValue(2.5);
        pandaMeta.setSitting(true);
        queueNPC.setInstance(lobbyInstance, QUEUE_NPC_POSITION);
        return queueNPC;
    }

    private static void configureQueueNameTag() {
        TextDisplayMeta queueNameTagMeta = queueNameTag.getEntityMeta();
        queueNameTagMeta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED);
        queueNameTagMeta.setScale(QUEUE_NAME_TAG_SCALE);
        queueNameTag.setTranslation(QUEUE_NAME_TAG_TRANSLATION);
    }

    private static void registerEventListeners(NameTagManager nameTagManager) {
        GlobalEventHandler handler = MinecraftServer.getGlobalEventHandler();

        MinecraftServer.getCommandManager().setUnknownCommandCallback((sender, command) -> {
            sender.sendMessage(MM.deserialize("<red>Unknown command"));
        });

        MinecraftServer.getConnectionManager().setPlayerProvider(GamePlayer::new);
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                GamePlayer gamePlayer = (GamePlayer) player;
                gamePlayer.saveStats();
                gamePlayer.kick(MM.deserialize("<red>Server is shutting down."));
            }
        });

        MinecraftServer.getSchedulerManager().submitTask(() -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                GamePlayer gamePlayer = (GamePlayer) player;
                gamePlayer.saveStats();
            }
            return TaskSchedule.minutes(1);
        });

        handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(lobbyInstance);
            player.setRespawnPoint(new Pos(0, 1, 0));
        });

        handler.addListener(PlayerDisconnectEvent.class, event -> {
            final Player player = event.getPlayer();
            Audiences.players().sendMessage(MM.deserialize("<gray>[<red>-</red>]</gray> " + player.getUsername()));

            if (queue.contains(player)) {
                queue.remove(player);
                updateQueue();
            }

            GamePlayer gamePlayer = (GamePlayer) player;
            gamePlayer.saveStats();
        });

        handler.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) return;
            final Player player = event.getPlayer();

            Potion potion = new Potion(PotionEffect.JUMP_BOOST, 1, Potion.INFINITE_DURATION);
            player.addEffect(potion);

            GamePlayer gamePlayer = (GamePlayer) player;
            gamePlayer.initializeSidebar();
            gamePlayer.initializeNameTag(nameTagManager);

            Audiences.players().sendMessage(MM.deserialize("<gray>[<green>+</green>]</gray> " + gamePlayer.rank.getPrefix() + " " + player.getUsername()));
        });

        handler.addListener(PlayerChatEvent.class, event -> {
            event.setCancelled(true);
            final Player player = event.getPlayer();
            GamePlayer gamePlayer = (GamePlayer) player;
            String message = event.getRawMessage();
            Audiences.players().sendMessage(MM.deserialize(gamePlayer.rank.getPrefix() + " " + player.getUsername() + "<white> → " + message));
        });

        handler.addListener(ItemDropEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerBlockPlaceEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(true));
    }

    private static void joinQueue(Player player) {

        if (GameInstance.isInGame(player)) return;

        if (queue.contains(player)) {
            player.sendMessage(MM.deserialize("<red>You have left the queue."));
            queue.remove(player);
            updateQueue();
            return;
        }

        queue.add(player);
        updateQueue();
        player.sendMessage(MM.deserialize("<green>You have joined the queue."));

        if (queue.size() >= 2) {
            Player player1 = queue.removeFirst();
            Player player2 = queue.removeFirst();

            // Randomize player order
            if (Math.random() > 0.5) {
                Player temp = player1;
                player1 = player2;
                player2 = temp;
            }

            GamePlayer gamePlayer1 = (GamePlayer) player1;
            GamePlayer gamePlayer2 = (GamePlayer) player2;

            Component message = MM.deserialize("<green>Match found! " + gamePlayer1.rank.getPrefix() + " " + player1.getUsername() + "<green> vs " + gamePlayer2.rank.getPrefix() + " " + player2.getUsername());
            player1.sendMessage(message);
            player2.sendMessage(message);

            GameInstance gameInstance = new GameInstance(player1, player2);

            Component transferMessage = MM.deserialize("<gray>Transferring to <white>game-" + gameInstance.getUuid());
            player1.sendMessage(transferMessage);
            player2.sendMessage(transferMessage);

            player1.setInstance(gameInstance, PLAYER1_GAME_POS);
            player2.setInstance(gameInstance, PLAYER2_GAME_POS);

            updateQueue();
        }
    }

    private static void updateQueue() {
        queueNameTag.setText(MM.deserialize("<gradient:#4169e1:#ff00ff><bold>QUEUE</bold> " + queue.size() + "</gradient>"));
    }
}