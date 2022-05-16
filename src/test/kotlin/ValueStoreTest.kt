import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch

internal class ValueStoreTest {

    @Test
    fun store() = runTest {
        val store = ValueStore(initialValue = "❤️")

        val latch = CountDownLatch(2)
        val jobs = mutableListOf<Job>()

        jobs += launch(Dispatchers.Default) {
            store.stream().collectLatest {
                println(it)
                latch.countDown()
            }
        }

        store.set("💛")
        store.set("💚")

        latch.await()
        jobs.map { it.cancelAndJoin() }
    }


}