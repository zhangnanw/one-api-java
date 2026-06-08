package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelayCoordinatorTest {

    private static RoutedVendor rv(int id, int pref, String layer, int status) {
        String meta = "{\"pref\":" + pref + (layer != null ? ",\"layer\":\"" + layer + "\"" : "") + "}";
        return new RoutedVendor(null, "model", "upstream", id, "", meta, status);
    }

    private final Comparator<RoutedVendor> sorter =
        RelayCoordinator.buildSorter(new AppConfig());

    @Test
    void byPrefPrimary_lowerPrefComesFirst() {
        var a = rv(1, 10, "free", InstanceRepo.STATUS_RAW);
        var b = rv(2, 50, "free", InstanceRepo.STATUS_RAW);

        assertTrue(sorter.compare(a, b) < 0, "pref=10 should come before pref=50");
    }

    @Test
    void samePref_statusDescTiebreak() {
        var raw = rv(1, 50, "free", InstanceRepo.STATUS_RAW);    // STATUS_RAW=1
        var tagged = rv(2, 50, "free", InstanceRepo.STATUS_TAGGED); // TAGGED=2

        // TAGGED (higher status) should come first
        assertTrue(sorter.compare(tagged, raw) < 0, "same pref: TAGGED should come before RAW");
    }

    @Test
    void layerOffset_freeBeforePayg() {
        // free=100, payg=1+20000=20001 → free comes first
        var free = rv(1, 100, "free", InstanceRepo.STATUS_RAW);
        var payg = rv(2, 1, "payg", InstanceRepo.STATUS_RAW);

        assertTrue(sorter.compare(free, payg) < 0,
            "free(100+0=100) should come before payg(1+20000=20001)");
    }

    @Test
    void layerOffset_subscriptionBeforePayg() {
        // subscription=50+10000=10050, payg=20+20000=20020 → sub comes first
        var sub = rv(1, 50, "subscription", InstanceRepo.STATUS_RAW);
        var payg = rv(2, 20, "payg", InstanceRepo.STATUS_RAW);

        assertTrue(sorter.compare(sub, payg) < 0,
            "subscription(50+10000=10050) should come before payg(20+20000=20020)");
    }

    @Test
    void unknownLayer_treatedAsPayg() {
        // null layer → offset=20000 (same as payg)
        var a = rv(1, 10, null, InstanceRepo.STATUS_RAW);
        var b = rv(2, 10, "payg", InstanceRepo.STATUS_RAW);

        // Same effective pref: 10+20000 vs 10+20000 → tie on pref, tie on status → order preserved
        assertEquals(0, sorter.compare(a, b));
    }

    @Test
    void equalAllFields_stableOrder() {
        var a = rv(1, 50, "free", InstanceRepo.STATUS_RAW);
        var b = rv(2, 50, "free", InstanceRepo.STATUS_RAW);

        assertEquals(0, sorter.compare(a, b));
    }

    @Test
    void sortList_verifiesOrdering() {
        var list = new ArrayList<>(List.of(
            rv(1, 100, "payg", InstanceRepo.STATUS_RAW),   // 20100
            rv(2, 50, "subscription", InstanceRepo.STATUS_RAW), // 10050
            rv(3, 10, "free", InstanceRepo.STATUS_TAGGED), // 10, TAGGED
            rv(4, 10, "free", InstanceRepo.STATUS_RAW)     // 10, RAW
        ));
        list.sort(sorter);

        assertEquals(3, list.get(0).instanceId(), "first: free+TAGGED pref=10");
        assertEquals(4, list.get(1).instanceId(), "second: free+RAW pref=10 (same pref, lower status)");
        assertEquals(2, list.get(2).instanceId(), "third: subscription pref=10050");
        assertEquals(1, list.get(3).instanceId(), "last: payg pref=20100");
    }
}
