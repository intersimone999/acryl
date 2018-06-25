package it.unimol.sdkanalyzer.topic_analysis;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import it.unimol.sdkanalyzer.runnable.APIVersionExtractor;
import it.unimol.sdkanalyzer.static_analysis.contexts.AndroidJarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.GlobalContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;

/**
 * @author Simone Scalabrino.
 */
class MessageAssignerTest {
    @Test
    void testAssignment() throws IOException, GitAPIException, ClassHierarchyException {
        File localTempCloneDirectory = new File("testAppForMA");
        localTempCloneDirectory.deleteOnExit();

        Git git;
        if (!localTempCloneDirectory.exists()) {
            git = Git.cloneRepository().setDirectory(localTempCloneDirectory).setURI("https://github.com/amasciul/Drinks.git").call();
        } else {
            git = Git.open(localTempCloneDirectory);
        }
//        git.checkout().setName("d915cfd9874591b8380be559c53d113af6a4b68e").call();


        JarContext context = GlobalContext.getAndroidContext("src/test/resources/amasciul_Drinks_25e2f870d90ef6537801e8cecbe22cf98cda4fa4.jar");
        MethodContext methodContext = context.resolveMethodContext("fr.masciulli.drinks.ui.fragment.DrinksFragment.onItemClick(ILfr/masciulli/drinks/core/Drink;)V");

        MessageAssigner assigner = new MessageAssigner(localTempCloneDirectory);

        APIVersionExtractor.APIUsageReport report = new APIVersionExtractor.APIUsageReport();
        report.setCheck("SDK_INT < 23");
        report.setMethod(methodContext);
        report.setApis("android.content.res.Resources.getColor(I)I");
        assigner.assign(report, "25e2f870d90ef6537801e8cecbe22cf98cda4fa4");

        assertNotNull(report.getMessage());
    }

    @Test
    void testAssignmentInInitializer() throws IOException, GitAPIException, ClassHierarchyException {
        File localTempCloneDirectory = new File("testAppForMA");
        localTempCloneDirectory.deleteOnExit();

        Git git;
        if (!localTempCloneDirectory.exists()) {
            git = Git.cloneRepository().setDirectory(localTempCloneDirectory).setURI("https://github.com/amasciul/Drinks.git").call();
        } else {
            git = Git.open(localTempCloneDirectory);
        }
//        git.checkout().setName("d915cfd9874591b8380be559c53d113af6a4b68e").call();


        JarContext context = GlobalContext.getAndroidContext("src/test/resources/amasciul_Drinks_25e2f870d90ef6537801e8cecbe22cf98cda4fa4.jar");
        MethodContext methodContext = context.resolveMethodContext("fr.masciulli.drinks.ui.EnterPostponeTransitionCallback.<clinit>()V");

        MessageAssigner assigner = new MessageAssigner(localTempCloneDirectory);

        APIVersionExtractor.APIUsageReport report = new APIVersionExtractor.APIUsageReport();
        report.setCheck("SDK_INT >= 21");
        report.setMethod(methodContext);
        report.setApis("");
        assigner.assign(report, "25e2f870d90ef6537801e8cecbe22cf98cda4fa4");

        assertNull(report.getMessage());
    }
}