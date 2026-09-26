package com.altronixsoft.securerag.startup;

import java.util.List;

/** One row of {@code seed-docs/manifest.json}. */
record SeedManifestEntry(String file, List<String> groups) {
}
