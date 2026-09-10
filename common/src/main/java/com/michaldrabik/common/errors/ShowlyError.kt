package com.michaldrabik.common.errors

sealed class ShowlyError(
  errorMessage: String?,
) : Throwable(errorMessage) {

  class ValidationError : ShowlyError("ValidationError")

  class ResourceConflictError : ShowlyError("ResourceConflictError")

  class ResourceNotFoundError : ShowlyError("ResourceNotFoundError")

  class AccountLockedError : ShowlyError("AccountLockedError")

  class AccountLimitsError : ShowlyError("AccountLimitsError")

  data class UnauthorizedError(
    val errorMessage: String?,
  ) : ShowlyError(errorMessage)

  data class UnknownHttpError(
    val errorMessage: String?,
  ) : ShowlyError(errorMessage)

  data class UnknownError(
    val errorMessage: String?,
  ) : ShowlyError(errorMessage)

  class CoroutineCancellation : ShowlyError("")
}
