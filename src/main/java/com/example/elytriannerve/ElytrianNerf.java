package com.example.elytriannerve;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ElytrianNerf extends org.bukkit.plugin.java.JavaPlugin implements Listener {

    private static final long MESSAGE_INTERVAL_MS = 2000L;
    /** Период главного таймера в тиках (см. runTaskTimer в onEnable). */
    private static final int TICK_PERIOD = 2;
    private static final String ARMOR_MODIFIER_NAME = "ElytrianNerfIronArmor";
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab");

    private final Map<UUID, Long> hitUntil = new HashMap<>();
    private final Map<UUID, Long> waterUntil = new HashMap<>();
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    /** Накопленные тики полёта для «топлива» (раньше использовался getTicksLived() % interval, что не работало). */
    private final Map<UUID, Integer> fuelTicks = new HashMap<>();

    private boolean chatNotifications;
    private String hitTarget;
    private double hitBlockSeconds;
    private boolean onlyBareHand;
    private boolean stopGlidingOnHit;
    private boolean creativeFlightFix;
    private boolean hitRealElytraDebuff;
    private boolean waterEnabled;
    private double waterBlockSeconds;
    private boolean waterRealElytraDebuff;
    private boolean fuelEnabled;
    private int fuelInterval;
    private int fuelDepletion;
    private boolean fuelGiveEffect;
    private int fuelAmplifier;
    private int fuelDuration;
    private boolean fuelRealElytraDebuff;
    private boolean realElytraArmorEnabled;
    private boolean giveIronArmorStats;
    private String msgInWater;
    private String msgRecentlyHit;

    private static final Key ELYTRA_ABILITY = Key.key("origins:elytra");
    private Method hasAbilityMethod;

    private boolean tickErrorLogged = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        hookOrigins();
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, TICK_PERIOD);
        getLogger().info("ElytrianNerf v1.0 успешно активирован!");
    }

    @Override
    public void onDisable() {
        // Модификатор брони сохраняется в данных игрока, поэтому обязательно снимаем его.
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeArmorModifier(player);
        }
    }

    public void loadSettings() {
        reloadConfig();
        chatNotifications = getConfig().getBoolean("chat-notifications", true);
        hitTarget = getConfig().getString("hit.target", "player");
        hitBlockSeconds = getConfig().getDouble("hit.block-seconds", 10.0);
        onlyBareHand = getConfig().getBoolean("hit.only-bare-hand", false);
        stopGlidingOnHit = getConfig().getBoolean("hit.stop-gliding-on-hit", true);
        creativeFlightFix = getConfig().getBoolean("creative-flight-fix", true);
        hitRealElytraDebuff = getConfig().getBoolean("hit.real-elytra-debuff", false);
        waterEnabled = getConfig().getBoolean("water.enabled", true);
        waterBlockSeconds = getConfig().getDouble("water.block-seconds", 2.0);
        waterRealElytraDebuff = getConfig().getBoolean("water.real-elytra-debuff", false);
        fuelEnabled = getConfig().getBoolean("fuel.enabled", true);
        fuelInterval = Math.max(1, getConfig().getInt("fuel.interval-ticks", 20));
        fuelDepletion = getConfig().getInt("fuel.depletion", 1);
        fuelGiveEffect = getConfig().getBoolean("fuel.give-hunger-effect", true);
        fuelAmplifier = getConfig().getInt("fuel.amplifier", 0);
        fuelDuration = getConfig().getInt("fuel.duration-ticks", 40);
        fuelRealElytraDebuff = getConfig().getBoolean("fuel.real-elytra-debuff", false);
        realElytraArmorEnabled = getConfig().getBoolean("real-elytra-armor.enabled", true);
        giveIronArmorStats = getConfig().getBoolean("real-elytra-armor.give-iron-armor-stats", true);
        msgInWater = getConfig().getString("messages.in-water", "&cВ воде элитры недоступны!");
        msgRecentlyHit = getConfig().getString("messages.recently-hit",
                "&cЭлитры заблокированы ещё на %seconds% сек. после удара!");
    }

    private void hookOrigins() {
        Plugin origins = getServer().getPluginManager().getPlugin("Origins-Reborn");
        if (origins == null) {
            getLogger().warning("Origins-Reborn не найден: ограничения применяются ко всем игрокам без настоящих элитр.");
            return;
        }
        try {
            Class<?> register = Class.forName("com.starshootercity.util.AbilityRegister", true,
                    origins.getClass().getClassLoader());
            hasAbilityMethod = register.getMethod("hasAbility", Player.class, Key.class);
            getLogger().info("Подключён Origins-Reborn: ограничения действуют на игроков со способностью origins:elytra.");
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().warning("Не удалось подключиться к API Origins-Reborn (" + e
                    + "). Ограничения применяются ко всем игрокам без настоящих элитр.");
        }
    }

    /** Есть ли у игрока способность origins:elytra (без Origins — любой игрок без настоящих элитр). */
    private boolean isElytrian(Player player) {
        if (hasAbilityMethod != null) {
            try {
                Object result = hasAbilityMethod.invoke(null, player, ELYTRA_ABILITY);
                return result instanceof Boolean && (Boolean) result;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return !hasRealElytra(player);
    }

    private boolean hasRealElytra(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && chest.getType() == Material.ELYTRA;
    }

    /**
     * Попадает ли игрок под ограничение: Элитриан без настоящих элитр — всегда,
     * игрок с настоящими элитрами — только если включён соответствующий real-elytra-debuff.
     */
    private boolean affected(Player player, boolean realElytraDebuff) {
        if (hasRealElytra(player)) {
            return realElytraDebuff;
        }
        return isElytrian(player);
    }

    private static boolean isCreativeLike(Player player) {
        GameMode gm = player.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    // ------------------------------------------------------------------
    // Броня для настоящих элитр
    // ------------------------------------------------------------------

    private static boolean isOurModifier(AttributeModifier m) {
        try {
            String name = m.getName();
            if (name != null && (name.equals(ARMOR_MODIFIER_NAME) || name.contains(ARMOR_MODIFIER_UUID.toString()))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return ARMOR_MODIFIER_UUID.equals(m.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasArmorModifier(AttributeInstance armor) {
        for (AttributeModifier modifier : new ArrayList<>(armor.getModifiers())) {
            if (isOurModifier(modifier)) {
                return true;
            }
        }
        return false;
    }

    /** Добавляет бонус брони, пока надеты настоящие элитры, и СНИМАЕТ его, когда они сняты. */
    private void updateArmorModifier(Player player, boolean realElytra) {
        AttributeInstance armor = player.getAttribute(Attribute.GENERIC_ARMOR);
        if (armor == null) {
            return;
        }
        boolean want = realElytraArmorEnabled && giveIronArmorStats && realElytra;
        boolean has = hasArmorModifier(armor);
        if (want && !has) {
            try {
                armor.addModifier(new AttributeModifier(ARMOR_MODIFIER_UUID, ARMOR_MODIFIER_NAME, 6.0,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
            } catch (IllegalArgumentException alreadyApplied) {
                // модификатор уже есть — ничего не делаем
            }
        } else if (!want && has) {
            removeArmorModifier(player);
        }
    }

    private void removeArmorModifier(Player player) {
        try {
            AttributeInstance armor = player.getAttribute(Attribute.GENERIC_ARMOR);
            if (armor == null) {
                return;
            }
            for (AttributeModifier modifier : new ArrayList<>(armor.getModifiers())) {
                if (isOurModifier(modifier)) {
                    armor.removeModifier(modifier);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Блокировки: вода и удар
    // ------------------------------------------------------------------

    /** Сколько мс ещё действует блокировка от воды (в самой воде — всегда не меньше 1). */
    private long waterLeft(Player player, long now) {
        if (!waterEnabled || !affected(player, waterRealElytraDebuff)) {
            return 0L;
        }
        if (player.isInWater()) {
            return Math.max((long) (waterBlockSeconds * 1000.0), 1L);
        }
        return waterUntil.getOrDefault(player.getUniqueId(), 0L) - now;
    }

    private long hitLeft(Player player, long now) {
        if (!affected(player, hitRealElytraDebuff)) {
            return 0L;
        }
        return hitUntil.getOrDefault(player.getUniqueId(), 0L) - now;
    }

    private boolean isBlocked(Player player, long now) {
        return waterLeft(player, now) > 0L || hitLeft(player, now) > 0L;
    }

    private void notifyBlocked(Player player, long now) {
        if (waterLeft(player, now) > 0L) {
            notifyPlayer(player, msgInWater);
            return;
        }
        long left = hitLeft(player, now);
        if (left > 0L) {
            long seconds = Math.max(1L, (left + 999L) / 1000L);
            notifyPlayer(player, msgRecentlyHit.replace("%seconds%", String.valueOf(seconds)));
        }
    }

    // ------------------------------------------------------------------
    // Главный таймер
    // ------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                tickPlayer(player, now);
            } catch (Throwable t) {
                // Ошибка у одного игрока не должна ломать обработку всех остальных.
                if (!tickErrorLogged) {
                    tickErrorLogged = true;
                    getLogger().warning("Ошибка в обработчике игрока " + player.getName() + ": " + t);
                }
            }
        }
    }

    private void tickPlayer(Player player, long now) {
        UUID uuid = player.getUniqueId();
        boolean realElytra = hasRealElytra(player);

        updateArmorModifier(player, realElytra);

        if (isCreativeLike(player)) {
            fuelTicks.remove(uuid);
            return;
        }

        boolean inWater = player.isInWater();
        boolean gliding = player.isGliding();

        // Таймер «после воды» запускается при любом нахождении в воде, а не только в полёте.
        if (inWater && waterEnabled && affected(player, waterRealElytraDebuff)) {
            waterUntil.put(uuid, now + (long) (waterBlockSeconds * 1000.0));
        }

        if (!gliding) {
            fuelTicks.remove(uuid);
            return;
        }

        // Блокировка (вода / удар) прерывает полёт.
        if (isBlocked(player, now)) {
            player.setGliding(false);
            notifyBlocked(player, now);
            fuelTicks.remove(uuid);
            return;
        }

        // «Топливо»: расход сытости в полёте.
        if (fuelEnabled && (fuelRealElytraDebuff || !realElytra)) {
            int acc = fuelTicks.getOrDefault(uuid, 0) + TICK_PERIOD;
            if (acc >= fuelInterval) {
                acc -= fuelInterval;
                int food = player.getFoodLevel();
                player.setSaturation(0f);
                if (food > 0) {
                    player.setFoodLevel(Math.max(0, food - fuelDepletion));
                } else if (fuelGiveEffect) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, fuelDuration,
                            fuelAmplifier, true, false));
                }
            }
            fuelTicks.put(uuid, acc);
        } else {
            fuelTicks.remove(uuid);
        }
    }

    // ------------------------------------------------------------------
    // События
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        if (event.getDamager().getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        // Только ближний бой: стрелы, шипы и т.п. не считаются.
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        boolean isPlayerDamager = event.getDamager() instanceof Player;
        boolean isMobDamager = event.getDamager() instanceof LivingEntity && !isPlayerDamager;

        if (hitTarget.equals("player") && !isPlayerDamager) {
            return;
        }
        if (hitTarget.equals("only_mobs") && !isMobDamager) {
            return;
        }
        if (hitTarget.equals("all_mobs") && !isPlayerDamager && !isMobDamager) {
            return;
        }

        if (onlyBareHand && isPlayerDamager) {
            Player damager = (Player) event.getDamager();
            if (damager.getInventory().getItemInMainHand().getType() != Material.AIR) {
                return;
            }
        }

        if (isCreativeLike(victim) || !affected(victim, hitRealElytraDebuff)) {
            return;
        }

        long now = System.currentTimeMillis();
        hitUntil.put(victim.getUniqueId(), now + (long) (hitBlockSeconds * 1000.0));
        if (stopGlidingOnHit && victim.isGliding()) {
            victim.setGliding(false);
        }
        notifyBlocked(victim, now);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) {
            return;
        }
        Player player = event.getPlayer();
        GameMode gm = player.getGameMode();
        if (gm == GameMode.SPECTATOR) {
            return;
        }
        if (!isElytrian(player)) {
            return;
        }

        if (gm == GameMode.CREATIVE) {
            // Origins отменяет двойной прыжок и включает элитры — возвращаем обычный креативный полёт.
            if (creativeFlightFix) {
                if (player.isGliding()) {
                    player.setGliding(false);
                }
                event.setCancelled(false);
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (isBlocked(player, now)) {
            if (player.isGliding()) {
                player.setGliding(false);
            }
            notifyBlocked(player, now);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isGliding() || isCreativeLike(player)) {
            return;
        }
        if (isBlocked(player, System.currentTimeMillis())) {
            player.setGliding(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        hitUntil.remove(uuid);
        waterUntil.remove(uuid);
        lastMessage.remove(uuid);
        fuelTicks.remove(uuid);
        removeArmorModifier(player);
    }

    private void notifyPlayer(Player player, String message) {
        if (!chatNotifications || message == null || message.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < MESSAGE_INTERVAL_MS) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}
