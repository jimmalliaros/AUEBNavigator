package com.example.auebnavigator;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {

    @Before
    public void setUp() {
        // Ξεκινάει το MainActivity και αρχικοποιεί τον "κατάσκοπο" των Intents
        ActivityScenario.launch(MainActivity.class);
        Intents.init();
    }

    @After
    public void tearDown() {
        // Καθαρίζει μετά το τέλος του test
        Intents.release();
    }

    @Test
    public void testMicrophoneButtonIsVisible() {
        // Ελέγχει αν το κουμπί του μικροφώνου φορτώνει και φαίνεται στην οθόνη
        onView(withId(R.id.btn_mic)).check(matches(isDisplayed()));
    }

    @Test
    public void testSettingsNavigation() {
        // Υποθέτοντας ότι έχεις ένα κουμπί/view με id nav_settings στο activity_main.xml
        // ACT: Βρίσκει το κουμπί των ρυθμίσεων και του κάνει κλικ
        onView(withId(R.id.nav_settings)).perform(click());

        // ASSERT: Ελέγχει αν το Android προσπάθησε να ανοίξει το SettingsActivity
        intended(hasComponent(SettingsActivity.class.getName()));
    }
}