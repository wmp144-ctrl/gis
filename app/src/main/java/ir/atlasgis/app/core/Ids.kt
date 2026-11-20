package ir.atlasgis.app.core

import java.util.UUID

/** Shared id factory so pure (JVM-testable) modules never touch Android classes. */
fun newId(): String = UUID.randomUUID().toString()
