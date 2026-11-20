package net.gameworld.mtls;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GwConfigTest {
    static GwConfig write(Path dir, String json, boolean withCa) throws Exception {
        Files.writeString(dir.resolve("servers.json"), json);
        if (withCa) {
            TestKeys.Pair ca = TestKeys.card(dir, "ca", "test Server-CA");
            Files.writeString(dir.resolve("server-ca.pem"), CardStore.pem("CERTIFICATE", ca.cert().getEncoded()));
        }
        return GwConfig.load(dir);
    }

    @Test
    void classifiesGateAndEnrollAddresses(@TempDir Path dir) throws Exception {
        GwConfig c = write(dir, "{\"servers\":[{\"name\":\"Gameworld\",\"host\":\"Aero.Example.org\"}]}", true);
        assertNull(c.error());
        assertEquals(1, c.serverCAs().size());
        assertEquals(GwConfig.Role.GATE, c.match("aero.example.org").role());
        assertEquals(GwConfig.Role.GATE, c.match("AERO.example.org:25565").role());
        assertEquals(GwConfig.Role.GATE, c.match("aero.example.org.:25565").role());
        assertEquals(GwConfig.Role.ENROLL, c.match("aero.example.org:25566").role());
        assertEquals(GwConfig.Role.NONE, c.match("aero.example.org:25567").role());
        assertEquals(GwConfig.Role.NONE, c.match("other.example.org").role());
        assertEquals(GwConfig.Role.NONE, c.match("[::1]:25565").role());
        assertEquals(GwConfig.Role.NONE, c.match("aero.example.org:x").role());
        assertEquals("gate.aero.example.org", c.servers().get(0).gateName());
        assertEquals(5, c.renewDays());
    }

    @Test
    void customPortsAndGateName(@TempDir Path dir) throws Exception {
        GwConfig c = write(dir, "{\"renewDays\":99,\"servers\":[{\"host\":\"localhost\",\"gatePort\":35565,\"enrollPort\":35566,\"gateName\":\"Gate.Localhost\"}]}", true);
        assertEquals(GwConfig.Role.GATE, c.match("localhost:35565").role());
        assertEquals(GwConfig.Role.ENROLL, c.match("localhost", 35566).role());
        assertEquals("gate.localhost", c.servers().get(0).gateName());
        assertEquals(13, c.renewDays(), "renewal window is clamped below the 14-day card life");
    }

    @Test
    void missingServerCaIsAnError(@TempDir Path dir) throws Exception {
        GwConfig c = write(dir, "{\"servers\":[{\"host\":\"aero.example.org\"}]}", false);
        assertNotNull(c.error(), "gate connections must fail closed without a trust anchor");
    }

    @Test
    void brokenJsonIsAnErrorNotACrash(@TempDir Path dir) throws Exception {
        GwConfig c = write(dir, "{\"servers\":[{", true);
        assertNotNull(c.error());
    }

    @Test
    void noConfigMeansNoGameworldServers(@TempDir Path dir) {
        GwConfig c = GwConfig.load(dir);
        assertTrue(c.servers().isEmpty());
        assertNull(c.error());
        assertEquals(GwConfig.Role.NONE, c.match("anything:25565").role());
    }
}
