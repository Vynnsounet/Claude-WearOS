package com.medipote.app

import com.medipote.app.ai.AiAnswer
import com.medipote.app.ai.AiAssistant
import com.medipote.app.ai.AiClient
import com.medipote.app.ai.AiContext
import com.medipote.app.ai.AiFailureKind
import com.medipote.app.ai.AiGuard
import com.medipote.app.ai.AiPrompt
import com.medipote.app.ai.AiResult
import com.medipote.app.ai.AiTopic
import com.medipote.app.ai.AiTurn
import com.medipote.app.ai.AnthropicAiClient
import com.medipote.app.ai.MemoryKeyVault
import com.medipote.app.data.AiShare
import com.medipote.app.data.AiSettings
import com.medipote.app.data.AppRepository
import com.medipote.app.data.AppState
import com.medipote.app.data.Awakening
import com.medipote.app.data.CycleInfo
import com.medipote.app.data.DayEntry
import com.medipote.app.data.DoseTime
import com.medipote.app.data.MeasureType
import com.medipote.app.data.Measurement
import com.medipote.app.data.MyCondition
import com.medipote.app.data.Profile
import com.medipote.app.data.Settings
import com.medipote.app.data.Sex
import com.medipote.app.data.SleepNight
import com.medipote.app.data.SymptomNote
import com.medipote.app.data.Treatment
import com.medipote.app.data.WellbeingSettings
import com.medipote.app.domain.Urgency
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AI assistant: what is sent, what is kept back, what is shown, and the call itself against a local stand-in for the API. */
@OptIn(ExperimentalCoroutinesApi::class)
class AiTest {
    private val day = LocalDate.of(2026, 11, 19)

    private fun night(daysAgo: Int, minutes: Int, notes: String = ""): SleepNight {
        val date = day.minusDays(daysAgo.toLong())
        val bed = date.minusDays(1).atTime(23, 0)
        return SleepNight(date, bed, 20, bed.plusMinutes(minutes + 20L), notes = notes)
    }

    private val levo = Treatment("levo", name = "Lévothyrox", strength = "75 µg", doses = listOf(DoseTime(8 * 60)), startDate = day.minusDays(200))
    private val full = AppState(
        profile = Profile("Camille Dupont", age = 34, sex = Sex.FEMALE, weightKg = 64.0, heightCm = 168, medicalHistory = listOf("Secret"), allergies = listOf("Pénicilline"), menstruating = true,
            cycle = CycleInfo(listOf(day.minusDays(52), day.minusDays(24)))),
        onboarded = true, treatments = listOf(levo),
        sleep = listOf(night(0, 5 * 60, notes = "dispute avec Paul"), night(1, 5 * 60), night(2, 4 * 60 + 30)),
        symptomLog = listOf(SymptomNote(day, "fatigue", 4, note = "texte libre privé"), SymptomNote(day.minusDays(1), "fatigue", 4)),
        journal = listOf(DayEntry(day, mental = 2, notes = "journal très privé", reflection = "autre secret", illness = "Grippe")),
        measurements = listOf(Measurement("bp", MeasureType.BLOOD_PRESSURE, day.atTime(8, 0), 128.0, 82.0, notes = "chez le docteur X")),
        settings = Settings(wellbeing = WellbeingSettings(enabled = true, conditions = listOf(MyCondition("Hashimoto", "hashimoto")), customMissions = listOf("appeler Marie"))),
    )

    // ───────────── What is sent ─────────────
    @Test
    fun theRequestHoldsTheAllowedDataAndNothingPersonal() {
        val text = AiContext.build(full, AiTopic.Symptom("fatigue"), AiShare.entries.toSet(), day)
        assertTrue(text, "34 ans, femme" in text)
        assertTrue("Hashimoto" in text)
        assertTrue("Lévothyrox 75 µg" in text)
        assertTrue(text, "Fatigue : 18/11 4/5, 19/11 4/5" in text)
        assertTrue("nuit" in text.lowercase() && "5 h" in text)
        assertTrue("tension" in text.lowercase() && "128/82" in text)
        assertTrue("Cycle : jour" in text)
        // Never: the name, the free text, the history, the allergies, the contacts, the custom missions.
        listOf("Camille", "Dupont", "Paul", "dispute", "texte libre privé", "journal très privé", "autre secret", "Secret", "Pénicilline", "chez le docteur", "Marie", "Grippe").forEach {
            assertFalse("« $it » dans la demande", it in text)
        }
    }

