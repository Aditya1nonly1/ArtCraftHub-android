package io.github.l41n_pr0t0.artcrafthub.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun headingsBulletsAndParagraphs() = assertEquals(
        listOf(
            NoteLine.Heading("What's Changed"),
            NoteLine.Bullet("Faster export"),
            NoteLine.Para("See #12"),
        ),
        markdownLines("## What's Changed\n\n- **Faster** export\nSee [#12](https://github.com/x/y/pull/12)\n"),
    )

    @Test fun blankNotesHaveNoLines() = assertEquals(emptyList<NoteLine>(), markdownLines("  \n\n"))
}
