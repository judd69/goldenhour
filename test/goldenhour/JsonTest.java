package goldenhour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The minimal JSON parser GoldenHour shares with the RupeeRates pattern. */
class JsonTest {

    @Test
    void parsesObjectsArraysAndScalars() {
        var root = Json.obj(Json.parse(
                "{\"a\":1,\"b\":\"x\",\"c\":[true,null],\"d\":{\"e\":2.5}}"));
        assertEquals(1.0, Json.num(Json.field(root, "a")));
        assertEquals("x", Json.str(Json.field(root, "b")));
        assertEquals(2.5, Json.num(Json.field(Json.obj(Json.field(root, "d")), "e")));
    }

    @Test
    void escapesStrings() {
        assertEquals("a\\\"b\\\\c\\nd", Json.escape("a\"b\\c\nd"));
    }

    @Test
    void rejectsTrailingData() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} {}"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"abc"));
    }

    @Test
    void rejectsMissingField() {
        var root = Json.obj(Json.parse("{\"a\":1}"));
        assertThrows(IllegalArgumentException.class, () -> Json.field(root, "zzz"));
    }

    @Test
    void handlesEscapesAndUnicode() {
        var root = Json.obj(Json.parse("{\"s\":\"a\\u0041\\n\\t\"}"));
        assertEquals("aA\n\t", Json.str(Json.field(root, "s")));
    }

    @Test
    void emptyObjectAndArray() {
        assertTrue(Json.obj(Json.parse("{}")).isEmpty());
        assertTrue(((java.util.List<?>) Json.parse("[]")).isEmpty());
    }

    @Test
    void rejectsWrongTypeAccessors() {
        assertThrows(IllegalArgumentException.class, () -> Json.str(Json.parse("42")));
        assertThrows(IllegalArgumentException.class, () -> Json.num(Json.parse("\"x\"")));
        assertThrows(IllegalArgumentException.class, () -> Json.obj(Json.parse("[1]")));
    }

    @Test
    void parsesNegativeAndExponentNumbers() {
        assertEquals(-3.5, Json.num(Json.parse("-3.5")));
        assertEquals(1000.0, Json.num(Json.parse("1e3")));
    }

    @Test
    void parsesBooleansAndNull() {
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertEquals(null, Json.parse("null"));
    }
}
