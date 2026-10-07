package com.scenevibe.tvcompanionpoc.wall;

import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Run each standalone JDK contract as a separately counted Gradle/JUnit case. */
@RunWith(Parameterized.class)
public final class M6WallCoreTest {
    private final String name;

    /** Bind a single named contract; no platform clock or fixture adaptation is involved. */
    public M6WallCoreTest(String name) { this.name = name; }

    /** Retain the exact same finite inventory used by the standalone pure-JDK gate. */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        Collection<Object[]> result = new ArrayList<>();
        for (String name : M6WallContract.cases()) result.add(new Object[]{name});
        return result;
    }

    /** Execute the actual source contract with no skipped or conditional case. */
    @Test public void wallContract() { M6WallContract.run(name); }
}
