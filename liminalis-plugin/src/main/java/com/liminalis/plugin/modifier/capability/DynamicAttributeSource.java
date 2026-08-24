package com.liminalis.plugin.modifier.capability;

/**
 * An {@link AttributeSource} whose contributions change as the player's state changes, and
 * which therefore needs recomputing continuously rather than only on attach.
 *
 * <p>This is the primitive behind the whole "stat scales along a curve with a live variable"
 * family of traits, and it is worth noticing that Resilience and Coward are the same thing
 * with different parameters:
 *
 * <ul>
 *   <li><strong>Resilience</strong> - armour rises as health falls</li>
 *   <li><strong>Coward</strong> - attack damage falls as health falls</li>
 * </ul>
 *
 * <p>Both read current health and return a different {@code ARMOR} or {@code ATTACK_DAMAGE}
 * contribution each time they are asked. Building this once buys both, and most of the traits
 * that have not been thought of yet.
 *
 * <p>Marking a modifier dynamic costs a periodic recompute, so plain
 * {@link AttributeSource} remains the right choice for anything with a fixed value.
 */
public interface DynamicAttributeSource extends AttributeSource {
}
