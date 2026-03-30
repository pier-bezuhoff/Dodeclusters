package domain

sealed interface Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>
    data class Right<R>(val value: R) : Either<R, Nothing>
}

inline fun <reified L, reified R, T> Either<L, R>.fold(
    crossinline onLeft: (L) -> T,
    crossinline onRight: (R) -> T,
): T = when (this) {
    is Either.Left<*> -> onLeft(this.value as L)
    is Either.Right<*> -> onRight(this.value as R)
}