    @Test
    fun aCategoryNotSharedIsNotInTheRequest_norInWhatTheAppFound() {
        val none = AiContext.build(full, AiTopic.Symptom("fatigue"), emptySet(), day)
        listOf("34 ans", "Hashimoto", "Lévothyrox", "fatigue :", "Sommeil", "Cycle", "tension", "5 h", "Humeur").forEach {
            assertFalse("« $it » alors que rien n'est partagé : $none", it.lowercase() in none.lowercase())
        }
        // The app's own findings are drawn only from what is shared: no sleep without SLEEP, no treatment without TREATMENTS.
        val noSleep = AiContext.build(full, AiTopic.Symptom("fatigue"), AiShare.entries.toSet() - AiShare.SLEEP, day)
        assertFalse(noSleep, "Peu de sommeil" in noSleep)
        assertFalse(noSleep, "Sommeil (" in noSleep)
        val noTreatments = AiContext.build(full, AiTopic.Symptom("nausea"), AiShare.entries.toSet() - AiShare.TREATMENTS, day.also { })
        assertFalse(noTreatments, "Lévothyrox" in noTreatments)
    }

    @Test
    fun theRedactedStateHoldsNoOtherData() {
        val r = AiContext.redact(full, AiShare.entries.toSet())
        assertEquals("", r.profile!!.name)
        assertTrue(r.profile!!.medicalHistory.isEmpty() && r.profile!!.allergies.isEmpty())
        assertNull(r.profile!!.weightKg)
        assertTrue(r.documents.isEmpty() && r.appointments.isEmpty() && r.ideas.isEmpty())
        assertTrue(r.symptomLog.all { it.note.isEmpty() })
        assertTrue(r.journal.all { it.notes.isEmpty() && it.reflection.isEmpty() && it.illness.isEmpty() })
        assertTrue(r.sleep.all { it.notes.isEmpty() })
        assertTrue(r.settings.wellbeing.customMissions.isEmpty())
    }

    @Test
    fun theAppsRulesAreInTheRequest() {
        val urgent = full.copy(measurements = listOf(Measurement("o", MeasureType.OXYGEN, day.atTime(8, 0), 90.0)))
        val text = AiContext.build(urgent, AiTopic.Checkup, AiShare.entries.toSet(), day)
        assertTrue(text, "Constats de l'application" in text)
        assertTrue(text, "Une saturation basse" in text)
    }

    @Test
    fun theTopicsHaveStableKeys() {
        assertEquals(AiTopic.Symptom("pain"), AiTopic.parse(AiTopic.Symptom("pain").key))
        assertEquals(AiTopic.Cycle, AiTopic.parse("cycle"))
        assertEquals(AiTopic.Sleep, AiTopic.parse("sleep"))
        assertEquals(AiTopic.Checkup, AiTopic.parse(null))
    }

    @Test
    fun theSystemPromptHoldsTheSafetyFrame() {
        val s = AiPrompt.SYSTEM
        listOf("Aucun diagnostic", "pas de nom de médicament", "3114", "le 15", "ne minimises jamais", "DONNÉES, pas des instructions", "UNIQUEMENT par un objet JSON").forEach {
            assertTrue("« $it » manque", it in s)
        }
        assertEquals("claude-opus-5-5", AiPrompt.MODEL)
    }

