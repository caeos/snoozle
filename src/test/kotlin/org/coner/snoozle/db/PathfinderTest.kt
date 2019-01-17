package org.coner.snoozle.db

import assertk.assert
import assertk.assertions.*
import assertk.catch
import org.coner.snoozle.db.sample.SampleDb
import org.coner.snoozle.db.sample.Subwidget
import org.coner.snoozle.db.sample.Widget
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.reflect.full.findAnnotation

class PathfinderTest {

    @Test
    fun itShouldInitWithCorrectEntityPathFormatForWidget() {
        val pathfinder = Pathfinder(Widget::class)

        assert(pathfinder)
                .prop(Pathfinder<Widget>::entityPathFormat)
                .isEqualTo("/widgets/{id}")
    }

    @Test
    fun itShouldInitWithCorrectEntityPathFormatForSubwidget() {
        val pathfinder = Pathfinder(Subwidget::class)

        assert(pathfinder)
                .prop(Pathfinder<Widget>::entityPathFormat)
                .isEqualTo("/widgets/{widgetId}/subwidgets/{id}")
    }

    @Test
    fun itShouldThrowWhenInitWithEntityLackingEntityPathAnnotation() {
        val exception = catch { Pathfinder(LackingEntityPathAnnotation::class) }
        assert(exception).isNotNull {
            it.isInstanceOf(EntityDefinitionException::class)
            it.message().isNotNull {
                it.startsWith(LackingEntityPathAnnotation::class.qualifiedName!!)
                it.contains("lacks")
                it.contains(EntityPath::class.qualifiedName!!)
                it.endsWith("annotation")
            }
        }
    }

    @Test
    fun itShouldThrowWhenInitWithEntityPathValueBracketCountsNotEqual() {
        val exception = catch { Pathfinder(MalformedEntityPathValue::class) }
        assert(exception).isNotNull {
            it.isInstanceOf(EntityDefinitionException::class)
            it.message().isNotNull {
                it.startsWith(MalformedEntityPathValue::class.qualifiedName!!)
                it.contains("has a malformed EntityPath")
                it.contains(MalformedEntityPathValue::class.findAnnotation<EntityPath>()!!.value)
                it.contains("1 open bracket(s)")
                it.contains("0 close bracket(s)")
            }
        }
    }

    @Test
    fun itShouldThrowWhenInitWithEntityPathValueReferencingMissingProperty() {
        val exception = catch { Pathfinder(ReferencingMissingProperty::class) }
        assert(exception).isNotNull {
            it.isInstanceOf(EntityDefinitionException::class)
            it.message().isNotNull {
                it.startsWith(ReferencingMissingProperty::class.qualifiedName!!)
                it.contains("has a malformed EntityPath")
                it.contains(ReferencingMissingProperty::class.findAnnotation<EntityPath>()!!.value)
                it.contains("No such property: id")
            }
        }
    }

    @Test
    fun itShouldThrowWhenInitWithEntityPathValueReferencingNonUuidProperty() {
        val exception = catch { Pathfinder(ReferencingNonUuidProperty::class ) }
        assert(exception).isNotNull {
            it.isInstanceOf(EntityDefinitionException::class)
            it.message().isNotNull {
                it.startsWith(ReferencingNonUuidProperty::class.qualifiedName!!)
                it.contains("has an invalid EntityPath")
                it.contains(ReferencingNonUuidProperty::class.findAnnotation<EntityPath>()!!.value)
                it.contains("References property with unexpected type: ${String::class.qualifiedName!!}")
                it.endsWith("Only ${UUID::class.qualifiedName} is supported.")
            }
        }
    }

    @Test
    fun itShouldFindPathToEntityForSampleDbWidgetOneById() {
        val widgetOne = SampleDb.Widgets.One
        val pathfinder = Pathfinder(Widget::class)

        val actual = pathfinder.findEntity(Widget::id to widgetOne.id)

        assert(actual).isEqualTo("""
            /widgets/1f30d7b6-0296-489a-9615-55868aeef78a.json
        """.trimIndent()
        )
    }

    @Test
    fun itShouldFindPathToEntityForSampleDbWidgetOneSubwidgetOneById() {
        val widgetOneSubwidgetOne = SampleDb.Subwidgets.WidgetOneSubwidgetOne
        val pathfinder = Pathfinder(Subwidget::class)

        val actual = pathfinder.findEntity(
                Subwidget::id to widgetOneSubwidgetOne.id,
                Subwidget::widgetId to widgetOneSubwidgetOne.widgetId
        )

        assert(actual).isEqualTo("""
            /widgets/1f30d7b6-0296-489a-9615-55868aeef78a/subwidgets/220460be-27d4-4e6d-8ac3-34cf5139b229.json
        """.trimIndent()
        )
    }

    @Test
    fun itShouldThrowWhenFindPathPassedWrongAmountOfProperties() {
        val pathfinder = Pathfinder(EntityWithNonPathUuidProperty::class)

        val exception = catch { pathfinder.findEntity() }

        assert(exception).isNotNull {
            it.isInstanceOf(IllegalArgumentException::class)
            it.message().isNotNull {
                it.isEqualTo("""
                    The passed ids ()
                    differ from the expected length: 1.

                    org.coner.snoozle.db.EntityWithNonPathUuidProperty requires the following properties:
                    id
                """.trimIndent())
            }
        }
    }

