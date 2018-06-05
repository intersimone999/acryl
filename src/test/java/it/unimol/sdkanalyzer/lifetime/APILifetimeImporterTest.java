package it.unimol.sdkanalyzer.lifetime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simone Scalabrino.
 */
class APILifetimeImporterTest {
    @Test
    public void testLoad() throws IOException {
        List<APILife> apiLives = APILifetimeImporter.getInstance().load("/home/simone/University/Ricerca/2018/AndroidSDK/CiD/res/android_api_lifetime.txt");
        assertTrue(apiLives.size() > 0);

        assertEquals(37_044, apiLives.size());
    }
}