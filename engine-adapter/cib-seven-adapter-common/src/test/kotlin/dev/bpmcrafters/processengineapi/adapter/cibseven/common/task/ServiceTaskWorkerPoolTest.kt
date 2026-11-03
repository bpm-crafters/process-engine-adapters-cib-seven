package dev.bpmcrafters.processengineapi.adapter.cibseven.common.task

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class ServiceTaskWorkerPoolTest {

  private val executor: ExecutorService = Executors.newFixedThreadPool(4)
  private val release = CountDownLatch(1)

  @AfterEach
  fun shutdown() {
    release.countDown()
    executor.shutdownNow()
  }

  @Test
  fun `reserves at most the free capacity without blocking`() {
    val pool = ServiceTaskWorkerPool(executor, maxConcurrentTasks = 2)

    pool.reserve(5).use { reservation ->
      assertThat(reservation.slots).isEqualTo(2)
      assertThat(pool.reserve(5).use { it.slots }).isZero()
    }
  }

  @Test
  fun `returns unused slots when the reservation is closed`() {
    val pool = ServiceTaskWorkerPool(executor, maxConcurrentTasks = 3)

    pool.reserve(3).use { it.submit("task-1") { release.await() } }

    assertThat(pool.freeSlotCount).isEqualTo(2)
    assertThat(pool.runningTaskCount).isEqualTo(1)
  }

  @Test
  fun `returns the slot when the task finishes, also after an exception`() {
    val pool = ServiceTaskWorkerPool(executor, maxConcurrentTasks = 2)

    pool.reserve(2).use {
      it.submit("task-1") { }
      it.submit("task-2") { throw IllegalStateException("boom") }
    }

    await untilAsserted { assertThat(pool.freeSlotCount).isEqualTo(2) }
    assertThat(pool.runningTaskCount).isZero()
  }

  @Test
  fun `does not start a task that is already running`() {
    val pool = ServiceTaskWorkerPool(executor, maxConcurrentTasks = 2)
    pool.reserve(1).use { it.submit("task-1") { release.await() } }

    val submittedAgain = pool.reserve(1).use { it.submit("task-1") { } }

    assertThat(submittedAgain).isFalse()
    assertThat(pool.isRunning("task-1")).isTrue()
    assertThat(pool.freeSlotCount).isEqualTo(1)
  }

  @Test
  fun `returns the slot when the executor rejects the task`() {
    val pool = ServiceTaskWorkerPool({ throw RejectedExecutionException("full") }, maxConcurrentTasks = 1)

    val submitted = pool.reserve(1).use { it.submit("task-1") { } }

    assertThat(submitted).isFalse()
    assertThat(pool.freeSlotCount).isEqualTo(1)
    assertThat(pool.isRunning("task-1")).isFalse()
  }

  @Test
  fun `offers a pool of platform threads limited to its thread count`() {
    ServiceTaskWorkerPool.withPlatformThreads(3).use { pool ->
      assertThat(pool.maxConcurrentTasks).isEqualTo(3)
      assertThat(pool.reserve().use { it.slots }).isEqualTo(3)
    }
  }

  @Test
  fun `shuts down its executor when closed`() {
    val ownExecutor = Executors.newSingleThreadExecutor()

    ServiceTaskWorkerPool(ownExecutor, maxConcurrentTasks = 1).close()

    assertThat(ownExecutor.isShutdown).isTrue()
  }

  @Test
  fun `runs on a virtual thread executor`() {
    val virtualThreads = runCatching { Executors::class.java.getMethod("newVirtualThreadPerTaskExecutor").invoke(null) as Executor }.getOrNull() ?: return
    val pool = ServiceTaskWorkerPool(virtualThreads, maxConcurrentTasks = 1)
    val threadClassNames = mutableListOf<String>()

    pool.reserve(1).use { it.submit("task-1") { threadClassNames.add(Thread.currentThread().javaClass.name) } }

    await untilAsserted { assertThat(threadClassNames).singleElement().asString().contains("Virtual") }
  }
}
