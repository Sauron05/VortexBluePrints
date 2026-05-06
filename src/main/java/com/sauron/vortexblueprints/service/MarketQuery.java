package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.MarketView;

public record MarketQuery(
    String text,
    BlueprintCategory category,
    MarketView view,
    double minPrice,
    double maxPrice
) {

    public static MarketQuery defaultQuery() {
        return new MarketQuery("", null, MarketView.TRENDING, -1.0D, -1.0D);
    }
}