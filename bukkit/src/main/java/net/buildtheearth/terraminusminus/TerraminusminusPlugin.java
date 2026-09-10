package net.buildtheearth.terraminusminus;

import java.io.File;
import net.buildtheearth.terraminusminus.util.http.Disk;
import net.buildtheearth.terraminusminus.util.http.Http;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class TerraminusminusPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Centralize configuration and cache
        Disk.setConfigRoot(this.getDataFolder());
        Disk.setCacheRoot(new File(this.getDataFolder(), "cache"));

        Http.configChanged();

        // Register the service
        getServer().getServicesManager().register(TerraminusminusService.class, new TerraminusminusServiceImpl(), this, ServicePriority.Normal);

        getLogger().info("Terraminusminus plugin enabled!");
    }
}
