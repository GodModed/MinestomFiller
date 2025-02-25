package social.godmode;

import com.github.echolightmc.msnametags.NameTag;
import com.github.echolightmc.msnametags.NameTagManager;
import com.github.echolightmc.msnpcs.NPC;
import com.github.echolightmc.msnpcs.NPCManager;
import net.kyori.adventure.bossbar.BossBar;
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
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.ping.ResponseData;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.scoreboard.Team;
import net.minestom.server.scoreboard.TeamBuilder;
import net.minestom.server.scoreboard.TeamManager;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.identity.NamedAndIdentified;
import net.minestom.server.world.DimensionType;
import social.godmode.instance.GameInstance;
import social.godmode.instance.MapInstance;
import social.godmode.replay.ReplayManager;
import social.godmode.user.GamePlayer;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class Main {

    public static Instance lobbyInstance;
    public static InstanceContainer sharedGameInstance;

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<Player> queue = new LinkedList<>();
    private static NameTag queueNameTag;

    private static final Pos QUEUE_NPC_POSITION = new Pos(0, 1, 10, 180, 0);
    private static final Pos QUEUE_NAME_TAG_TRANSLATION = new Pos(0, 4, 0);
    private static final Vec QUEUE_NAME_TAG_SCALE = new Vec(5);
    public static final Pos PLAYER1_GAME_POS = new Pos(0, 138, -6).withLookAt(new Pos(0, 140, 0));
    public static final Pos PLAYER2_GAME_POS = new Pos(0, 138, 6).withLookAt(new Pos(0, 140, 0));
    private static final String[] MOTD = {"Block by block, let the adventure begin!", "Who needs a plan when you’ve got creativity on tap?", "Mix a little luck with every block, and watch the magic happen.", "Every block tells a story—what’s yours?"};
    private static final String DIMENSION_ID = "dimension:bright";
    private static final String TEAM_NAME_TAGS = "name-tags";
    private static final Random RANDOM = new Random();
    public static final ReplayManager REPLAY_MANAGER = new ReplayManager();

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init();
        initializeDimensionAndInstances();

        NameTagManager nameTagManager = initializeNameTags(MinecraftServer.getGlobalEventHandler());
        NPC queueNPC = createQueueNPC(nameTagManager, MinecraftServer.getGlobalEventHandler());

        queueNameTag = nameTagManager.createNameTag(queueNPC);
        configureQueueNameTag();
        updateQueue();

        registerEventListeners(nameTagManager);
        CommandLoader.loadCommands();

        MojangAuth.init();
        server.start("0.0.0.0", 25565);
    }

    private static void initializeDimensionAndInstances() {
        DimensionType type = DimensionType.builder()
                .ambientLight(1)
                .fixedTime(18000L)
                .build();
        DynamicRegistry.Key<DimensionType> dimensionKey = MinecraftServer.getDimensionTypeRegistry().register(DIMENSION_ID, type);
        lobbyInstance = MinecraftServer.getInstanceManager().createInstanceContainer(dimensionKey);

        lobbyInstance.setGenerator(chunk -> chunk.modifier().fillHeight(0, 1, Block.QUARTZ_BRICKS));

        sharedGameInstance = new MapInstance(dimensionKey);
    }

    private static NameTagManager initializeNameTags(GlobalEventHandler handler) {
        TeamManager teamManager = MinecraftServer.getTeamManager();
        Team nameTagTeam = new TeamBuilder(TEAM_NAME_TAGS, teamManager)
                .seeInvisiblePlayers()
                .build();
        return new NameTagManager(handler, entity -> nameTagTeam);
    }

    private static NPC createQueueNPC(NameTagManager nameTagManager, GlobalEventHandler handler) {
        Consumer<net.minestom.server.event.Event> eventConsumer = event -> {
            if (event instanceof PlayerEntityInteractEvent interactEvent) {
                joinQueue(interactEvent.getPlayer());
            } else if (event instanceof EntityAttackEvent attackEvent) {
                Player attacked = (Player) attackEvent.getEntity();
                joinQueue(attacked);
            }
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

        MinecraftServer.getCommandManager().setUnknownCommandCallback((sender, command) ->
                sender.sendMessage(MM.deserialize("<red>Unknown command"))
        );

        MinecraftServer.getConnectionManager().setPlayerProvider(GamePlayer::new);

        setupShutdownTasks();

        handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(lobbyInstance);
            player.setRespawnPoint(new Pos(0, 1, 0));
        });

        handler.addListener(PlayerDisconnectEvent.class, event -> {
            final Player player = event.getPlayer();
            final GamePlayer gamePlayer = (GamePlayer) player;
            Audiences.players().sendMessage(MM.deserialize("<gray>[<red>-</red>]</gray> " + gamePlayer.rank.getPrefix() + " " + gamePlayer.getUsername()));
            removeFromQueue(player);

            gamePlayer.saveStats();
        });

        handler.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) return;
            final GamePlayer player = (GamePlayer) event.getPlayer();

            Potion potion = new Potion(PotionEffect.JUMP_BOOST, 1, Potion.INFINITE_DURATION);
            player.addEffect(potion);

            player.initializeSidebar();
            player.initializeNameTag(nameTagManager);

            Audiences.players().sendMessage(MM.deserialize("<gray>[<green>+</green>]</gray> " + player.rank.getPrefix() + " " + player.getUsername()));
        });

        handler.addListener(PlayerChatEvent.class, event -> {
            event.setCancelled(true);
            GamePlayer player = (GamePlayer) event.getPlayer();
            String message = event.getRawMessage();
            Audiences.players().sendMessage(MM.deserialize(player.rank.getPrefix() + " " + player.getUsername() + "<white> → " + message));
        });

        handler.addListener(ServerListPingEvent.class, event -> {
            ResponseData responseData = event.getResponseData();

            int maxPlayers = 1_000_000_000 + RANDOM.nextInt(Integer.MAX_VALUE - 1_000_000_000);
            responseData.setMaxPlayer(maxPlayers);


            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                GamePlayer gamePlayer = (GamePlayer) player;
                Component nameTagText = MM.deserialize(gamePlayer.rank.getPrefix() + " " + player.getUsername());
                responseData.addEntry(NamedAndIdentified.of(nameTagText, player.getUuid()));
            }

            String motd = MOTD[RANDOM.nextInt(MOTD.length)];
            responseData.setFavicon("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIW2NYqtf7HwAFPAJgumgTFgAAAABJRU5ErkJggg==");
            responseData.setDescription(MM.deserialize("<b><gradient:#4169e1:#ff00ff><obf>*</obf> Filler <obf>*</obf></gradient></b><newline><gray>" + motd));
        });

        BossBar bossBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        DecimalFormat dec = new DecimalFormat("0.00");
        MinecraftServer.getGlobalEventHandler().addListener(ServerTickMonitorEvent.class, e -> {
            double tickTime = Math.floor(e.getTickMonitor().getTickTime() * 100.0) / 100.0;
            bossBar.name(
                    Component.text()
                            .append(Component.text("MSPT: " + dec.format(tickTime)))
            );
            bossBar.progress(Math.min((float)tickTime / (float)MinecraftServer.TICK_MS, 1f));

            if (tickTime > MinecraftServer.TICK_MS) {
                bossBar.color(BossBar.Color.RED);
            } else {
                bossBar.color(BossBar.Color.GREEN);
            }
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, e -> {
            e.getPlayer().showBossBar(bossBar);
        });

        handler.addListener(ItemDropEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerBlockPlaceEvent.class, event -> event.setCancelled(true));
        handler.addListener(PlayerBlockBreakEvent.class, event -> event.setCancelled(true));
    }

    private static void setupShutdownTasks() {
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                GamePlayer gamePlayer = (GamePlayer) player;
                gamePlayer.saveStats();
                gamePlayer.kick(MM.deserialize("<red>Server is shutting down."));
            }
        });

        MinecraftServer.getSchedulerManager().submitTask(() -> {
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
                GamePlayer gamePlayer = (GamePlayer) player;
                gamePlayer.saveStats();
            });
            return TaskSchedule.minutes(1);
        });
    }

    private static void joinQueue(Player player) {
        if (GameInstance.isInGame(player)) return;

        if (queue.contains(player)) {
            removeFromQueue(player);
            player.sendMessage(MM.deserialize("<red>You have left the queue."));
            return;
        }

        queue.add(player);
        updateQueue();
        player.sendMessage(MM.deserialize("<green>You have joined the queue."));

        if (queue.size() >= 2) {
            GamePlayer player1 = (GamePlayer) queue.removeFirst();
            GamePlayer player2 = (GamePlayer) queue.removeFirst();

            // Randomize player order
            if (RANDOM.nextDouble() > 0.5) {
                GamePlayer temp = player1;
                player1 = player2;
                player2 = temp;
            }

            Component message = MM.deserialize("<green>Match found! " + player1.rank.getPrefix() + " " + player1.getUsername() + "<green> vs " + player2.rank.getPrefix() + " " + player2.getUsername());
            player1.sendMessage(message);
            player2.sendMessage(message);

            GameInstance gameInstance = new GameInstance(player1, player2);

            Component transferMessage = getTransferMessage("game", gameInstance);
            player1.sendMessage(transferMessage);
            player2.sendMessage(transferMessage);

            player1.setInstance(gameInstance, PLAYER1_GAME_POS);
            player2.setInstance(gameInstance, PLAYER2_GAME_POS);

            updateQueue();
        }
    }

    private static void removeFromQueue(Player player) {
        boolean removed = queue.remove(player);
        if (removed) {
            updateQueue();
        }
    }

    private static void updateQueue() {
        queueNameTag.setText(MM.deserialize("<gradient:#4169e1:#ff00ff><bold>QUEUE</bold> " + queue.size() + "</gradient>"));
    }

    public static Component getTransferMessage(String name, Instance instance) {
        String hexUUID = Long.toHexString(instance.getUuid().getLeastSignificantBits());
        return MM.deserialize("<gray>Transferring to <white>" + name +  "-" + hexUUID);
    }
}