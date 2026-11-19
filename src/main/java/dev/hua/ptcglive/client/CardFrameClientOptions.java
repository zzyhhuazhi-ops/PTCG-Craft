package dev.hua.ptcglive.client;

/** Client-only switch; loading does not create or overwrite the user's configuration. */
public final class CardFrameClientOptions {
    public static boolean dynamicEffects = true;
    private CardFrameClientOptions() { }
    public static void load() {
        var path=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ptcg-display-frames.properties");
        if(!java.nio.file.Files.isRegularFile(path)) return;
        try(var reader=java.nio.file.Files.newBufferedReader(path)) {
            var properties=new java.util.Properties(); properties.load(reader);
            dynamicEffects=Boolean.parseBoolean(properties.getProperty("dynamicEffects","true"));
        } catch(java.io.IOException error) {
            org.slf4j.LoggerFactory.getLogger("ptcglive").warn("Cannot read display frame options",error);
        }
    }
}
