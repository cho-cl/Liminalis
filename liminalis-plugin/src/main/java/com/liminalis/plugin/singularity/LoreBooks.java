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
     * The book the whole rescue loop hangs off.
     *
     * <p>Written last, once the expedition actually existed. It carries real, followable
     * instructions rather than hints, because this is the one document in the game that a
     * player has to be able to act on - a book that gestured at a rescue nobody could
     * perform would be worse than no book at all.
     *
     * <p>Everything stated here is true of the implementation: the recipe, that the way in is
     * the way out, that holding someone is enough, and exactly what running out of time costs.
     */
    private static LoreBook onReturn() {
        return new LoreBook("on_return", "On Getting Them Back", "R.", List.of(
                """
                Four came back. I said so at the beginning and I have been
                asked about it every day since.

                So: yes. It can be done. What follows is how.
                """,
                """
                It cannot be done from here.

                I have tried every rite, every offering and every act of
                will that has ever been suggested to me. Nothing done in
                this world reaches into that one.

                Someone has to go and fetch them.
                """,
                """
                To open a way you will need what the creatures leave when
                they stop. Eight pieces of it, set around a pearl.

                It makes a stone. The stone is a door and it is only a door
                once - it goes with you and does not come back.
                """,
                """
                Hold it and you will be through.

                Now attend, because this is the part that kills people.

                The way in is the way out. Where you arrive, a light will
                stand. That light is the only exit there is.
                """,
                """
                The grey has no landmarks. None. You will walk twenty paces
                and lose the light behind you and not believe how quickly it
                happened.

                Know where it is at every moment. If you have to choose
                between finding them and finding your way back, you have
                already made a mistake you cannot undo.
                """,
                """
                When you find them, take their hand. That is all it takes.
                They will come out with you.

                Take as many as you can find. Nothing about holding a second
                one is harder than the first.
                """,
                """
                Be at the light when your time runs out.

                If you are not, the grey will let you go and keep a life for
                the trouble. If you had no life left to give it, it will not
                let you go at all.

                Do not send anyone in on their last one. Do not go in on
                yours, unless the person you are going for is worth it.
                """,
                """
                What comes back is not quite what went in. They arrive
                thinner in a way that has nothing to do with the body, and
                they never lose it.

                They can feel the grey afterward. When something from that
                side is close, they know.

                It is not much to carry. I have asked all four. Not one of
                them would give it back.
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
