package social.godmode;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import social.godmode.commands.GrantCommand;
import social.godmode.commands.SpectateCommand;
import social.godmode.commands.StopCommand;

public class CommandLoader {

    public static void loadCommands() {
        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new GrantCommand());
        commandManager.register(new SpectateCommand());
        commandManager.register(new StopCommand());
    }

}
