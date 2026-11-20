# Gameworld mTLS

Client-only NeoForge mod (Minecraft 1.21.1, NeoForge 21.1.x) that lets a modpack's players reach a
server that sits behind a mutual-TLS gate ([ghostunnel](https://github.com/ghostunnel/ghostunnel)),
using a personal, short-lived client certificate (a "key card") that the mod obtains and renews by
itself.

Fork of [MinecraftTLS](https://gitlab.com/Arisa_Snowbell/MinecraftTLS) by Arisa Snowbell (GPL-3.0),
reworked for the Gameworld Aeronautics server. Licence: GPL-3.0, see `LICENSE`.

## What it does

- **Gate entries** (`host:gatePort` from the pack config): the connection always goes through TLS
  with the player's card. The gate's certificate must chain to the **Server-CA shipped in the pack**
  (never the JVM's CA store, never anything learned from a server) and carry the name `gate.<host>`.
  If there is no usable card, the config is broken, or TLS can't be set up, the connection is closed
  before a single byte is written (**fail closed**; upstream continued without TLS).
- **Vanilla encryption stays on** inside TLS (upstream's suppression mixin and its server side are
  removed). The server runs no mod; online-mode works as usual.
- **Enroll entry** (`host:enrollPort`, a plain Minecraft connection): answers the
  `gameworld:enroll` login plugin queries. After the server has verified the account with Mojang it
  asks for a CSR; the mod generates a fresh EC P-256 key, sends a PKCS#10 request, receives the card
  and installs key + card atomically. On every other connection the query gets the vanilla
  "unknown" answer.
- **Silent renewal**: at game start and when the server list opens, a card with fewer than
  `renewDays` (default 5) left is renewed in the background over the enroll entry. The old card is
  kept until the new one is saved.
- Creates the pack's server entries (`<name>` and `<name> enroll`) on first launch, and never sends a
  plaintext legacy ping to a configured address.

## Pack configuration (`config/gameworld-mtls/`)

`servers.json`:

```json
{ "servers": [ { "name": "Gameworld", "host": "aero.example.org", "gatePort": 25565, "enrollPort": 25566 } ],
  "renewDays": 5 }
```

`server-ca.pem`: the only trust anchor for gates. Cards are stored per host and account as
`config/gameworld-mtls/<host>/<uuid-without-dashes>/card.pem` (PKCS#8 key + certificate, mode 0600
where the OS supports it).

## `gameworld:enroll` payload (version 1)

| step | server → client | client → server |
|---|---|---|
| query 1 | `[1, 1, uuid(16 bytes)]` (the Mojang-verified UUID) | `[1, 1, CSR DER]`, or "unknown" if the UUID is not this account |
| query 2 | `[1, 2, card DER]` | `[1, 2, 1]` once installed, `[1, 2, 0]` on failure |

The client checks that the card is for its pending key and names its own UUID before installing.

## Build and test

    ./gradlew build          # jar in build/libs, unit tests (CSR, card store, config)

End-to-end tests with a real client against the gate and the enroll service live in the server
repo (`tests/client-e2e.sh`); they use the `e2eClient` run, which loads the built jar from
`run-e2e/mods`.

## Changes from MinecraftTLS

Removed: the server-side TLS listener, the vanilla-encryption suppression, the per-entry
certificate settings screen and its config. Added: fail-closed TLS with pack-pinned trust and
hostname verification, the enrollment and renewal client, atomic card storage, pack-provided
server entries. Package and mod id changed to `net.gameworld.mtls` / `gameworld_mtls`.
