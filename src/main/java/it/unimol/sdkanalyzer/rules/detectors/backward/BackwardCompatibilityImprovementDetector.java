package it.unimol.sdkanalyzer.rules.detectors.backward;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class BackwardCompatibilityImprovementDetector extends SingleRuleViolationDetector {
    private static final String MESSAGE = "If you want to support older versions (<= %d), add a check and handle using these APIs: %s";
    private final APILifetime apiLifetime;

    public BackwardCompatibilityImprovementDetector(APILifetime apiLifetime) {
        this.apiLifetime = apiLifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        if (rule.getFalseApis().size() == 0)
            return false;

        if (!apisInCode.containsAll(rule.getFalseApis()))
            return false;

        if (!codeCheck.isNull())
            return false;

        if (methodContext.getTargetAndroidSDK() > rule.getChecker().getCheckedVersion()) {
            Logger.getAnonymousLogger().info("Checking of " + methodContext.getIMethod().getSignature() + " aborted because it has a compatible TargetApi");
            return false;
        }

        return (apk.getMinSDKVersion() > rule.getChecker().getCheckedVersion());
    }

    public CombinedViolationDetector.RuleViolationReport buildReport(
            ApkContainer apk,
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    ) {
        String alternativeApisString = StringUtils.join(alternativeApis, " --- ");

        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.BackwardImprovement,
                String.format(MESSAGE, rule.getChecker().getCheckedVersion(), alternativeApisString),
                rule.getConfidence(),
                usedApis
        );
    }
}
