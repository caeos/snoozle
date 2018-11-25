package org.coner.snoozle.db

import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure

class Pathfinder<E : Entity>(
        val kclass: KClass<E>
) {
    val entityPathFormat: String
    val entityPathReplaceProperties: List<KProperty1<E, UUID>>
    val listingPathFormat: String
    val listingPathReplaceProperties: List<KProperty1<E, UUID>>

    init {
        fun buildPathReplacementProperties(pathReplacementFormat: String): List<KProperty1<E, UUID>> {
            val openBrackets = pathReplacementFormat.count { it == '{' }
            val closeBrackets = pathReplacementFormat.count { it == '}' }
            if (openBrackets != closeBrackets) {
                throw EntityDefinitionException("""
                    ${kclass.qualifiedName} has a malformed EntityPath: $pathReplacementFormat.

                    There are $openBrackets open bracket(s) and $closeBrackets close bracket(s).
                """.trimIndent())
            }
            val entityPathReplacements = openBrackets
            val entityPathReplacementProperties = mutableListOf<KProperty1<E, UUID>>()
            var openBracketPosition: Int
            var closeBracketPosition = -1
            for (i in 1 .. entityPathReplacements) {
                openBracketPosition = pathReplacementFormat.indexOf('{', closeBracketPosition + 1)
                closeBracketPosition = pathReplacementFormat.indexOf('}', openBracketPosition)
                val entityPathReplacementPropertyName = pathReplacementFormat.substring(
                        openBracketPosition + 1,
                        closeBracketPosition
                )
                val property = kclass.declaredMemberProperties
                        .firstOrNull { it.name ==  entityPathReplacementPropertyName } as KProperty1<E, UUID>?
                if (property != null) {
                    if (property.returnType != UUID::class.starProjectedType) {
                        throw EntityDefinitionException("""
                            ${kclass.qualifiedName} has an invalid EntityPath: $pathReplacementFormat.

                            References property with unexpected type: ${property.returnType.jvmErasure.qualifiedName}.

                            Only ${UUID::class.qualifiedName} is supported.
                        """.trimIndent())
                    }
                } else {
                    throw EntityDefinitionException("""
                        ${kclass.qualifiedName} has a malformed EntityPath: $pathReplacementFormat.

                        No such property: $entityPathReplacementPropertyName
                    """.trimIndent())
                }
                entityPathReplacementProperties.add(property)
            }
            return entityPathReplacementProperties.toList()
        }

        val entityPath: EntityPath = kclass.findAnnotation()
            ?: throw EntityDefinitionException("""
                ${kclass.qualifiedName} lacks ${EntityPath::class.qualifiedName} annotation
            """.trimIndent())
        entityPathFormat = entityPath.value
        entityPathReplaceProperties = buildPathReplacementProperties(entityPathFormat)
        listingPathFormat = entityPathFormat.substring(0..entityPathFormat.lastIndexOf('/'))
        listingPathReplaceProperties = buildPathReplacementProperties(listingPathFormat)
    }

    fun findEntity(vararg ids: Pair<KProperty1<E, UUID>, UUID>): String {
        enforcePropertyArgumentsMatch(ids, entityPathReplaceProperties, entityPathFormat)
        var path = entityPathFormat
        for (id in ids) {
            path = path.replace("{${id.first.name}}", id.second.toString())
        }
        return "$path.json"
    }

    fun findEntity(entity: E): String {
        var path = entityPathFormat
        for (property in entityPathReplaceProperties) {
            path = path.replace("{${property.name}}", property.get(entity).toString())
        }
        return "$path.json"
    }

    fun findListing(vararg ids: Pair<KProperty1<E, UUID>, UUID>): String {
        enforcePropertyArgumentsMatch(ids, listingPathReplaceProperties, listingPathFormat)
        var path = listingPathFormat
        if (ids.isNotEmpty()) {
            for (id in ids) {
                path = path.replace("{${id.first.name}}", id.second.toString())
            }
        }
        return path
    }

    private fun enforcePropertyArgumentsMatch(
            ids: Array<out Pair<KProperty1<E, UUID>, UUID>>,
            replacementProperties: List<KProperty1<E, UUID>>,
            replacementFormat: String
    ) {
        // check counts match
        if (replacementProperties.size != ids.size) throw IllegalArgumentException("""
            The passed ids (${ids.joinToString(", ") { it.first.name }})
            differ from the expected length: ${replacementProperties.size}.

            ${kclass.qualifiedName} requires the following properties:
            ${replacementProperties.joinToString(", ") { it.name }}
        """.trimIndent())

        // check all passed properties match
        if (!ids.map { it.first }.containsAll(replacementProperties)) throw IllegalArgumentException("""
            The passed id properties do not contain the expected properties referenced by the format:

            $replacementFormat

            Verify the arguments passed only contain properties referenced by the above format.

            Expected: ${replacementProperties.joinToString(", ") { it.name }}
            Actual: ${ids.joinToString(", ") { it.first.name }}
        """.trimIndent())
    }
}