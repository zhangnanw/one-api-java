package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.model.Instance;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelayCoordinatorTest {

    private static RoutedVendor rv(int id, float pref, String layer, int status) {
        String meta = "{\"pref\":" + pref + (layer != null ? ",\"layer\":\"" + layer + "\"" : "") + "}";
        String effectiveLayer = layer != null ? layer : "payg";
        return new RoutedVendor(null, "model", "upstream", id, "", meta, status, pref, effectiveLayer);
    }

    private final Comparator<RoutedVendor> sorter =
        RelayCoordinator.buildSorter(new AppConfig());

    @Test
    void byPrefPrimary_lowerPrefComesFirst() {
        var a = rv(1, 0.1f, "free", Instance.STATUS_RAW);
        var b = rv(2, 0.5f, "free", Instance.STATUS_RAW);

        assertTrue(sorter.compare(a, b) < 0, "pref=0.1 should come before pref=0.5");
    }

    @Test
    void layerOffset_freeBeforePayg() {
        // free=0.8, payg=2.0+0.001=2.001 → free comes first
        var free = rv(1, 0.8f, "free", Instance.STATUS_RAW);
        var payg = rv(2, 0.001f, "payg", Instance.STATUS_RAW);

        assertTrue(sorter.compare(free, payg) < 0,
            "free(0.8) should come before payg(2.001)");
    }

    @Test
    void layerOffset_subscriptionBeforePayg() {
        // sub=1.5, payg=2.0+0.02=2.02 → sub comes first
        var sub = rv(1, 0.5f, "subscription", Instance.STATUS_RAW);
        var payg = rv(2, 0.02f, "payg", Instance.STATUS_RAW);

        assertTrue(sorter.compare(sub, payg) < 0,
            "subscription(1.5) should come before payg(2.02)");
    }

    @Test
    void samePref_statusDescTiebreak() {
        var raw = rv(1, 0.5f, "free", Instance.STATUS_RAW);
        var tagged = rv(2, 0.5f, "free", Instance.STATUS_TAGGED);

        assertTrue(sorter.compare(tagged, raw) < 0, "same pref: TAGGED should come before RAW");
    }

    @Test
    void unknownLayer_treatedAsPayg() {
        var a = rv(1, 0.1f, null, Instance.STATUS_RAW);
        var b = rv(2, 0.1f, "payg", Instance.STATUS_RAW);

        // Same effective pref: 10+20000 vs 10+20000 → tie on pref, tie on status → order preserved
        assertEquals(0, sorter.compare(a, b));
    }

    @Test
    void equalAllFields_stableOrder() {
        var a = rv(1, 0.5f, "free", Instance.STATUS_RAW);
        var b = rv(2, 0.5f, "free", Instance.STATUS_RAW);

        assertEquals(0, sorter.compare(a, b));
    }

    @Test
    void sortList_verifiesOrdering() {
        var list = new ArrayList<>(List.of(
            rv(1, 0.5f, "payg", Instance.STATUS_RAW),           // 2.5
            rv(2, 0.3f, "subscription", Instance.STATUS_RAW),   // 1.3
            rv(3, 0.1f, "free", Instance.STATUS_TAGGED),        // 0.1, TAGGED
            rv(4, 0.1f, "free", Instance.STATUS_RAW)            // 0.1, RAW
        ));
        list.sort(sorter);

        assertEquals(3, list.get(0).instanceId(), "first: free+TAGGED pref=0.1");
        assertEquals(4, list.get(1).instanceId(), "second: free+RAW pref=0.1");
        assertEquals(2, list.get(2).instanceId(), "third: subscription pref=1.3");
        assertEquals(1, list.get(3).instanceId(), "last: payg pref=2.5");
    }
}
