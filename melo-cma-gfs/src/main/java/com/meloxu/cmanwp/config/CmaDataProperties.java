package com.meloxu.cmanwp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "cma")
public class CmaDataProperties {
    private Map<String, Source> sources = new LinkedHashMap<>();
    private List<Derived> derived = new ArrayList<>();
    private List<Contour> contours = new ArrayList<>();
    private List<UnitConversion> conversions = new ArrayList<>();
    private int normalDataCacheSize = 8;
    private int contourCacheSize = 32;

    public Map<String, Source> getSources() {
        return sources;
    }

    public void setSources(Map<String, Source> sources) {
        this.sources = sources;
    }

    public List<Derived> getDerived() {
        return derived;
    }

    public void setDerived(List<Derived> derived) {
        this.derived = derived;
    }

    public List<Contour> getContours() {
        return contours;
    }

    public void setContours(List<Contour> contours) {
        this.contours = contours;
    }

    public List<UnitConversion> getConversions() {
        return conversions;
    }

    public void setConversions(List<UnitConversion> conversions) {
        this.conversions = conversions;
    }

    public int getNormalDataCacheSize() {
        return normalDataCacheSize;
    }

    public void setNormalDataCacheSize(int normalDataCacheSize) {
        this.normalDataCacheSize = normalDataCacheSize;
    }

    public int getContourCacheSize() {
        return contourCacheSize;
    }

    public void setContourCacheSize(int contourCacheSize) {
        this.contourCacheSize = contourCacheSize;
    }

    public static class Source {
        private String root;
        private String filePrefix = "gmf.gra.";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public String getFilePrefix() {
            return filePrefix;
        }

        public void setFilePrefix(String filePrefix) {
            this.filePrefix = filePrefix;
        }
    }

    public static class Derived {
        private String dataPath;
        private String ref;
        private int step;
        private int count;
        private int minVT;
        private String aggr;

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getMinVT() {
            return minVT;
        }

        public void setMinVT(int minVT) {
            this.minVT = minVT;
        }

        public String getAggr() {
            return aggr;
        }

        public void setAggr(String aggr) {
            this.aggr = aggr;
        }
    }

    public static class Contour {
        private String dataPath;
        private List<Double> clevs = new ArrayList<>();

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public List<Double> getClevs() {
            return clevs;
        }

        public void setClevs(List<Double> clevs) {
            this.clevs = clevs;
        }
    }

    public static class UnitConversion {
        private String dataPath;
        private double scale = 1.0;
        private double offset;
        private String from;
        private String to;

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public double getScale() {
            return scale;
        }

        public void setScale(double scale) {
            this.scale = scale;
        }

        public double getOffset() {
            return offset;
        }

        public void setOffset(double offset) {
            this.offset = offset;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }
}
