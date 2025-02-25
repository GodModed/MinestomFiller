package social.godmode;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

public class CommandLoader {

    public static void loadCommands() {
        CommandManager commandManager = MinecraftServer.getCommandManager();

        Reflections reflections = new Reflections("social.godmode.commands");

        Set<Class<? extends Command>> commandClasses = reflections.getSubTypesOf(Command.class);

        for (Class<? extends Command> commandClass : commandClasses) {
            try {
                Constructor<? extends Command> constructor = commandClass.getDeclaredConstructor();
                constructor.setAccessible(true);

                Command command = constructor.newInstance();
                commandManager.register(command);

                System.out.println("Registered command: " + commandClass.getName());

            } catch (NoSuchMethodException e) {
                System.err.println("Error: Command class " + commandClass.getName() + " must have a default (no-argument) constructor.");
                e.printStackTrace();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                System.err.println("Error: Could not instantiate command class " + commandClass.getName());
                e.printStackTrace();
            }
        }
    }
}