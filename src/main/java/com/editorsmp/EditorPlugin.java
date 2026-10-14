package com.editorsmp;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;

public final class EditorPlugin extends JavaPlugin implements Listener {
    private NamespacedKey stoneKey;
    private final List<String> stones = List.of("Flame", "Ice", "Wind", "Earth", "Thunder", "Ocean", "Shadow", "Life");
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        stoneKey = new NamespacedKey(this, "editor_stone");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("EditorPlugin enabled!");
    }

    private ItemStack makeStone(String name) {
        Material material = switch (name) {
            case "Flame" -> Material.BLAZE_POWDER;
            case "Ice" -> Material.PRISMARINE_SHARD;
            case "Wind" -> Material.FEATHER;
            case "Earth" -> Material.IRON_INGOT;
            case "Thunder" -> Material.AMETHYST_SHARD;
            case "Ocean" -> Material.HEART_OF_THE_SEA;
            case "Shadow" -> Material.ENDER_PEARL;
            default -> Material.GHAST_TEAR;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name + " Stone");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click to use its power", ChatColor.YELLOW + "Editor SMP"));
        meta.getPersistentDataContainer().set(stoneKey, PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        return item;
    }

    private String getStone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(stoneKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getContents() != null) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (getStone(item) != null) return;
            }
        }
        List<String> available = new ArrayList<>(stones);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            for (ItemStack item : online.getInventory().getContents()) {
                String name = getStone(item);
                if (name != null) available.remove(name);
            }
        }
        if (available.isEmpty()) {
            player.sendMessage(ChatColor.RED + "All starting Stones are currently held by online players.");
            return;
        }
        String chosen = available.get(random.nextInt(available.size()));
        player.getInventory().addItem(makeStone(chosen));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "You received the " + chosen + " Stone!");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        String stone = getStone(player.getInventory().getItemInMainHand());
        if (stone == null) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long ready = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            player.sendMessage(ChatColor.RED + "Cooldown: " + ((ready - now + 999) / 1000) + "s");
            return;
        }
        cooldowns.put(player.getUniqueId(), now + 8000);
        switch (stone) {
            case "Flame" -> {
                Fireball fireball = player.launchProjectile(Fireball.class);
                fireball.setYield(1.5f);
                player.sendMessage(ChatColor.GOLD + "Flame Burst!");
            }
            case "Ice" -> {
                Snowball spike = player.launchProjectile(Snowball.class);
                spike.setCustomName("EditorIceSpike");
                spike.setVelocity(player.getEyeLocation().getDirection().multiply(2.2));
                player.sendMessage(ChatColor.AQUA + "Ice Spike!");
            }
            case "Wind" -> {
                player.setVelocity(new Vector(0, 1.6, 0).add(player.getLocation().getDirection().multiply(0.5)));
                player.setFallDistance(0);
                for (Entity entity : player.getNearbyEntities(4, 3, 4)) {
                    if (entity instanceof LivingEntity target && target != player) {
                        target.setVelocity(target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.4).setY(0.6));
                    }
                }
                player.sendMessage(ChatColor.WHITE + "Wind Shockwave!");
            }
            case "Earth" -> {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 100, 1));
                player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation(), 35, 1, 0.5, 1, Material.STONE.createBlockData());
                player.sendMessage(ChatColor.GRAY + "Earth Guard!");
            }
            case "Thunder" -> {
                LivingEntity target = getTarget(player, 15);
                if (target != null) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.damage(6, player);
                } else player.sendMessage(ChatColor.RED + "Look at an enemy first!");
            }
            case "Ocean" -> {
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 6));
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 30, 0.7, 0.8, 0.7, 0.1);
                player.sendMessage(ChatColor.BLUE + "Ocean Renewal!");
            }
            case "Shadow" -> {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 80, 0));
                for (Entity entity : player.getNearbyEntities(5, 3, 5)) {
                    if (entity instanceof Player target && target != player) {
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0));
                    }
                }
                player.sendMessage(ChatColor.DARK_PURPLE + "Shadow Veil!");
            }
            case "Life" -> {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 100, 1));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 160, 0));
                player.sendMessage(ChatColor.GREEN + "Life Force!");
            }
        }
    }

    @EventHandler
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player shooter)) return;
        if (!"EditorIceSpike".equals(snowball.getCustomName())) return;
        if (event.getHitEntity() instanceof LivingEntity target && target != shooter) {
            target.damage(5, shooter);
            target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 100));
            target.setVelocity(target.getVelocity().multiply(0.35));
        }
        snowball.getWorld().spawnParticle(Particle.SNOWFLAKE, snowball.getLocation(), 20, 0.5, 0.5, 0.5, 0.05);
    }

    private LivingEntity getTarget(Player player, double range) {
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity target) || target == player) continue;
            Vector direction = target.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
            if (direction.dot(player.getEyeLocation().getDirection()) > 0.97 &&
                player.hasLineOfSight(target)) return target;
        }
        return null;
    }
                           }
