package fr.coppernic.samples.pcsc.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.function.BiConsumer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class Coroutines {
    @JvmOverloads
    fun <R> getContinuation(onFinished: BiConsumer<R?, Throwable?>, dispatcher: CoroutineDispatcher = Dispatchers.Default): Continuation<R> {
        return object : Continuation<R> {
            override val context: CoroutineContext
                get() = dispatcher

            override fun resumeWith(result: Result<R>) {
                onFinished.accept(result.getOrNull(), result.exceptionOrNull())
            }
        }
    }
}