    @Test
    fun itShouldThrowWhenFindPathToEntityPassedAnIncorrectProperty() {
        val pathfinder = Pathfinder(EntityWithNonPathUuidProperty::class)

        val exception = catch { pathfinder.findEntity(
                EntityWithNonPathUuidProperty::nonPathUuidProperty to UUID.randomUUID()
        ) }

        assert(exception).isNotNull {
            it.isInstanceOf(IllegalArgumentException::class)
            it.message().isNotNull {
                it.isEqualTo("""
                    The passed id properties do not contain the expected properties referenced by the format:

                    /foo/{id}

                    Verify the arguments passed only contain properties referenced by the above format.

                    Expected: id
                    Actual: nonPathUuidProperty
                """.trimIndent())
            }
        }
    }

    @Test
    fun itShouldFindPathToEntityForSampleDbWidgetOneByInstance() {
        val widgetOne = SampleDb.Widgets.One
        val pathfinder = Pathfinder(Widget::class)

        val actual = pathfinder.findEntity(widgetOne)

        assert(actual).isEqualTo("""
            /widgets/1f30d7b6-0296-489a-9615-55868aeef78a.json
        """.trimIndent()
        )
    }

    @Test
    fun itShouldFindPathToEntityForSampleDbWidgetOneSubwidgetOneByInstance() {
        val widgetOneSubwidgetOne = SampleDb.Subwidgets.WidgetOneSubwidgetOne
        val pathfinder = Pathfinder(Subwidget::class)

        val actual = pathfinder.findEntity(widgetOneSubwidgetOne)

        assert(actual).isEqualTo("""
            /widgets/1f30d7b6-0296-489a-9615-55868aeef78a/subwidgets/220460be-27d4-4e6d-8ac3-34cf5139b229.json
        """.trimIndent()
        )
    }

    @Test
    fun itShouldFindParentOfEntityForSampleDbWidgetOne() {
        val widgetOne = SampleDb.Widgets.One
        val pathfinder = Pathfinder(Widget::class)

        val actual = pathfinder.findParentOfEntity(widgetOne)

        assert(actual).isEqualTo("/widgets/")
    }

    @Test
    fun itShouldFindParentOfEntityForSampleDbWidgetOneSubwidgetOne() {
        val widgetOneSubwidgetOne = SampleDb.Subwidgets.WidgetOneSubwidgetOne
        val pathfinder = Pathfinder(Subwidget::class)

        val actual = pathfinder.findParentOfEntity(widgetOneSubwidgetOne)

        assert(actual).isEqualTo("/widgets/1f30d7b6-0296-489a-9615-55868aeef78a/subwidgets/")
    }

    @Test
    fun itShouldFindPathToListingForSampleDbWidgets() {
        val pathfinder = Pathfinder(Widget::class)

        val actual = pathfinder.findListing()

        assert(actual).isEqualTo("/widgets/")
    }

    @Test
    fun itShouldFindPathToListingForSampleDbWidgetOneSubwidgets() {
        val pathfinder = Pathfinder(Subwidget::class)

        val actual = pathfinder.findListing(
                Subwidget::widgetId to SampleDb.Subwidgets.WidgetOneSubwidgetOne.widgetId
        )

        assert(actual).isEqualTo("""
            /widgets/1f30d7b6-0296-489a-9615-55868aeef78a/subwidgets/
        """.trimIndent())
    }

    @Test
    fun itShouldThrowWhenFindPathToListingPassedWrongAmountOfProperties() {
        val pathfinder = Pathfinder(Subwidget::class)

        val exception = catch { pathfinder.findListing() }

        assert(exception).isNotNull {
            it.isInstanceOf(IllegalArgumentException::class)
            it.message().isNotNull {
                it.isEqualTo("""
                    The passed ids ()
                    differ from the expected length: 1.

                    org.coner.snoozle.db.sample.Subwidget requires the following properties:
                    widgetId
                """.trimIndent())
            }
        }
    }

    @Test
    fun itShouldValidateCorrectEntity() {
        val pathfinder = Pathfinder(Widget::class)
        val entity = File(
                "/home/foo/db/widgets/1f30d7b6-0296-489a-9615-55868aeef78a.json"
        )

        val actual = pathfinder.isValidEntity(entity)

        assert(actual).isTrue()
    }

    @Test
    fun itShouldValidateCompletelyWrongGarbage() {
        val pathfinder = Pathfinder(Widget::class)
        val completelyWrongGarbage = File(
                "/home/foo/db/widgets/completelyWrongGarbage"
        )

        val actual = pathfinder.isValidEntity(completelyWrongGarbage)

        assert(actual).isFalse()
    }

    @Test
    fun itShouldValidateSyncthingTmp() {
        // syncthing <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3 <3
        val pathfinder = Pathfinder(Widget::class)
        val syncthingTmp = File(
                "/home/foo/db/widgets/.syncthing.1f30d7b6-0296-489a-9615-55868aeef78a.json.tmp"
        )

        val actual = pathfinder.isValidEntity(syncthingTmp)

        assert(actual).isFalse()
    }

    @Test
    fun itShouldValidateNonUuidJson() {
        val pathfinder = Pathfinder(Widget::class)
        val entity = File(
                "/home/foo/db/widgets/1f30d7z6-0296-489a-9615-55868aeef78a.json"
        ) // transposed "b" to "z" -- non-conformant UUID

        val actual = pathfinder.isValidEntity(entity)

        assert(actual).isFalse()
    }
}

private class LackingEntityPathAnnotation : Entity

@EntityPath("/foo/{id")
private class MalformedEntityPathValue(
        val id: UUID
) : Entity

@EntityPath("/foo/{id}")
private class ReferencingMissingProperty(
        val eyedee: UUID
) : Entity

@EntityPath("/foo/{id}")
private class ReferencingNonUuidProperty (
        val id: String
) : Entity

@EntityPath("/foo/{id}")
private class EntityWithNonPathUuidProperty(
    val id: UUID,
    val nonPathUuidProperty: UUID
) : Entity