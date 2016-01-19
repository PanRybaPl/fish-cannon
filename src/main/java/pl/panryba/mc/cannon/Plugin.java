/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.panryba.mc.cannon;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author PanRyba.pl
 */
public class Plugin extends JavaPlugin implements Listener {

    ProtectedRegion region;
    int xMin, zMin, xMax, zMax;
    ProtectedRegion igniteRegion;
    World world;
    double x, y, z;
    double tntx, tnty, tntz;

    Location explosion;

    boolean fired;

    @Override
    public void onEnable() {
        FileConfiguration config = getConfig();
        String worldName = config.getString("world");
        world = getServer().getWorld(worldName);

        RegionManager manager = WGBukkit.getRegionManager(world);
        String regionName = config.getString("region");
        region = manager.getRegion(regionName);

        BlockVector vmin = this.region.getMinimumPoint();
        BlockVector vmax = this.region.getMaximumPoint();
        Location min = new Location(this.world, vmin.getX(), vmin.getY(), vmin.getZ());
        Location max = new Location(this.world, vmax.getX(), vmax.getY(), vmax.getZ());
        Chunk c1 = min.getChunk();
        Chunk c2 = max.getChunk();
        xMin = Math.min(c1.getX(), c2.getX());
        xMax = Math.max(c1.getX(), c2.getX());
        zMin = Math.min(c1.getZ(), c2.getZ());
        zMax = Math.max(c1.getZ(), c2.getZ());

        String igniteName = config.getString("ignite");
        igniteRegion = manager.getRegion(igniteName);

        x = config.getDouble("velocity.player.x");
        y = config.getDouble("velocity.player.y");
        z = config.getDouble("velocity.player.z");

        tntx = config.getDouble("velocity.tnt.x");
        tnty = config.getDouble("velocity.tnt.y");
        tntz = config.getDouble("velocity.tnt.z");

        double ex = config.getDouble("explosion.x");
        double ey = config.getDouble("explosion.y");
        double ez = config.getDouble("explosion.z");

        explosion = new Location(world, ex, ey, ez);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ignite").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }

        ignite((Player) sender);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    private void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        if (isIgnition(event.getBlock().getLocation())) {
            ignite(event.getPlayer());
        }
    }

    private void ignite(Player ignitedBy) {
        long now = new Date().getTime();
        if (fired) {
            ignitedBy.sendMessage(ChatColor.BLUE + "Lont juz jest podpalony - armata za chwile wystrzeli!");
            return;
        }

        fired = true;
        scheduleIgnition(ignitedBy);
    }

    private boolean inCannon(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() != this.world) {
            return false;
        }

        Vector v = new Vector(loc.getX(), loc.getY(), loc.getZ());
        return this.region.contains(v);
    }

    private void launch(Player player) {
        player.setVelocity(new org.bukkit.util.Vector(x, y, z));
    }

    private boolean isIgnition(Location location) {
        if(this.igniteRegion == null) {
            return false;
        }

        if (location.getWorld() != this.world) {
            return false;
        }

        Vector v = new Vector(location.getX(), location.getY(), location.getZ());
        return this.igniteRegion.contains(v);
    }

    private Set<TNTPrimed> prepareTnts() {
        Set<TNTPrimed> tnts = new HashSet<>();

        for (int cx = xMin; cx <= xMax; cx++) {
            for (int cz = zMin; cz <= zMax; cz++) {
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    if ((e instanceof TNTPrimed)) {
                        Location eLoc = e.getLocation();
                        BlockVector loc = new BlockVector(eLoc.getX(), eLoc.getY(), eLoc.getZ());

                        if (region.contains(loc)) {
                            tnts.add((TNTPrimed) e);
                        }
                    }
                }
            }
        }

        return tnts;
    }

    private void launchTnts(Set<TNTPrimed> tnts) {
        for (TNTPrimed tnt : tnts) {
            tnt.setVelocity(new org.bukkit.util.Vector(tntx, tnty, tntz));
        }
    }

    private Set<Player> preparePlayers() {
        Set<Player> players = new HashSet<>();

        for (Player player : this.getServer().getOnlinePlayers()) {
            if (inCannon(player)) {
                players.add(player);
            }
        }

        return players;
    }

    private void launchPlayers(Set<Player> players) {
        for (Player player : players) {
            launch(player);
        }
    }

    private void scheduleIgnition(final Player ignitedBy) {
        ignitedBy.sendMessage(ChatColor.BLUE + "Podpaliles lont - armata za chwile wystrzeli!");

        getServer().getScheduler().runTaskLaterAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final Set<TNTPrimed> tnts = prepareTnts();
                final Set<Player> players = preparePlayers();

                getServer().getScheduler().runTask(Plugin.this, new Runnable() {

                    @Override
                    public void run() {
                        fired = false;

                        launchTnts(tnts);
                        launchPlayers(players);
                        Plugin.this.world.createExplosion(explosion, 1.0f, false);
                    }
                });

            }
        }, 20 * 5);
    }
}