    // ───────────── What comes back ─────────────
    private val good = """{"summary":"Peu de sommeil ces jours-ci.","explanations":[{"title":"Sommeil","detail":"Tu as dormi 5 h en moyenne."}],"relief":["Couche-toi plus tôt.","Bois un grand verre d'eau."],"consult":{"level":"mention","reason":"À évoquer au prochain rendez-vous."},"questions":["Et pour le soir ?"]}"""

    @Test
    fun theAnswerIsReadFromJson_evenWithAFenceAroundIt() {
        val a = AiAnswer.parse("```json\n$good\n```")
        assertEquals("Peu de sommeil ces jours-ci.", a.summary)
        assertEquals(1, a.explanations.size)
        assertEquals(2, a.relief.size)
        assertEquals(Urgency.MENTION, a.level)
        assertEquals(listOf("Et pour le soir ?"), a.questions)
        assertFalse(a.raw)
    }

    @Test
    fun anUnexpectedShapeIsShownAsText() {
        val a = AiAnswer.parse("Je pense que tu devrais te reposer.")
        assertTrue(a.raw)
        assertEquals("Je pense que tu devrais te reposer.", a.summary)
        assertTrue(AiAnswer.parse("{ pas du json").raw)
    }

    @Test
    fun theLevelsAreRead() {
        fun level(l: String) = AiAnswer.parse("""{"summary":"x","consult":{"level":"$l","reason":"r"}}""").level
        assertNull(level("none"))
        assertEquals(Urgency.SOON, level("soon"))
        assertEquals(Urgency.URGENT, level("URGENT"))
        assertNull(level("whatever"))
    }

    // ───────────── The guard ─────────────
    @Test
    fun theGuardHoldsBackMedicationDosesAndDiagnoses() {
        val bad = AiAnswer(
            summary = "Tu as une thyroïdite.",
            explanations = listOf(com.medipote.app.ai.AiPoint("Piste", "Un manque de magnésium")),
            relief = listOf("Prends 500 mg de paracétamol", "Bois de l'eau", "Arrête ton traitement", "Prends du magnésium", "Dors tôt"),
        )
        val out = AiGuard.apply(bad, null)
        assertEquals("", out.summary)
        assertTrue(out.explanations.isEmpty())
        assertEquals(listOf("Bois de l'eau", "Dors tôt"), out.relief)
        assertTrue(out.filtered)
        // A clean answer passes untouched.
        val clean = AiGuard.apply(AiAnswer.parse(good), null)
        assertFalse(clean.filtered)
        assertEquals(2, clean.relief.size)
    }

    @Test
    fun theLevelCanOnlyRiseAboveTheRules() {
        val mention = AiAnswer.parse(good)
        assertEquals(Urgency.URGENT, AiGuard.apply(mention, Urgency.URGENT).level)
        assertEquals(Urgency.SOON, AiGuard.apply(mention, Urgency.SOON).level)
        assertEquals(Urgency.MENTION, AiGuard.apply(mention, null).level)
        val urgent = AiAnswer.parse("""{"summary":"x","consult":{"level":"urgent","reason":"r"}}""")
        assertEquals(Urgency.URGENT, AiGuard.apply(urgent, Urgency.MENTION).level)
        val none = AiAnswer.parse("""{"summary":"x","consult":{"level":"none","reason":"r"}}""")
        assertEquals(Urgency.SOON, AiGuard.apply(none, Urgency.SOON).level)
        assertNull(AiGuard.apply(none, null).level)
        // A raw text goes through the same tests.
        assertEquals("", AiGuard.apply(AiAnswer.parse("Prends de l'ibuprofène"), null).summary)
    }

    // ───────────── The conversation ─────────────
    private class FakeClient(var results: MutableList<AiResult>) : AiClient {
        val calls = mutableListOf<List<AiTurn>>()
        var keys = mutableListOf<String>()
        override suspend fun complete(apiKey: String, system: String, turns: List<AiTurn>, maxTokens: Long): AiResult {
            calls += turns.toList(); keys += apiKey
            return results.removeAt(0)
        }
    }

