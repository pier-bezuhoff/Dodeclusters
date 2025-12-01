package domain

/**
 * ```
 * Lens S T A B =
 * Functor F => (A -> F B) -> S -> F T
 * ```
 *
 * here S == T, F == Id, so
 *
 * ```
 * Lens<S, A, B> = (A -> B) -> S -> S
 * ```
 */
interface Lens<S, A, B> {
    // id : A -> F B (== A -> A)
    fun get(s: S): A
    // const b : A -> F B (== A -> B)
    fun set(s: S, b: B): S
}

