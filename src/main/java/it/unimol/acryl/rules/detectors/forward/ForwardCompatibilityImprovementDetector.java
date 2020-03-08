package it.unimol.acryl.rules.detectors.forward;

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

/**
 * @author Simone Scalabrino.
 */
public class ForwardCompatibilityImprovementDetector extends PotentialForwardCompatibilityDetector {
    private final int apiLevel;
    private final APILifetime apiLifetime;

    public ForwardCompatibilityImprovementDetector(int apiLevel, APILifetime apiLifetime) {
        this.apiLevel = apiLevel;
        this.apiLifetime = apiLifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        if (!super.violatesRule(apk, methodContext, codeCheck, rule, apisInCode))
            return false;

        for (String api : rule.getTrueApis()) {
            APILife apiLife = this.apiLifetime.getLifeFor(api);

            if (apiLife.getMaxVersion() < apiLevel && apiLife.getMaxVersion() != -1)
                return false;
        }

        return true;
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
        StringBuilder messageBuilder = new StringBuilder("[Info] ");

        int minSdk;
        try {
            minSdk = apk.getMinSDKVersion();
        } catch (IOException e) {
            minSdk = 0;
        }

        if (minSdk > rule.getChecker().getCheckedVersion()) {
            if (alternativeApis.size() > 0) {
                messageBuilder.append("You should use alternative APIs, since you do not support old Android versions (")
                        .append(checkToImplement.toString())
                        .append("). Please, use: ")
                        .append(alternativeApisString)
                        .append(".");
            } else {
                messageBuilder.append("You should not use these APIs, which are commonly used in old Android versions which you do not support (")
                        .append(checkToImplement.toString())
                        .append(").");
            }
        } else if (alternativeApis.containsAll(usedApis)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using should be used in a different way in newer Android versions (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). Consider using: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using should be used only in older Android versions (")
                    .append(checkToImplement.toString())
                    .append("). Add a check.");
        } else {
            messageBuilder.append("You should use these APIs differently in newer Android releases (")
                    .append(checkToImplement.getInverse(true))
                    .append("). Add a check and handle with: ")
                    .append(alternativeApisString)
                    .append(".");
        }
        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.ForwardImprovement,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis,
                alternativeApis,
                rule.getChecker().toString()
        );
    }
}
