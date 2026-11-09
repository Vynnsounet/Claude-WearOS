package com.claude.wear.core

import com.anthropic.errors.UnauthorizedException
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ClaudeChatTest {
    private data class Recorded(val body: String, val beta: String?, val apiKey: String?)

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<Recorded>()
    private val responses = ArrayDeque<Pair<Int, String>>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { exchange ->
            requests += Recorded(
                body = exchange.requestBody.readBytes().decodeToString(),
                beta = exchange.requestHeaders.getFirst("anthropic-beta"),
                apiKey = exchange.requestHeaders.getFirst("x-api-key")
                    ?: exchange.requestHeaders.getFirst("authorization")?.removePrefix("Bearer "),
            )
            val (status, payload) = synchronized(responses) { responses.removeFirst() }
            val bytes = payload.toByteArray()
            val type = if (status == 200) "text/event-stream" else "application/json"
            exchange.responseHeaders.add("Content-Type", type)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun chat(): ClaudeChat {
        val url = "http://127.0.0.1:${server.address.port}"
        return ClaudeChat(ClaudeChat.createClient(TEST_KEY, baseUrl = url))
    }

    private fun enqueue(status: Int, body: String) = synchronized(responses) { responses.addLast(status to body) }

    @Test
    fun streamsReplyAndKeepsHistory() = runBlocking {
        enqueue(200, sse(listOf("Paris ", "is the capital."), stopReason = "end_turn"))
        val chat = chat()

        val events = chat.send("Capital of France?").toList()

        assertEquals(
            listOf(
                ChatEvent.TextDelta("Paris "),
                ChatEvent.TextDelta("is the capital."),
                ChatEvent.Completed("Paris is the capital.", truncated = false),
            ),
            events,
        )
        assertEquals(2, chat.size)

        val request = requests.single()
        assertEquals(TEST_KEY, request.apiKey)
        assertTrue(request.beta.orEmpty().contains(ClaudeChat.SERVER_SIDE_FALLBACK_BETA))
        val body = request.body
        assertTrue(body, body.contains("\"model\":\"claude-opus-5-5\""))
        assertTrue(body, body.contains("\"stream\":true"))
        assertTrue(body, body.contains("\"fallbacks\":\"default\""))
        assertTrue(body, body.contains("\"effort\":\"low\""))
        assertTrue(body, body.contains("\"type\":\"adaptive\""))
        assertTrue(body, body.contains("smartwatch"))
    }

    @Test
    fun secondTurnReplaysEarlierAssistantMessage() = runBlocking {
        enqueue(200, sse(listOf("Hi there."), stopReason = "end_turn"))
        enqueue(200, sse(listOf("You said hello."), stopReason = "end_turn"))
        val chat = chat()

        chat.send("Hello").toList()
        chat.send("What did I say?").toList()

        assertEquals(4, chat.size)
        val second = requests[1].body
        assertTrue(second, second.contains("Hi there."))
        assertTrue(second, second.contains("What did I say?"))
        assertTrue(second, second.indexOf("Hello") < second.indexOf("Hi there."))
    }

    @Test
    fun refusalIsReportedAndNotKept() = runBlocking {
        enqueue(200, sse(listOf("Partial"), stopReason = "refusal"))
        val chat = chat()

        val events = chat.send("something declined").toList()

        assertTrue(events.last() is ChatEvent.Refused)
        assertEquals(0, chat.size)
    }

    @Test
    fun maxTokensMarksReplyTruncated() = runBlocking {
        enqueue(200, sse(listOf("A long answer"), stopReason = "max_tokens"))
        val chat = chat()

        val last = chat.send("Tell me everything").toList().last()

        assertEquals(ChatEvent.Completed("A long answer", truncated = true), last)
        assertEquals(2, chat.size)
    }

    @Test
    fun badKeyThrowsTypedErrorAndKeepsNothing() = runBlocking {
        enqueue(
            401,
            """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""",
        )
        val chat = chat()

        try {
            chat.send("Hello").toList()
            fail("expected UnauthorizedException")
        } catch (expected: UnauthorizedException) {
            // expected
        }
        assertEquals(0, chat.size)
    }

    @Test
    fun midReplyFallbackDropsDeclinedThinkingOnReplay() = runBlocking {
        enqueue(
            200,
            sseOf(
                listOf(
                    Block.Thinking("declined-sig"),
                    Block.Text(listOf("Partial ")),
                    Block.Fallback(from = "claude-opus-5-5", to = "claude-opus-5"),
                    Block.Text(listOf("rest.")),
                ),
                stopReason = "end_turn",
            ),
        )
        enqueue(200, sse(listOf("Sure."), stopReason = "end_turn"))
        val chat = chat()

        val first = chat.send("Question").toList()
        chat.send("Follow-up").toList()

        assertEquals(ChatEvent.Completed("Partial rest.", truncated = false), first.last())
        val replay = requests[1].body
        assertTrue(replay, replay.contains("Partial "))
        assertTrue(replay, replay.contains("rest."))
        assertTrue(replay, replay.contains("\"type\":\"fallback\""))
        assertTrue(replay, !replay.contains("declined-sig"))
    }

    @Test
    fun thinkingBlocksAreReplayedWithoutFallback() = runBlocking {
        enqueue(200, sse(listOf("Hi."), stopReason = "end_turn"))
        enqueue(200, sse(listOf("Again."), stopReason = "end_turn"))
        val chat = chat()

        chat.send("Hello").toList()
        chat.send("Again").toList()

        val replay = requests[1].body
        assertTrue(replay, replay.contains("\"signature\":\"sig123\""))
    }

    @Test
    fun resetClearsHistory() = runBlocking {
        enqueue(200, sse(listOf("Hi."), stopReason = "end_turn"))
        val chat = chat()
        chat.send("Hello").toList()

        chat.reset()

        assertEquals(0, chat.size)
    }

    private sealed interface Block {
        data class Thinking(val signature: String) : Block
        data class Text(val deltas: List<String>) : Block
        data class Fallback(val from: String, val to: String) : Block
    }

    private fun sse(deltas: List<String>, stopReason: String): String =
        sseOf(listOf(Block.Thinking("sig123"), Block.Text(deltas)), stopReason)

    private fun sseOf(blocks: List<Block>, stopReason: String): String = buildString {
        fun event(name: String, data: String) = append("event: $name\ndata: $data\n\n")
        fun start(index: Int, block: String) =
            event("content_block_start", """{"type":"content_block_start","index":$index,"content_block":$block}""")
        fun delta(index: Int, delta: String) =
            event("content_block_delta", """{"type":"content_block_delta","index":$index,"delta":$delta}""")

        event(
            "message_start",
            """{"type":"message_start","message":{"id":"msg_test","type":"message","role":"assistant",""" +
                """"model":"claude-opus-5-5","content":[],"stop_reason":null,"stop_sequence":null,""" +
                """"usage":{"input_tokens":12,"output_tokens":1}}}""",
        )
        blocks.forEachIndexed { index, block ->
            when (block) {
                is Block.Thinking -> {
                    start(index, """{"type":"thinking","thinking":"","signature":""}""")
                    delta(index, """{"type":"signature_delta","signature":"${block.signature}"}""")
                }
                is Block.Text -> {
                    start(index, """{"type":"text","text":""}""")
                    block.deltas.forEach { delta(index, """{"type":"text_delta","text":"$it"}""") }
                }
                is Block.Fallback -> start(
                    index,
                    """{"type":"fallback","from":{"model":"${block.from}"},"to":{"model":"${block.to}"}}""",
                )
            }
            event("content_block_stop", """{"type":"content_block_stop","index":$index}""")
        }
        event(
            "message_delta",
            """{"type":"message_delta","delta":{"stop_reason":"$stopReason","stop_sequence":null},""" +
                """"usage":{"output_tokens":20}}""",
        )
        event("message_stop", """{"type":"message_stop"}""")
    }

    private companion object {
        const val TEST_KEY = "sk-ant-api03-test"
    }
}
