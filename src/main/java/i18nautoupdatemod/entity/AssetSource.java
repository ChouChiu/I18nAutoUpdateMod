package i18nautoupdatemod.entity;

public class AssetSource {
    public final String name;
    public final String fileUrl;
    public final String checksumUrl;

    public AssetSource(String name, String fileUrl, String checksumUrl) {
        this.name = name;
        this.fileUrl = fileUrl;
        this.checksumUrl = checksumUrl;
    }

    @Override
    public String toString() {
        return name;
    }
}
