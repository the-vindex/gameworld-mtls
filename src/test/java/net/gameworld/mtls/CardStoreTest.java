package net.gameworld.mtls;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CardStoreTest {
    static final String UUID = "0123456789abcdef0123456789abcdef";

    @Test
    void installThenLoadRoundTrips(@TempDir Path dir) throws Exception {
        TestKeys.Pair a = TestKeys.card(dir, "a", UUID);
        CardStore s = new CardStore(dir.resolve("store"));
        assertNull(s.load("aero.example.org", UUID), "no card yet");
        s.install("aero.example.org", UUID, a.key(), a.cert());
        CardStore.Card c = s.load("aero.example.org", UUID);
        assertNotNull(c);
        assertEquals(a.cert(), c.cert());
        assertArrayEquals(a.key().getEncoded(), c.key().getEncoded());
        assertTrue(c.validAt(Instant.now()));
        assertFalse(Files.exists(s.cardFile("aero.example.org", UUID).resolveSibling("card.pem.tmp")), "no temp file left");
        assertEquals(Path.of("aero.example.org", UUID, "card.pem"), dir.resolve("store").relativize(s.cardFile("aero.example.org", UUID)));
    }

    @Test
    void reinstallReplacesKeyAndCardTogether(@TempDir Path dir) throws Exception {
        TestKeys.Pair a = TestKeys.card(dir, "a", UUID), b = TestKeys.card(dir, "b", UUID);
        CardStore s = new CardStore(dir.resolve("store"));
        s.install("h.example", UUID, a.key(), a.cert());
        s.install("h.example", UUID, b.key(), b.cert());
        CardStore.Card c = s.load("h.example", UUID);
        assertEquals(b.cert(), c.cert());
        assertArrayEquals(b.key().getEncoded(), c.key().getEncoded());
    }

    @Test
    void leftoverTempFileNeverReplacesTheCard(@TempDir Path dir) throws Exception {
        TestKeys.Pair a = TestKeys.card(dir, "a", UUID);
        CardStore s = new CardStore(dir.resolve("store"));
        s.install("h.example", UUID, a.key(), a.cert());
        Path tmp = s.cardFile("h.example", UUID).resolveSibling("card.pem.tmp");
        Files.writeString(tmp, "-----BEGIN CERTIFICATE-----\ngarbage\n-----END CERTIFICATE-----\n"); // a crash mid-write
        assertEquals(a.cert(), s.load("h.example", UUID).cert(), "the old card is still the card");
    }

    @Test
    void corruptOrHugeCardIsTreatedAsMissing(@TempDir Path dir) throws Exception {
        CardStore s = new CardStore(dir.resolve("store"));
        Path f = s.cardFile("h.example", UUID);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "not a card");
        assertNull(s.load("h.example", UUID));
        Files.write(f, new byte[100_000]);
        assertNull(s.load("h.example", UUID));
    }

    @Test
    void hostAndUuidCannotEscapeTheStore(@TempDir Path dir) {
        CardStore s = new CardStore(dir);
        assertThrows(IllegalArgumentException.class, () -> s.cardFile("../etc", UUID));
        assertThrows(IllegalArgumentException.class, () -> s.cardFile("a..b", UUID));
        assertThrows(IllegalArgumentException.class, () -> s.cardFile("h.example", "../../x"));
        assertThrows(IllegalArgumentException.class, () -> s.cardFile("h.example", UUID.toUpperCase()));
        assertThrows(IllegalArgumentException.class, () -> s.cardFile("h/example", UUID));
    }

    @Test
    void dashlessIsLowercaseWithoutDashes() {
        assertEquals(UUID, CardStore.dashless(java.util.UUID.fromString("01234567-89AB-CDEF-0123-456789ABCDEF")));
    }
}
