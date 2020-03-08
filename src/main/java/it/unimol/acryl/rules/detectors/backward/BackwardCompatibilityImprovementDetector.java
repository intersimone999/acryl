package it.unimol.acryl.rules.detectors.backward;

import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.android.ApkContainer;
import it.unimol.acryl.lifetime.APILife;
import it.unimol.acryl.lifetime.APILifetime;
import it.unimol.acryl.rules.CombinedViolationDetector;
import it.unimol.acryl.rules.Rule;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class BackwardCompatibilityImprovementDetector extends PotentialBackwardCompatibilityDetector {
    private static final String MESSAGE = "If you want to support older versions (<= %d), add a check and handle using these APIs: %s";
    private final APILifetime apiLifetime;

    public BackwardCompatibilityImprovementDetector(APILifetime lifetime) {
        this.apiLifetime = lifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        if (!super.violatesRule(apk, methodContext, codeCheck, rule, apisInCode))
            return false;

        if (methodContext.getTargetAndroidSDK() > rule.getChecker().getCheckedVersion() ||
                methodContext.getClassContext().getTargetAndroidSDK() > rule.getChecker().getCheckedVersion()) {
            Logger.getAnonymousLogger().info("Checking of " + methodContext.getIMethod().getSignature() + " aborted because it has a compatible TargetApi");
            return false;
        }

        if (apk.getMinSDKVersion() <= rule.getChecker().getCheckedVersion())
            return false;

        for (String api : rule.getFalseApis()) {
            APILife apiLife = this.apiLifetime.getLifeFor(api);

            // It is an improvement only if it is a potential bug, i.e., at least one of the APIs did not exist in the past
            if (apiLife.getMinVersion() > rule.getChecker().getCheckedVersion())
                return true;
        }

        return false;
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
                usedApis,
                alternativeApis,
                rule.getChecker().toString()
        );
    }
}
