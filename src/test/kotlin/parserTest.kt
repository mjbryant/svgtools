import com.plangrid.svgtools.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

fun assertFoundFloat(expected: String?, input: String) {
    val match = FLOAT_RE.find(input)
    if (match == null) {
        assertNull(expected)
    } else {
        val found = match.groups[0]?.value
        assertEquals(expected, found)
    }
}

class TestCommand {
    @Test fun testFloatRegex() {
        assertFoundFloat("100", "100")
        assertFoundFloat("100", " 100L")
        assertFoundFloat("1", " 1")
        assertFoundFloat("1", " 1 ")
        assertFoundFloat("100.5", "100.5")
        assertFoundFloat("+1", "+1")
        assertFoundFloat("+1.5", "+1.5")
        assertFoundFloat("-5", "  -5 ")
        assertFoundFloat("-0.5", "-0.5")
        assertFoundFloat("10", "A10B")
        assertFoundFloat("1e10", "1e10")
        assertFoundFloat("1e-10", " 1e-10")
        assertFoundFloat(null, "abc")
    }

    @Test fun testSplitCommand() {
        assertEquals(listOf("M", "1", "1"), splitCommand("M1 1"))
        assertEquals(listOf("m", "100", "-1"), splitCommand("m 100  -1 "))
        assertEquals(listOf("m", "+100", "-1"), splitCommand(" m +100  -1 "))
        assertEquals(listOf("Z"), splitCommand("Z"))
        assertEquals(listOf("Z"), splitCommand("Z "))
        assertEquals(listOf("Z"), splitCommand(" Z "))
        assertEquals(listOf("M", "1", "1"), splitCommand("M1,1"))
        assertEquals(listOf("M", "1", "1"), splitCommand("M 1, 1"))
    }

    @Test fun testParseAbsoluteLines() {
        val commands = parsePath("""
            M1 1 L1 2 L3 4 Z
            M2.5 3.5 L4.5 5.5 H1
        """.trimIndent())
        val expectedCommands = listOf<Command>(
                Line(Point(1f, 1f), Point(1f, 2f)),
                Line(Point(1f, 2f), Point(3f, 4f)),
                Line(Point(3f, 4f), Point(1f, 1f)),
                Line(Point(2.5f, 3.5f), Point(4.5f, 5.5f)),
                Line(Point(4.5f, 5.5f), Point(1f, 5.5f))
        )
        assertEquals(expectedCommands, commands)
    }

    @Test fun testParseRelativeLines() {
        val commands = parsePath("""
            l1 0 l0 1 z
            m-1 -2 m 0 1 h 5 v 6
        """.trimIndent())
        val expectedCommands = listOf<Command>(
                Line(Point(0f, 0f), Point(1f, 0f)),
                Line(Point(1f, 0f), Point(1f, 1f)),
                Line(Point(1f, 1f), Point(0f, 0f)),
                Line(Point(-1f, -1f), Point(4f, -1f)),
                Line(Point(4f, -1f), Point(4f, 5f))
        )
        assertEquals(expectedCommands, commands)
    }

    @Test fun testConsistentParsing() {
        assertEquals(
                parsePath("M 100 100 L 200 200"),
                parsePath("M100 100L200 200")
        )

        assertEquals(
                parsePath("M 100 200 L 200 100 L -100 -200"),
                parsePath("M 100 200 L 200 100 -100 -200")
        )
    }

    @Test fun testMalformedZPath() {
        assertFailsWith(MalformedPathException::class) {
            parsePath("Z 1 1")
        }
    }

