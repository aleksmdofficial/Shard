/*
 * This file is part of Shard - https://github.com/KaelusAI/Shard
 * Copyright (C) 2026 KaelusAI
 *
 * Shard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package ac.shard.presentation

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CompletionTest {
  @Test
  fun `timeout disconnect and retirement continue the punishment only once`() {
    val calls = AtomicInteger()
    val completion = Completion { calls.incrementAndGet() }
    val start = CountDownLatch(1)
    val workers =
      List(12) {
        thread {
          start.await()
          repeat(100) { completion.finish() }
        }
      }
    start.countDown()
    workers.forEach { it.join() }
    assertEquals(1, calls.get())
  }
}
