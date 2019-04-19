# svgtools
A no-dependency kotlin library for parsing SVG paths

## Usage
```kotlin
import com.plangrid.svgtools.parsePath
import com.plangrid.svgtools.Command
import com.plangrid.svgtools.Line
import com.plangrid.svgtools.Point

import kotlin.test.assertEquals

fun checkPath() {
  val path = parsePath("M1 1 L 2 2")
  val expectedPath = listOf<Command>(Line(Point(1f, 1f), Point(2f, 2f)))
  assertEquals(expectedPath, path)
}


```