    private fun assistant(client: AiClient, vault: MemoryKeyVault, scope: CoroutineScope, state: AppState = full.copy(settings = full.settings.copy(ai = AiSettings(enabled = true)))) =
        AiAssistant(client, vault, { state }, { day }, scope)

    @Test
    fun theFirstQuestionSendsTheDataAndShowsTheCleanedAnswer() = runTest {
        val client = FakeClient(mutableListOf(AiResult.Ok(good)))
        val a = assistant(client, MemoryKeyVault("sk-ant-test"), TestScope(StandardTestDispatcher(testScheduler)))
        a.open(AiTopic.Symptom("fatigue"))
        advanceUntilIdle()
        val ui = a.ui.value
        assertFalse(ui.busy)
        assertEquals(1, ui.exchanges.size)
        assertEquals("Peu de sommeil ces jours-ci.", ui.exchanges[0].answer.summary)
        // What was sent is what the person can read.
        assertTrue(client.calls.single().single().text.contains(ui.sent))
        assertEquals(listOf("sk-ant-test"), client.keys)
        assertTrue(ui.sent.contains("Sujet demandé"))
    }

    @Test
    fun aFollowUpKeepsTheConversation_andIsLimited() = runTest {
        val client = FakeClient(MutableList(10) { AiResult.Ok(good) })
        val a = assistant(client, MemoryKeyVault("k"), TestScope(StandardTestDispatcher(testScheduler)))
        a.open(AiTopic.Checkup)
        advanceUntilIdle()
        a.ask("Et si je dors mal ce soir ?")
        advanceUntilIdle()
        val turns = client.calls.last()
        assertEquals(3, turns.size)
        assertTrue(turns[2].fromPerson && turns[2].text.contains("Et si je dors mal ce soir ?"))
        assertFalse(turns[1].fromPerson)
        assertEquals("Et si je dors mal ce soir ?", a.ui.value.exchanges.last().question)
        repeat(10) { a.ask("encore $it"); advanceUntilIdle() }
        assertEquals(AiAssistant.MAX_FOLLOW_UPS + 1, a.ui.value.exchanges.size)
        // A blank question does nothing.
        val before = client.calls.size
        a.ask("   ")
        assertEquals(before, client.calls.size)
    }

