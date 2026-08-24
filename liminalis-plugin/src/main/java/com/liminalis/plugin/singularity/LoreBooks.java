package com.liminalis.plugin.singularity;

import com.liminalis.core.roll.WeightedEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/**
 * The five books, and the only way anyone finds out what any of this is.
 *
 * <p>Written as fragments of someone else's research rather than as a manual. Nobody in the
 * fiction understands the whole thing, so the books disagree slightly, trail off, and are
 * confident about things they should not be. That is deliberate - a player who has read all
 * five should feel like they have assembled something, not like they were handed a wiki page.
 *
 * <p>They are physical written books rather than an unlockable codex, which means they can be
 * traded, hoarded, copied at a lectern and argued over. The knowledge spreading through the
 * server socially is the point; a per-player unlock would have made it private.
 */
public final class LoreBooks {

    private LoreBooks() {
    }

    /** The library, as roll-table entries. Every one is equally likely to fall. */
    public static List<WeightedEntry> asEntries() {
        return all().stream().map(book -> new WeightedEntry(book.id(), 1.0)).toList();
    }

    public static List<LoreBook> all() {
        return List.of(onLimbo(), onTheLiminalis(), onBoons(), onTheSingularity(), onReturn());
    }

    public static LoreBook byId(String id) {
        return all().stream()
                .filter(book -> book.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    // ------------------------------------------------------------------------ the books

    private static LoreBook onLimbo() {
        return new LoreBook("on_limbo", "On the Grey", "an unsteady hand", List.of(
                """
                I have spoken to four people who went there and came back.

                None of them agree on how long they were gone. One said an
                afternoon. One said years. They were both away eleven days.
                """,
                """
                What they agree on:

                It is a forest with no trees. Grey ground in every direction
                and a sky that never moves.

                Nothing hurts. Nothing is hungry. Nothing happens.
                """,
                """
                That last is the part they could not explain to me and could
                not stop trying to.

                A man who has been starved can tell you about hunger. A man
                who has been in the grey has nothing to compare it to. He
                just stops talking and looks at the door.
                """,
                """
                You do not die there. I want to be clear, because the ones
                who go always ask.

                You cannot die there. That is not mercy.
                """));
    }

    private static LoreBook onTheLiminalis() {
        return new LoreBook("on_the_liminalis", "On the Liminalis", "R.", List.of(
                """
                The word is not mine. I found it cut into a stone that was
                older than the stone around it.

                Liminalis. The threshold. The place a thing is when it is no
                longer one thing and not yet another.
                """,
                """
                I had assumed it named the grey. I no longer think so.

                The grey is a room. The Liminalis is the door, and a door is
                not a place - it is a fact about two places.
                """,
                """
                Which raises the question I have been avoiding for some time.

                If the grey is on one side, and we are on the other, then
                something has been holding the door open. Doors do not stay
                open on their own.
                """,
                """
                I have written elsewhere about the creatures that come
                through. I called them intrusions. That was wrong, and I
                would like it struck from the record.

                They are not coming through the door.

                They are what the door is made of.
                """));
    }

    private static LoreBook onBoons() {
        return new LoreBook("on_blessings_and_curses", "On Gifts", "R.", List.of(
                """
                Some are born stronger. This is not remarkable and I would
                not write it down.

                What is remarkable is that it happens at the moment of
                arrival, and never afterward.
                """,
                """
                I have watched eleven people arrive. Three were changed. Two
                for the better and one for the worse, though he would argue
                that and he has a case.

                His bargain gave him more than either of theirs did. It only
                asked for something back.
                """,
                """
                That is the shape of it, and it is why I call them bargains
                and not gifts.

                A gift is given. A bargain is agreed. And nobody I have
                spoken to remembers agreeing to anything.
                """,
                """
                Which means somebody agreed on their behalf.

                I have stopped asking who. The question upsets people and I
                already suspect the answer.
                """));
    }

    private static LoreBook onTheSingularity() {
        return new LoreBook("on_the_singularity", "On the Thing That Drops Them",
                "an unsteady hand", List.of(
                """
                They arrive. That is the whole of what I can prove.

                Not born, not summoned, not walked in from anywhere. One
                moment the field is empty and the next it is not.
                """,
                """
                They are wrong in a specific way that took me a long time to
                name.

                A wolf wants something. A drowned man wants something. These
                do not want. They arrive already having decided, and the
                decision was made somewhere else.
                """,
                """
                Kill one and it leaves paper.

                I cannot express how much this has cost me to accept. They
                carry writing. Not treasure, not trophies. Notes.

                Some of it is in my hand. I have not written it yet.
                """,
                """
                I no longer believe they are being sent to hunt us.

                I think they are being sent to tell us, and whatever is
                sending them has only ever learned one way to start a
                conversation.
                """));
    }

    /**
     * The book that matters.
     *
     * <p>Deliberately short on specifics, because the expedition it describes is Phase 7 and
     * does not exist yet. Writing step-by-step instructions for a mechanic that has not been
     * built would be lying to players in the one place the design cannot afford it - this is
     * the book the whole rescue loop hangs off. It states the shape of the answer and stops
     * where the shape stops.
     */
    private static LoreBook onReturn() {
        return new LoreBook("on_return", "On Getting Them Back", "R.", List.of(
                """
                Four came back. I said so at the beginning and I have been
                asked about it every day since.

                So: yes. It can be done. Stop reading if that was all you
                needed.
                """,
                """
                It cannot be done from here.

                I have tried every rite, every offering and every act of
                will that has ever been suggested to me, and I have watched
                other people try harder. Nothing done in this world reaches
                into that one.
                """,
                """
                Someone has to go and fetch them.

                Not a ritual. Not a bargain. A journey, made by the living,
                to a place I have not yet been able to write down - and back
                again, carrying someone who does not weigh anything.
                """,
                """
                What comes back is not quite what went in. They arrive
                thinner in a way that has nothing to do with the body, and
                they never lose it.

                They can feel the grey afterward. When something from that
                side is nearby, they know. They will not tell you how.
                """,
                """
                I am still working out the route.

                If you are reading this and I have not finished, then either
                I ran out of time or I went to check something myself.

                Do not wait for me. Keep the ones you still have.
                """));
    }

    /**
     * One book, ready to be turned into an item.
     *
     * @param pages plain text; formatting is applied when the item is built
     */
    public record LoreBook(String id, String title, String author, List<String> pages) {

        /** Builds the physical book that drops in the world. */
        public ItemStack toItem() {
            ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) item.getItemMeta();

            meta.title(Component.text(title, NamedTextColor.WHITE));
            meta.author(Component.text(author));
            meta.addPages(pages.stream()
                    .map(page -> (Component) Component.text(page.strip()))
                    .toArray(Component[]::new));
            meta.displayName(Component.text(title, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            item.setItemMeta(meta);
            return item;
        }
    }
}
