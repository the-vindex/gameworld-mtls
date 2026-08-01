package arisa.minecrafttls.mixin;

import arisa.minecrafttls.ImTLSServerData;
import arisa.minecrafttls.mTLSContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import static arisa.minecrafttls.MinecraftTLS.LOGGER;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Unique
    private ServerData mtls$attachedServer;

    @Unique
    private static X509ExtendedTrustManager mtls$createCaTrustManager(File caFile) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        try (InputStream is = new FileInputStream(caFile)) {
            Collection<? extends Certificate> certificates = certFactory.generateCertificates(is);
            int idx = 0;
            for (Certificate cert : certificates) {
                keyStore.setCertificateEntry("ca_" + (idx++), cert);
            }
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        X509TrustManager realManager = (X509TrustManager) tmf.getTrustManagers()[0];

        return new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                realManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                realManager.checkServerTrusted(chain, authType);
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return realManager.getAcceptedIssuers();
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                realManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                realManager.checkServerTrusted(chain, authType);
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                realManager.checkClientTrusted(chain, authType);
            }

            // Bypasses Hostname checking while maintaining strict CA signature & expiration verification
            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                realManager.checkServerTrusted(chain, authType);
            }
        };
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(PacketFlow receiving, CallbackInfo ci) {
        ServerData data = mTLSContext.CONNECTING_SERVER.get();
        if (data != null) {
            this.mtls$attachedServer = data;
            mTLSContext.CONNECTING_SERVER.remove();
        }
    }

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void onChannelActive(ChannelHandlerContext ctx, CallbackInfo ci) {
        if (this.mtls$attachedServer == null) {
            LOGGER.error("[mTLS] No active server attached to this connection!");
            return;
        }

        ImTLSServerData mtlsData = (ImTLSServerData) this.mtls$attachedServer;
        if (!mtlsData.mtls$isEnabled()) {
            LOGGER.debug("[mTLS] mTLS is disabled for this server entry.");
            return;
        }

        File ca = new File(mtlsData.mtls$getCaPath());
        File cert = new File(mtlsData.mtls$getCertPath());
        File key = new File(mtlsData.mtls$getKeyPath());

        LOGGER.debug("[mTLS] CA Path: {}", ca.getAbsolutePath());
        LOGGER.debug("[mTLS] Client Cert Path: {}", cert.getAbsolutePath());
        LOGGER.debug("[mTLS] Client Key Path: {}", key.getAbsolutePath());

        if (!ca.exists() || !cert.exists() || !key.exists()) {
            LOGGER.warn("[mTLS] Handshake aborted: One or more required key/cert files do not exist.");
            return;
        }

        try {
            SslContext sslContext = mtls$createSslContext(ca, cert, key);

            ServerAddress serverAddress = ServerAddress.parseString(this.mtls$attachedServer.ip);
            String host = serverAddress.getHost();
            int port = serverAddress.getPort();

            SslHandler sslHandler = sslContext.newHandler(ctx.channel().alloc(), host, port);

            sslHandler.handshakeFuture().addListener(future -> {
                if (future.isSuccess()) {
                    LOGGER.info("[mTLS] SSL/TLS Handshake SUCCESSFUL with {}", ctx.channel().remoteAddress());
                } else {
                    LOGGER.error("[mTLS] SSL/TLS Handshake FAILED for {}", ctx.channel().remoteAddress(), future.cause());
                }
            });

            ctx.pipeline().addFirst("ssl", sslHandler);
            LOGGER.info("[mTLS] Successfully injected SslHandler into pipeline for host {}:{}", host, port);

        } catch (Exception e) {
            LOGGER.error("[mTLS] Critical failure during SSL pipeline initialization", e);
        }
    }

    @Unique
    private SslContext mtls$createSslContext(File caFile, File certFile, File keyFile) throws Exception {
        return SslContextBuilder.forClient()
                .trustManager(mtls$createCaTrustManager(caFile))
                .keyManager(certFile, keyFile)
                .build();
    }
}