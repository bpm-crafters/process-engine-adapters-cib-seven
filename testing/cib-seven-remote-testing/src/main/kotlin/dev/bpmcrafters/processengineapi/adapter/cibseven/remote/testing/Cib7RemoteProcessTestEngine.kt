package dev.bpmcrafters.processengineapi.adapter.cibseven.remote.testing

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Cib7RemoteProcessTestEngine(
  val url: String = USE_TESTCONTAINERS,
  val image: String = DEFAULT_IMAGE,
) {
  companion object {
    const val USE_TESTCONTAINERS = ""
    const val DEFAULT_IMAGE = "cibseven/cibseven:run-2.2.0"
  }
}
