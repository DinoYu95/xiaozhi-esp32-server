package xiaozhi.modules.ota;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import xiaozhi.modules.ota.util.OtaRolloutMatcher;
import xiaozhi.modules.ota.util.OtaVersionUtils;

class OtaRolloutMatcherTest {

    @Test
    void hashIsStableAndBounded() {
        String mac = "b0:8c:b3:c6:cf:78";
        int h1 = OtaRolloutMatcher.hashMac(mac);
        int h2 = OtaRolloutMatcher.hashMac("B0:8C:B3:C6:CF:78");
        assertEquals(h1, h2);
        assertTrue(h1 >= 0 && h1 < 100);
        assertEquals(h1, OtaRolloutMatcher.hashMac("b08cb3c6cf78"));
    }

    @Test
    void rolloutZeroOnlyHitsWhitelist() {
        String mac = "aa:bb:cc:dd:ee:01";
        assertFalse(OtaRolloutMatcher.hitRollout(mac, 0));
        assertFalse(OtaRolloutMatcher.isEligible(mac, 0, List.of()));
        assertTrue(OtaRolloutMatcher.isEligible(mac, 0, List.of("AA:BB:CC:DD:EE:01")));
    }

    @Test
    void rolloutHundredHitsAll() {
        assertTrue(OtaRolloutMatcher.isEligible("11:22:33:44:55:66", 100, List.of()));
    }

    @Test
    void channelVisibilityFollowsSpec() {
        assertTrue(OtaRolloutMatcher.channelCanSeeRelease("stable", "stable"));
        assertFalse(OtaRolloutMatcher.channelCanSeeRelease("stable", "beta"));
        assertTrue(OtaRolloutMatcher.channelCanSeeRelease("beta", "beta"));
        assertTrue(OtaRolloutMatcher.channelCanSeeRelease("beta", "stable"));
        assertArrayEquals(new String[] { "stable" }, OtaRolloutMatcher.visibleChannels("stable"));
        assertArrayEquals(new String[] { "beta", "stable" }, OtaRolloutMatcher.visibleChannels("beta"));
    }

    @Test
    void versionCompare() {
        assertTrue(OtaVersionUtils.isNewer("1.3.1", "1.3.0"));
        assertFalse(OtaVersionUtils.isNewer("1.3.0", "1.3.1"));
        assertTrue(OtaVersionUtils.compare("1.3.1", "1.3.1-rc.1") > 0);
        assertEquals(0, OtaVersionUtils.compare("2.0.0", "2.0.0"));
    }
}
