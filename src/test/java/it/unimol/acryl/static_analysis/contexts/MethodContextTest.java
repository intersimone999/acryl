package it.unimol.acryl.static_analysis.contexts;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import it.unimol.acryl.analysis.JarTester;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

/**
 * @author Simone Scalabrino.
 */
class MethodContextTest extends JarTester {
    @Test
    void testDeprecation() throws ClassHierarchyException, IOException {
        JarContext context = getTestJar();

        ClassContext classContext = context.resolveClassContext("android.view.View");

        assertTrue(classContext.resolveMethodContext("requestFitSystemWindows()V").isDeprecated());
        assertFalse(classContext.resolveMethodContext("getFitsSystemWindows()Z").isDeprecated());
        assertFalse(classContext.resolveMethodContext("isShown()Z").isDeprecated());
    }

    @Test
    void testDirectMethodResolution() throws ClassHierarchyException, IOException {
        JarContext context = getTestJar();

        MethodContext m1 = context.resolveMethodContext("android.view.View.requestFitSystemWindows()V");
        MethodContext m2 = context.resolveMethodContext("android.view.View.getFitsSystemWindows()Z");
        MethodContext m3 = context.resolveMethodContext("android.view.View.isShown()Z");

        assertNotNull(m1);
        assertNotNull(m2);
        assertNotNull(m3);

        try {
            context.resolveMethodContext("blablabla.failed");
            fail("Failed");
        } catch (RuntimeException ignored) {
        }


        try {
            context.resolveMethodContext("blablabla.failed()V");
            fail("Failed");
        } catch (RuntimeException ignored) {
        }

        try {
            context.resolveMethodContext("blablablafailed()V");
            fail("Failed");
        } catch (RuntimeException ignored) {
        }
    }
}