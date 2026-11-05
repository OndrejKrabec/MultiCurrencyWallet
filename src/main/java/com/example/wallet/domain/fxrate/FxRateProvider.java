package com.example.wallet.domain.fxrate;

import com.example.wallet.common.exception.RatesUnavailableException;

public interface FxRateProvider {

    /**
     * The latest fixing loaded; its {@code validFor} says which day it is.
     *
     * @throws RatesUnavailableException if no rates have ever been loaded
     */
    FxRateTable getCurrentRates();
}
