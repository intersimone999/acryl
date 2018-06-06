package it.unimol.sdkanalyzer.lifetime;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simone Scalabrino.
 */
class APILifetimeTest {
    @Test
    public void testLoad() throws IOException {
        APILifetime apiLives = APILifetime.load(new File("/home/simone/University/Ricerca/2018/AndroidSDK/CiD/res/android_api_lifetime.txt"));
        assertTrue(apiLives.size() > 0);

        assertEquals(37_032, apiLives.size());
    }
}