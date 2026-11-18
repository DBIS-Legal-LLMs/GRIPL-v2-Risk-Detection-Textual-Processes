package de.mertendieckmann.griplbackend

import de.mertendieckmann.griplbackend.evaluation.text.*
import de.mertendieckmann.griplbackend.model.dto.TextLabel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TextScoringTest {

    /** Creates a label for the first occurrence of [passage] in [text]. */
    private fun label(text: String, id: String, passage: String, critical: Boolean): TextLabel {
        val start = text.indexOf(passage)
        require(start >= 0) { "'$passage' not in text" }
        return TextLabel(id = id, start = start, end = start + passage.length, text = passage, isCritical = critical)
    }

    /** The worked example of the analysis document (section 3.4). */
    @Nested
    inner class WorkedExample {
        private val text = "Der Kunde füllt das Formular aus. Ein Mitarbeiter prüft die Identität und speichert die " +
            "Daten im CRM. Danach wird eine Bestätigung versendet."
        private val labels = listOf(
            label(text, "g1", "Der Kunde füllt das Formular aus", true),
            label(text, "g2", "Ein Mitarbeiter prüft die Identität", true),
            label(text, "g3", "speichert die Daten im CRM", true),
            label(text, "g4", "wird eine Bestätigung versendet", false),
        )
        private val predictions = listOf(
            PredictedActivity("p1", "Der Kunde füllt das Formular aus.", true),
            PredictedActivity("p2", "Ein Mitarbeiter prüft die Identität und speichert die Daten im CRM.", true),
            // paraphrased, not locatable
            PredictedActivity("p3", "Die Bestätigung wird verschickt", false),
        )
        private val result = scoreText(text, labels, predictions)

        @Test
        fun `locates the quoted passages`() {
            assertEquals(listOf(TextSpan(0, 33), TextSpan(34, 101), null), result.located.map { it.span })
        }

        @Test
        fun `matches one-to-one by largest overlap`() {
            assertEquals(
                mapOf("g1" to "p1", "g2" to "p2"),
                result.matches.associate { it.labelId to it.predictionId }
            )
            assertEquals(35, result.matches.single { it.labelId == "g2" }.overlap)
        }

        @Test
        fun `classification metrics`() {
            assertEquals(listOf("g1", "g2"), result.truePositiveLabelIds)
            assertEquals(mapOf("g3" to FalseNegativeCause.EXTRACTION), result.falseNegativeLabelIds)
            assertEquals(0, result.falsePositives)
            assertEquals(listOf("g4"), result.trueNegativeLabelIds)
        }

        @Test
        fun `text specific metrics`() {
            assertEquals(2.0 / 3, result.extractionPrecision, 1e-9)
            assertEquals(0.5, result.extractionRecall, 1e-9)
            assertEquals(2.0 / 3, result.localizationRate, 1e-9)
            assertEquals((32.0 / 33 + 35.0 / 67) / 2, result.meanIou, 1e-9)
            assertEquals(1, result.falseNegativesByExtraction)
            assertEquals(0, result.falseNegativesByClassification)
        }

        @Test
        fun `strict mode keeps both matches since their IoU is at least 0_5`() {
            val strict = scoreText(text, labels, predictions, MatchMode.STRICT)
            assertEquals(result.matches, strict.matches)
        }
    }

    @Nested
    inner class Locating {
        @Test
        fun `lenient match ignores case and whitespace`() {
            val text = "The clerk  checks\nthe Data."
            assertEquals(TextSpan(4, 26), locatePassage(text, "CLERK checks the data"))
        }

        @Test
        fun `passage quoted twice maps to two occurrences`() {
            val text = "the form is checked. Later the form is checked."
            val located = locateActivities(
                text,
                listOf(PredictedActivity("a", "the form is checked", false), PredictedActivity("b", "the form is checked", false))
            )
            assertEquals(listOf(TextSpan(0, 19), TextSpan(27, 46)), located.map { it.span })
        }

        @Test
        fun `unknown or blank passage is not located`() {
            assertNull(locatePassage("Some text.", "completely different"))
            assertNull(locatePassage("Some text.", "   "))
        }

        // The following cases are quotes of gpt-oss-20b from the first evaluation runs (Hospital, HR)

        @Test
        fun `added final period is ignored`() {
            val text = "Ist sie in Ordnung, erteilt die Sachbearbeitung die Genehmigung, und der Vorgang endet."
            val passage = "erteilt die Sachbearbeitung die Genehmigung"
            assertEquals(spanOf(text, passage), locatePassage(text, "$passage."))
        }

        @Test
        fun `reordered German word order is located`() {
            val text = "Anschließend überarbeitet sie oder er die Reisedaten. Danach geht es weiter."
            assertEquals(
                spanOf(text, "überarbeitet sie oder er die Reisedaten"),
                locatePassage(text, "sie oder er überarbeitet die Reisedaten")
            )
        }

        @Test
        fun `inflected and reordered quote is located`() {
            val text = "A rejection ends the process. An interview invitation is answered with a confirmation."
            assertEquals(
                spanOf(text, "An interview invitation is answered with a confirmation"),
                locatePassage(text, "answers an interview invitation with a confirmation")
            )
        }

        @Test
        fun `added subject is tolerated and the quote is matched to its label`() {
            val text = "After that, the caregiver holds the discharge conversation with the patient and goes " +
                "through the paperwork and the following steps with the patient."
            val labels = listOf(
                label(text, "talk", "holds the discharge conversation with the patient", true),
                label(text, "paperwork", "goes through the paperwork and the following steps with the patient", true),
            )
            val result = scoreText(text, labels, listOf(
                PredictedActivity("p", "The caregiver goes through the paperwork and the following steps with the patient.", true)
            ))
            // The window boundaries may be off by a word, the overlap still decides for the right label
            assertEquals(mapOf("paperwork" to "p"), result.matches.associate { it.labelId to it.predictionId })
        }

        @Test
        fun `paraphrase and short passages are not located by words`() {
            val text = "Danach wird eine Bestätigung versendet. Sie gibt Kürzungen an."
            assertNull(locatePassage(text, "Die Bestätigung wird verschickt"))
            // Two words only: located literally or not at all
            assertNull(locatePassage(text, "Kürzungen sie"))
        }

        @Test
        fun `word-based locating skips occupied passages`() {
            val text = "Fällt eine Übernachtung an, erfasst sie diese. Fallen Nebenkosten an, erfasst sie diese."
            val located = locateActivities(text, listOf(
                PredictedActivity("a", "sie erfasst diese", false),
                PredictedActivity("b", "sie erfasst diese", false),
            ))
            assertEquals(listOf(TextSpan(28, 45), TextSpan(70, 87)), located.map { it.span })
        }

        private fun spanOf(text: String, passage: String): TextSpan {
            val start = text.indexOf(passage)
            require(start >= 0) { "'$passage' not in text" }
            return TextSpan(start, start + passage.length)
        }
    }

    @Nested
    inner class Classification {
        private val text = "A clerk records the name. The case is archived."
        private val critical = label(text, "l1", "A clerk records the name", true)
        private val nonCritical = label(text, "l2", "The case is archived", false)

        @Test
        fun `critical label extracted but classified non-critical is a classification false negative`() {
            val result = scoreText(text, listOf(critical), listOf(PredictedActivity("p", "A clerk records the name.", false)))
            assertEquals(mapOf("l1" to FalseNegativeCause.CLASSIFICATION), result.falseNegativeLabelIds)
        }

        @Test
        fun `non-critical label predicted critical is a false positive`() {
            val result = scoreText(text, listOf(nonCritical), listOf(PredictedActivity("p", "The case is archived.", true)))
            assertEquals(listOf("l2"), result.falsePositiveLabelIds)
            assertEquals(1, result.falsePositives)
        }

        @Test
        fun `critical prediction outside all labels is a false positive`() {
            val result = scoreText(text, listOf(critical), listOf(
                PredictedActivity("p1", "A clerk records the name.", true),
                PredictedActivity("p2", "The case is archived.", true),
                PredictedActivity("p3", "invented activity", true),
            ))
            assertEquals(listOf("l1"), result.truePositiveLabelIds)
            assertEquals(listOf("p2", "p3"), result.unmatchedCriticalPredictionIds)
            assertEquals(2, result.falsePositives)
        }

        @Test
        fun `no predictions makes every critical label an extraction false negative`() {
            val result = scoreText(text, listOf(critical, nonCritical), emptyList())
            assertEquals(mapOf("l1" to FalseNegativeCause.EXTRACTION), result.falseNegativeLabelIds)
            assertEquals(listOf("l2"), result.trueNegativeLabelIds)
            assertEquals(0.0, result.extractionPrecision)
            assertEquals(0.0, result.extractionRecall)
        }

        @Test
        fun `orphaned labels are ignored`() {
            val orphan = TextLabel(id = "o", start = 0, end = 7, text = "removed passage", isCritical = true)
            val result = scoreText(text, listOf(critical, orphan), emptyList())
            assertEquals(listOf("o"), result.ignoredLabelIds)
            assertEquals(1, result.labelCount)
            assertFalse("o" in result.falseNegativeLabelIds)
        }
    }

    @Nested
    inner class Matching {
        private val text = "The clerk checks the identity of the customer and stores it."
        private val labels = listOf(label(text, "l", "checks the identity", true))

        @Test
        fun `partial mode matches a small overlap that strict mode rejects`() {
            // prediction covers the whole sentence: IoU = 19 / 60 < 0.5
            val predictions = listOf(PredictedActivity("p", text, true))
            assertEquals(1, scoreText(text, labels, predictions, MatchMode.PARTIAL).truePositives)

            val strict = scoreText(text, labels, predictions, MatchMode.STRICT)
            assertEquals(0, strict.truePositives)
            assertEquals(mapOf("l" to FalseNegativeCause.EXTRACTION), strict.falseNegativeLabelIds)
            assertEquals(listOf("p"), strict.unmatchedCriticalPredictionIds)
        }

        @Test
        fun `each prediction is matched to at most one label`() {
            val twoLabels = listOf(
                label(text, "l1", "checks the identity of the customer", true),
                label(text, "l2", "stores it", true),
            )
            val result = scoreText(text, twoLabels, listOf(PredictedActivity("p", text, true)))
            assertEquals(listOf("l1"), result.matches.map { it.labelId })
            assertEquals(mapOf("l2" to FalseNegativeCause.EXTRACTION), result.falseNegativeLabelIds)
        }

        @Test
        fun `span overlap and IoU`() {
            assertEquals(3, TextSpan(0, 5).overlap(TextSpan(2, 8)))
            assertEquals(3.0 / 8, TextSpan(0, 5).iou(TextSpan(2, 8)), 1e-9)
            assertEquals(0, TextSpan(0, 5).overlap(TextSpan(5, 8)))
            assertEquals(0.0, TextSpan(0, 5).iou(TextSpan(5, 8)))
        }
    }
}
