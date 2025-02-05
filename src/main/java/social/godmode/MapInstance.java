package social.godmode;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MapInstance extends InstanceContainer {

    public MapInstance(DynamicRegistry.@NotNull Key<DimensionType> dimensionType) {
        super(UUID.randomUUID(), dimensionType);
        MinecraftServer.getInstanceManager().registerInstance(this);
        setChunkLoader(new AnvilLoader("abc"));

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                setBlock(x - 4, y + 138, 0, Block.BARRIER);
            }
        }

    }

}
