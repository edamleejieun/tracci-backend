package com.tracci.module.trip.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItineraryNodeDetailsSupportTest {

    @Test
    void summaryLine_transportFlight() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", "transport");
        details.put("mode", "flight");
        details.put("flightNo", "MU523");
        details.put("airline", "东航");
        String json = ItineraryNodeDetailsSupport.toJson(details);
        assertEquals("MU523 · 东航", ItineraryNodeDetailsSupport.summaryLine("transport", json));
    }

    @Test
    void summaryLine_hotelStayNights() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", "hotel");
        details.put("nightIndex", 2);
        details.put("totalNights", 3);
        details.put("checkOutDate", "2026-08-04");
        String json = ItineraryNodeDetailsSupport.toJson(details);
        String summary = ItineraryNodeDetailsSupport.summaryLine("hotel", json);
        assertTrue(summary != null && summary.contains("第 2/3 晚"));
        assertTrue(summary.contains("离店 8月4日"));
    }

    @Test
    void stripStayMaterializationFields_removesCrossDayKeys() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("kind", "hotel");
        raw.put("confirmationCode", "ABC");
        raw.put("checkOutDate", "2026-08-04");
        raw.put("stayGroupId", "g-1");
        raw.put("stayRole", "check_in");
        raw.put("nightIndex", 1);
        raw.put("totalNights", 3);

        Map<String, Object> stripped = ItineraryNodeDetailsSupport.stripStayMaterializationFields(raw);
        assertEquals("hotel", stripped.get("kind"));
        assertEquals("ABC", stripped.get("confirmationCode"));
        assertFalse(stripped.containsKey("checkOutDate"));
        assertFalse(stripped.containsKey("stayGroupId"));
        assertFalse(stripped.containsKey("stayRole"));
        assertFalse(stripped.containsKey("nightIndex"));
        assertFalse(stripped.containsKey("totalNights"));
    }

    @Test
    void stripStayMaterializationFields_emptyAfterStrip_returnsNull() {
        Map<String, Object> raw = Map.of(
                "checkOutDate", "2026-08-04",
                "stayGroupId", "g-1");
        assertNull(ItineraryNodeDetailsSupport.stripStayMaterializationFields(new LinkedHashMap<>(raw)));
    }
}
