package com.example.wallet.common.cnb;

import org.springframework.context.ApplicationContext;

/** Lets tests outside this package run the CNB refresh, which the test profile doesn't schedule. */
public final class CnbRefresh {

    private CnbRefresh() {}

    public static void now(ApplicationContext context) {
        context.getBean(CnbFxRateProvider.class).refresh();
    }
}
