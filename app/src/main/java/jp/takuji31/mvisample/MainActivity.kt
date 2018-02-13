package jp.takuji31.mvisample

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {


    val viewModel: MainViewModel by lazy {
        MainViewModel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bind()
    }

    private fun bind() {
        viewModel.state.subscribe(this::render)
        viewModel.processIntent(intents())
    }

    fun intents(): Observable<MainIntent> {
        return Observable.merge(
            initialIntent(),
            helloIntent()
        )
    }

    private fun initialIntent(): Observable<MainIntent.InitialIntent> {
        return Observable.just(MainIntent.InitialIntent)
    }

    private fun helloIntent(): Observable<MainIntent.HelloIntent> {
        return RxView.clicks(button)
            .map { MainIntent.HelloIntent(editText.text.toString()) }
    }

    private fun render(viewState: MainViewState) {
        textView.text = viewState.helloMessage
    }
}

class MainViewModel {
    private val intentSubject: PublishSubject<MainIntent> = PublishSubject.create()

    fun processIntent(intents: Observable<MainIntent>) {
        intents.subscribe(intentSubject)
    }

    val state: Observable<MainViewState> = compose()

    private fun compose(): Observable<MainViewState> {
        return intentSubject
            .compose {
                it.publish {
                    Observable.merge(
                        it.ofType(MainIntent.InitialIntent::class.java).take(1),
                        it.ofType(MainIntent.HelloIntent::class.java)
                    )
                }
            }
            .map(this::actionFromIntent)
            .map {
                return@map when (it) {
                    is MainAction.CreateHelloMessageAction -> {
                        // Repositoryなどを使って処理をするべき
                        if (it.name.isNotEmpty()) {
                            MainTaskResult.CreateHelloMessageResult.Created("Hello ${it.name}")
                        } else {
                            MainTaskResult.CreateHelloMessageResult.Failure(
                                IllegalArgumentException(
                                    "Name is empty!"
                                )
                            )
                        }
                    }
                }
            }
            .scan(MainViewState.idle(), BiFunction { previousState, result ->
                return@BiFunction when (result) {
                    is MainTaskResult.CreateHelloMessageResult.Created -> {
                        previousState.copy(helloMessage = result.message)
                    }
                    is MainTaskResult.CreateHelloMessageResult.Failure -> {
                        previousState.copy(helloMessage = result.error.message!!)
                    }
                }
            })
            .replay(1)
            .autoConnect(0)
    }

    private fun actionFromIntent(intent: MainIntent): MainAction {
        return when (intent) {
            MainIntent.InitialIntent -> MainAction.CreateHelloMessageAction("DroidKaigi")
            is MainIntent.HelloIntent -> MainAction.CreateHelloMessageAction(intent.name)
        }
    }
}

sealed class MainIntent {
    object InitialIntent : MainIntent()
    data class HelloIntent(val name: String) : MainIntent()
}

sealed class MainAction {
    data class CreateHelloMessageAction(val name: String) : MainAction()
}

sealed class MainTaskResult {
    sealed class CreateHelloMessageResult : MainTaskResult() {
        data class Created(val message: String) : CreateHelloMessageResult()
        data class Failure(val error: Throwable) : CreateHelloMessageResult()
    }
}

data class MainViewState(
    val helloMessage: String
) {
    companion object {
        fun idle(): MainViewState {
            return MainViewState("")
        }
    }
}