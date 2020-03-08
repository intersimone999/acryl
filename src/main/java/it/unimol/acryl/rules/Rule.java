package it.unimol.acryl.rules;

import it.unimol.acryl.analysis.VersionChecker;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Simone Scalabrino.
 */
public class Rule {
    private final VersionChecker checker;
    private final Collection<String> trueApis;
    private final Collection<String> falseApis;
    private double confidence;

    public Rule(VersionChecker checker, Collection<String> trueApis, Collection<String> falseApis) {
        this.checker = checker;
        this.trueApis = trueApis;
        this.falseApis = falseApis;
    }

    public Collection<String> getTrueApis() {
        return trueApis;
    }

    public Collection<String> getFalseApis() {
        return falseApis;
    }

    public Collection<String> getInvolvedApis() {
        Set<String> total = new HashSet<>(trueApis);
        total.addAll(falseApis);
        return total;
    }

    public VersionChecker getChecker() {
        return checker;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getConfidence() {
        return confidence;
    }
}
