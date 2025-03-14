package social.godmode.user;

import com.github.echolightmc.msnametags.NameTag;
import com.github.echolightmc.msnametags.NameTagManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.scoreboard.Sidebar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import social.godmode.replay.Replay;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class GamePlayer extends Player {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Logger log = LoggerFactory.getLogger(GamePlayer.class);
    public static final String DATABASE_URL = "jdbc:sqlite:playerdata.db";  // Database URL

    public int wins = 0;
    public int losses = 0;
    public PlayerRank rank = PlayerRank.DEFAULT;
    public boolean inGame = false;
    public NameTag nameTag;
    public Set<Integer> replayIDs = new HashSet<>();

    private Sidebar sidebar;
    private Sidebar.ScoreboardLine winsLine;
    private Sidebar.ScoreboardLine lossesLine;

    public GamePlayer(@NotNull PlayerConnection playerConnection, @NotNull GameProfile gameProfile) {
        super(playerConnection, gameProfile);
        createTableIfNotExists(); // Ensure the table exists
        loadStats();
    }

    public void setRank(PlayerRank rank) {
        this.rank = rank;
        updateNameTag();
    }

    public void initializeNameTag(NameTagManager nameTagManager) {
        nameTag = nameTagManager.createNameTag(this);
        nameTag.addViewer(this);
        nameTag.mount();
        updateNameTag();
    }

    public void updateNameTag() {
        nameTag.setText(MM.deserialize(rank.getPrefix() + " " + getUsername()));
        setDisplayName(MM.deserialize(rank.getPrefix() + " " + getUsername()));
    }

    public void addWin() {
        wins++;
        updateScoreboard();
        saveStats();
    }

    public void addLoss() {
        losses++;
        updateScoreboard();
        saveStats();
    }


    public void initializeSidebar() {
        sidebar = new Sidebar(MM.deserialize("<gradient:#4169e1:#ff00ff><bold>Filler</bold></gradient>"));
        sidebar.addViewer(this);


        winsLine = new Sidebar.ScoreboardLine(
                "wins",
                Component.empty(),
                2,
                Sidebar.NumberFormat.blank()
        );

        lossesLine = new Sidebar.ScoreboardLine(
                "losses",
                Component.empty(),
                1,
                Sidebar.NumberFormat.blank()
        );

        sidebar.createLine(generateEmptyLine(3));
        sidebar.createLine(winsLine);
        sidebar.createLine(lossesLine);
        sidebar.createLine(generateEmptyLine(0));

        updateScoreboard();
    }

    public Sidebar.ScoreboardLine generateEmptyLine(int line) {
        return new Sidebar.ScoreboardLine(
                UUID.randomUUID() + "",
                Component.empty(),
                line,
                Sidebar.NumberFormat.blank()
        );
    }

    public void updateScoreboard() {
        sidebar.updateLineContent(winsLine.getId(), MM.deserialize("<gradient:#4169e1:#ff00ff>→ <white>" + wins + "</white> wins</gradient>"));
        sidebar.updateLineContent(lossesLine.getId(), MM.deserialize("<gradient:#4169e1:#ff00ff>→ <white>" + losses + "</white> losses</gradient>"));
    }



    private void createTableIfNotExists() {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {

            try (Statement statement = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS player_stats (" +
                        "uuid TEXT PRIMARY KEY," +
                        "wins INTEGER," +
                        "losses INTEGER," +
                        "rank TEXT," +
                        "replays TEXT" +
                        ")";
                statement.execute(sql);
            }

        } catch (SQLException e) {
            log.error("Error creating or updating table", e);
        }
    }

    public void loadStats() {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement preparedStatement = connection.prepareStatement(
                     "SELECT wins, losses, rank, replays FROM player_stats WHERE uuid = ?")) {

            preparedStatement.setString(1, getUuid().toString());
            ResultSet resultSet = preparedStatement.executeQuery();

            if (resultSet.next()) {
                wins = resultSet.getInt("wins");
                losses = resultSet.getInt("losses");
                rank = PlayerRank.valueOf(resultSet.getString("rank"));

                String replaysString = resultSet.getString("replays");
                if (replaysString != null && !replaysString.isEmpty()) {
                    try {
                        replayIDs = Arrays.stream(replaysString.substring(1, replaysString.length() - 1).split(", "))
                                .filter(s -> !s.isEmpty())
                                .map(Integer::parseInt)
                                .collect(Collectors.toSet());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid replay ID format for player {}: {}", getUuid(), replaysString);
                        replayIDs.clear();
                    }

                }
            } else {
                saveStats();
            }

        } catch (SQLException e) {
            log.error("Error loading stats", e);
        }
    }
    public void saveStats() {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement preparedStatement = connection.prepareStatement(
                     "INSERT INTO player_stats (uuid, wins, losses, rank, replays) VALUES (?, ?, ?, ?, ?) " +
                             "ON CONFLICT(uuid) DO UPDATE SET wins=?, losses=?, rank=?, replays=?")) {

            preparedStatement.setString(1, getUuid().toString());
            preparedStatement.setInt(2, wins);
            preparedStatement.setInt(3, losses);
            preparedStatement.setString(4, rank.name());
            preparedStatement.setString(5, replayIDs.toString());
            preparedStatement.setInt(6, wins);
            preparedStatement.setInt(7, losses);
            preparedStatement.setString(8, rank.name());
            preparedStatement.setString(9, replayIDs.toString());

            preparedStatement.executeUpdate();

        } catch (SQLException e) {
            log.error("Error saving stats", e);
        }
    }
}