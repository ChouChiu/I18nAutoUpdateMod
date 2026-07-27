package i18nautoupdatemod.entity;

import java.util.List;

public class GameAssetDetail {
    public List<AssetDownloadDetail> downloads;
    public String convertedFileName;

    public static class AssetDownloadDetail {
        public String fileName;
        public String checksumFileName;
        public String targetVersion;
        public List<AssetSource> sources;
    }
}
