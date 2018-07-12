package it.unimol.sdkanalyzer.rules.detectors.forward;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public abstract class PotentialForwardCompatibilityDetector extends SingleRuleViolationDetector {

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        if (rule.getTrueApis().size() == 0)
            return false;

        if (!apisInCode.containsAll(rule.getTrueApis()))
            return false;

        if (!codeCheck.isNull())
            return false;

        if (rule.getChecker().getComparator().equals(VersionChecker.Comparator.NE))
            return false;

        int targetSDK = methodContext.getTargetAndroidSDK();
        if (targetSDK == -1)
            targetSDK = methodContext.getClassContext().getTargetAndroidSDK();

        if (targetSDK != -1 && targetSDK <= rule.getChecker().getCheckedVersion()) {
            Logger.getAnonymousLogger().info("Checking of " + methodContext.getIMethod().getSignature() + " aborted because it has a compatible TargetApi");
            return false;
        }

        return true;
    }
}
