package org.coner.snoozle.db

import java.nio.file.Path
import java.util.*

class KeyMapper<K : Key, R : Record<K>>(
        private val definition: RecordDefinition<K, R>,
        private val pathfinder: Pathfinder<K, R>,
        private val relativeRecordFn: KeyMapper<K, R>.RelativeRecordContext.() -> K,
        private val instanceFn: R.() -> K
) {

    fun fromRelativeRecord(relativeRecord: Path): K {
        val rawStringParts = pathfinder.findVariableStringParts(relativeRecord)
        return relativeRecordFn.invoke(RelativeRecordContext(rawStringParts))
    }

    fun fromInstance(instance: R): K {
        return instanceFn(instance)
    }

    inner class RelativeRecordContext(
            private val rawStringParts: Array<String>
    ) {
        fun uuidAt(index: Int): UUID {
            return UUID.fromString(rawStringParts[index])
        }

        fun stringAt(index: Int): String {
            return rawStringParts[index]
        }
    }

}
