package com.xceptance.neodymium.util;

import static com.codeborne.selenide.Selenide.$$;

import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;

import com.codeborne.selenide.SelenideElement;
import com.xceptance.neodymium.util.Context;

/**
 * Additional helpers for limits chained lookup in Selenide. Contribute that later back to Selenide if it proves to
 * work, so it can become API or better fully integrated so we don't need that workaround concept.
 * 
 * @author rschwietzke
 */
public class SelenidePlus
{
    private SelenidePlus()
    {

    }

    /**
     * Returns an supplier that will return exactly one result if any. It will return an element that is found by
     * parentSelector and has a result for subElementSelector. It does NOT return the subelements, it is meant to be a
     * workaround for poor CSS where the parent is only recognizable by looking at its children, but we don't need the
     * children. Important, this is meant to be lazy so don't call get() when you setup a field or so, only when you
     * really need the element. It reselects all the time!
     *
     * @param parentSelector
     * @param subElementSelector
     * @return an supplier that will return the result later
     */
    public static Supplier<SelenideElement> parentBySubElement(final By parentSelector, final By subElementSelector)
    {
        return new Supplier<SelenideElement>()
        {
            @Override
            public SelenideElement get()
            {
                return $$(parentSelector)
                                         .stream().filter(e -> {
                                             return e.$(subElementSelector).exists();
                                         })
                                         .findFirst()
                                         .get();
            };
        };
    }

    /**
     * Re-execute the entire code when a stale element exception comes up
     *
     * @param code
     *            the code to run
     * @return the element of the execution or any exception that might bubble up
     */
    public static SelenideElement $safe(final Supplier<SelenideElement> code)
    {
        int retryCounter = Context.get().configuration.staleElementRetryCount();

        while (retryCounter >= 0)
        {
            try
            {
                return code.get();
            }
            catch (final StaleElementReferenceException e)
            {
                retryCounter--;

                if (retryCounter < 0)
                {
                    // fail
                    throw e;
                }

                // wait
                try
                {
                    Thread.sleep(Context.get().configuration.staleElementRetryTimeout());
                }
                catch (final InterruptedException e1)
                {
                }
            }
        }

        // never get here
        return null;
    }
}