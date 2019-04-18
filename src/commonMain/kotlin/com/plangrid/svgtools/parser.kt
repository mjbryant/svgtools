package com.plangrid.svgtools

import kotlin.math.abs
import kotlin.text.Regex

val COMMANDS = hashSetOf(
        'M', 'm', 'Z', 'z', 'L', 'l', 'H', 'h', 'V', 'v',
        'C', 'c', 'S', 's', 'Q', 'q', 'T', 't', 'A', 'a'
)
val STR_COMMANDS = hashSetOf(
        "M", "m", "Z", "z", "L", "l", "H", "h", "V", "v",
        "C", "c", "S", "s", "Q", "q", "T", "t", "A", "a"
)
val ABSOLUTE_COMMANDS = hashSetOf(
        "M", "Z", "L", "H", "V", "C", "S", "Q", "T", "A"
)
val FLOAT_RE = Regex("[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?")

class MalformedPathException(message: String) : Exception(message)

// For code that should be unreachable, but we need a statement
class UnreachableException(message: String = "") : Exception(message)

/**
 * Parse an SVG path string into a list of SVG path commands. A lot of this
 * was ported from the svgpathtools python library.
 */
fun parsePath(d: String): List<Command> {
    val elements = tokenizePath(d)
    val segments = mutableListOf<Command>()

    var i = -1
    var command: String? = null
    var lastCommand: String? = null
    var absolute = false
    var current = Point(0f, 0f)
    var start = Point(0f, 0f)

    while (i < elements.size - 1) {
        if (STR_COMMANDS.contains(elements[i + 1])) {
            // New command
            lastCommand = command
            command = elements[++i]
            absolute = ABSOLUTE_COMMANDS.contains(command)
            command = command.toUpperCase()
        } else {
            // Operands to an existing command
            if (command == null) {
                throw MalformedPathException("Illegal implicit command in $d")
            }
        }
        // Handle the current command, incrementing i according to the expected # of operands
        when (command) {
            "M" -> {
                // Move to
                val pos = Point(elements[++i].toFloat(), elements[++i].toFloat())
                current = when (absolute) {
                    true -> pos
                    false -> current + pos
                }
                // When move is called, reset start position for close path commands
                // See https://www.w3.org/TR/SVG/paths.html#PathDataClosePathCommand
                start = current

                // Implicit move commands are treated as line commands
                command = "L"
            }
            "Z" -> {
                // Close the path. If we're already at where we'd return to, do nothing.
                // Note that z and Z do the same thing.
                if (current != start) {
                    segments.add(Line(current, start))
                    current = start
                }
                // By setting command to null, we avoid an infinite loop for the invalid path 'Z 1 1'
                command = null
            }
            "L" -> {
                // Line
                var pos = Point(elements[++i].toFloat(), elements[++i].toFloat())
                if (!absolute) {
                    pos += current
                }
                segments.add(Line(current, pos))
                current = pos
            }
            "H" -> {
                // Horizontal line
                var pos = Point(elements[++i].toFloat(), current.y)
                if (!absolute) {
                    pos += Point(current.x, 0f)
                }
                segments.add(Line(current, pos))
                current = pos
            }
            "V" -> {
                // Vertical line
                var pos = Point(current.x, elements[++i].toFloat())
                if (!absolute) {
                    pos += Point(0f, current.y)
                }
                segments.add(Line(current, pos))
                current = pos
            }
            "C" -> {
                // Cubic bezier
                var control1 = Point(elements[++i].toFloat(), elements[++i].toFloat())
                var control2 = Point(elements[++i].toFloat(), elements[++i].toFloat())
                var end = Point(elements[++i].toFloat(), elements[++i].toFloat())
                if (!absolute) {
                    control1 += current
                    control2 += current
                    end += current
                }
                segments.add(CubicBezier(current, control1, control2, end))
                current = end
            }
            "S" -> {
                // Smooth cubic bezier curve. The first control point is the "reflection" of
                // the second control point in the previous segment
                val control1 = when (lastCommand) {
                    "C", "S" -> {
                        val lastSegment = segments[segments.size - 1]
                        if (lastSegment is CubicBezier) {
                            current + current - lastSegment.control2
                        } else {
                            throw UnreachableException()
                        }
                    }
                    else -> current
                }
                var control2 = Point(elements[++i].toFloat(), elements[++i].toFloat())
                var end = Point(elements[++i].toFloat(), elements[++i].toFloat())
                if (!absolute) {
                    control2 += current
                    end += current
                }
                segments.add(CubicBezier(current, control1, control2, end))
                current = end
            }
            "Q" -> {
                // Quadratic bezier
                var control = Point(elements[++i].toFloat(), elements[++i].toFloat())
                var end = Point(elements[++i].toFloat(), elements[++i].toFloat())
                if (!absolute) {
                    control += current
                    end += current
                }
                segments.add(QuadraticBezier(current, control, end))
                current = end
            }
            "T" -> {
                // Smooth quadratic bezier curve. The control point is the "reflection"
                // of the control point from the previous segment
                val control = when (lastCommand) {
                    "Q", "T" -> {
                        val lastSegment = segments[segments.size - 1]
                        if (lastSegment is QuadraticBezier) {
                            current + current - lastSegment.control
                        } else {
                            throw UnreachableException()
                        }
                    } else -> current
                }
                var end = Point(elements[++i].toFloat(), elements[++i].toFloat())
                if (!absolute) {
                    end += current
                }
                segments.add(QuadraticBezier(current, control, end))
                current = end
            }
            "A" -> {
                // Arc
                val radius = Point(elements[++i].toFloat(), elements[++i].toFloat())
                val rotation = elements[++i].toFloat()
                val arc = elements[++i] == "1"
                val sweep = elements[++i] == "1"
                var end = Point(elements[++i].toFloat(), elements[++i].toFloat())
                if (!absolute) {
                    end += current
                }
                segments.add(Arc(current, radius, rotation, arc, sweep, end))
                current = end
            }
            else -> throw UnreachableException()
        }
    }
    return segments
}

fun splitCommand(c: String): List<String> {
    val tokenRanges = mutableListOf<IntRange>()
    val floatMatches = FLOAT_RE.findAll(c)
    for (match in floatMatches) {
        tokenRanges.add(match.range)
    }
    if (tokenRanges.size > 0 && tokenRanges[0].first > 0) {
        tokenRanges.add(0, IntRange(0, tokenRanges[0].first - 1))
    } else if (tokenRanges.size == 0) {
        tokenRanges.add(IntRange(0, c.length - 1))
    }
    val tokens = mutableListOf<String>()
    for (range in tokenRanges) {
        tokens.add(c.substring(range).trim())
    }
    return tokens
}

fun tokenizePath(d: String): List<String> {
    // There's no regex split in kotlin that preserves the string being found,
    // so we have to operate on substrings. There may very well be a faster way
    val commandRanges = mutableListOf<IntRange>()
    var i = 0
    for (j in d.indices) {
        if (COMMANDS.contains(d[j])) {
            commandRanges.add(IntRange(i, j - 1))
            i = j
        }
    }
    commandRanges.add(IntRange(i, d.length - 1))
    // If the first character was a command, leave that 0-length range out
    if (commandRanges.size >= 1 && commandRanges[0] == IntRange(0, -1)) {
        commandRanges.removeAt(0)
    }
    val tokens = mutableListOf<String>()
    for (range in commandRanges) {
        tokens.addAll(splitCommand(d.substring(range)))
    }
    return tokens
}