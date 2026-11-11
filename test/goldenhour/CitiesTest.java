package goldenhour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Preset places: sane coordinates, lookup behaviour. */
class CitiesTest {

    @Test
    void twentyPresets() {
        assertEquals(20, Cities.ALL.size());
    }

    @Test
    void coordinatesArePlausible() {
        for (Cities.City c : Cities.ALL) {
            assertTrue(c.latitude() >= -90 && c.latitude() <= 90, c.name());
            assertTrue(c.longitude() >= -180 && c.longitude() <= 180, c.name());
            assertTrue(c.name().length() >= 3, c.name());
        }
    }

    @Test
    void mumbaiIsDefault() {
        assertEquals("Mumbai", Cities.DEFAULT.name());
        assertEquals(19.0760, Cities.DEFAULT.latitude(), 0.0001);
        assertEquals(72.8777, Cities.DEFAULT.longitude(), 0.0001);
        assertEquals("Asia/Kolkata", Cities.DEFAULT.zone().getId());
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertEquals("Mumbai", Cities.find("mumbai").orElseThrow().name());
        assertEquals("Jaipur", Cities.find("JAIPUR").orElseThrow().name());
        assertEquals("Udaipur", Cities.find("  Udaipur ").orElseThrow().name());
    }

    @Test
    void unknownPlaceIsEmpty() {
        assertTrue(Cities.find("Atlantis").isEmpty());
        assertTrue(Cities.find("").isEmpty());
    }

    @Test
    void lehIsNorthernmost() {
        double maxLat = Cities.ALL.stream().mapToDouble(Cities.City::latitude).max().orElseThrow();
        assertEquals(34.1526, maxLat, 0.0001);
    }
}
