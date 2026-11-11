package goldenhour;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Preset places — the Indian cities photographers actually shoot in, plus
 * a few classic light destinations. Coordinates are the city centres as
 * listed by Open-Meteo's geocoding; timezones are the IANA zones.
 */
public final class Cities {

    private Cities() {
    }

    public record City(String name, double latitude, double longitude, ZoneId zone) {
    }

    public static final List<City> ALL = List.of(
            new City("Mumbai", 19.0760, 72.8777, ZoneId.of("Asia/Kolkata")),
            new City("Delhi", 28.6139, 77.2090, ZoneId.of("Asia/Kolkata")),
            new City("Bengaluru", 12.9716, 77.5946, ZoneId.of("Asia/Kolkata")),
            new City("Chennai", 13.0827, 80.2707, ZoneId.of("Asia/Kolkata")),
            new City("Kolkata", 22.5726, 88.3639, ZoneId.of("Asia/Kolkata")),
            new City("Hyderabad", 17.3850, 78.4867, ZoneId.of("Asia/Kolkata")),
            new City("Pune", 18.5204, 73.8567, ZoneId.of("Asia/Kolkata")),
            new City("Jaipur", 26.9124, 75.7873, ZoneId.of("Asia/Kolkata")),
            new City("Udaipur", 24.5854, 73.7125, ZoneId.of("Asia/Kolkata")),
            new City("Goa", 15.4909, 73.8278, ZoneId.of("Asia/Kolkata")),
            new City("Varanasi", 25.3176, 82.9739, ZoneId.of("Asia/Kolkata")),
            new City("Kochi", 9.9312, 76.2673, ZoneId.of("Asia/Kolkata")),
            new City("Ahmedabad", 23.0225, 72.5714, ZoneId.of("Asia/Kolkata")),
            new City("Leh", 34.1526, 77.5770, ZoneId.of("Asia/Kolkata")),
            new City("Shimla", 31.1048, 77.1734, ZoneId.of("Asia/Kolkata")),
            new City("Amritsar", 31.6340, 74.8723, ZoneId.of("Asia/Kolkata")),
            new City("Darjeeling", 27.0410, 88.2663, ZoneId.of("Asia/Kolkata")),
            new City("Ooty", 11.4102, 76.6950, ZoneId.of("Asia/Kolkata")),
            new City("Rishikesh", 30.0869, 78.2676, ZoneId.of("Asia/Kolkata")),
            new City("Hampi", 15.3350, 76.4600, ZoneId.of("Asia/Kolkata")));

    public static final City DEFAULT = ALL.get(0); // Mumbai

    /** Case-insensitive lookup by name ("mumbai", "MUMBAI", "Mumbai" all work). */
    public static Optional<City> find(String name) {
        String wanted = name.toLowerCase(Locale.ROOT).trim();
        return ALL.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }
}
