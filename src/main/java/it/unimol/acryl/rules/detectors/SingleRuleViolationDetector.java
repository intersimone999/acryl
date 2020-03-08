package it.unimol.acryl.rules.detectors;

import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.android.ApkContainer;
import it.unimol.acryl.rules.Rule;
import it.unimol.acryl.rules.CombinedViolationDetector;
import it.unimol.acryl.static_analysis.contexts.MethodContext;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public abstract class SingleRuleViolationDetector {
    public abstract boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException;

    /**
     * Builds a report given the specified parameters
     * @param apk APK under analysis
     * @param rule violated rule
     * @param checkToImplement check in the rule that should be implemented
     * @param actualCheck check actually implemented in code
     * @param usedApis APIs used in code
     * @param alternativeApis APIs suggested in alternative version of checkToImplement
     * @return the report
     */
    public abstract CombinedViolationDetector.RuleViolationReport buildReport(
            ApkContainer apk,
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    );
}
