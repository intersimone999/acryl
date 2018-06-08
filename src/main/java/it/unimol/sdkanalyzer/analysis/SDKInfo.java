package it.unimol.sdkanalyzer.analysis;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simone Scalabrino.
 */
public class SDKInfo implements Cloneable {
    private boolean direct;
    private Map<Object, VersionChecker> checkerMap;
    private boolean actuallyNull;

    public SDKInfo() {
        this.direct = false;

        this.checkerMap = new HashMap<>();

        this.actuallyNull = true;
    }

    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
        this.actuallyNull = false;
    }

    /**
     * Returns the version corresponding to a specific return value of the method
     * @param value return value
     * @return Map return value => SDK version condition
     */
    public VersionChecker getVersionFor(Object value) {
        return this.checkerMap.getOrDefault(value, null);
    }

    public void addChecker(Object value, VersionChecker checker) {
        this.checkerMap.put(value, checker);
        this.actuallyNull = false;
    }

    public void addAllCheckers(Object key, SDKInfo info) {
        for (VersionChecker versionChecker : info.getCheckerMap().values()) {
            info.addChecker(key, versionChecker);
        }
    }

    public Map<Object, VersionChecker> getCheckerMap() {
        return checkerMap;
    }

    public boolean isActuallyNull() {
        return actuallyNull;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        if (direct)
            builder.append("version");
        else
            builder.append("dependent");
        builder.append("] ");
        if (!direct) {
            for (Map.Entry<Object, VersionChecker> checkerEntry : this.checkerMap.entrySet()) {
                String key      = checkerEntry.getKey()   == null ? "null" : checkerEntry.getKey().toString();
                String value    = checkerEntry.getValue() == null ? "null" : checkerEntry.getValue().toString();
                builder.append("(").append(key).append(" => ").append(value).append(") ");
            }
        }

        return builder.toString();
    }

    public SDKInfo copy() {
        try {
            SDKInfo copy = ((SDKInfo) this.clone());

            copy.checkerMap = new HashMap<>();
            for (Map.Entry<Object, VersionChecker> entry : this.checkerMap.entrySet()) {
                copy.checkerMap.put(entry.getKey(), entry.getValue().copy());
            }

            return copy;
        } catch (CloneNotSupportedException e) {
            assert false : "This should never happen, the class is cloneable";
        }
        return null;
    }

    public void invertAllComparators(boolean b) {
        for (VersionChecker versionChecker : this.getCheckerMap().values()) {
            versionChecker.invertComparator(b);
        }
    }
}