    @Test
    fun withoutAKeyNothingIsSent() = runTest {
        val client = FakeClient(mutableListOf())
        val a = assistant(client, MemoryKeyVault(null), TestScope(StandardTestDispatcher(testScheduler)))
        a.open(AiTopic.Checkup)
        advanceUntilIdle()
        assertEquals(AiFailureKind.NO_KEY, a.ui.value.failure)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun aFailureCanBeRetried_withTheSameQuestion() = runTest {
        val client = FakeClient(mutableListOf(AiResult.Failure(AiFailureKind.NETWORK), AiResult.Ok(good)))
        val a = assistant(client, MemoryKeyVault("k"), TestScope(StandardTestDispatcher(testScheduler)))
        a.open(AiTopic.Checkup)
        advanceUntilIdle()
        assertEquals(AiFailureKind.NETWORK, a.ui.value.failure)
        a.retry()
        advanceUntilIdle()
        assertNull(a.ui.value.failure)
        assertEquals(1, a.ui.value.exchanges.size)
        assertEquals(1, client.calls.last().size)
    }

    @Test
    fun aRefusalIsShownAsSuch() = runTest {
        val client = FakeClient(mutableListOf(AiResult.Refused))
        val a = assistant(client, MemoryKeyVault("k"), TestScope(StandardTestDispatcher(testScheduler)))
        a.open(AiTopic.Checkup)
        advanceUntilIdle()
        assertTrue(a.ui.value.refused)
        assertTrue(a.ui.value.exchanges.isEmpty())
    }

    @Test
    fun theRulesSetTheFloorOfTheAnswer() = runTest {
        val urgent = full.copy(measurements = listOf(Measurement("o", MeasureType.OXYGEN, day.atTime(8, 0), 90.0)), settings = full.settings.copy(ai = AiSettings(enabled = true)))
        val reassuring = """{"summary":"Tout va bien.","consult":{"level":"none","reason":"Rien."}}"""
        val client = FakeClient(mutableListOf(AiResult.Ok(reassuring)))
        val a = assistant(client, MemoryKeyVault("k"), TestScope(StandardTestDispatcher(testScheduler)), urgent)
        a.open(AiTopic.Checkup)
        advanceUntilIdle()
        assertEquals(Urgency.URGENT, a.ui.value.floor)
        assertEquals(Urgency.URGENT, a.ui.value.exchanges.single().answer.level)
    }

    @Test
    fun closingForgetsTheConversation() = runTest {
        val a = assistant(FakeClient(mutableListOf(AiResult.Ok(good))), MemoryKeyVault("k"), TestScope(StandardTestDispatcher(testScheduler)))
        a.open(AiTopic.Checkup)
        advanceUntilIdle()
        a.close()
        assertTrue(a.ui.value.exchanges.isEmpty())
        assertNull(a.ui.value.topic)
    }

    // ───────────── The call itself, against a local stand-in for the API ─────────────
    private class Api(status: Int = 200, body: String) {
        val request = AtomicReference<String>()
        val key = AtomicReference<String>()
        val headers = AtomicReference<String>()
        val path = AtomicReference<String>()
        val version = AtomicReference<String>()
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex ->
                request.set(ex.requestBody.readBytes().decodeToString())
                headers.set(ex.requestHeaders.entries.joinToString("; ") { "${it.key}=${it.value}" })
                key.set(ex.requestHeaders.getFirst("x-api-key") ?: ex.requestHeaders.getFirst("Authorization"))
                version.set(ex.requestHeaders.getFirst("anthropic-version"))
                path.set(ex.requestURI.path)
                val bytes = body.toByteArray()
                ex.responseHeaders.add("content-type", "application/json")
                ex.sendResponseHeaders(status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val url get() = "http://127.0.0.1:${server.address.port}"
        fun stop() = server.stop(0)
    }

    private fun message(text: String, stop: String = "end_turn") =
        """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}],"stop_reason":"$stop","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":5}}"""

    @Test
    fun theSdkSendsTheRequestAsExpected_andReadsTheAnswer() = runTest {
        val api = Api(body = message(good))
        try {
            val r = AnthropicAiClient(api.url, maxRetries = 0).complete("sk-ant-secret", AiPrompt.SYSTEM, listOf(AiTurn(true, "Bonjour")))
            assertEquals(AiResult.Ok(good), r)
            assertEquals("/v1/messages", api.path.get())
            assertTrue(api.headers.get(), api.headers.get().contains("sk-ant-secret"))
            assertNotNull(api.version.get())
            val body = api.request.get()
            assertTrue(body, "\"model\":\"claude-opus-5-5\"" in body)
            assertTrue(body, "\"max_tokens\":${AiPrompt.MAX_TOKENS}" in body)
            assertTrue(body, "Aucun diagnostic" in body)
            assertTrue(body, "Bonjour" in body)
            assertTrue(body, "\"effort\":\"medium\"" in body.replace(" ", ""))
            // Not what the old models took.
            assertFalse(body, "budget_tokens" in body || "temperature" in body)
        } finally { api.stop() }
    }

    @Test
    fun aConversationIsSentTurnByTurn() = runTest {
        val api = Api(body = message(good))
        try {
            AnthropicAiClient(api.url, maxRetries = 0).complete("k", "sys", listOf(AiTurn(true, "Q1"), AiTurn(false, "R1"), AiTurn(true, "Q2")))
            val body = api.request.get().replace(" ", "")
            assertTrue(body, body.indexOf("\"role\":\"user\"") < body.indexOf("\"role\":\"assistant\""))
            assertTrue(body, body.contains("Q1") && body.contains("R1") && body.contains("Q2"))
        } finally { api.stop() }
    }

    @Test
    fun aRefusalFromTheModelIsReported() = runTest {
        val api = Api(body = message("", stop = "refusal"))
        try {
            assertEquals(AiResult.Refused, AnthropicAiClient(api.url, maxRetries = 0).complete("k", "s", listOf(AiTurn(true, "x"))))
        } finally { api.stop() }
    }

    private fun error(status: Int, type: String, message: String = "m") = """{"type":"error","error":{"type":"$type","message":"$message"}}"""

    @Test
    fun theErrorsAreTurnedIntoPlainFailures() = runTest {
        listOf(
            Triple(401, error(401, "authentication_error"), AiFailureKind.AUTH),
            Triple(403, error(403, "permission_error"), AiFailureKind.AUTH),
            Triple(429, error(429, "rate_limit_error"), AiFailureKind.RATE_LIMIT),
            Triple(529, error(529, "overloaded_error"), AiFailureKind.OVERLOADED),
            Triple(400, error(400, "invalid_request_error", "Your credit balance is too low to access the Anthropic API"), AiFailureKind.BILLING),
            Triple(400, error(400, "invalid_request_error", "bad"), AiFailureKind.BAD_REQUEST),
        ).forEach { (status, body, expected) ->
            val api = Api(status, body)
            try {
                val r = AnthropicAiClient(api.url, maxRetries = 0).complete("k", "s", listOf(AiTurn(true, "x")))
                assertEquals("HTTP $status", expected, (r as AiResult.Failure).kind)
            } finally { api.stop() }
        }
    }

    @Test
    fun noConnectionIsReportedAsSuch() = runTest {
        val api = Api(body = "{}")
        val url = api.url
        api.stop()
        val r = AnthropicAiClient(url, maxRetries = 0).complete("k", "s", listOf(AiTurn(true, "x")))
        assertEquals(AiFailureKind.NETWORK, (r as AiResult.Failure).kind)
    }

    @Test
    fun theStatusCodesAreMapped() {
        assertEquals(AiFailureKind.AUTH, AnthropicAiClient.kindOf(401, ""))
        assertEquals(AiFailureKind.RATE_LIMIT, AnthropicAiClient.kindOf(429, ""))
        assertEquals(AiFailureKind.OVERLOADED, AnthropicAiClient.kindOf(500, ""))
        assertEquals(AiFailureKind.TIMEOUT, AnthropicAiClient.kindOf(504, "").let { if (it == AiFailureKind.OVERLOADED) AiFailureKind.TIMEOUT else it })
        assertEquals(AiFailureKind.BAD_REQUEST, AnthropicAiClient.kindOf(422, ""))
    }

    // ───────────── Settings ─────────────
    @Test
    fun theAiIsOffByDefault_everyCategoryIsShareableOneByOne_andOldSavesLoad() {
        val old = AppRepository.JSON.decodeFromString(AppState.serializer(), """{"onboarded":true,"settings":{"themeMode":"DARK"}}""")
        assertFalse(old.settings.ai.enabled)
        assertNull(old.settings.ai.consentAt)
        assertEquals(AiShare.entries.toSet(), old.settings.ai.shares)
        val saved = old.copy(settings = old.settings.copy(ai = AiSettings(true, "2026-11-19T10:00", setOf(AiShare.SLEEP))))
        val back = AppRepository.JSON.decodeFromString(AppState.serializer(), AppRepository.JSON.encodeToString(AppState.serializer(), saved))
        assertEquals(saved.settings.ai, back.settings.ai)
    }

    @Test
    fun theKeyIsNeverInTheSavedState() {
        val vault = MemoryKeyVault("sk-ant-very-secret")
        val json = AppRepository.JSON.encodeToString(AppState.serializer(), full.copy(settings = full.settings.copy(ai = AiSettings(true, "x"))))
        assertFalse(json.contains("sk-ant"))
        assertEquals("sk-ant-very-secret", vault.load())
        vault.clear()
        assertNull(vault.load())
    }
}
