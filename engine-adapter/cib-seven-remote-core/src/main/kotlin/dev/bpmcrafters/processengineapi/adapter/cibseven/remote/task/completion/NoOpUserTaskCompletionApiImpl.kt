package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task.completion

import dev.bpmcrafters.processengineapi.Empty
import dev.bpmcrafters.processengineapi.task.CompleteTaskByErrorCmd
import dev.bpmcrafters.processengineapi.task.CompleteTaskCmd
import dev.bpmcrafters.processengineapi.task.UserTaskCompletionApi
import java.util.concurrent.CompletableFuture

class NoOpUserTaskCompletionApiImpl : UserTaskCompletionApi {

  override fun completeTask(cmd: CompleteTaskCmd): CompletableFuture<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }

  override fun completeTaskByError(cmd: CompleteTaskByErrorCmd): CompletableFuture<Empty> {
    return CompletableFuture.completedFuture(Empty)
  }
}
