package dev.bpmcrafters.processengineapi.adapter.cibseven.common.task

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory

private val logger = KotlinLogging.logger {}

/**
 * Runs service tasks on an [executor] and limits how many of them are locked and running at the same time.
 * Executor and limit are set together, so the limit always fits the executor:
 * - `ServiceTaskWorkerPool.withPlatformThreads(10)`: ten platform threads, at most ten tasks.
 * - `ServiceTaskWorkerPool(Executors.newVirtualThreadPerTaskExecutor(), maxConcurrentTasks = 200)`: a virtual thread per task, at most 200 tasks.
 *
 * A delivery reserves free slots without blocking, fetches at most that many tasks and submits them to the slots.
 * One pool is shared by all deliveries of an adapter. Closing the pool shuts down its executor.
 */
class ServiceTaskWorkerPool(
  private val executor: Executor,
  val maxConcurrentTasks: Int,
) : AutoCloseable {

  companion object {
    @JvmStatic
    @JvmOverloads
    fun withPlatformThreads(threads: Int, threadFactory: ThreadFactory = Executors.defaultThreadFactory()) =
      ServiceTaskWorkerPool(Executors.newFixedThreadPool(threads, threadFactory), maxConcurrentTasks = threads)
  }

  init {
    require(maxConcurrentTasks >= 1) { "maxConcurrentTasks must be at least 1, but was $maxConcurrentTasks." }
  }

  private val freeSlots = Semaphore(maxConcurrentTasks)
  private val runningTaskIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

  val runningTaskCount: Int get() = runningTaskIds.size

  val freeSlotCount: Int get() = freeSlots.availablePermits()

  fun isRunning(taskId: String): Boolean = taskId in runningTaskIds

  /**
   * Reserves up to [maxSlots] free slots without waiting. Use it with `use { }`, so unused slots are freed again.
   */
  fun reserve(maxSlots: Int = maxConcurrentTasks): Reservation {
    var reservedSlots = 0
    while (reservedSlots < maxSlots && freeSlots.tryAcquire()) {
      reservedSlots++
    }
    return Reservation(reservedSlots)
  }

  override fun close() {
    when (executor) {
      is ExecutorService -> executor.shutdown()
      is AutoCloseable -> executor.close()
    }
  }

  inner class Reservation internal constructor(val slots: Int) : AutoCloseable {

    private var unusedSlots = slots

    /**
     * Runs [work] in one of the reserved slots.
     * @return `false` if the task is already running or the executor rejected it; the slot stays unused then.
     */
    fun submit(taskId: String, work: () -> Unit): Boolean {
      check(unusedSlots > 0) { "No reserved slot left for task $taskId." }
      val isAlreadyRunning = !runningTaskIds.add(taskId)
      if (isAlreadyRunning) return false
      return try {
        executor.execute { runInSlot(taskId, work) }
        unusedSlots--
        true
      } catch (e: RejectedExecutionException) {
        runningTaskIds.remove(taskId)
        logger.warn { "PROCESS-ENGINE-CIB7-POOL-001: Executor rejected task $taskId: ${e.message}" }
        false
      }
    }

    override fun close() {
      freeSlots.release(unusedSlots)
      unusedSlots = 0
    }
  }

  private fun runInSlot(taskId: String, work: () -> Unit) {
    try {
      work()
    } catch (t: Throwable) {
      logger.error(t) { "PROCESS-ENGINE-CIB7-POOL-002: Unexpected error while executing task $taskId." }
    } finally {
      runningTaskIds.remove(taskId)
      freeSlots.release()
    }
  }
}
