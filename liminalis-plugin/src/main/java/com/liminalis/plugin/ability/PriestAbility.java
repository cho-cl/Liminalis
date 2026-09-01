package com.liminalis.plugin.ability;

import com.liminalis.core.ability.Undead;
import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.injury.Injury;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.TraitTuning;
import com.liminalis.plugin.modifier.capability.Ticking;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Priest - the reference implementation, and the Creator's own.
 *
 * <p>Exists as much to be copied as to be played. Every ability written after this one is five
 * powers on the same frame, so this is written longhand rather than assembled from helpers:
 * the next one should be writable by reading this one and changing what each power does.
 *
 * <p>The five are deliberately not five sizes of the same thing. One helps someone else, one
 * helps you, one hurts what deserves it, one protects a group, and one does something nothing
 * else in the world can. An ability whose powers are all the same verb has one power and four
 * numbers.
 *
 * <p>Nothing here touches a player as a target of harm. Abilities are for surviving and for
 * each other, and the one that heals should be the clearest example of that.
 *
 * <p>It used to declare its own unlock conditions too - so many hearts healed for the first
 * half, so many undead felled for the second. That is gone, along with the two counters it
 * kept. Every ability climbs the same ladder now: use it, and the next power opens. What is
 * left here is five powers and nothing else, which is exactly what the next ability written
 * by copying this one should have to be.
 */
public final class PriestAbility implements Ability, Ticking {

    public static final String ID = "priest";

    private final JavaPlugin plugin;
    private final TraitTuning tuning;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;

    /** Consecrations still burning, so the shared loop can keep drawing them. */
    private final Map<UUID, Consecration> consecrations = new ConcurrentHashMap<>();

    /** Shared-loop intervals since each priest last gave off a mote. */
    private final Map<UUID, Integer> ambientCounters = new ConcurrentHashMap<>();

