package org.coner.snoozle.db.entity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import org.coner.snoozle.db.path.Pathfinder
import org.coner.snoozle.util.nameWithoutExtension
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.BiPredicate
import java.util.stream.Collectors
import kotlin.streams.toList

class VersionedEntityResource<VE : VersionedEntity>(
        private val root: Path,
        internal val versionedEntityDefinition: VersionedEntityDefinition<VE>,
        private val objectMapper: ObjectMapper,
        private val reader: ObjectReader,
        private val writer: ObjectWriter,
        private val path: Pathfinder<VersionedEntityContainer<VE>>
) {

    fun getEntity(vararg args: Any): VersionedEntityContainer<VE> {
        require(args.isNotEmpty()) { "Minimum one argument" }
        val versionArgument = args.singleOrNull { it is VersionArgument.Readable }
        val useArgs = args.toMutableList()
        when (versionArgument) {
            VersionArgument.Highest -> useArgs[useArgs.lastIndex] = resolveHighestVersion(*useArgs.toTypedArray())
            null -> useArgs.add(resolveHighestVersion(*useArgs.toTypedArray()))
        }
        val entityPath = path.findRecordByArgs(*useArgs.toTypedArray())
        val file = root.resolve(entityPath)
        return read(file)
    }

    fun getAllVersionsOfEntity(vararg args: Any): List<VersionedEntityContainer<VE>> {
        require(args.isNotEmpty()) { "Minimum one argument" }
        val relativeVersionsPath = path.findVersions(*args)
        val versionsPath = root.resolve(relativeVersionsPath)
        if (!Files.exists(versionsPath)) {
            throw EntityIoException.NotFound("No versions found for entity: $relativeVersionsPath")
        }
        return Files.list(versionsPath)
                .filter { Files.isRegularFile(it) && path.isRecord(root.relativize(it)) }
                .map { read(it) }
                .toList()
                .sorted()
    }

    private fun read(file: Path): VersionedEntityContainer<VE> {
        return if (Files.exists(file)) {
            Files.newInputStream(file).use { inputStream ->
                try {
                    reader.readValue<VersionedEntityContainer<VE>>(inputStream)
                } catch (t: Throwable) {
                    throw EntityIoException.ReadFailure("Failed to read versioned entity: ${file.relativize(root)}", t)
                }
            }
        } else {
            throw EntityIoException.NotFound("Versioned entity not found: ${root.relativize(file)}")
        }
    }

    private fun resolveHighestVersion(vararg args: Any): VersionArgument.Specific {
        val relativeVersionsPath = path.findVersions(*args)
        val versionsPath = root.resolve(relativeVersionsPath)
        if (!Files.exists(versionsPath)) {
            throw EntityIoException.NotFound("No versions found for entity: $relativeVersionsPath")
        }
        val version = Files.list(versionsPath)
                .filter { Files.isRegularFile(it) && path.isRecord(root.relativize(it)) }
                .map { it.nameWithoutExtension.toInt() }
                .sorted()
                .max(compareBy { it })
                .orElseThrow { throw EntityIoException.NotFound("No versions found for entity: $relativeVersionsPath") }
        return VersionArgument.Specific(version)
    }

    fun listAll(): List<VersionedEntityContainer<VE>> {
        return Files.find(
                root,
                Int.MAX_VALUE,
                BiPredicate { candidate: Path, attrs: BasicFileAttributes ->
                    attrs.isDirectory && path.isVersionedEntityContainerListing(root.relativize(candidate))
                }
        )
                .sorted()
                .map { versionListing: Path -> {
                    val args = path.extractArgsWithoutVersion(versionListing)
                    getEntity(*args)
                } }
                .collect(Collectors.toList())
                .map { it() }
    }

    fun put(entity: VE, versionArgument: VersionArgument.Writable): VersionedEntityContainer<VE> {
        TODO()
    }

    fun delete(entity: VE, versionArgument: VersionArgument.Specific) {
        TODO()
    }
}