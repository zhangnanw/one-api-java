package com.oneapi.comparator;

import com.oneapi.model.Vendor;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparatorTest {

    @Test
    void byPref_lowerFirst() {
        RoutedVendor high = rv(1, 90);
        RoutedVendor low = rv(2, 10);
        RoutedVendor mid = rv(3, 50);

        List<RoutedVendor> list = new ArrayList<>(List.of(low, high, mid));
        list.sort(new ByPref());

        // ByPref sorts ascending — lower pref first
        assertThat(list).extracting(RoutedVendor::instanceId)
            .containsExactly(2, 3, 1); // 10, 50, 90
    }

    @Test
    void byId_ascending() {
        RoutedVendor a = rv(3, 0);
        RoutedVendor b = rv(1, 0);
        RoutedVendor c = rv(2, 0);

        List<RoutedVendor> list = new ArrayList<>(List.of(a, b, c));
        list.sort(new ById());

        assertThat(list).extracting(RoutedVendor::instanceId)
            .containsExactly(1, 2, 3);
    }

    @Test
    void byStatusDesc_rawBeforeTagged() {
        // STATUS_RAW=1, STATUS_TAGGED=2
        RoutedVendor raw = rvWithStatus(1, 1); // RAW
        RoutedVendor tagged = rvWithStatus(2, 2); // TAGGED

        List<RoutedVendor> list = new ArrayList<>(List.of(tagged, raw));
        list.sort(new ByStatusDesc());

        // ByStatusDesc sorts by status descending — TAGGED (2) before RAW (1)
        assertThat(list.get(0).instanceStatus()).isEqualTo(2);
        assertThat(list.get(1).instanceStatus()).isEqualTo(1);
    }

    @Test
    void byPref_equalPref_stable() {
        RoutedVendor a = rv(1, 50);
        RoutedVendor b = rv(2, 50);

        List<RoutedVendor> list = new ArrayList<>(List.of(a, b));
        list.sort(new ByPref());

        // Equal pref — order preserved (stable sort)
        assertThat(list).extracting(RoutedVendor::instanceId)
            .containsExactly(1, 2);
    }

    private static RoutedVendor rv(int id, int pref) {
        String meta = pref > 0 ? "{\"pref\":" + pref + "}" : "{}";
        return new RoutedVendor(null, "model", "upstream", id, "", meta, 1, pref, "payg");
    }

    private static RoutedVendor rvWithStatus(int id, int status) {
        return new RoutedVendor(null, "model", "upstream", id, "", "{}", status, 0f, "payg");
    }
}
