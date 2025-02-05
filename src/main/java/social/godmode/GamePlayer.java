package social.godmode;

import com.github.echolightmc.msnametags.NameTag;
import com.github.echolightmc.msnametags.NameTagManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.scoreboard.Sidebar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public class GamePlayer extends Player {

    private static final Gson GSON = new Gson();
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Logger log = LoggerFactory.getLogger(GamePlayer.class);

    public int wins = 0;
    public int losses = 0;
    public PlayerRank rank = PlayerRank.DEFAULT;
    public boolean inGame = false;
    public NameTag nameTag;

    public File dataFile = new File("data/" + getUuid());
    private Sidebar sidebar;
    private Sidebar.ScoreboardLine winsLine;
    private Sidebar.ScoreboardLine lossesLine;

    public GamePlayer(@NotNull PlayerConnection playerConnection, @NotNull GameProfile gameProfile) {
        super(playerConnection, gameProfile);
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
    }

    public void addWin() {
        wins++;
        updateScoreboard();
    }

    public void addLoss() {
        losses++;
        updateScoreboard();
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

    public void loadStats() {
        if (!dataFile.exists()) return;
        try {
            JsonObject jsonObject = GSON.fromJson(new java.io.FileReader(dataFile), JsonObject.class);
            wins = jsonObject.get("wins").getAsInt();
            losses = jsonObject.get("losses").getAsInt();
            rank = PlayerRank.valueOf(jsonObject.get("rank").getAsString());
        } catch (IOException e) {
            log.error("Error loading stats", e);
        }
    }

    public void saveStats() {
        // make a new gson object
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("wins", new JsonPrimitive(wins));
        jsonObject.add("losses", new JsonPrimitive(losses));
        jsonObject.add("rank", new JsonPrimitive(rank.name()));
        try {
            JsonWriter jsonWriter = new JsonWriter(new FileWriter(dataFile));
            jsonWriter.setIndent("\t");
            Gson gson = new Gson();
            gson.toJson(jsonObject, jsonWriter);
            jsonWriter.close();
        } catch (IOException e) {
            log.error("Error saving stats", e);
        }

    }
}
