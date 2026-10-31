package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.task

import dev.bpmcrafters.processengineapi.MetaInfo
import dev.bpmcrafters.processengineapi.MetaInfoAware
import dev.bpmcrafters.processengineapi.impl.task.AbstractTaskSubscriptionApiImpl
import dev.bpmcrafters.processengineapi.impl.task.SubscriptionRepository
import dev.bpmcrafters.processengineapi.task.TaskHandlerInterceptor

/**
 * CIB-Seven remote task subscription implementation.
 * @param subscriptionRepository repository storing the subscriptions.
 * @param interceptors interceptors wrapped around every task handler of a subscription, executed in list order.
 */
class TaskSubscriptionApiImpl(
  subscriptionRepository: SubscriptionRepository,
  interceptors: List<TaskHandlerInterceptor> = emptyList()
) : AbstractTaskSubscriptionApiImpl(
  subscriptionRepository = subscriptionRepository,
  interceptors = interceptors
) {

  override fun meta(instance: MetaInfoAware): MetaInfo {
    TODO("Not yet implemented")
  }
}
