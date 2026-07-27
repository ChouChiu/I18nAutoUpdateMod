package i18nautoupdatemod.entity;

public class GameMetaData {
    public String range;
    public Integer packFormat, minFormat, maxFormat;

    public boolean useNewFormat() {
        return minFormat != null && maxFormat != null;
    }
}
