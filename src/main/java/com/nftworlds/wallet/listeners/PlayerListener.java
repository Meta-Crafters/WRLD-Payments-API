package com.nftworlds.wallet.listeners;

import com.nftworlds.wallet.NFTWorlds;
import com.nftworlds.wallet.objects.NFTPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private NFTWorlds plugin;

    public PlayerListener() {
        this.plugin = NFTWorlds.getInstance();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        new NFTPlayer(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void postJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!NFTPlayer.getByUUID(p.getUniqueId()).isLinked()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', " \n&f&lIMPORTANT: &cYou do not have a wallet linked!\n&7Link your wallet at &a&nhttps://nftworlds.com/login&r\n "));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
            }, 20L);
        }
    }

    public void onQuit(PlayerQuitEvent event) {
        NFTPlayer.remove(event.getPlayer().getUniqueId());
    }

}
