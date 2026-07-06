package arisa.minecrafttls;

public interface ImTLSServerData {
    boolean mtls$isEnabled();

    void mtls$setEnabled(boolean enabled);

    String mtls$getCaPath();

    void mtls$setCaPath(String path);

    String mtls$getCertPath();

    void mtls$setCertPath(String path);

    String mtls$getKeyPath();

    void mtls$setKeyPath(String path);
}