    @Test fun testSvgExamples() {
        // Examples from the SVG spec, and taken from the svgpathtools library
        var commands = parsePath("M 100 100 L 300 100 L 200 300 z")
        var expectedCommands = listOf<Command>(
                Line(Point(100f, 100f), Point(300f, 100f)),
                Line(Point(300f, 100f), Point(200f, 300f)),
                Line(Point(200f, 300f), Point(100f, 100f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M 0 0 L 50 20 M 100 100 L 300 100 L 200 300 z")
        expectedCommands = listOf<Command>(
                Line(Point(0f, 0f), Point(50f, 20f)),
                Line(Point(100f, 100f), Point(300f, 100f)),
                Line(Point(300f, 100f), Point(200f, 300f)),
                Line(Point(200f, 300f), Point(100f, 100f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("""M100,200 C100,100 250,100 250,200
                              S400,300 400,200""")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(100f, 200f),
                        Point(100f, 100f),
                        Point(250f, 100f),
                        Point(250f, 200f)),
                CubicBezier(Point(250f, 200f),
                        Point(250f, 300f),
                        Point(400f, 300f),
                        Point(400f, 200f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M100,200 C100,100 400,100 400,200")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(100f, 200f),
                        Point(100f, 100f),
                        Point(400f, 100f),
                        Point(400f, 200f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M100,500 C25,400 475,400 400,500")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(100f, 500f),
                        Point(25f, 400f),
                        Point(475f, 400f),
                        Point(400f, 500f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M100,800 C175,700 325,700 400,800")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(100f, 800f),
                        Point(175f, 700f),
                        Point(325f, 700f),
                        Point(400f, 800f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M600,200 C675,100 975,100 900,200")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(600f, 200f),
                        Point(675f, 100f),
                        Point(975f, 100f),
                        Point(900f, 200f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M600,500 C600,350 900,650 900,500")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(600f, 500f),
                        Point(600f, 350f),
                        Point(900f, 650f),
                        Point(900f, 500f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("""M600,800 C625,700 725,700 750,800
                              S875,900 900,800""")
        expectedCommands = listOf<Command>(
                CubicBezier(Point(600f, 800f),
                        Point(625f, 700f),
                        Point(725f, 700f),
                        Point(750f, 800f)),
                CubicBezier(Point(750f, 800f),
                        Point(775f, 900f),
                        Point(875f, 900f),
                        Point(900f, 800f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M200,300 Q400,50 600,300 T1000,300")
        expectedCommands = listOf<Command>(
                QuadraticBezier(Point(200f, 300f),
                        Point(400f, 50f),
                        Point(600f, 300f)),
                QuadraticBezier(Point(600f, 300f),
                        Point(800f, 550f),
                        Point(1000f, 300f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M300,200 h-150 a150,150 0 1,0 150,-150 z")
        expectedCommands = listOf<Command>(
                Line(Point(300f, 200f), Point(150f, 200f)),
                Arc(
                        Point(150f, 200f),
                        Point(150f, 150f),
                        0f, true, false,
                        Point(300f, 50f)),
                Line(Point(300f, 50f), Point(300f, 200f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("M275,175 v-150 a150,150 0 0,0 -150,150 z")
        expectedCommands = listOf<Command>(
                Line(Point(275f, 175f), Point(275f, 25f)),
                Arc(
                        Point(275f, 25f),
                        Point(150f, 150f),
                        0f, false, false,
                        Point(125f, 175f)),
                Line(Point(125f, 175f), Point(275f, 175f))
        )
        assertEquals(expectedCommands, commands)

        commands = parsePath("""M600,350 l 50,-25
                              a25,25 -30 0,1 50,-25 l 50,-25
                              a25,50 -30 0,1 50,-25 l 50,-25
                              a25,75 -30 0,1 50,-25 l 50,-25
                              a25,100 -30 0,1 50,-25 l 50,-25""")
        expectedCommands = listOf<Command>(
                Line(Point(600f, 350f), Point(650f, 325f)),
                Arc(
                        Point(650f, 325f),
                        Point(25f, 25f),
                        -30f, false, true,
                        Point(700f, 300f)),
                Line(Point(700f, 300f), Point(750f, 275f)),
                Arc(
                        Point(750f, 275f),
                        Point(25f, 50f),
                        -30f, false, true,
                        Point(800f, 250f)),
                Line(Point(800f, 250f), Point(850f, 225f)),
                Arc(
                        Point(850f, 225f),
                        Point(25f, 75f),
                        -30f, false, true,
                        Point(900f, 200f)),
                Line(Point(900f, 200f), Point(950f, 175f)),
                Arc(
                        Point(950f, 175f),
                        Point(25f, 100f),
                        -30f, false, true,
                        Point(1000f, 150f)),
                Line(Point(1000f, 150f), Point(1050f, 125f))
        )
        assertEquals(expectedCommands, commands)

    }
}