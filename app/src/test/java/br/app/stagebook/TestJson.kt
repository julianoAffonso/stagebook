package br.app.stagebook

import kotlinx.serialization.json.Json

/** Reads what an older or newer app wrote: fields it doesn't know are skipped. One instance for every test. */
internal val lenientJson = Json { ignoreUnknownKeys = true }

/** Writes as the app writes to the band: values left at their default don't go. */
internal val sparseJson = Json { encodeDefaults = false }
