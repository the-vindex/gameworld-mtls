# Minecraft TLS (mTLS)

Adds Mutual TLS (mTLS) support to Minecraft. Encrypts traffic and acts as a strict player allowlist using certificates - only players with a valid client certificate can connect.

> *Made so friends don't need to install external tools like `stunnel`.*

---

## 🛠️ Server Setup

Place your certificates in `config/mtls_server/`:

* `ca.crt` (CA certificate)
* `server.crt` (Server certificate)
* `server.key` (Server private key, PKCS#8 format)

> **Using an Nginx SSL Reverse Proxy?**
> You still need this mod on the server to disable vanilla Minecraft encryption.
> 1. Edit `config/minecrafttls-common.toml` and set `alwaysDisableVanillaEncryption` to `true`.
> 2. Do **not** place certificates in `config/mtls_server/` (this disables the mod's native TLS so Nginx can handle it).
---

## 🖥️ Client Setup

Configure mTLS settings directly in the server edit screen:

![mTLS Server Config Screen](https://cdn.modrinth.com/data/cached_images/340ac3d2896bbf6a664f0cc4e476a0a9489f17f4_0.webp)

![mTLS Server Connection Details](https://cdn.modrinth.com/data/cached_images/dac076f4e08c6c503121b6cb6823a3089269b8cf_0.webp)

---

## 📌 Versions

* **Supported:** NeoForge 1.21.1
* **Planned:** Fabric & newer/older MC versions
