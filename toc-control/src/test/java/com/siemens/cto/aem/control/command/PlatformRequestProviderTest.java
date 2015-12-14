package com.siemens.cto.aem.control.command;

import com.siemens.cto.aem.common.domain.model.platform.Platform;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class PlatformRequestProviderTest {

    @Test
    public void testProviderForEveryPlatform() throws Exception {

        final Set<Platform> platformsMissingMappings = new HashSet<>();
        for (final Platform  p : Platform.values()) {
            if (PlatformCommandProvider.lookup(p) == null) {
                platformsMissingMappings.add(p);
            }
        }

        assertEquals(Collections.<Platform>emptySet(),
                     platformsMissingMappings);
    }
}