    public PriestAbility(JavaPlugin plugin,
                         TraitTuning tuning,
                         ProfileManager profiles,
                         ModifierRegistry registry,
                         ModifierService modifiers,
                         Messages messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String id() {
        return ID;
    }

    /**
     * Drives everything the Priest shows that is not a single moment.
     *
     * <p>Through the shared modifier loop, like every other ticking thing in the plugin. An
     * ability that scheduled its own repeating task for a glow would be the first one to do
     * it, and the tenth ability written by copying this one would make ten of them.
     */
    @Override
    public void tick(Player priest) {
        sustainConsecration(priest);
        ambient(priest);
    }

    @Override
    public void onDetach(Player priest) {
        consecrations.remove(priest.getUniqueId());
        ambientCounters.remove(priest.getUniqueId());
    }

    @Override
    public List<Power> powers() {
        return List.of(new LayHands(), new MendSelf(), new HolySmite(),
                new Consecrate(), new CloseTheWound());
    }

    // ------------------------------------------------------------------------ 1. others

    /**
     * Heal someone else, and close what is closeable.
     *
     * <p>Clears ordinary injuries as well as restoring health, which is what keeps the first
     * power worth using once the later ones exist. Mortal wounds are untouched - those are
     * power five, and the whole arc of the ability is earning the right to fix them.
     */
    private final class LayHands implements Power {

        @Override
        public int slot() {
            return 1;
        }

        @Override
        public String id() {
            return "lay_hands";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.lay-hands-cooldown", 8);
        }

        @Override
        public boolean needsTarget() {
            return true;
        }

        @Override
        public boolean use(Player priest, LivingEntity target) {
            int healed = restore(target, tuning.get("priest.lay-hands-heal", 6.0));
            List<String> cured = cureMinorInjuries(target);

            if (healed == 0 && cured.isEmpty()) {
                messages.send(priest, "ability.priest.already-whole",
                        Messages.placeholder("player", nameOf(target)));
                return false;
            }

            layHandsEffect(priest, target);

            messages.send(priest, "ability.priest.healed",
                    Messages.placeholder("player", nameOf(target)));
            if (target instanceof Player healed2) {
                messages.send(healed2, "ability.priest.healed-by",
                        Messages.placeholder("player", priest.getName()));
                if (!cured.isEmpty()) {
                    messages.send(healed2, "ability.priest.injuries-closed",
                            Messages.placeholder("injuries", String.join(", ", cured)));
                }
            }
            return true;
        }
    }

    // -------------------------------------------------------------------------- 2. self

    /**
     * Patch yourself up.
     *
     * <p>Deliberately weaker and far slower than laying hands on somebody else. A priest who
     * keeps themselves standing better than they keep anyone else standing is not a priest,
     * they are a warrior with extra steps - so this exists to stop them being helpless alone,
     * not to make them self-sufficient. It earns no progress for the same reason: the counters
     * measure what you do for other people.
     */
    private final class MendSelf implements Power {

        @Override
        public int slot() {
            return 2;
        }

        @Override
        public String id() {
            return "mend_self";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.mend-self-cooldown", 25);
        }

        @Override
        public boolean use(Player priest, LivingEntity ignored) {
            if (restore(priest, tuning.get("priest.mend-self-heal", 4.0)) == 0) {
                messages.send(priest, "ability.priest.self-whole");
                return false;
            }
            mendSelfEffect(priest);
            messages.send(priest, "ability.priest.mended");
            return true;
        }
    }

    // ------------------------------------------------------------------------- 3. smite

    /**
     * Burn everything undead standing near you.
     *
     * <p>An area effect rather than a targeted bolt, because the moment a priest actually
     * needs this is when several things have closed on them at once. The living are untouched
     * entirely - it is not a weapon, it is an objection.
     */
    private final class HolySmite implements Power {

        @Override
        public int slot() {
            return 3;
        }

        @Override
        public String id() {
            return "holy_smite";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.smite-cooldown", 20);
        }

        @Override
        public boolean use(Player priest, LivingEntity ignored) {
            double range = tuning.get("priest.smite-range", 6.0);
            double damage = tuning.get("priest.smite-damage", 9.0);

            List<LivingEntity> struck = new ArrayList<>();
            for (Entity nearby : priest.getNearbyEntities(range, range, range)) {
                if (nearby instanceof LivingEntity living && isUndead(nearby)) {
                    struck.add(living);
                }
            }

            if (struck.isEmpty()) {
                messages.send(priest, "ability.priest.nothing-to-smite");
                return false;
            }

            for (LivingEntity victim : struck) {
                // Dealt as damage from the priest, so kills credit their counter and any
                // other system that cares who did it sees the right answer.
                victim.damage(damage, priest);
            }
            smiteEffect(priest, range, struck);

            messages.send(priest, "ability.priest.smote",
                    Messages.placeholder("count", struck.size()));
            return true;
        }
    }

    // -------------------------------------------------------------------- 4. consecrate

    /**
     * A moment of protection for everyone standing with you.
     *
     * <p>The only power that does almost nothing for a priest travelling alone, which is the
     * point of putting it this late: by tier four they should be someone a group forms around.
     */
    private final class Consecrate implements Power {

        @Override
        public int slot() {
            return 4;
        }

        @Override
        public String id() {
            return "consecrate";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.consecrate-cooldown", 60);
        }

        @Override
        public boolean use(Player priest, LivingEntity ignored) {
            double range = tuning.get("priest.consecrate-range", 10.0);
            int ticks = (int) tuning.get("priest.consecrate-seconds", 12) * 20;

            List<Player> blessed = new ArrayList<>();
            blessed.add(priest);
            for (Entity nearby : priest.getNearbyEntities(range, range, range)) {
                if (nearby instanceof Player other) {
                    blessed.add(other);
                }
            }

            for (Player person : blessed) {
                person.addPotionEffect(new PotionEffect(
                        PotionEffectType.REGENERATION, ticks, 0, true, true));
                person.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, ticks, 0, true, true));
                HolyEffects.halo(person, 12, Particle.DUST, HolyEffects.GOLD);
                person.getWorld().spawnParticle(Particle.END_ROD,
                        person.getLocation().add(0, 1.2, 0), 12, 0.4, 0.7, 0.4, 0.01);
                if (!person.equals(priest)) {
                    messages.send(person, "ability.priest.consecrated-by",
                            Messages.placeholder("player", priest.getName()));
                }
            }
            consecrateEffect(priest, range, ticks);

            messages.send(priest, "ability.priest.consecrated",
                    Messages.placeholder("count", blessed.size()));
            return true;
        }
    }

    // ----------------------------------------------------------------- 5. mortal wounds

    /**
     * Closes a mortal wound - the only thing in the world that can, short of dying.
     *
     * <p>What the whole ability is for. Until one priest reaches this tier, a lost arm is
     * permanent until its owner spends a life; the moment somebody gets here, that stops being
     * true for everyone they can reach.
     */
    private final class CloseTheWound implements Power {

        @Override
        public int slot() {
            return 5;
        }

        @Override
        public String id() {
            return "close_the_wound";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("priest.close-wound-cooldown", 300);
        }

        @Override
        public boolean needsTarget() {
            return true;
        }

        @Override
        public boolean use(Player priest, LivingEntity target) {
            // The only power that genuinely cannot work on a mob: mortal wounds are a thing
            // a profile carries, and a wolf does not have one. Said out loud rather than
            // refused silently, which would look identical to the power being broken.
            if (!(target instanceof Player person)) {
                messages.send(priest, "ability.priest.only-people",
                        Messages.placeholder("player", nameOf(target)));
                return false;
            }
            PlayerProfile theirs = profiles.resident(person.getUniqueId()).orElse(null);
            if (theirs == null) {
                return false;
            }

            Optional<ActiveInjury> mortal = theirs.injuries().stream()
                    .filter(injury -> severityOf(injury.id()) == InjurySeverity.MORTAL_WOUND)
                    .findFirst();

            if (mortal.isEmpty()) {
                messages.send(priest, "ability.priest.nothing-to-treat",
                        Messages.placeholder("player", person.getName()));
                return false;
            }

            theirs.removeInjury(mortal.get().id());
            profiles.saveNow(theirs);
            modifiers.applyFromProfile(person);

            closeWoundEffect(priest, person);

            messages.send(priest, "ability.priest.treated",
                    Messages.placeholder("player", person.getName()));
            messages.send(person, "ability.priest.treated-by",
                    Messages.placeholder("player", priest.getName()));
            return true;
        }
    }

    // -------------------------------------------------------------------------- helpers

    /**
     * Heals up to {@code amount}, returning what actually landed.
     *
     * <p>Progress counts what landed rather than what was attempted, so topping up someone who
     * was nearly full is worth almost nothing and pulling somebody off the floor is worth the
     * lot.
     */
    private int restore(LivingEntity player, double amount) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        double healed = Math.min(amount, max - player.getHealth());
        if (healed <= 0) {
            return 0;
        }
        player.setHealth(player.getHealth() + healed);
        return (int) Math.round(healed);
    }

    /** Clears every ordinary injury, leaving mortal wounds for power five. */
    private List<String> cureMinorInjuries(LivingEntity target) {
        if (!(target instanceof Player person)) {
            // A mob has no wounds to close, and that is not a failure - the healing itself
            // still landed. Returning nothing lets the caller say so accurately.
            return List.of();
        }
        PlayerProfile theirs = profiles.resident(person.getUniqueId()).orElse(null);
        if (theirs == null) {
            return List.of();
        }
        List<String> cured = theirs.injuries().stream()
                .map(ActiveInjury::id)
                .filter(id -> severityOf(id) == InjurySeverity.INJURY)
                .toList();
        if (cured.isEmpty()) {
            return cured;
        }
        cured.forEach(theirs::removeInjury);
        profiles.saveNow(theirs);
        modifiers.applyFromProfile(person);
        return cured;
    }

    private InjurySeverity severityOf(String injuryId) {
        return registry.find(injuryId)
                .filter(Injury.class::isInstance)
                .map(Injury.class::cast)
                .map(Injury::severity)
                .orElse(null);
    }

    // --------------------------------------------------------------------- holy effects

    /**
     * A priest reaching somebody, and that person coming back up.
     *
     * <p>The beam is the whole point of this one: healing at a distance used to look
     * identical whether you did it or somebody else did, because all anyone saw was particles
     * appearing on the person who got better. Drawing the line makes it obvious across a
     * field who is keeping who alive - which on a server built around cooperating is the
     * single most useful thing a visual effect here can do.
     */
    private void layHandsEffect(Player priest, LivingEntity target) {
        HolyEffects.beam(priest.getEyeLocation(), target.getLocation().add(0, 1.2, 0),
                24, Particle.END_ROD, null);
        HolyEffects.spiral(target.getLocation(), 2.2, 0.6, 28, 2.0,
                Particle.DUST, HolyEffects.GOLD);
        HolyEffects.halo(target, 14, Particle.END_ROD, null);
        target.getWorld().spawnParticle(Particle.HEART,
                target.getLocation().add(0, 1.4, 0), 6, 0.3, 0.4, 0.3, 0.0);

        HolyEffects.chord(target.getLocation(), 1.0f,
                new Sound[] {Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_BELL},
                new float[] {1.4f, 1.7f});
    }

    /**
     * Quieter than everything else on purpose.
     *
     * <p>Mending yourself is the power a priest uses when nobody came, so it gets a column and
     * a halo and no chord - it should read as somebody keeping themselves upright rather than
     * as an event anyone else is meant to notice.
     */
    private void mendSelfEffect(Player priest) {
        HolyEffects.pillar(priest.getLocation(), 2.6, 16, Particle.END_ROD, null);
        HolyEffects.spiral(priest.getLocation(), 2.0, 0.45, 18, 1.5,
                Particle.DUST, HolyEffects.PALE);
        HolyEffects.halo(priest, 10, Particle.DUST, HolyEffects.GOLD);

        priest.playSound(priest.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.1f);
    }

    /**
     * Light falling on everything that should not be standing there.
     *
     * <p>The ring is drawn at the real radius, so the boundary of the power is a thing players
     * can see rather than infer. The pillars come down rather than up - the only shape in the
     * ability that does, because it is the only power that is not a kindness.
     */
    private void smiteEffect(Player priest, double range, List<LivingEntity> struck) {
        HolyEffects.shockwave(priest.getLocation(), range, 3,
                Particle.DUST, HolyEffects.AMBER);
        priest.getWorld().spawnParticle(Particle.FLASH, priest.getLocation().add(0, 1, 0), 1);

        // Capped rather than per-victim without limit: a priest who catches a horde would
        // otherwise draw several thousand particles in one frame and stutter every client
        // watching. The first few pillars carry the idea; the rest is noise.
        int shown = Math.min(struck.size(), (int) tuning.get("priest.smite-max-pillars", 8));
        for (LivingEntity victim : struck.subList(0, shown)) {
            Location at = victim.getLocation();
            HolyEffects.pillar(at, 5.0, 16, Particle.END_ROD, null);
            HolyEffects.ring(at, 0.8, 10, Particle.DUST, HolyEffects.GOLD);
            victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    at.add(0, 1.0, 0), 10, 0.3, 0.5, 0.3, 0.05);
        }

        HolyEffects.chord(priest.getLocation(), 0.8f,
                new Sound[] {Sound.ITEM_TRIDENT_THUNDER, Sound.BLOCK_BELL_USE},
                new float[] {1.6f, 1.9f});
    }

    /**
     * Ground marked out as somewhere safe, for as long as it lasts.
     *
     * <p>Draws the dome once at full strength and then hands the place to
     * {@link #sustainConsecration}, which keeps a quieter version of it alive for the whole
     * duration. A protection that flashed once and then looked exactly like open ground for
     * the next twelve seconds was the effect players could not tell was still running.
     */
    private void consecrateEffect(Player priest, double range, int ticks) {
        Location centre = priest.getLocation();
        HolyEffects.dome(centre, range, 4, Particle.DUST, HolyEffects.PALE);
        HolyEffects.ring(centre, range, (int) (range * 6), Particle.END_ROD, null);
        HolyEffects.pillar(centre, 4.0, 20, Particle.DUST, HolyEffects.GOLD);
        centre.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                centre.clone().add(0, 1.2, 0), 40, 0.6, 0.8, 0.6, 0.3);

        consecrations.put(priest.getUniqueId(),
                new Consecration(centre.clone(), System.currentTimeMillis() + ticks * 50L));

        HolyEffects.chord(centre, 0.9f,
                new Sound[] {Sound.BLOCK_BEACON_ACTIVATE, Sound.BLOCK_CONDUIT_ACTIVATE},
                new float[] {1.3f, 1.1f});
    }

    /**
     * The largest thing the ability does, and now the largest thing it looks like.
     *
     * <p>Closing a mortal wound is rarer than anything else on the server and used to draw
     * the same puff of particles as a three-heart top-up. A six-block pillar, three rings and
     * a double helix is not excess - it is the only signal anybody watching gets that
     * something happened here that nothing else in the world can do.
     */
    private void closeWoundEffect(Player priest, Player target) {
        Location at = target.getLocation();

        HolyEffects.pillar(at, 6.0, 40, Particle.END_ROD, null);
        HolyEffects.shockwave(at, 3.0, 3, Particle.DUST, HolyEffects.GOLD);
        HolyEffects.doubleSpiral(at, 3.0, 0.8, 30, 3.0, Particle.DUST, HolyEffects.PALE);
        HolyEffects.halo(target, 20, Particle.DUST, HolyEffects.AMBER);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                at.clone().add(0, 1.2, 0), 60, 0.5, 0.9, 0.5, 0.4);
        at.getWorld().spawnParticle(Particle.FLASH, at.clone().add(0, 1.2, 0), 1);

        HolyEffects.beam(priest.getEyeLocation(), at.clone().add(0, 1.2, 0),
                20, Particle.WAX_ON, null);

        HolyEffects.chord(at, 1.0f,
                new Sound[] {Sound.BLOCK_BEACON_POWER_SELECT, Sound.ITEM_TOTEM_USE,
                             Sound.BLOCK_BELL_RESONATE},
                new float[] {1.2f, 1.0f, 1.4f});
    }

    // ------------------------------------------------------------------ sustained effects

    /**
     * Keeps a consecration visible for as long as it is protecting anybody.
     *
     * <p>Driven from the shared modifier loop rather than a task of its own, because no
     * modifier in this plugin owns a timer - which also means it stops if the priest logs out.
     * That is the honest outcome: the blessing on everyone else was already handed over as a
     * potion effect and keeps running, and the light was only ever a description of where the
     * priest was standing when they called it.
     */
    private void sustainConsecration(Player priest) {
        Consecration active = consecrations.get(priest.getUniqueId());
        if (active == null) {
            return;
        }
        if (System.currentTimeMillis() >= active.expiresAt()) {
            consecrations.remove(priest.getUniqueId());
            active.centre().getWorld().playSound(
                    active.centre(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.4f);
            return;
        }
        double range = tuning.get("priest.consecrate-range", 10.0);
        HolyEffects.ring(active.centre(), range, (int) (range * 4),
                Particle.DUST, HolyEffects.PALE);
        active.centre().getWorld().spawnParticle(Particle.END_ROD,
                active.centre().clone().add(0, 0.4, 0), 3, range / 3, 0.2, range / 3, 0.0);
    }

    /**
     * The quiet glow a priest carries while their staff is in hand.
     *
     * <p>This is the effect that is on almost all the time, so it is the one most able to
     * become irritating - a handful of motes every two seconds, and nothing at all when the
     * staff is put away. The halo grows with their tier, which makes progress something other
     * players can see rather than something buried in {@code /profile}: you can tell a priest
     * who can close a mortal wound from one who cannot by looking at them.
     */
    private void ambient(Player priest) {
        if (tuning.get("priest.ambient-effects", 1.0) <= 0
                || !AbilityFocus.held(plugin, priest, ID)) {
            ambientCounters.remove(priest.getUniqueId());
            return;
        }
        int waited = ambientCounters.merge(priest.getUniqueId(), 1, Integer::sum);
        int every = (int) tuning.get("priest.ambient-interval", 4);
        if (waited < Math.max(1, every)) {
            return;
        }
        ambientCounters.put(priest.getUniqueId(), 0);

        int tier = profiles.resident(priest.getUniqueId())
                .map(PlayerProfile::abilityTier)
                .orElse(1);

        HolyEffects.halo(priest, 4 + tier * 2, Particle.DUST, HolyEffects.GOLD);
        priest.getWorld().spawnParticle(Particle.END_ROD,
                priest.getLocation().add(0, 1.1, 0), 1 + tier / 2, 0.35, 0.5, 0.35, 0.005);
    }

    /** Where a consecration was called, and when its light should go out. */
    private record Consecration(Location centre, long expiresAt) {
    }

    /**
     * Whether this is something Holy Smite is meant to burn.
     *
     * <p>Was {@code mob.getCategory() == EntityCategory.UNDEAD}, which is why the power never
     * worked: the category came back as something other than UNDEAD for ordinary zombies, so
     * the search found nothing every single time and reported "nothing to smite" perfectly
     * politely. The list is now written out in core, where it can be tested.
     */
    private static boolean isUndead(Entity entity) {
        return Undead.is(entity.getType().name());
    }

    /** A name worth showing, for anything alive rather than only for players. */
    private static String nameOf(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        if (entity.customName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(entity.customName());
        }
        return entity.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
