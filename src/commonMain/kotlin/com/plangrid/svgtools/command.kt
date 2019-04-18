package com.plangrid.svgtools

interface Command

data class Point(val x: Float, val y: Float) {
    operator fun plus(p: Point): Point {
        return Point(this.x + p.x, this.y + p.y)
    }

    operator fun minus(p: Point): Point {
        return Point(this.x - p.x, this.y - p.y)
    }
}

data class Line(val start: Point, val end: Point) : Command

data class QuadraticBezier(
        val start: Point,
        val control: Point,
        val end: Point
) : Command

data class CubicBezier(
        val start: Point,
        val control1: Point,
        val control2: Point,
        val end: Point
) : Command

data class Arc(
        val start: Point,
        val radii: Point,
        val rotation: Float,
        val largeArc: Boolean,
        val sweep: Boolean,
        val end: Point
) : Command
