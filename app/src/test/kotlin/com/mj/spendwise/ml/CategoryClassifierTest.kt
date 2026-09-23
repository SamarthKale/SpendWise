package com.mj.spendwise.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun indianBrands_mapToCategories() {
        assertEquals("Food", CategoryClassifier.classify("Swiggy"))
        assertEquals("Food", CategoryClassifier.classify("Domino's Pizza"))
        assertEquals("Groceries", CategoryClassifier.classify("BigBasket"))
        assertEquals("Fuel", CategoryClassifier.classify("Indian Oil"))
        assertEquals("Shopping", CategoryClassifier.classify("Amazon"))
        assertEquals("Travel", CategoryClassifier.classify("Uber"))
        assertEquals("Bills", CategoryClassifier.classify("Airtel Recharge"))
        assertEquals("Entertainment", CategoryClassifier.classify("Netflix"))
        assertEquals("Health", CategoryClassifier.classify("Apollo Pharmacy"))
    }

    @Test
    fun caseInsensitive_andUsesNotes() {
        assertEquals("Food", CategoryClassifier.classify("STARBUCKS"))
        assertEquals("Fuel", CategoryClassifier.classify("Some Station", "petrol fill up"))
    }

    @Test
    fun shortKeywordsNeedWholeWord() {
        assertNull(CategoryClassifier.classify("Vivek Stores")) // "vi" must not match inside "Vivek"
        assertEquals("Bills", CategoryClassifier.classify("Vi Postpaid"))
    }

    @Test
    fun unknownOrBlank_returnsNull() {
        assertNull(CategoryClassifier.classify("Random Shop"))
        assertNull(CategoryClassifier.classify("", ""))
        assertNull(CategoryClassifier.classify(null, null))
    }
}
